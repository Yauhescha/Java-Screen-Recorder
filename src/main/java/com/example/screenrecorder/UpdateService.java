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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Lightweight update client backed by a release manifest hosted over HTTPS. */
final class UpdateService {
    private static final Pattern FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

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
        dialog.setSize(430, 120);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);

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
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.accept("Update failed: " + cause.getMessage());
                    JOptionPane.showMessageDialog(owner,
                            "Could not install the update:\n" + cause.getMessage(),
                            "Update failed", JOptionPane.ERROR_MESSAGE);
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
        extractZip(zip, staging);

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
        String parent = psQuote(target.toAbsolutePath().getParent().toString());
        String name = target.getFileName().toString();
        String backup = psQuote(target.toAbsolutePath().getParent().resolve(name + ".update-backup").toString());
        String exe = psQuote(target.toAbsolutePath().resolve("JavaScreenRecorder.exe").toString());
        return "$ErrorActionPreference = 'Stop'\r\n" +
                "$pidToWait = " + pid + "\r\n" +
                "$source = '" + src + "'\r\n" +
                "$target = '" + dst + "'\r\n" +
                "$parent = '" + parent + "'\r\n" +
                "$backup = '" + backup + "'\r\n" +
                "$exe = '" + exe + "'\r\n" +
                "try { Wait-Process -Id $pidToWait -ErrorAction SilentlyContinue } catch {}\r\n" +
                "Start-Sleep -Milliseconds 500\r\n" +
                "try {\r\n" +
                "  if (Test-Path -LiteralPath $backup) { Remove-Item -LiteralPath $backup -Recurse -Force }\r\n" +
                "  Move-Item -LiteralPath $target -Destination $backup -Force\r\n" +
                "  New-Item -ItemType Directory -Path $target -Force | Out-Null\r\n" +
                "  Copy-Item -Path (Join-Path $source '*') -Destination $target -Recurse -Force\r\n" +
                "  if (-not (Test-Path -LiteralPath $exe)) { throw 'Updated executable is missing.' }\r\n" +
                "  Start-Process -FilePath $exe\r\n" +
                "  Start-Sleep -Seconds 3\r\n" +
                "  Remove-Item -LiteralPath $backup -Recurse -Force -ErrorAction SilentlyContinue\r\n" +
                "} catch {\r\n" +
                "  $message = $_.Exception.Message\r\n" +
                "  if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force -ErrorAction SilentlyContinue }\r\n" +
                "  if (Test-Path -LiteralPath $backup) { Move-Item -LiteralPath $backup -Destination $target -Force }\r\n" +
                "  Add-Type -AssemblyName PresentationFramework -ErrorAction SilentlyContinue\r\n" +
                "  [System.Windows.MessageBox]::Show('Java Screen Recorder update failed: ' + $message, 'Update failed') | Out-Null\r\n" +
                "  if (Test-Path -LiteralPath $exe) { Start-Process -FilePath $exe }\r\n" +
                "}\r\n";
    }

    private static void extractZip(Path zip, Path destination) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path out = destination.resolve(entry.getName()).normalize();
                if (!out.startsWith(destination)) {
                    throw new IOException("Unsafe path in update ZIP: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    if (out.getParent() != null) Files.createDirectories(out.getParent());
                    Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zin.closeEntry();
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
