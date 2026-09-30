package com.example.screenrecorder;

import java.util.function.DoubleConsumer;

/** Lightweight peak meter for little-endian signed 16-bit PCM. */
final class PcmLevelTracker {
    private static final long EMIT_INTERVAL_NS = 40_000_000L; // 25 Hz
    private final DoubleConsumer consumer;
    private volatile long nextEmitNs;

    PcmLevelTracker(DoubleConsumer consumer) {
        this.consumer = consumer != null ? consumer : value -> {};
    }

    void accept(byte[] data, int length) {
        long now = System.nanoTime();
        if (now < nextEmitNs || data == null || length < 2) return;
        nextEmitNs = now + EMIT_INTERVAL_NS;
        int aligned = length & ~1;
        int peak = 0;
        for (int i = 0; i < aligned; i += 2) {
            int sample = (short) ((data[i] & 0xff) | (data[i + 1] << 8));
            int abs = sample == Short.MIN_VALUE ? 32768 : Math.abs(sample);
            if (abs > peak) peak = abs;
        }
        consumer.accept(Math.min(1.0, peak / 32768.0));
    }

    void silence() {
        long now = System.nanoTime();
        if (now < nextEmitNs) return;
        nextEmitNs = now + EMIT_INTERVAL_NS;
        consumer.accept(0.0);
    }

    void reset() { consumer.accept(0.0); }
}
