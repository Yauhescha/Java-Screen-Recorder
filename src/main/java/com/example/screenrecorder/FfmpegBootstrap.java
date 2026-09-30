package com.example.screenrecorder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * FFmpeg bootstrap logic:
 * 1. Use FFmpeg from PATH if one is already installed and working.
 * 2. Otherwise use ffmpeg.exe next to the application launcher if it already exists.
 * 3. Otherwise extract the embedded Windows FFmpeg binary next to the launcher.
 */
public final class FfmpegBootstrap {
    private static final String EMBEDDED_FFMPEG = "/native/windows-x64/ffmpeg.exe";
    private static final String EMBEDDED_FFPROBE = "/native/windows-x64/ffprobe.exe";

    private FfmpegBootstrap() {
    }

    public static String resolve(Consumer<String> log) {
        if (!isWindows()) {
            if (isCompatible("ffmpeg")) {
                return "ffmpeg";
            }
            throw new IllegalStateException("This build currently bundles FFmpeg only for Windows x64.");
        }

        Path system = findSystemFfmpeg();
        if (system != null) {
            log.accept("Using system FFmpeg: " + system);
            return system.toString();
        }

        Path appDir = AppPaths.applicationDirectory();
        Path localFfmpeg = appDir.resolve("ffmpeg.exe");

        if (Files.isRegularFile(localFfmpeg) && isCompatible(localFfmpeg.toString())) {
            extractEmbeddedSidecar(EMBEDDED_FFPROBE, appDir.resolve("ffprobe.exe"), false);
            log.accept("Using local FFmpeg: " + localFfmpeg);
            return localFfmpeg.toString();
        }

        log.accept("System FFmpeg was not found. Extracting bundled FFmpeg to: " + localFfmpeg);
        extractEmbeddedFfmpeg(localFfmpeg);
        extractEmbeddedSidecar(EMBEDDED_FFPROBE, appDir.resolve("ffprobe.exe"), false);

        if (!isCompatible(localFfmpeg.toString())) {
            throw new IllegalStateException("Bundled FFmpeg was extracted, but it could not be started: " + localFfmpeg);
        }

        log.accept("Bundled FFmpeg is ready: " + localFfmpeg);
        return localFfmpeg.toString();
    }

    private static Path findSystemFfmpeg() {
        List<String> candidates = new ArrayList<>();

        try {
            Process process = new ProcessBuilder("where.exe", "ffmpeg.exe")
                    .redirectErrorStream(true)
                    .start();

            if (process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0) {
                try (var reader = process.inputReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            candidates.add(line.trim());
                        }
                    }
                }
            } else if (process.isAlive()) {
                process.destroyForcibly();
            }
        } catch (Exception ignored) {
        }

        for (String candidate : candidates) {
            if (isCompatible(candidate)) {
                return Path.of(candidate).toAbsolutePath().normalize();
            }
        }

        // Covers custom shells where `where.exe` is unavailable for some reason.
        if (isCompatible("ffmpeg")) {
            return Path.of("ffmpeg");
        }

        return null;
    }

    private static void extractEmbeddedFfmpeg(Path target) {
        try (InputStream input = FfmpegBootstrap.class.getResourceAsStream(EMBEDDED_FFMPEG)) {
            if (input == null) {
                throw new IllegalStateException(
                        "Embedded ffmpeg.exe is missing from the application. " +
                        "For a source build, run scripts\\prepare-ffmpeg.ps1 before Maven packaging."
                );
            }

            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "ffmpeg-", ".tmp");
            Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);

            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not extract ffmpeg.exe next to the application. " +
                    "Install/run the application from a directory writable by the current user. Target: " + target,
                    e
            );
        }
    }

    private static void extractEmbeddedSidecar(String resource, Path target, boolean required) {
        if (Files.isRegularFile(target)) return;
        try (InputStream input = FfmpegBootstrap.class.getResourceAsStream(resource)) {
            if (input == null) {
                if (required) throw new IllegalStateException("Embedded native tool is missing: " + resource);
                return;
            }
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "native-tool-", ".tmp");
            Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            if (required) throw new IllegalStateException("Could not extract native tool: " + target);
        }
    }

    private static boolean isCompatible(String executable) {
        if (!isWorking(executable)) {
            return false;
        }

        String devices = capture(executable, "-hide_banner", "-devices");
        String encoders = capture(executable, "-hide_banner", "-encoders");
        return devices != null
                && encoders != null
                && devices.toLowerCase(Locale.ROOT).contains("gdigrab")
                && devices.toLowerCase(Locale.ROOT).contains("dshow")
                && encoders.toLowerCase(Locale.ROOT).contains("libx264");
    }

    private static String capture(String executable, String... arguments) {
        try {
            List<String> command = new ArrayList<>();
            command.add(executable);
            command.addAll(List.of(arguments));

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            byte[] data = process.getInputStream().readAllBytes();
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                return null;
            }
            return new String(data);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isWorking(String executable) {
        try {
            Process process = new ProcessBuilder(executable, "-version")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
