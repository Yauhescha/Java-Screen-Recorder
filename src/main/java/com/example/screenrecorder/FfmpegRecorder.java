package com.example.screenrecorder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FfmpegRecorder {
    private static final Pattern START_TIME_PATTERN =
            Pattern.compile("\\bstart:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern FRAME_PATTERN = Pattern.compile("\\bframe=\\s*(\\d+)");
    private static final Pattern FPS_PATTERN = Pattern.compile("\\bfps=\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern DROP_PATTERN = Pattern.compile("\\bdrop=\\s*(\\d+)");

    private Process process;
    private BufferedWriter processInput;
    private final List<AudioPcmSource> audioSources = new ArrayList<>();
    private final MicrophoneService microphoneService = new MicrophoneService();
    private volatile boolean stopping;
    private volatile MicrophonePcmSource microphoneSource;
    private volatile boolean microphoneMuted;
    private volatile RecordingStats lastStats = RecordingStats.ZERO;
    private volatile RecordingStatsListener statsListener = RecordingStatsListener.NONE;
    private volatile RecordingHealthListener healthListener = RecordingHealthListener.NONE;

    public synchronized boolean isRecording() {
        return process != null && process.isAlive();
    }

    public synchronized void start(RecorderConfig config, Consumer<String> log, AudioLevelListener levels) throws Exception {
        start(config, log, levels, RecordingStatsListener.NONE, RecordingHealthListener.NONE);
    }

    public synchronized void start(RecorderConfig config, Consumer<String> log, AudioLevelListener levels,
                                   RecordingStatsListener stats, RecordingHealthListener health) throws Exception {
        if (isRecording()) throw new IllegalStateException("Recording is already running");
        Files.createDirectories(config.outputFile().toAbsolutePath().getParent());
        stopping = false;
        lastStats = RecordingStats.ZERO;
        statsListener = stats != null ? stats : RecordingStatsListener.NONE;
        healthListener = health != null ? health : RecordingHealthListener.NONE;

        audioSources.clear();
        AudioLevelListener safeLevels = levels != null ? levels : AudioLevelListener.NONE;
        if (config.recordSystemAudio()) {
            audioSources.add(new WasapiLoopbackPcmSource(safeLevels));
        }
        microphoneSource = null;
        if (config.recordMicrophone()) {
            microphoneSource = new MicrophonePcmSource(config.microphoneDevice(), microphoneService, safeLevels,
                    message -> healthListener.onMicrophoneFailure(message));
            microphoneSource.setMuted(microphoneMuted);
            audioSources.add(microphoneSource);
        }

        CaptureTimeline timeline = new CaptureTimeline();

        try {
            for (AudioPcmSource source : audioSources) source.prepare();
            for (AudioPcmSource source : audioSources) source.start(timeline, log);
            for (AudioPcmSource source : audioSources) source.awaitReady(5, TimeUnit.SECONDS);

            if (!audioSources.isEmpty()) {
                log.accept("Audio sources armed. Starting synchronized video capture...");
            }

            List<String> command = buildCommand(config, audioSources);
            log.accept("FFmpeg command:");
            log.accept(String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            timeline.markFfmpegLaunch();
            process = pb.start();
            Process launchedProcess = process;
            processInput = new BufferedWriter(new OutputStreamWriter(
                    launchedProcess.getOutputStream(), StandardCharsets.UTF_8));

            Thread ffmpegLogThread = new Thread(() -> {
                try (var reader = launchedProcess.inputReader(StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.accept(line);
                        parseVideoStart(line, timeline, log);
                        parseStats(line);
                    }
                } catch (IOException e) {
                    if (!stopping && launchedProcess.isAlive()) log.accept("FFmpeg log error: " + e.getMessage());
                }
            }, "ffmpeg-output");
            ffmpegLogThread.setDaemon(true);
            ffmpegLogThread.start();

            java.util.concurrent.atomic.AtomicBoolean established = new java.util.concurrent.atomic.AtomicBoolean(false);
            Thread watcher = new Thread(() -> {
                try {
                    int exit = launchedProcess.waitFor();
                    if (!stopping && established.get() && exit != 0) {
                        String message = "FFmpeg stopped unexpectedly (exit " + exit + "). " +
                                "The current MKV segment was kept for recovery.";
                        log.accept("WARNING: " + message);
                        healthListener.onRecorderFailure(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "ffmpeg-exit-watcher");
            watcher.setDaemon(true);
            watcher.start();

            Thread.sleep(350);
            if (!launchedProcess.isAlive()) {
                throw new IllegalStateException("FFmpeg exited immediately. Check the log.");
            }
            established.set(true);
        } catch (Exception e) {
            stopSources();
            if (process != null && process.isAlive()) process.destroyForcibly();
            process = null;
            processInput = null;
            throw e;
        }
    }

    private void parseStats(String line) {
        Matcher frameMatcher = FRAME_PATTERN.matcher(line);
        Matcher fpsMatcher = FPS_PATTERN.matcher(line);
        Matcher dropMatcher = DROP_PATTERN.matcher(line);
        long frames = lastStats.frames();
        double fps = lastStats.fps();
        long dropped = lastStats.droppedFrames();
        boolean changed = false;
        if (frameMatcher.find()) { frames = Long.parseLong(frameMatcher.group(1)); changed = true; }
        if (fpsMatcher.find()) { fps = Double.parseDouble(fpsMatcher.group(1)); changed = true; }
        if (dropMatcher.find()) { dropped = Long.parseLong(dropMatcher.group(1)); changed = true; }
        if (changed) {
            RecordingStats next = new RecordingStats(frames, fps, dropped);
            lastStats = next;
            statsListener.onStats(next);
        }
    }

    public RecordingStats lastStats() { return lastStats; }

    private void parseVideoStart(String line, CaptureTimeline timeline, Consumer<String> log) {
        if (timeline.hasExactVideoStart()) return;
        Matcher matcher = START_TIME_PATTERN.matcher(line);
        while (matcher.find()) {
            try {
                double seconds = Double.parseDouble(matcher.group(1));
                if (timeline.trySetVideoStartSeconds(seconds)) {
                    log.accept("Automatic A/V sync: first gdigrab frame timestamp detected.");
                    return;
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private List<String> buildCommand(RecorderConfig c, List<AudioPcmSource> sources) {
        List<String> cmd = new ArrayList<>();
        cmd.add(c.ffmpegPath());
        cmd.add("-y");
        cmd.add("-hide_banner");
        cmd.add("-stats_period"); cmd.add("0.5");
        cmd.add("-fflags"); cmd.add("+genpts");

        // Screen inputs always come first. For a single monitor or a region fully
        // contained on one monitor, NVENC can use FFmpeg's Desktop Duplication API
        // (ddagrab) and keep frames on the GPU. This is far lighter than GDI capture.
        // Unsupported systems fall back to gdigrab in RecordingSession automatically.
        List<java.awt.Rectangle> screenRects = new ArrayList<>();
        int screenInputCount;
        boolean hardwareScreenInput = false;

        if (c.captureMode() == CaptureMode.WINDOW) {
            if (c.windowTarget() == null) throw new IllegalArgumentException("Select a window to record");
            java.awt.Rectangle windowBounds = c.windowTarget().bounds();
            addWindowInput(cmd, c.windowTarget(), c.fps(), c.showCursor());
            screenRects.add(windowBounds);
            screenInputCount = 1;
        } else if (c.captureMode() == CaptureMode.REGION) {
            CaptureRegion r = c.region().evenSized();
            java.awt.Rectangle regionRect = new java.awt.Rectangle(r.x(), r.y(), r.width(), r.height());
            DisplayMonitor owner = null;
            if (c.fastGpuCapture()) {
                for (DisplayMonitor monitor : c.captureMonitors()) {
                    if (monitor.dxgiOutputIndex() >= 0 && monitor.bounds().contains(regionRect)) {
                        owner = monitor;
                        break;
                    }
                }
            }
            if (owner != null) {
                java.awt.Rectangle mb = owner.bounds();
                addDdaInput(cmd, owner.dxgiOutputIndex(),
                        regionRect.x - mb.x, regionRect.y - mb.y,
                        regionRect.width, regionRect.height, c.fps(), c.showCursor());
                hardwareScreenInput = true;
            } else {
                addDesktopInput(cmd, regionRect, c.fps(), c.showCursor());
            }
            screenRects.add(regionRect);
            screenInputCount = 1;
        } else {
            List<DisplayMonitor> monitors = c.captureMonitors();
            if (monitors == null || monitors.isEmpty()) {
                throw new IllegalArgumentException("No monitor is selected for capture");
            }

            // Desktop Duplication can capture more than one DXGI output as separate
            // hardware inputs. Multi-monitor composition still needs a download for
            // xstack, but capture itself no longer relies on the much heavier GDI path.
            boolean canUseDdaForAll = c.fastGpuCapture() &&
                    monitors.stream().allMatch(m -> m.dxgiOutputIndex() >= 0);
            if (canUseDdaForAll) {
                for (DisplayMonitor monitor : monitors) {
                    java.awt.Rectangle b = monitor.bounds();
                    addDdaInput(cmd, monitor.dxgiOutputIndex(), 0, 0,
                            b.width, b.height, c.fps(), c.showCursor());
                    screenRects.add(b);
                }
                hardwareScreenInput = true;
            } else {
                for (DisplayMonitor monitor : monitors) {
                    java.awt.Rectangle b = monitor.bounds();
                    addDesktopInput(cmd, b, c.fps(), c.showCursor());
                    screenRects.add(b);
                }
            }
            screenInputCount = monitors.size();
        }

        int nextInputIndex = screenInputCount;
        Integer webcamInputIndex = null;
        if (c.recordWebcam()) {
            if (c.webcamDevice() == null) throw new IllegalArgumentException("Select a webcam");
            webcamInputIndex = nextInputIndex++;
            cmd.add("-thread_queue_size"); cmd.add("8");
            if (c.webcamInputUrl() != null && !c.webcamInputUrl().isBlank()) {
                cmd.add("-f"); cmd.add("rawvideo");
                cmd.add("-pixel_format"); cmd.add("bgr24");
                cmd.add("-video_size"); cmd.add(c.webcamInputWidth() + "x" + c.webcamInputHeight());
                cmd.add("-framerate"); cmd.add(String.valueOf(c.webcamInputFps()));
                cmd.add("-i"); cmd.add(c.webcamInputUrl());
            } else {
                // Fallback kept for compatibility if a bridge could not be prepared.
                cmd.add("-rtbufsize"); cmd.add("64M");
                cmd.add("-f"); cmd.add("dshow");
                cmd.add("-i"); cmd.add("video=" + c.webcamDevice().name());
            }
        }

        List<Integer> audioInputIndexes = new ArrayList<>();
        for (AudioPcmSource source : sources) {
            audioInputIndexes.add(nextInputIndex++);
            cmd.add("-thread_queue_size"); cmd.add("64");
            cmd.add("-f"); cmd.add("s16le");
            cmd.add("-ar"); cmd.add(String.valueOf(source.sampleRate()));
            cmd.add("-ac"); cmd.add(String.valueOf(source.channels()));
            cmd.add("-i"); cmd.add(source.inputUrl());
        }

        List<String> filters = new ArrayList<>();
        String screenBase = null;
        String videoMap;
        boolean directHardwareVideo = hardwareScreenInput && screenInputCount == 1 && webcamInputIndex == null;

        if (screenInputCount == 1) {
            if (directHardwareVideo) {
                // Official FFmpeg ddagrab -> h264_nvenc path: keep D3D11 frames on GPU.
                videoMap = "0:v:0";
            } else {
                screenBase = "screenBase";
                String prefix = hardwareScreenInput
                        ? "[0:v]hwdownload,format=bgra,setpts=PTS-STARTPTS,"
                        : "[0:v]setpts=PTS-STARTPTS,";
                filters.add(prefix + "pad=ceil(iw/2)*2:ceil(ih/2)*2[" + screenBase + "]");
                videoMap = "[" + screenBase + "]";
            }
        } else {
            java.awt.Rectangle union = null;
            StringBuilder inputs = new StringBuilder();
            StringBuilder layout = new StringBuilder();
            for (int i = 0; i < screenInputCount; i++) {
                java.awt.Rectangle b = screenRects.get(i);
                union = union == null ? new java.awt.Rectangle(b) : union.union(b);
            }
            for (int i = 0; i < screenInputCount; i++) {
                java.awt.Rectangle b = screenRects.get(i);
                String label = "screen" + i;
                String chain = hardwareScreenInput
                        ? "[" + i + ":v]hwdownload,format=bgra,setpts=PTS-STARTPTS[" + label + "]"
                        : "[" + i + ":v]setpts=PTS-STARTPTS[" + label + "]";
                filters.add(chain);
                inputs.append("[").append(label).append("]");
                if (i > 0) layout.append("|");
                layout.append(b.x - union.x).append("_").append(b.y - union.y);
            }
            filters.add(inputs + "xstack=inputs=" + screenInputCount + ":layout=" + layout +
                    ":fill=black[screenStack]");
            filters.add("[screenStack]pad=ceil(iw/2)*2:ceil(ih/2)*2[screenBase]");
            screenBase = "screenBase";
            videoMap = "[screenBase]";
        }

        if (webcamInputIndex != null) {
            WebcamPlacement p = c.webcamPlacement();
            if (p == null) throw new IllegalArgumentException("Webcam overlay placement is missing");
            p = p.evenSized();
            WebcamShape shape = c.webcamShape() == null ? WebcamShape.ROUNDED : c.webcamShape();
            int border = c.webcamBorder() ? Math.max(2, Math.min(6, Math.min(p.width(), p.height()) / 40)) : 0;
            int innerW = Math.max(2, (p.width() - border * 2) & ~1);
            int innerH = Math.max(2, (p.height() - border * 2) & ~1);

            String camPrefix = "[" + webcamInputIndex + ":v]setpts=PTS-STARTPTS," +
                    (c.webcamMirror() ? "hflip," : "");
            String camFinal;

            if (border > 0) {
                String borderBase = "wborderbase";
                filters.add("color=c=" + ffmpegColor(c.webcamBorderRgb(), 1.0) +
                        ":s=" + p.width() + "x" + p.height() + ":r=" + c.webcamInputFps() +
                        ",format=rgba[" + borderBase + "]");
                String borderLayer = borderBase;
                if (shape != WebcamShape.RECTANGLE) {
                    String mask = "wborderMask";
                    filters.add(shapeMaskFilter(mask, p.width(), p.height(), c.webcamInputFps(), shape));
                    borderLayer = "wborderShape";
                    filters.add("[" + borderBase + "][" + mask + "]alphamerge[" + borderLayer + "]");
                }

                filters.add(camPrefix + "scale=" + innerW + ":" + innerH +
                        ":force_original_aspect_ratio=increase,crop=" + innerW + ":" + innerH +
                        ",format=rgba[wcamInnerBase]");
                String camInner = "wcamInnerBase";
                if (shape != WebcamShape.RECTANGLE) {
                    String mask = "wcamInnerMask";
                    filters.add(shapeMaskFilter(mask, innerW, innerH, c.webcamInputFps(), shape));
                    camInner = "wcamInnerShape";
                    filters.add("[wcamInnerBase][" + mask + "]alphamerge[" + camInner + "]");
                }
                camFinal = "wcamFinal";
                filters.add("[" + borderLayer + "][" + camInner + "]overlay=" + border + ":" + border +
                        ":eof_action=pass:format=auto[" + camFinal + "]");
            } else {
                filters.add(camPrefix + "scale=" + p.width() + ":" + p.height() +
                        ":force_original_aspect_ratio=increase,crop=" + p.width() + ":" + p.height() +
                        ",format=rgba[wcamBase]");
                camFinal = "wcamBase";
                if (shape != WebcamShape.RECTANGLE) {
                    String mask = "wcamMask";
                    filters.add(shapeMaskFilter(mask, p.width(), p.height(), c.webcamInputFps(), shape));
                    camFinal = "wcamShape";
                    filters.add("[wcamBase][" + mask + "]alphamerge[" + camFinal + "]");
                }
            }

            String screenLayer = screenBase;
            if (c.webcamShadow()) {
                filters.add("color=c=black@0.38:s=" + p.width() + "x" + p.height() +
                        ":r=" + c.webcamInputFps() + ",format=rgba[wshadowBase]");
                String shadowLayer = "wshadowBase";
                if (shape != WebcamShape.RECTANGLE) {
                    String mask = "wshadowMask";
                    filters.add(shapeMaskFilter(mask, p.width(), p.height(), c.webcamInputFps(), shape));
                    shadowLayer = "wshadowShape";
                    filters.add("[wshadowBase][" + mask + "]alphamerge[" + shadowLayer + "]");
                }
                filters.add("[" + screenBase + "][" + shadowLayer + "]overlay=" + (p.x() + 7) + ":" + (p.y() + 7) +
                        ":eof_action=pass:format=auto[screenWithShadow]");
                screenLayer = "screenWithShadow";
            }

            filters.add("[" + screenLayer + "][" + camFinal + "]overlay=" + p.x() + ":" + p.y() +
                    ":eof_action=pass:format=auto[vout]");
            videoMap = "[vout]";
        }

        List<String> audioMaps = new ArrayList<>();
        int audioPos = 0;
        String systemLabel = null;
        String micLabel = null;
        if (c.recordSystemAudio()) {
            int idx = audioInputIndexes.get(audioPos++);
            systemLabel = "asystem";
            filters.add("[" + idx + ":a]aresample=async=1:first_pts=0,volume=" +
                    volumeFactor(c.systemVolumePercent()) + "[" + systemLabel + "]");
        }
        if (c.recordMicrophone()) {
            int idx = audioInputIndexes.get(audioPos);
            micLabel = "amic";
            StringBuilder chain = new StringBuilder("[").append(idx).append(":a]aresample=async=1:first_pts=0");
            if (c.microphoneNoiseSuppression()) chain.append(",afftdn=nr=12:nf=-50");
            if (c.microphoneNoiseGate()) {
                chain.append(",agate=threshold=").append(gateThreshold(c.microphoneNoiseGateDb()))
                        .append(":ratio=8:attack=5:release=120");
            }
            chain.append(",volume=").append(volumeFactor(c.microphoneVolumePercent()))
                    .append("[").append(micLabel).append("]");
            filters.add(chain.toString());
        }

        if (systemLabel != null && micLabel != null && c.separateAudioTracks()) {
            audioMaps.add("[" + systemLabel + "]");
            audioMaps.add("[" + micLabel + "]");
        } else if (systemLabel != null && micLabel != null) {
            filters.add("[" + systemLabel + "][" + micLabel + "]amix=inputs=2:duration=longest:dropout_transition=2[aout]");
            audioMaps.add("[aout]");
        } else if (systemLabel != null) {
            audioMaps.add("[" + systemLabel + "]");
        } else if (micLabel != null) {
            audioMaps.add("[" + micLabel + "]");
        }

        if (!filters.isEmpty()) {
            cmd.add("-filter_complex");
            cmd.add(String.join(";", filters));
        }
        cmd.add("-map"); cmd.add(videoMap);
        for (String audioMap : audioMaps) {
            cmd.add("-map"); cmd.add(audioMap);
        }

        if (sources.size() == 1) {
            cmd.add("-metadata:s:a:0");
            cmd.add("title=" + (c.recordSystemAudio() ? "System audio" : "Microphone"));
        } else if (sources.size() == 2 && c.separateAudioTracks()) {
            cmd.add("-metadata:s:a:0"); cmd.add("title=System audio");
            cmd.add("-metadata:s:a:1"); cmd.add("title=Microphone");
        } else if (sources.size() == 2) {
            cmd.add("-metadata:s:a:0"); cmd.add("title=Mixed audio");
        }

        if (c.videoEncoder() == VideoEncoder.NVIDIA_NVENC) addNvencSettings(cmd, c.qualityPercent(), directHardwareVideo);
        else addCpuSettings(cmd, c.qualityPercent());

        if (!sources.isEmpty()) {
            cmd.add("-c:a"); cmd.add("aac");
            cmd.add("-b:a"); cmd.add("192k");
            cmd.add("-ar"); cmd.add("48000");
        }

        // Force constant frame pacing in the saved stream. gdigrab timestamps can be
        // slightly irregular under desktop load; CFR duplicates/drops only when needed
        // instead of producing visibly jerky playback timing.
        cmd.add("-fps_mode"); cmd.add("cfr");
        cmd.add("-r"); cmd.add(String.valueOf(c.fps()));
        cmd.add("-flush_packets"); cmd.add("1");
        cmd.add("-f"); cmd.add("matroska");
        cmd.add(c.outputFile().toAbsolutePath().toString());
        return cmd;
    }

    private static void addDdaInput(List<String> cmd, int outputIndex, int offsetX, int offsetY,
                                    int width, int height, int fps, boolean showCursor) {
        String source = "ddagrab=output_idx=" + outputIndex +
                ":framerate=" + fps +
                ":draw_mouse=" + (showCursor ? 1 : 0) +
                ":video_size=" + width + "x" + height +
                ":offset_x=" + Math.max(0, offsetX) +
                ":offset_y=" + Math.max(0, offsetY) +
                ":dup_frames=1";
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add(source);
    }

    private static void addDesktopInput(List<String> cmd, java.awt.Rectangle area, int fps, boolean showCursor) {
        // A raw 2560x1600 BGRA frame is ~16 MiB. A queue of 1024 frames could let
        // FFmpeg reserve/retain enormous amounts of memory. Keep only a handful of
        // fresh frames: a screen recorder should drop stale frames, never buffer seconds.
        cmd.add("-thread_queue_size"); cmd.add("8");
        cmd.add("-f"); cmd.add("gdigrab");
        cmd.add("-framerate"); cmd.add(String.valueOf(fps));
        cmd.add("-use_wallclock_as_timestamps"); cmd.add("1");
        cmd.add("-draw_mouse"); cmd.add(showCursor ? "1" : "0");
        cmd.add("-offset_x"); cmd.add(String.valueOf(area.x));
        cmd.add("-offset_y"); cmd.add(String.valueOf(area.y));
        cmd.add("-video_size"); cmd.add(area.width + "x" + area.height);
        cmd.add("-i"); cmd.add("desktop");
    }

    private static void addWindowInput(List<String> cmd, WindowTarget target, int fps, boolean showCursor) {
        cmd.add("-thread_queue_size"); cmd.add("8");
        cmd.add("-f"); cmd.add("gdigrab");
        cmd.add("-framerate"); cmd.add(String.valueOf(fps));
        cmd.add("-use_wallclock_as_timestamps"); cmd.add("1");
        cmd.add("-draw_mouse"); cmd.add(showCursor ? "1" : "0");
        cmd.add("-i"); cmd.add("hwnd=" + target.ffmpegHandle());
    }

    private static String ffmpegColor(int rgb, double alpha) {
        int value = rgb & 0x00FFFFFF;
        return String.format(java.util.Locale.ROOT, "0x%06X@%.2f", value, alpha);
    }

    private static String shapeMaskFilter(String label, int width, int height, int fps, WebcamShape shape) {
        String expr;
        if (shape == WebcamShape.CIRCLE) {
            expr = "if(lte((X-W/2)*(X-W/2)+(Y-H/2)*(Y-H/2),(min(W,H)/2)*(min(W,H)/2)),255,0)";
        } else {
            int radius = Math.max(8, Math.min(width, height) / 9);
            expr = "if(lte((X-clip(X," + radius + ",W-" + radius + "))*(X-clip(X," + radius + ",W-" + radius + "))" +
                    "+(Y-clip(Y," + radius + ",H-" + radius + "))*(Y-clip(Y," + radius + ",H-" + radius + "))," +
                    radius + "*" + radius + "),255,0)";
        }
        return "nullsrc=s=" + width + "x" + height + ":r=" + fps +
                ",format=gray,geq=lum='" + expr + "'[" + label + "]";
    }

    private static String volumeFactor(int percent) {
        return String.format(java.util.Locale.ROOT, "%.3f", Math.max(0, Math.min(200, percent)) / 100.0);
    }

    private static String gateThreshold(int db) {
        double linear = Math.pow(10.0, Math.max(-70, Math.min(-10, db)) / 20.0);
        return String.format(java.util.Locale.ROOT, "%.6f", linear);
    }

    public synchronized void setMicrophoneMuted(boolean muted) {
        microphoneMuted = muted;
        MicrophonePcmSource source = microphoneSource;
        if (source != null) source.setMuted(muted);
    }

    public boolean isMicrophoneMuted() { return microphoneMuted; }

    private void addCpuSettings(List<String> cmd, int qualityPercent) {
        int crf = qualityToCrf(qualityPercent);
        cmd.add("-c:v"); cmd.add("libx264");
        cmd.add("-preset"); cmd.add(qualityPercent >= 90 ? "veryfast" : "faster");
        cmd.add("-crf"); cmd.add(String.valueOf(crf));
        cmd.add("-pix_fmt"); cmd.add("yuv420p");
    }

    private void addNvencSettings(List<String> cmd, int qualityPercent, boolean hardwareFrames) {
        cmd.add("-c:v"); cmd.add("h264_nvenc");
        cmd.add("-gpu"); cmd.add("any");
        if (!hardwareFrames) {
            cmd.add("-pix_fmt"); cmd.add("yuv420p");
        }

        if (qualityPercent >= 100) {
            cmd.add("-preset"); cmd.add("p6");
            cmd.add("-tune"); cmd.add("lossless");
            cmd.add("-rc"); cmd.add("constqp");
            cmd.add("-qp"); cmd.add("0");
        } else {
            // p4/p5 is substantially lighter than p6/p7 and is a better default
            // for real-time desktop capture. CQ still controls visual quality.
            cmd.add("-preset"); cmd.add(qualityPercent >= 90 ? "p5" : "p4");
            cmd.add("-tune"); cmd.add("hq");
            cmd.add("-rc"); cmd.add("vbr");
            cmd.add("-multipass"); cmd.add("disabled");
            cmd.add("-rc-lookahead"); cmd.add("0");
            cmd.add("-cq"); cmd.add(String.valueOf(qualityToNvencCq(qualityPercent)));
            cmd.add("-b:v"); cmd.add("0");
        }
    }

    /** 100 => CRF 0. 50 => CRF ~23. 1 => CRF 45. Resolution is never scaled. */
    static int qualityToCrf(int qualityPercent) {
        int q = Math.max(1, Math.min(100, qualityPercent));
        return Math.max(0, Math.min(45, Math.round((100 - q) * 45f / 99f)));
    }

    /** 100 => CQ 0, 80 => ~10, 50 => ~26, 1 => 51. */
    static int qualityToNvencCq(int qualityPercent) {
        int q = Math.max(1, Math.min(100, qualityPercent));
        return Math.max(0, Math.min(51, Math.round((100 - q) * 51f / 99f)));
    }

    public synchronized void stop(Consumer<String> log) {
        stopping = true;
        if (process == null) {
            stopSources();
            return;
        }

        try {
            if (process.isAlive() && processInput != null) {
                processInput.write("q");
                processInput.newLine();
                processInput.flush();
            }
        } catch (Exception e) {
            log.accept("Could not send graceful stop to FFmpeg: " + e.getMessage());
        }

        stopSources();

        try {
            if (process.isAlive() && !process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process.isAlive()) process.destroyForcibly();
        } finally {
            try { if (processInput != null) processInput.close(); } catch (IOException ignored) {}
            processInput = null;
            process = null;
        }
    }

    private void stopSources() {
        for (AudioPcmSource source : audioSources) {
            try { source.stop(); } catch (Exception ignored) {}
        }
        audioSources.clear();
        microphoneSource = null;
    }
}
