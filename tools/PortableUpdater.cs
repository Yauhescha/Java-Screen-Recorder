using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

internal static class PortableUpdater
{
    // Replaced by scripts/build-portable-updater.ps1 before compilation.
    private const string ManifestUrl = "@@MANIFEST_URL@@";
    private const string AppExeName = "JavaScreenRecorder.exe";

    [StructLayout(LayoutKind.Sequential)]
    private struct ProcessBasicInformation
    {
        public IntPtr Reserved1;
        public IntPtr PebBaseAddress;
        public IntPtr Reserved2_0;
        public IntPtr Reserved2_1;
        public IntPtr UniqueProcessId;
        public IntPtr InheritedFromUniqueProcessId;
    }

    [DllImport("ntdll.dll")]
    private static extern int NtQueryInformationProcess(
        IntPtr processHandle,
        int processInformationClass,
        ref ProcessBasicInformation processInformation,
        int processInformationLength,
        out int returnLength);

    [STAThread]
    private static void Main()
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        ServicePointManager.SecurityProtocol |= SecurityProtocolType.Tls12;

        int parentPid;
        string targetDirectory = ResolveTargetDirectory(out parentPid);
        string safeWorkingDirectory = Path.Combine(Path.GetTempPath(), "JavaScreenRecorderUpdater");
        Directory.CreateDirectory(safeWorkingDirectory);

        // Critical for portable updates: a Windows process whose current working
        // directory is the application directory can prevent that directory from
        // being renamed. Older recorder versions launched the updater from their
        // own directory, so release that directory handle immediately.
        Environment.CurrentDirectory = safeWorkingDirectory;

