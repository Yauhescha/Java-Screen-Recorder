package com.example.screenrecorder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class FfmpegService {
    public record NvencStatus(boolean encoderPresent, boolean probeSucceeded, String detail) {
        public boolean selectable() {
            return encoderPresent;
        }
    }

    private record ProcessResult(int exitCode, String output, boolean timedOut) {}

    public void verify(String path) {
        try {
            Process p = new ProcessBuilder(path, "-version").redirectErrorStream(true).start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("FFmpeg check timed out");
            }
            if (p.exitValue() != 0) throw new IllegalStateException("FFmpeg exit code " + p.exitValue());
        } catch (Exception e) {
            throw new IllegalStateException("FFmpeg could not be started.", e);
        }
    }

    /**
     * Detects NVENC in two stages. The encoder-list check decides whether the
     * option is exposed. A real one-frame encode is then used as a diagnostic,
     * but a failed synthetic probe no longer hides NVENC entirely: actual screen
     * recording will try it once and RecordingSession can fall back to CPU.
     */
    public NvencStatus detectNvenc(String ffmpegPath, Consumer<String> log) {
        ProcessResult encoderList = run(ffmpegPath, 8,
                "-hide_banner", "-encoders");
        if (encoderList.timedOut() || encoderList.exitCode() != 0) {
            String detail = "Could not read FFmpeg encoder list.";
            log.accept("NVENC: " + detail);
            return new NvencStatus(false, false, detail);
        }

        String encoders = encoderList.output().toLowerCase(Locale.ROOT);
        if (!encoders.contains("h264_nvenc")) {
            String detail = "h264_nvenc is not present in this FFmpeg build.";
            log.accept("NVENC: " + detail);
            return new NvencStatus(false, false, detail);
        }

        log.accept("NVENC: h264_nvenc is present in FFmpeg. Running a one-frame hardware probe...");
        ProcessResult probe = run(ffmpegPath, 12,
                "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "color=c=black:s=640x360:r=30:d=0.2",
                "-frames:v", "1", "-an",
                "-c:v", "h264_nvenc",
                "-gpu", "any",
                "-pix_fmt", "yuv420p",
                "-f", "null", "-");

        if (!probe.timedOut() && probe.exitCode() == 0) {
            String detail = "NVIDIA hardware H.264 encoding was validated.";
            log.accept("NVENC: " + detail);
            return new NvencStatus(true, true, detail);
        }

        String raw = probe.output() == null ? "" : probe.output().trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        String detail;
        if (probe.timedOut()) {
            detail = "h264_nvenc exists, but the validation probe timed out.";
        } else if (lower.contains("minimum required nvidia driver") || lower.contains("driver does not support")) {
            detail = "h264_nvenc exists, but the NVIDIA driver is too old for this FFmpeg build.";
        } else if (lower.contains("cannot load nvencodeapi64") || lower.contains("nvencodeapi64.dll")) {
            detail = "h264_nvenc exists, but FFmpeg could not load the NVIDIA NVENC driver library.";
        } else if (lower.contains("no capable devices found") || lower.contains("no nvenc capable devices")) {
            detail = "h264_nvenc exists, but FFmpeg reported no NVENC-capable device.";
        } else if (lower.contains("open encode session ex failed") || lower.contains("unsupported device")) {
            detail = "h264_nvenc exists, but the hardware session could not be opened.";
        } else if (lower.contains("frame dimension less than the minimum supported value")) {
            detail = "h264_nvenc is available, but the diagnostic frame was too small for this NVIDIA encoder.";
        } else {
            detail = "h264_nvenc exists, but the synthetic validation probe failed. " +
                    "The recorder will still try NVENC on a real screen recording and fall back to CPU if needed.";
        }

        log.accept("NVENC: " + detail);
        if (!raw.isBlank()) {
            log.accept("NVENC probe output: " + raw.replace("\r", " ").replace("\n", " | "));
        }
        return new NvencStatus(true, false, detail);
    }

    private ProcessResult run(String executable, int timeoutSeconds, String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(executable);
            cmd.addAll(List.of(args));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();

            // Read output concurrently so a verbose child process cannot fill its pipe and deadlock.
            var output = new java.io.ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (var in = p.getInputStream()) {
                    in.transferTo(output);
                } catch (IOException ignored) {
                }
            }, "ffmpeg-probe-output");
            reader.setDaemon(true);
            reader.start();

            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                try { reader.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return new ProcessResult(-1, output.toString(StandardCharsets.UTF_8), true);
            }
            try { reader.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new ProcessResult(p.exitValue(), output.toString(StandardCharsets.UTF_8), false);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new ProcessResult(-1, e.getMessage() == null ? e.toString() : e.getMessage(), false);
        }
    }
}
