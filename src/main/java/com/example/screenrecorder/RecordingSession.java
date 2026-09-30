package com.example.screenrecorder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * High-level recording session with pause/resume and crash recovery.
 *
 * Every active segment is written as MKV into a persistent recovery directory.
 * On a normal Stop all segments are remuxed into the requested output container without
 * re-encoding, then the recovery directory is removed. If the JVM/PC/FFmpeg
 * crashes, the MKV data and manifest remain and can be recovered next launch.
 */
public final class RecordingSession {
    public enum State { IDLE, RECORDING, PAUSED }

    private final FfmpegRecorder recorder = new FfmpegRecorder();
    private final RecoveryService recoveryService = new RecoveryService();
    private final List<Path> segments = new ArrayList<>();

    private State state = State.IDLE;
    private RecorderConfig baseConfig;
    private Consumer<String> log;
    private Path recoveryDir;
    private int segmentIndex;
    private AudioLevelListener audioLevels = AudioLevelListener.NONE;
    private boolean microphoneMuted;
    private RecordingStatsListener statsListener = RecordingStatsListener.NONE;
    private RecordingHealthListener healthListener = RecordingHealthListener.NONE;
    private volatile long completedFrames;
    private volatile long completedDroppedFrames;
    private volatile RecordingStats currentStats = RecordingStats.ZERO;

    public synchronized State state() { return state; }
    public synchronized boolean isActive() { return state != State.IDLE; }
    public synchronized boolean isRecording() { return state == State.RECORDING; }
    public synchronized boolean isPaused() { return state == State.PAUSED; }

    public synchronized void start(RecorderConfig config, Consumer<String> log, AudioLevelListener levels) throws Exception {
        start(config, log, levels, RecordingStatsListener.NONE, RecordingHealthListener.NONE);
    }

    public synchronized void start(RecorderConfig config, Consumer<String> log, AudioLevelListener levels,
                                   RecordingStatsListener stats, RecordingHealthListener health) throws Exception {
        if (state != State.IDLE) throw new IllegalStateException("Recording session is already active");

        this.baseConfig = config;
        this.log = log;
        this.audioLevels = levels != null ? levels : AudioLevelListener.NONE;
        this.microphoneMuted = false;
        this.statsListener = stats != null ? stats : RecordingStatsListener.NONE;
        this.healthListener = health != null ? health : RecordingHealthListener.NONE;
        this.completedFrames = 0;
        this.completedDroppedFrames = 0;
        this.currentStats = RecordingStats.ZERO;
        this.segments.clear();
        this.segmentIndex = 0;

        Path parent = config.outputFile().toAbsolutePath().getParent();
        Files.createDirectories(parent);
        recoveryDir = recoveryService.createSession(parent, config.outputFile());
        log.accept("Crash recovery enabled: " + recoveryDir);

        try {
            startNextSegment();
            state = State.RECORDING;
            recoveryService.updateState(recoveryDir, baseConfig.outputFile(), "RECORDING");
        } catch (Exception e) {
            RecoveryService.deleteSession(recoveryDir);
            reset();
            throw e;
        }
    }

    public synchronized void pause() {
        if (state != State.RECORDING) return;
        recorder.stop(log);
        accumulateSegmentStats();
        state = State.PAUSED;
        recoveryService.updateState(recoveryDir, baseConfig.outputFile(), "PAUSED");
        log.accept("Recording paused. Current segment is safely stored.");
    }

    public synchronized void resume() throws Exception {
        if (state != State.PAUSED) return;
        startNextSegment();
        state = State.RECORDING;
        recoveryService.updateState(recoveryDir, baseConfig.outputFile(), "RECORDING");
        log.accept("Recording resumed.");
    }

    /** Stops the session, remuxes recoverable MKV segments into the selected output container and returns its path. */
    public synchronized Path stop() throws Exception {
        if (state == State.IDLE) return null;

        if (state == State.RECORDING) {
            recorder.stop(log);
            accumulateSegmentStats();
        }

        Path finalOutput = baseConfig.outputFile().toAbsolutePath();
        recoveryService.updateState(recoveryDir, finalOutput, "FINALIZING");
        try {
            List<Path> valid = RecoveryService.listValidSegments(recoveryDir);
            if (valid.isEmpty()) throw new IllegalStateException("No valid video segment was produced.");

            RecoveryService.finalizeSegmentsResilient(baseConfig.ffmpegPath(), recoveryDir, valid, finalOutput, log);
            log.accept("Final video: " + finalOutput);
            RecoveryService.deleteSession(recoveryDir);
            return finalOutput;
        } catch (Exception e) {
            log.accept("Finalization failed, but recoverable MKV data was kept in: " + recoveryDir);
            throw e;
        } finally {
            reset();
        }
    }

    /** Discards the recording intentionally; crash-recovery data is removed. */
    public synchronized void abort() {
        try { recorder.stop(s -> {}); } catch (Exception ignored) {}
        RecoveryService.deleteSession(recoveryDir);
        reset();
    }

    private void startNextSegment() throws Exception {
        Path part = recoveryDir.resolve(String.format("part_%04d.mkv", ++segmentIndex));
        RecorderConfig segmentConfig = baseConfig.withOutputFile(part);

        try {
            recorder.setMicrophoneMuted(microphoneMuted);
            recorder.start(segmentConfig, log, audioLevels, this::onSegmentStats, healthListener);
        } catch (Exception first) {
            if (segmentConfig.videoEncoder() == VideoEncoder.NVIDIA_NVENC) {
                log.accept("NVENC could not start this recording. Falling back to CPU H.264 automatically.");
                baseConfig = baseConfig.withVideoEncoder(VideoEncoder.CPU_X264);
                segmentConfig = baseConfig.withOutputFile(part);
                recorder.setMicrophoneMuted(microphoneMuted);
                recorder.start(segmentConfig, log, audioLevels, this::onSegmentStats, healthListener);
            } else {
                throw first;
            }
        }

        segments.add(part);
        log.accept("Recording segment started: " + part.getFileName() + " [" + baseConfig.videoEncoder() + "]");
    }


    private void onSegmentStats(RecordingStats segmentStats) {
        RecordingStats combined = new RecordingStats(
                completedFrames + segmentStats.frames(),
                segmentStats.fps(),
                completedDroppedFrames + segmentStats.droppedFrames());
        currentStats = combined;
        statsListener.onStats(combined);
    }

    private void accumulateSegmentStats() {
        RecordingStats segment = recorder.lastStats();
        completedFrames += segment.frames();
        completedDroppedFrames += segment.droppedFrames();
        currentStats = new RecordingStats(completedFrames, 0.0, completedDroppedFrames);
        statsListener.onStats(currentStats);
    }

    public RecordingStats stats() { return currentStats; }

    public synchronized void setMicrophoneMuted(boolean muted) {
        microphoneMuted = muted;
        recorder.setMicrophoneMuted(muted);
        if (log != null) log.accept("Microphone " + (muted ? "muted" : "unmuted") + ".");
    }

    public synchronized boolean isMicrophoneMuted() { return microphoneMuted; }

    private void reset() {
        state = State.IDLE;
        baseConfig = null;
        log = null;
        segments.clear();
        segmentIndex = 0;
        recoveryDir = null;
        audioLevels = AudioLevelListener.NONE;
        statsListener = RecordingStatsListener.NONE;
        healthListener = RecordingHealthListener.NONE;
        completedFrames = 0;
        completedDroppedFrames = 0;
        currentStats = RecordingStats.ZERO;
        microphoneMuted = false;
    }
}