        using (var form = new UpdateForm())
        {
            form.Shown += async delegate
            {
                try
                {
                    await Task.Run(() => Install(targetDirectory, parentPid, form));
                    form.SafeClose();
                }
                catch (Exception ex)
                {
                    string details = ex.ToString();
                    TryWriteFailureLog(details);
                    TryRestoreAndRestart(targetDirectory);
                    form.SafeClose();
                    ShowDarkError("Update failed",
                        "Java Screen Recorder could not be updated. The existing version was left in place or restored.",
                        details);
                }
            };
            Application.Run(form);
        }
    }

    private static void Install(string targetDirectory, int parentPid, UpdateForm form)
    {
        if (string.IsNullOrWhiteSpace(targetDirectory) || !File.Exists(Path.Combine(targetDirectory, AppExeName)))
            throw new InvalidOperationException("Could not determine the current Java Screen Recorder directory.");

        form.SetStatus("Waiting for Java Screen Recorder to close...");
        WaitForProcess(parentPid, TimeSpan.FromSeconds(30));
        WaitForProcessesInsideDirectory(targetDirectory, TimeSpan.FromSeconds(20));

        form.SetStatus("Reading update information...");
        string manifestText;
        using (var wc = NewWebClient())
            manifestText = wc.DownloadString(ManifestUrl);

        var manifest = ParseJsonStrings(manifestText);
        string payloadUrl = Require(manifest, "payloadUrl");
        string payloadSha = NormalizeHash(Require(manifest, "payloadSha256"));
        string version = manifest.ContainsKey("version") ? manifest["version"] : "latest";
        string entryExe = manifest.ContainsKey("payloadEntryExe")
            ? manifest["payloadEntryExe"]
            : "JavaScreenRecorder/JavaScreenRecorder.exe";

        string tempRoot = Path.Combine(Path.GetTempPath(), "JavaScreenRecorder-update-" + version + "-" + Guid.NewGuid().ToString("N"));
        string zipPath = Path.Combine(tempRoot, "package.zip");
        string extractRoot = Path.Combine(tempRoot, "extracted");
        Directory.CreateDirectory(tempRoot);
        Directory.CreateDirectory(extractRoot);

        try
        {
            form.SetStatus("Downloading Java Screen Recorder " + version + "...");
            using (var wc = NewWebClient())
                wc.DownloadFile(payloadUrl, zipPath);

            form.SetStatus("Verifying download...");
            string actualHash = Sha256(zipPath);
            if (!string.Equals(actualHash, payloadSha, StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException("Downloaded package SHA-256 does not match update.json.");

            form.SetStatus("Unpacking update...");
            ExtractZipSafely(zipPath, extractRoot);

            string stagedExe = Path.GetFullPath(Path.Combine(extractRoot, entryExe.Replace('/', Path.DirectorySeparatorChar)));
            string extractPrefix = EnsureTrailingSeparator(Path.GetFullPath(extractRoot));
            if (!stagedExe.StartsWith(extractPrefix, StringComparison.OrdinalIgnoreCase) || !File.Exists(stagedExe))
                throw new InvalidDataException("The update archive does not contain " + entryExe);

            string stagedApp = Path.GetDirectoryName(stagedExe);
            string parentDirectory = Directory.GetParent(targetDirectory).FullName;
            string backupDirectory = targetDirectory + ".update-backup";

            form.SetStatus("Replacing application files...");
            DeleteDirectoryWithRetry(backupDirectory, 8, 300);
            MoveDirectoryWithRetry(targetDirectory, backupDirectory, 60, 500);

            bool installed = false;
            try
            {
                Directory.CreateDirectory(targetDirectory);
                CopyDirectory(stagedApp, targetDirectory);
                string newExe = Path.Combine(targetDirectory, AppExeName);
                if (!File.Exists(newExe))
                    throw new FileNotFoundException("Updated executable is missing.", newExe);

                form.SetStatus("Starting updated version...");
                var start = new ProcessStartInfo(newExe)
                {
                    WorkingDirectory = targetDirectory,
                    UseShellExecute = true
                };
                Process newProcess = Process.Start(start);
                Thread.Sleep(2500);
                if (newProcess != null && newProcess.HasExited)
                    throw new InvalidOperationException("The updated application exited immediately after launch.");

                installed = true;
            }
            finally
            {
                if (!installed)
                {
                    try { if (Directory.Exists(targetDirectory)) Directory.Delete(targetDirectory, true); } catch { }
                    if (Directory.Exists(backupDirectory))
                        MoveDirectoryWithRetry(backupDirectory, targetDirectory, 20, 300);
                }
            }

            // Backup cleanup is best effort. Leaving it is safer than turning a
            // successful update into a failure because antivirus still scans it.
            try { DeleteDirectoryWithRetry(backupDirectory, 5, 300); } catch { }
            TryDeleteFailureLog();
        }
        finally
        {
            try { if (Directory.Exists(tempRoot)) Directory.Delete(tempRoot, true); } catch { }
        }
    }

    private static WebClient NewWebClient()
    {
        var wc = new WebClient();
        wc.Headers[HttpRequestHeader.UserAgent] = "JavaScreenRecorder-Updater";
        return wc;
    }

    private static string ResolveTargetDirectory(out int parentPid)
    {
        parentPid = GetParentPid();

        string explicitTarget = Environment.GetEnvironmentVariable("JSR_UPDATE_TARGET");
        if (!string.IsNullOrWhiteSpace(explicitTarget) && File.Exists(Path.Combine(explicitTarget, AppExeName)))
            return Path.GetFullPath(explicitTarget);

        if (parentPid > 0)
        {
            try
            {
                using (Process parent = Process.GetProcessById(parentPid))
                {
                    string parentExe = parent.MainModule.FileName;
                    if (string.Equals(Path.GetFileName(parentExe), AppExeName, StringComparison.OrdinalIgnoreCase))
                        return Path.GetDirectoryName(parentExe);
                }
            }
            catch { }
        }

        string current = Environment.CurrentDirectory;
        if (File.Exists(Path.Combine(current, AppExeName)))
            return Path.GetFullPath(current);

        throw new InvalidOperationException(
            "The updater could not locate JavaScreenRecorder.exe. Please download the new ZIP from GitHub Releases and replace the application folder manually.");
    }

    private static int GetParentPid()
    {
        try
        {
            var pbi = new ProcessBasicInformation();
            int returnLength;
            int status = NtQueryInformationProcess(Process.GetCurrentProcess().Handle, 0,
                ref pbi, Marshal.SizeOf(typeof(ProcessBasicInformation)), out returnLength);
            if (status == 0)
                return pbi.InheritedFromUniqueProcessId.ToInt32();
        }
        catch { }
        return -1;
    }

    private static void WaitForProcess(int pid, TimeSpan timeout)
    {
        if (pid <= 0) return;
        try
        {
            using (Process process = Process.GetProcessById(pid))
                process.WaitForExit((int)timeout.TotalMilliseconds);
        }
        catch { }
    }

    private static void WaitForProcessesInsideDirectory(string directory, TimeSpan timeout)
    {
        DateTime end = DateTime.UtcNow + timeout;
        string prefix = EnsureTrailingSeparator(Path.GetFullPath(directory));
        int currentPid = Process.GetCurrentProcess().Id;

        while (DateTime.UtcNow < end)
        {
            bool found = false;
            foreach (Process process in Process.GetProcesses())
            {
                try
                {
                    if (process.Id == currentPid) continue;
                    string exe = process.MainModule.FileName;
                    if (!string.IsNullOrEmpty(exe) && Path.GetFullPath(exe).StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
                    {
                        found = true;
                        break;
                    }
                }
                catch { }
                finally { process.Dispose(); }
            }
            if (!found) return;
            Thread.Sleep(250);
        }
    }

    private static void MoveDirectoryWithRetry(string source, string destination, int attempts, int delayMs)
    {
        Exception last = null;
        for (int i = 0; i < attempts; i++)
        {
            try
            {
                if (!Directory.Exists(source))
                    throw new DirectoryNotFoundException(source);
                if (Directory.Exists(destination)) Directory.Delete(destination, true);
                Directory.Move(source, destination);
                return;
            }
            catch (Exception ex)
            {
                last = ex;
                Thread.Sleep(delayMs);
            }
        }
        throw new IOException("Could not move application directory after multiple retries: " + source, last);
    }

    private static void DeleteDirectoryWithRetry(string path, int attempts, int delayMs)
    {
        if (!Directory.Exists(path)) return;
        Exception last = null;
        for (int i = 0; i < attempts; i++)
        {
            try
            {
                Directory.Delete(path, true);
                return;
            }
            catch (Exception ex)
            {
                last = ex;
                Thread.Sleep(delayMs);
            }
        }
        if (Directory.Exists(path)) throw new IOException("Could not remove directory: " + path, last);
    }

    private static void CopyDirectory(string source, string destination)
    {
        Directory.CreateDirectory(destination);
        foreach (string directory in Directory.GetDirectories(source, "*", SearchOption.AllDirectories))
        {
            string relative = directory.Substring(source.Length).TrimStart(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
            Directory.CreateDirectory(Path.Combine(destination, relative));
        }
        foreach (string file in Directory.GetFiles(source, "*", SearchOption.AllDirectories))
        {
            string relative = file.Substring(source.Length).TrimStart(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
            string target = Path.Combine(destination, relative);
            Directory.CreateDirectory(Path.GetDirectoryName(target));
            File.Copy(file, target, true);
        }
    }

    private static void ExtractZipSafely(string zipPath, string destination)
    {
        string root = EnsureTrailingSeparator(Path.GetFullPath(destination));
        using (ZipArchive archive = ZipFile.OpenRead(zipPath))
        {
            foreach (ZipArchiveEntry entry in archive.Entries)
            {
                string normalized = entry.FullName.Replace('/', Path.DirectorySeparatorChar).Replace('\\', Path.DirectorySeparatorChar);
                if (string.IsNullOrWhiteSpace(normalized)) continue;
                string output = Path.GetFullPath(Path.Combine(destination, normalized));
                if (!output.StartsWith(root, StringComparison.OrdinalIgnoreCase))
                    throw new InvalidDataException("Unsafe path in update ZIP: " + entry.FullName);

                if (entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\") || string.IsNullOrEmpty(entry.Name))
                {
                    Directory.CreateDirectory(output);
                    continue;
                }

                string parent = Path.GetDirectoryName(output);
                if (!string.IsNullOrEmpty(parent)) Directory.CreateDirectory(parent);
                using (Stream input = entry.Open())
                using (FileStream outputStream = new FileStream(output, FileMode.Create, FileAccess.Write, FileShare.None))
                    input.CopyTo(outputStream);
            }
        }
    }

    private static Dictionary<string, string> ParseJsonStrings(string json)
    {
        var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (Match match in Regex.Matches(json ?? "", "\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\""))
        {
            string value = match.Groups[2].Value
                .Replace("\\n", "\n")
                .Replace("\\r", "\r")
                .Replace("\\t", "\t")
                .Replace("\\\"", "\"")
                .Replace("\\\\", "\\");
            result[match.Groups[1].Value] = value;
        }
        return result;
    }

    private static string Require(Dictionary<string, string> values, string key)
    {
        string value;
        if (!values.TryGetValue(key, out value) || string.IsNullOrWhiteSpace(value))
            throw new InvalidDataException("update.json is missing required field: " + key);
        return value;
    }

    private static string Sha256(string path)
    {
        using (SHA256 sha = SHA256.Create())
        using (FileStream stream = File.OpenRead(path))
        {
            byte[] hash = sha.ComputeHash(stream);
            var sb = new StringBuilder(hash.Length * 2);
            foreach (byte b in hash) sb.Append(b.ToString("X2"));
            return sb.ToString();
        }
    }

    private static string NormalizeHash(string value)
    {
        return Regex.Replace(value ?? "", "[^0-9A-Fa-f]", "").ToUpperInvariant();
    }

    private static string EnsureTrailingSeparator(string path)
    {
        if (path.EndsWith(Path.DirectorySeparatorChar.ToString())) return path;
        return path + Path.DirectorySeparatorChar;
    }

    private static string FailureLogPath
    {
        get { return Path.Combine(Path.GetTempPath(), "JavaScreenRecorder-update-error.txt"); }
    }

    private static void TryWriteFailureLog(string details)
    {
        try { File.WriteAllText(FailureLogPath, details ?? "Unknown updater error", new UTF8Encoding(false)); } catch { }
    }

    private static void TryDeleteFailureLog()
    {
        try { if (File.Exists(FailureLogPath)) File.Delete(FailureLogPath); } catch { }
    }

    private static void TryRestoreAndRestart(string targetDirectory)
    {
        try
        {
            string backup = targetDirectory + ".update-backup";
            if (!Directory.Exists(targetDirectory) && Directory.Exists(backup))
                Directory.Move(backup, targetDirectory);
            string exe = Path.Combine(targetDirectory, AppExeName);
            if (File.Exists(exe))
                Process.Start(new ProcessStartInfo(exe) { WorkingDirectory = targetDirectory, UseShellExecute = true });
        }
        catch { }
    }

    private static void ShowDarkError(string title, string summary, string details)
    {
        using (var form = new Form())
        {
            form.Text = title;
            form.BackColor = Color.FromArgb(18, 21, 27);
            form.ForeColor = Color.FromArgb(236, 239, 244);
            form.StartPosition = FormStartPosition.CenterScreen;
            form.Size = new Size(720, 390);
            form.MinimizeBox = false;
            form.MaximizeBox = false;
            form.FormBorderStyle = FormBorderStyle.FixedDialog;

            var heading = new Label
            {
                Text = title,
                ForeColor = Color.FromArgb(232, 68, 73),
                Font = new Font("Segoe UI", 12F, FontStyle.Bold),
                AutoSize = true,
                Location = new Point(18, 18)
            };
            var message = new Label
            {
                Text = summary,
                ForeColor = Color.FromArgb(236, 239, 244),
                Font = new Font("Segoe UI", 10F),
                AutoSize = false,
                Location = new Point(18, 52),
                Size = new Size(670, 48)
            };
            var detailBox = new TextBox
            {
                Multiline = true,
                ReadOnly = true,
                ScrollBars = ScrollBars.Vertical,
                Text = details ?? "",
                BackColor = Color.FromArgb(34, 39, 49),
                ForeColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle,
                Font = new Font("Consolas", 9F),
                Location = new Point(18, 110),
                Size = new Size(670, 190)
            };
            var ok = new Button
            {
                Text = "OK",
                BackColor = Color.FromArgb(65, 145, 255),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Location = new Point(598, 316),
                Size = new Size(90, 32)
            };
            ok.FlatAppearance.BorderColor = Color.FromArgb(86, 161, 255);
            ok.Click += delegate { form.Close(); };

            form.Controls.Add(heading);
            form.Controls.Add(message);
            form.Controls.Add(detailBox);
            form.Controls.Add(ok);
            form.AcceptButton = ok;
            form.ShowDialog();
        }
    }

    private sealed class UpdateForm : Form
    {
        private readonly Label status;
        private readonly ProgressBar progress;

        public UpdateForm()
        {
            Text = "Java Screen Recorder update";
            BackColor = Color.FromArgb(18, 21, 27);
            ForeColor = Color.FromArgb(236, 239, 244);
            StartPosition = FormStartPosition.CenterScreen;
            Size = new Size(540, 170);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MinimizeBox = false;
            MaximizeBox = false;
            ControlBox = false;

            var title = new Label
            {
                Text = "Updating Java Screen Recorder",
                ForeColor = Color.White,
                Font = new Font("Segoe UI", 12F, FontStyle.Bold),
                AutoSize = true,
                Location = new Point(20, 18)
            };
            status = new Label
            {
                Text = "Preparing update...",
                ForeColor = Color.FromArgb(207, 214, 226),
                Font = new Font("Segoe UI", 9.5F),
                AutoSize = false,
                Location = new Point(20, 55),
                Size = new Size(485, 25)
            };
            progress = new ProgressBar
            {
                Style = ProgressBarStyle.Marquee,
                MarqueeAnimationSpeed = 30,
                Location = new Point(20, 91),
                Size = new Size(485, 18)
            };
            Controls.Add(title);
            Controls.Add(status);
            Controls.Add(progress);
        }

        public void SetStatus(string text)
        {
            if (IsDisposed) return;
            if (InvokeRequired)
            {
                try { BeginInvoke(new Action<string>(SetStatus), text); } catch { }
                return;
            }
            status.Text = text;
        }

        public void SafeClose()
        {
            if (IsDisposed) return;
            if (InvokeRequired)
            {
                try { BeginInvoke(new Action(SafeClose)); } catch { }
                return;
            }
            Close();
        }
    }
}
