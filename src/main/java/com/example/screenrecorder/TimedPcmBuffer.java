package com.example.screenrecorder;

import java.io.BufferedOutputStream;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * PCM queue with a sample-accurate timeline relative to the moment the capture
 * device was started. It keeps enough pre-roll for FFmpeg/gdigrab startup.
 */
final class TimedPcmBuffer {
    private static final int MAX_QUEUED_CHUNKS = 4096;

    private final int sampleRate;
    private final int channels;
    private final int bytesPerFrame;
    private final BlockingQueue<Chunk> chunks = new ArrayBlockingQueue<>(MAX_QUEUED_CHUNKS);
    private final AtomicLong nextFrame = new AtomicLong();
    private final CountDownLatch captureStarted = new CountDownLatch(1);

    private volatile long captureStartEpochNanos = Long.MIN_VALUE;
    private volatile Throwable startupFailure;

    TimedPcmBuffer(int sampleRate, int channels) {
        if (sampleRate <= 0 || channels <= 0) throw new IllegalArgumentException("Invalid PCM format");
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bytesPerFrame = channels * 2; // s16le
    }

    void markCaptureStarted(long epochNanos) {
        captureStartEpochNanos = epochNanos;
        captureStarted.countDown();
    }

    void markStartupFailure(Throwable failure) {
        startupFailure = failure;
        captureStarted.countDown();
    }

    void awaitCaptureStarted(long timeout, TimeUnit unit) throws Exception {
        if (!captureStarted.await(timeout, unit)) {
            throw new IllegalStateException("Audio capture did not become ready in time");
        }
        if (startupFailure != null) {
            if (startupFailure instanceof Exception e) throw e;
            throw new IllegalStateException(startupFailure);
        }
        if (captureStartEpochNanos == Long.MIN_VALUE) {
            throw new IllegalStateException("Audio capture start time was not initialized");
        }
    }

    long captureStartEpochNanos() {
        return captureStartEpochNanos;
    }

    void append(byte[] data, int length) throws InterruptedException {
        if (length <= 0) return;
        int aligned = length - (length % bytesPerFrame);
        if (aligned <= 0) return;

        byte[] copy = Arrays.copyOf(data, aligned);
        long frames = aligned / bytesPerFrame;
        long firstFrame = nextFrame.getAndAdd(frames);
        chunks.put(new Chunk(firstFrame, frames, copy));
    }

    void appendSilenceFrames(int frames) throws InterruptedException {
        if (frames <= 0) return;
        int remaining = frames;
        // 20 ms chunks keep latency low and allocations bounded.
        int maxFrames = Math.max(1, sampleRate / 50);
        while (remaining > 0) {
            int n = Math.min(remaining, maxFrames);
            append(new byte[n * bytesPerFrame], n * bytesPerFrame);
            remaining -= n;
        }
    }

    void streamAlignedTo(
            long videoStartEpochNanos,
            BufferedOutputStream out,
            BooleanSupplier running,
            Consumer<String> log,
            String sourceName
    ) throws Exception {
        awaitCaptureStarted(5, TimeUnit.SECONDS);

        long start = captureStartEpochNanos;
        long deltaNanos = videoStartEpochNanos - start;
        long targetFrame = deltaNanos <= 0
                ? 0
                : Math.round(deltaNanos * sampleRate / 1_000_000_000d);

        long trimMs = Math.max(0L, Math.round(targetFrame * 1000d / sampleRate));
        if (deltaNanos < 0) {
            log.accept(sourceName + ": capture started " + Math.round(-deltaNanos / 1_000_000d)
                    + " ms after video; starting from first available sample");
        } else {
            log.accept(sourceName + ": automatic pre-roll trim " + trimMs + " ms");
        }

        boolean firstWrite = true;
        while (running.getAsBoolean() || !chunks.isEmpty()) {
            Chunk chunk = chunks.poll(200, TimeUnit.MILLISECONDS);
            if (chunk == null) continue;

            long chunkEnd = chunk.firstFrame + chunk.frameCount;
            if (chunkEnd <= targetFrame) {
                continue;
            }

            int byteOffset = 0;
            if (chunk.firstFrame < targetFrame) {
                long framesToSkip = targetFrame - chunk.firstFrame;
                byteOffset = Math.toIntExact(framesToSkip * bytesPerFrame);
            }

            out.write(chunk.data, byteOffset, chunk.data.length - byteOffset);
            if (firstWrite) {
                out.flush();
                firstWrite = false;
            } else if (chunks.isEmpty()) {
                // Do not let BufferedOutputStream accumulate audible latency.
                out.flush();
            }

            // All later chunks are after the alignment point.
            targetFrame = Long.MIN_VALUE;
        }
        out.flush();
    }

    private record Chunk(long firstFrame, long frameCount, byte[] data) {}
}
