package com.example.screenrecorder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared start timeline for video and audio.
 *
 * gdigrab exposes the real wall-clock timestamp of its first video frame in
 * FFmpeg's input log ("start: <unix-seconds>"). Audio sources are started
 * before FFmpeg and keep a short PCM pre-roll. When the video timestamp becomes
 * known, each audio source drops only the pre-roll that happened before that
 * exact video start and then streams the remaining samples from PTS 0.
 */
final class CaptureTimeline {
    private static final long UNSET = Long.MIN_VALUE;

    private final AtomicLong videoStartEpochNanos = new AtomicLong(UNSET);
    private final CountDownLatch videoStartLatch = new CountDownLatch(1);
    private volatile long ffmpegLaunchEpochNanos = UNSET;

    void markFfmpegLaunch() {
        ffmpegLaunchEpochNanos = epochNanosNow();
    }

    boolean trySetVideoStartSeconds(double unixSeconds) {
        if (!Double.isFinite(unixSeconds) || unixSeconds < 100_000_000d) {
            return false;
        }

        long nanos = Math.round(unixSeconds * 1_000_000_000d);
        if (videoStartEpochNanos.compareAndSet(UNSET, nanos)) {
            videoStartLatch.countDown();
            return true;
        }
        return false;
    }

    long awaitVideoStartNanos(long timeout, TimeUnit unit) throws InterruptedException {
        if (videoStartLatch.await(timeout, unit)) {
            return videoStartEpochNanos.get();
        }
        long fallback = ffmpegLaunchEpochNanos;
        if (fallback == UNSET) {
            fallback = epochNanosNow();
        }
        return fallback;
    }

    boolean hasExactVideoStart() {
        return videoStartEpochNanos.get() != UNSET;
    }

    static long epochNanosNow() {
        return System.currentTimeMillis() * 1_000_000L;
    }
}
