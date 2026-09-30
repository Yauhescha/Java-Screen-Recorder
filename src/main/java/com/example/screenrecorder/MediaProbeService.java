package com.example.screenrecorder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MediaProbeService {
    private static final Pattern DURATION = Pattern.compile("Duration:\\s*(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
    private final String ffmpegPath;
    private final String ffprobePath;

    MediaProbeService(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = findFfprobe(ffmpegPath);
    }

    double durationSeconds(Path file) {
        if (file == null || !Files.isRegularFile(file)) return -1;
        if (ffprobePath != null) {
            try {
                Process p = new ProcessBuilder(
                        ffprobePath,
                        "-v", "error",
                        "-show_entries", "format=duration",
                        "-of", "default=noprint_wrappers=1:nokey=1",
                        file.toAbsolutePath().toString())
                        .redirectErrorStream(true)
                        .start();
                byte[] data = p.getInputStream().readAllBytes();
                if (p.waitFor(6, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    String text = new String(data, StandardCharsets.UTF_8).trim();
                    return Double.parseDouble(text);
                }
                if (p.isAlive()) p.destroyForcibly();
            } catch (Exception ignored) {
            }
        }

        // Fallback works even when only ffmpeg.exe is available.
        try {
            Process p = new ProcessBuilder(ffmpegPath, "-hide_banner", "-i", file.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(6, TimeUnit.SECONDS);
            if (p.isAlive()) p.destroyForcibly();
            Matcher matcher = DURATION.matcher(text);
            if (matcher.find()) {
                int h = Integer.parseInt(matcher.group(1));
                int m = Integer.parseInt(matcher.group(2));
                double s = Double.parseDouble(matcher.group(3));
                return h * 3600.0 + m * 60.0 + s;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static String findFfprobe(String ffmpegPath) {
        try {
            Path ffmpeg = Path.of(ffmpegPath);
            if (ffmpeg.isAbsolute() && ffmpeg.getParent() != null) {
                Path sibling = ffmpeg.getParent().resolve(isWindows() ? "ffprobe.exe" : "ffprobe");
                if (Files.isRegularFile(sibling)) return sibling.toString();
            }
        } catch (Exception ignored) {
        }

        String name = isWindows() ? "ffprobe.exe" : "ffprobe";
        try {
            List<String> cmd = new ArrayList<>();
            if (isWindows()) {
                cmd.add("where.exe");
                cmd.add(name);
            } else {
                cmd.add("which");
                cmd.add(name);
            }
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0 && !text.isBlank()) {
                return text.lines().findFirst().orElse(name).trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
