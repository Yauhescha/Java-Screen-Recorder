package com.example.screenrecorder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds Windows cameras that can be used by FFmpeg/DirectShow.
 *
 * FFmpeg's DirectShow device-list output changed between releases. Older builds
 * print separate "DirectShow video devices" / "DirectShow audio devices"
 * sections, while newer builds may annotate every source with "(video)" or
 * "(audio)". 0.7.0 only understood the old sectioned form, which made the
 * webcam combo appear empty with some FFmpeg 8 builds.
 */
public final class WebcamService {
    private static final Pattern QUOTED = Pattern.compile("\\\"([^\\\"]+)\\\"");
    private static final Pattern SOURCE_WITH_KIND = Pattern.compile(
            "^\\s*(?:\\*\\s*)?(.+?)\\s*(?:\\[[^]]*])?\\s*\\((video|audio)(?:[^)]*)?\\)\\s*$",
            Pattern.CASE_INSENSITIVE);

    public List<WebcamDevice> listDevices(String ffmpegPath) {
        return listDevices(ffmpegPath, null);
    }

    public List<WebcamDevice> listDevices(String ffmpegPath, Consumer<String> log) {
        LinkedHashSet<String> names = new LinkedHashSet<>();

        CommandResult sources = run(ffmpegPath, List.of("-hide_banner", "-sources", "dshow"), 6);
        parseOutput(sources.output(), names);
        if (log != null) {
            log.accept("Webcam scan: FFmpeg -sources dshow returned " + names.size() + " video device(s).");
        }

        // Keep the classic command as a second source. It is still the canonical
        // dshow enumeration command and covers older FFmpeg builds.
        CommandResult classic = run(ffmpegPath,
                List.of("-hide_banner", "-list_devices", "true", "-f", "dshow", "-i", "dummy"), 6);
        int beforeClassic = names.size();
        parseOutput(classic.output(), names);
        if (log != null && names.size() != beforeClassic) {
            log.accept("Webcam scan: DirectShow list added " + (names.size() - beforeClassic) + " device(s).");
        }

        // Windows Camera can work through Media Foundation even when a particular
        // FFmpeg build fails to print DirectShow devices. Friendly PnP names are a
        // useful fallback and normally match the DirectShow friendly name.
        if (names.isEmpty() && isWindows()) {
            List<String> pnp = listWindowsCameraFriendlyNames();
            names.addAll(pnp);
            if (log != null) {
                log.accept("Webcam scan: FFmpeg did not enumerate a camera; Windows PnP fallback found "
                        + pnp.size() + " camera device(s).");
            }
        }

        if (names.isEmpty() && log != null) {
            String detail = firstUsefulLine(classic.output());
            if (detail.isBlank()) detail = firstUsefulLine(sources.output());
            log.accept("Webcam scan: no camera found." + (detail.isBlank() ? "" : " FFmpeg: " + detail));
        }

        List<WebcamDevice> result = new ArrayList<>();
        for (String name : names) {
            String cleaned = cleanName(name);
            if (!cleaned.isBlank()) result.add(new WebcamDevice(cleaned));
        }
        return result;
    }

    /** Package-private so the parser can be validated without Windows hardware. */
    static List<String> parseDeviceNames(String output) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        parseOutput(output, names);
        return List.copyOf(names);
    }

    private static void parseOutput(String output, Set<String> result) {
        if (output == null || output.isBlank()) return;

        boolean videoSection = false;
        for (String raw : output.split("\\R")) {
            String line = stripLogPrefix(raw).trim();
            String lower = line.toLowerCase(Locale.ROOT);

            if (lower.contains("directshow video devices")) {
                videoSection = true;
                continue;
            }
            if (lower.contains("directshow audio devices")) {
                videoSection = false;
                continue;
            }
            if (lower.contains("alternative name")) continue;

            // Newer FFmpeg source/list output: Camera Name [@device...] (video)
            Matcher typed = SOURCE_WITH_KIND.matcher(line);
            if (typed.matches()) {
                if ("video".equalsIgnoreCase(typed.group(2))) {
                    addName(result, typed.group(1));
                }
                continue;
            }

            // Some FFmpeg 8 dshow builds keep quotes and append a media type.
            if (lower.contains("(video)")) {
                Matcher quoted = QUOTED.matcher(line);
                if (quoted.find()) addName(result, quoted.group(1));
                else addName(result, line.substring(0, lower.lastIndexOf("(video)")));
                continue;
            }

            // Classic FFmpeg output under the DirectShow video section.
            if (videoSection) {
                Matcher quoted = QUOTED.matcher(line);
                if (quoted.find()) addName(result, quoted.group(1));
            }
        }
    }

    private static void addName(Set<String> result, String value) {
        String cleaned = cleanName(value);
        if (!cleaned.isBlank()
                && !cleaned.startsWith("@device_")
                && !cleaned.toLowerCase(Locale.ROOT).contains("alternative name")) {
            result.add(cleaned);
        }
    }

    private static String cleanName(String value) {
        if (value == null) return "";
        String s = value.trim();
        while (s.startsWith("*") || s.startsWith(":")) s = s.substring(1).trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static String stripLogPrefix(String line) {
        if (line == null) return "";
        // e.g. [dshow @ 000001f...]  "Integrated Camera"
        int close = line.indexOf(']');
        if (line.startsWith("[") && close >= 0 && close + 1 < line.length()) {
            return line.substring(close + 1);
        }
        return line;
    }

    private static List<String> listWindowsCameraFriendlyNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Process p = null;
        try {
            String command = "$ErrorActionPreference='SilentlyContinue'; "
                    + "Get-PnpDevice | Where-Object { ($_.Class -eq 'Camera' -or $_.Class -eq 'Image') "
                    + "-and $_.Status -eq 'OK' -and $_.FriendlyName } | "
                    + "Select-Object -ExpandProperty FriendlyName";
            p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String value = cleanName(line);
                    if (!value.isBlank()) names.add(value);
                }
            }
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (Exception ignored) {
            if (p != null && p.isAlive()) p.destroyForcibly();
        }
        return List.copyOf(names);
    }

    private static CommandResult run(String executable, List<String> args, int timeoutSeconds) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(args);
        Process p = null;
        StringBuilder out = new StringBuilder();
        try {
            p = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append('\n');
            }
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new CommandResult(-1, out.toString());
            }
            return new CommandResult(p.exitValue(), out.toString());
        } catch (Exception e) {
            if (p != null && p.isAlive()) p.destroyForcibly();
            return new CommandResult(-1, out.append(e.getMessage()).toString());
        }
    }

    private static String firstUsefulLine(String text) {
        if (text == null) return "";
        for (String line : text.split("\\R")) {
            String v = stripLogPrefix(line).trim();
            if (!v.isBlank()
                    && !v.startsWith("ffmpeg version")
                    && !v.startsWith("built with")
                    && !v.startsWith("configuration:")) return v;
        }
        return "";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private record CommandResult(int exitCode, String output) {}
}
