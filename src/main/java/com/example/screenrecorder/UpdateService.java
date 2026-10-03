package com.example.screenrecorder;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Lightweight update client backed by a release manifest hosted over HTTPS. */
final class UpdateService {
    private static final Pattern FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    static String consumePreviousUpdateFailure() {
        Path failureLog = Path.of(System.getProperty("java.io.tmpdir"), "JavaScreenRecorder-update-error.txt");
        if (!Files.isRegularFile(failureLog)) return null;

        try {
            String text = Files.readString(failureLog, StandardCharsets.UTF_8).trim();
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
                text = text.substring(1).trim();
            }
            Files.deleteIfExists(failureLog);
            return text.isBlank() ? "Unknown file replacement error." : text;
        } catch (Exception ex) {
            try { Files.deleteIfExists(failureLog); } catch (Exception ignored) {}
            return "Could not read the previous update error: " + ex.getMessage();
        }
    }

    boolean isConfigured() {
        return !UpdateConfig.manifestUrl().isBlank();
    }

    CompletableFuture<UpdateInfo> check() {
        String url = UpdateConfig.manifestUrl();
        if (url.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Update source is not configured. Set JSR_UPDATE_URL or build with -UpdateManifestUrl."));
        }
        URI manifestUri = URI.create(url);
        requireHttps(manifestUri, "Update manifest");
        HttpRequest request = HttpRequest.newBuilder(manifestUri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", AppVersion.NAME.replace(' ', '-') + "/" + AppVersion.VERSION)
                .GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Update server returned HTTP " + response.statusCode());
                    }
                    UpdateInfo info = parse(response.body());
                    if (info.version() == null || info.version().isBlank()
                            || info.packageUrl() == null || info.packageUrl().isBlank()
                            || info.sha256() == null || info.sha256().isBlank()) {
                        throw new IllegalStateException("Invalid update manifest: version, packageUrl and sha256 are required.");
                    }
                    return info;
                });
    }

    boolean isNewer(String candidate) {
        return compareVersions(candidate, AppVersion.VERSION) > 0;
    }

    void downloadAndLaunch(Component owner, UpdateInfo info, Consumer<String> log, Runnable beforeExit) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Installing update", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.getContentPane().setBackground(AppTheme.BG);
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBackground(AppTheme.BG);
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        JLabel label = new JLabel("Downloading Java Screen Recorder " + info.version() + "…");
        label.setForeground(AppTheme.TEXT);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        content.add(label, BorderLayout.NORTH);
        content.add(bar, BorderLayout.CENTER);
        dialog.setContentPane(content);
        dialog.setSize(460, 135);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        dialog.addNotify();
        WindowsWindowStyler.apply(dialog);
        var dialogIcon = AppIcon.load();
        if (dialogIcon != null) dialog.setIconImage(dialogIcon);

        SwingWorker<Path, Void> worker = new SwingWorker<>() {
            @Override protected Path doInBackground() throws Exception {
                URI packageUri = URI.create(info.packageUrl());
                requireHttps(packageUri, "Update package");
                HttpRequest request = HttpRequest.newBuilder(packageUri)
                        .timeout(Duration.ofMinutes(10))
                        .header("User-Agent", AppVersion.NAME.replace(' ', '-') + "/" + AppVersion.VERSION)
                        .GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("Update download returned HTTP " + response.statusCode());
                }

                String type = normalizedType(info);
                String suffix = "zip".equals(type) ? ".zip" : ".exe";
                Path target = Files.createTempFile("JavaScreenRecorder-" + info.version() + "-", suffix);
                try (InputStream in = response.body()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                String actual = sha256(target);
                String expected = info.sha256().replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
                if (!actual.equals(expected)) {
                    Files.deleteIfExists(target);
                    throw new SecurityException("Update SHA-256 mismatch. The downloaded package was discarded.");
                }
                return target;
            }

            @Override protected void done() {
                dialog.dispose();
                try {
                    Path packageFile = get();
                    log.accept("Update package verified: " + packageFile);
                    if ("zip".equals(normalizedType(info))) {
                        launchPortableZipUpdater(packageFile, info, log);
                    } else {
                        new ProcessBuilder(packageFile.toAbsolutePath().toString()).start();
                    }
                    beforeExit.run();
                } catch (Exception e) {
                    Throwable cause = rootCause(e);
                    String technical = cause.getClass().getSimpleName() + ": " +
                            (cause.getMessage() == null ? cause.toString() : cause.getMessage());
                    log.accept("Update failed: " + technical);
                    DarkDialogs.error(owner,
                            "Update failed",
                            friendlyUpdateFailure(cause),
                            technical + "\n\nThe current application was not replaced.");
                }
            }
        };
        worker.execute();
        dialog.setVisible(true);
    }

    private static void launchPortableZipUpdater(Path zip, UpdateInfo info, Consumer<String> log) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            throw new IllegalStateException("Portable automatic replacement is currently supported on Windows only.");
        }

        Path staging = Files.createTempDirectory("jsr-update-" + info.version() + "-");
        try {
            extractZip(zip, staging);
        } catch (Exception ex) {
            try { deleteRecursively(staging); } catch (Exception ignored) {}
            throw new IllegalStateException(
                    "The downloaded update is valid, but its ZIP package could not be unpacked. " +
                    "No application files were changed.", ex);
        }

        String entry = info.entryExe() == null || info.entryExe().isBlank()
                ? "JavaScreenRecorder/JavaScreenRecorder.exe"
                : info.entryExe().replace('\\', '/');
        Path stagedExe = staging.resolve(entry).normalize();
        if (!stagedExe.startsWith(staging) || !Files.isRegularFile(stagedExe)) {
            throw new IllegalStateException("The update archive does not contain " + entry);
        }
        Path stagedApp = stagedExe.getParent();
        Path currentApp = AppPaths.applicationDirectory().toAbsolutePath().normalize();
        Path currentExe = currentApp.resolve("JavaScreenRecorder.exe");
        if (!Files.isRegularFile(currentExe)) {
            throw new IllegalStateException("Automatic ZIP update requires the packaged JavaScreenRecorder.exe application image.");
        }

        long pid = ProcessHandle.current().pid();
        Path script = Files.createTempFile("jsr-update-", ".ps1");
        String ps = portableUpdateScript(pid, stagedApp, currentApp);
        Files.writeString(script, ps, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        log.accept("Portable updater prepared. The application will restart after files are replaced.");

        new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-File", script.toAbsolutePath().toString())
                .start();
    }

    private static String portableUpdateScript(long pid, Path source, Path target) {
        String src = psQuote(source.toAbsolutePath().toString());
        String dst = psQuote(target.toAbsolutePath().toString());
        String name = target.getFileName().toString();
        String backup = psQuote(target.toAbsolutePath().getParent().resolve(name + ".update-backup").toString());
        String exe = psQuote(target.toAbsolutePath().resolve("JavaScreenRecorder.exe").toString());
        String failureLog = psQuote(Path.of(System.getProperty("java.io.tmpdir"),
                "JavaScreenRecorder-update-error.txt").toAbsolutePath().toString());

        return "$ErrorActionPreference = 'Stop'\r\n" +
                "$pidToWait = " + pid + "\r\n" +
                "$source = '" + src + "'\r\n" +
                "$target = '" + dst + "'\r\n" +
                "$backup = '" + backup + "'\r\n" +
                "$exe = '" + exe + "'\r\n" +
                "$failureLog = '" + failureLog + "'\r\n" +
                "try { Wait-Process -Id $pidToWait -ErrorAction SilentlyContinue } catch {}\r\n" +
                "Start-Sleep -Milliseconds 800\r\n" +
                "try {\r\n" +
                "  if (Test-Path -LiteralPath $backup) { Remove-Item -LiteralPath $backup -Recurse -Force }\r\n" +
                "  $moved = $false\r\n" +
                "  for ($i = 0; $i -lt 20 -and -not $moved; $i++) {\r\n" +
                "    try {\r\n" +
                "      Move-Item -LiteralPath $target -Destination $backup -Force\r\n" +
                "      $moved = $true\r\n" +
                "    } catch {\r\n" +
                "      if ($i -ge 19) { throw }\r\n" +
                "      Start-Sleep -Milliseconds 500\r\n" +
                "    }\r\n" +
                "  }\r\n" +
                "  New-Item -ItemType Directory -Path $target -Force | Out-Null\r\n" +
                "  Copy-Item -Path (Join-Path $source '*') -Destination $target -Recurse -Force\r\n" +
                "  if (-not (Test-Path -LiteralPath $exe)) { throw 'Updated executable is missing.' }\r\n" +
                "  Remove-Item -LiteralPath $failureLog -Force -ErrorAction SilentlyContinue\r\n" +
                "  Start-Process -FilePath $exe\r\n" +
                "  Start-Sleep -Seconds 3\r\n" +
                "  Remove-Item -LiteralPath $backup -Recurse -Force -ErrorAction SilentlyContinue\r\n" +
                "} catch {\r\n" +
                "  $message = $_.Exception.Message\r\n" +
                "  try { Set-Content -LiteralPath $failureLog -Value $message -Encoding UTF8 } catch {}\r\n" +
                "  if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force -ErrorAction SilentlyContinue }\r\n" +
                "  if (Test-Path -LiteralPath $backup) { Move-Item -LiteralPath $backup -Destination $target -Force }\r\n" +
                "  if (Test-Path -LiteralPath $exe) { Start-Process -FilePath $exe }\r\n" +
                "}\r\n";
    }

    private static void extractZip(Path zip, Path destination) throws IOException {
        Files.createDirectories(destination);

        try (ZipFile archive = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            List<? extends ZipEntry> entries = Collections.list(archive.entries());
            Set<String> inferredDirectories = new HashSet<>();

            // Infer directories from parent paths as well as ZipEntry flags. Some
            // PowerShell-created ZIPs contain directory entries without a trailing '/',
            // which makes ZipEntry.isDirectory() return false.
            for (ZipEntry entry : entries) {
                String normalized = normalizeZipName(entry.getName());
                if (normalized.isBlank()) continue;

                if (entry.isDirectory() || entry.getName().endsWith("/") || entry.getName().endsWith("\\")) {
                    inferredDirectories.add(stripTrailingSlash(normalized));
                }

                int slash = normalized.lastIndexOf('/');
                while (slash > 0) {
                    inferredDirectories.add(normalized.substring(0, slash));
                    slash = normalized.lastIndexOf('/', slash - 1);
                }
            }

            List<String> dirs = new ArrayList<>(inferredDirectories);
            dirs.sort(Comparator.comparingInt(String::length));
            for (String dir : dirs) {
                Path out = safeZipPath(destination, dir);
                Files.createDirectories(out);
            }

            for (ZipEntry entry : entries) {
                String normalized = normalizeZipName(entry.getName());
                if (normalized.isBlank()) continue;

                String noSlash = stripTrailingSlash(normalized);
                boolean directoryEntry = entry.isDirectory()
                        || entry.getName().endsWith("/")
                        || entry.getName().endsWith("\\")
                        || inferredDirectories.contains(noSlash) && hasChildren(entries, noSlash);

                if (directoryEntry) {
                    Files.createDirectories(safeZipPath(destination, noSlash));
                    continue;
                }

                Path out = safeZipPath(destination, normalized);
                if (out.getParent() != null) Files.createDirectories(out.getParent());

                if (Files.isDirectory(out)) {
                    throw new IOException("ZIP entry is a file but the destination is a directory: " + normalized);
                }

                try (InputStream in = new BufferedInputStream(archive.getInputStream(entry))) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static boolean hasChildren(List<? extends ZipEntry> entries, String parent) {
        String prefix = parent.endsWith("/") ? parent : parent + "/";
        for (ZipEntry other : entries) {
            String n = normalizeZipName(other.getName());
            if (n.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String normalizeZipName(String name) {
        if (name == null) return "";
        String normalized = name.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static Path safeZipPath(Path destination, String name) throws IOException {
        Path out = destination.resolve(name).normalize();
        if (!out.startsWith(destination)) {
            throw new IOException("Unsafe path in update ZIP: " + name);
        }
        return out;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path p : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static String normalizedType(UpdateInfo info) {
        String type = info.packageType() == null ? "" : info.packageType().trim().toLowerCase(Locale.ROOT);
        if (type.isBlank()) {
            String url = info.packageUrl() == null ? "" : info.packageUrl().toLowerCase(Locale.ROOT);
            return url.endsWith(".zip") ? "zip" : "exe";
        }
        if (!type.equals("zip") && !type.equals("exe")) {
            throw new IllegalArgumentException("Unsupported update package type: " + type);
        }
        return type;
    }

    private static UpdateInfo parse(String json) {
        String version = null, packageUrl = null, packageType = null, sha256 = null, notes = "", entryExe = null;
        String legacyInstallerUrl = null;
        Matcher matcher = FIELD.matcher(json == null ? "" : json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = unescape(matcher.group(2));
            switch (key) {
                case "version" -> version = value;
                case "packageUrl" -> packageUrl = value;
                case "packageType" -> packageType = value;
                case "installerUrl" -> legacyInstallerUrl = value;
                case "sha256" -> sha256 = value;
                case "notes" -> notes = value;
                case "entryExe" -> entryExe = value;
            }
        }
        if ((packageUrl == null || packageUrl.isBlank()) && legacyInstallerUrl != null) {
            packageUrl = legacyInstallerUrl;
            packageType = "exe";
        }
        return new UpdateInfo(version, packageUrl, packageType, sha256, notes, entryExe);
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\r", "\r")
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1024 * 128];
            int n;
            while ((n = in.read(buffer)) > 0) digest.update(buffer, 0, n);
        }
        return java.util.HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    private static String psQuote(String value) {
        return value.replace("'", "''");
    }

    private static void requireHttps(URI uri, String what) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(what + " URL must use HTTPS.");
        }
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String friendlyUpdateFailure(Throwable cause) {
        if (cause == null) {
            return "The update could not be installed. The current version was not changed.";
        }

        String raw = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        String lower = raw.toLowerCase(Locale.ROOT);

        if (cause instanceof FileAlreadyExistsException
                || lower.contains("could not be unpacked")
                || lower.contains("zip entry")
                || lower.contains("java.base")) {
            return "The update was downloaded and verified, but Windows could not unpack the application package. "
                    + "The current version was not changed. This build includes a compatibility fix for future ZIP updates.";
        }

        if (cause instanceof AccessDeniedException || lower.contains("access is denied")) {
            return "Windows denied access while updating the application. Close any open recorder/FFmpeg processes "
                    + "and make sure the application folder is writable.";
        }

        if (lower.contains("sha-256") || lower.contains("sha256")) {
            return "The downloaded update did not pass its integrity check, so it was discarded.";
        }

        if (lower.contains("http ")) {
            return "The update package could not be downloaded from GitHub. Check your Internet connection and try again.";
        }

        return "The update could not be installed. The current version was not changed.";
    }

    static int compareVersions(String left, String right) {
        int[] a = parseVersion(left);
        int[] b = parseVersion(right);
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int[] parseVersion(String value) {
        String clean = value == null ? "" : value.trim().replaceFirst("^[vV]", "");
        String numeric = clean.split("[-+]", 2)[0];
        String[] parts = numeric.split("\\.");
        int[] out = new int[Math.max(1, parts.length)];
        for (int i = 0; i < parts.length; i++) {
            try { out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", "")); }
            catch (Exception ignored) { out[i] = 0; }
        }
        return out;
    }
}
