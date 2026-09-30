package com.example.screenrecorder;

import javax.sound.sampled.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class MicrophonePcmSource extends TcpPcmSource {
    private static final List<AudioFormat> CANDIDATES = List.of(
            pcm(48_000, 1), pcm(48_000, 2), pcm(44_100, 1), pcm(44_100, 2), pcm(32_000, 1), pcm(16_000, 1)
    );

    private final MicrophoneDevice device;
    private final MicrophoneService service;
    private TargetDataLine line;
    private AudioFormat format;
    private final PcmLevelTracker levelTracker;
    private volatile boolean muted;
    private final Consumer<String> failureConsumer;
    private volatile Thread healthWorker;
    private volatile long lastPcmNanos;
    private volatile boolean captureStarted;
    private final java.util.concurrent.atomic.AtomicBoolean failureReported = new java.util.concurrent.atomic.AtomicBoolean();

    MicrophonePcmSource(MicrophoneDevice device, MicrophoneService service, AudioLevelListener levels,
                        Consumer<String> failureConsumer) {
        this.device = device;
        this.service = service;
        AudioLevelListener safe = levels != null ? levels : AudioLevelListener.NONE;
        this.levelTracker = new PcmLevelTracker(safe::onMicrophoneLevel);
        this.failureConsumer = failureConsumer != null ? failureConsumer : message -> {};
    }

    void setMuted(boolean muted) { this.muted = muted; }

    private static AudioFormat pcm(float rate, int channels) {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels,
                channels * 2, rate, false);
    }

    @Override
    public void prepare() throws Exception {
        super.prepare();
        if (line != null) return;

        Mixer mixer = service.findMixer(device);
        Exception last = null;
        for (AudioFormat candidate : CANDIDATES) {
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, candidate);
                TargetDataLine candidateLine;
                if (mixer == null) {
                    if (!AudioSystem.isLineSupported(info)) continue;
                    candidateLine = (TargetDataLine) AudioSystem.getLine(info);
                } else {
                    if (!mixer.isLineSupported(info)) continue;
                    candidateLine = (TargetDataLine) mixer.getLine(info);
                }

                // About 200 ms device buffer; Java reads it in smaller chunks below.
                candidateLine.open(candidate,
                        Math.max(4096, (int) candidate.getSampleRate() * candidate.getFrameSize() / 5));
                line = candidateLine;
                format = candidateLine.getFormat();
                return;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new LineUnavailableException("Cannot open microphone" +
                (last == null ? "" : ": " + last.getMessage()));
    }

    @Override public int sampleRate() {
        if (format == null) throw new IllegalStateException("Microphone is not prepared");
        return Math.round(format.getSampleRate());
    }

    @Override public int channels() {
        if (format == null) throw new IllegalStateException("Microphone is not prepared");
        return format.getChannels();
    }

    @Override
    public void start(CaptureTimeline timeline, Consumer<String> log) {
        running = true;
        captureStarted = false;
        lastPcmNanos = System.nanoTime();
        failureReported.set(false);
        pcmBuffer = new TimedPcmBuffer(sampleRate(), channels());
        startSender(timeline, log, "Microphone");

        captureWorker = new Thread(() -> {
            try {
                // Discard anything that might have accumulated while the line was open but stopped.
                line.flush();
                line.start();
                line.flush();
                captureStarted = true;
                lastPcmNanos = System.nanoTime();

                long started = CaptureTimeline.epochNanosNow();
                pcmBuffer.markCaptureStarted(started);
                log.accept("Microphone armed before video start: " + device +
                        " (" + sampleRate() + " Hz, " + channels() + " ch)");

                // ~20 ms chunks keep pre-roll timing granular without excessive overhead.
                int bytesPerFrame = channels() * 2;
                int targetBytes = Math.max(bytesPerFrame,
                        (sampleRate() / 50) * bytesPerFrame);
                byte[] buffer = new byte[targetBytes];

                while (running) {
                    int n = line.read(buffer, 0, buffer.length);
                    if (n <= 0 && (!line.isOpen() || !line.isActive())) {
                        throw new LineUnavailableException("Microphone device stopped or was disconnected");
                    }
                    if (n > 0) {
                        lastPcmNanos = System.nanoTime();
                        levelTracker.accept(buffer, n);
                        if (muted) {
                            byte[] silence = new byte[n];
                            pcmBuffer.append(silence, n);
                        } else {
                            pcmBuffer.append(buffer, n);
                        }
                    }
                }
            } catch (Exception e) {
                pcmBuffer.markStartupFailure(e);
                if (running) {
                    String message = "Microphone disconnected or stopped: " + e.getMessage();
                    log.accept(message);
                    reportFailure(message);
                }
            } finally {
                running = false;
            }
        }, "microphone-capture");
        captureWorker.setDaemon(true);
        captureWorker.start();

        healthWorker = new Thread(() -> {
            try {
                while (running) {
                    Thread.sleep(500);
                    if (captureStarted && System.nanoTime() - lastPcmNanos > TimeUnit.SECONDS.toNanos(4)) {
                        String message = "Microphone stopped delivering audio for 4 seconds; it may have been disconnected.";
                        log.accept(message);
                        reportFailure(message);
                        running = false;
                        TargetDataLine active = line;
                        if (active != null) {
                            try { active.close(); } catch (Exception ignored) {}
                        }
                        closeServer();
                        break;
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "microphone-health");
        healthWorker.setDaemon(true);
        healthWorker.start();
    }

    private void reportFailure(String message) {
        if (failureReported.compareAndSet(false, true)) failureConsumer.accept(message);
    }

    @Override
    public void stop() {
        running = false;
        if (line != null) {
            try { line.stop(); } catch (Exception ignored) {}
            try { line.flush(); } catch (Exception ignored) {}
            try { line.close(); } catch (Exception ignored) {}
            line = null;
        }
        closeServer();
        interruptWorkers();
        if (healthWorker != null) healthWorker.interrupt();
        healthWorker = null;
        captureStarted = false;
        levelTracker.reset();
    }
}
