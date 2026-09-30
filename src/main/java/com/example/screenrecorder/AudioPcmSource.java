package com.example.screenrecorder;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

interface AudioPcmSource extends AutoCloseable {
    void prepare() throws Exception;
    void start(CaptureTimeline timeline, Consumer<String> log) throws Exception;
    void awaitReady(long timeout, TimeUnit unit) throws Exception;
    String inputUrl();
    int sampleRate();
    int channels();
    void stop();
    @Override default void close() { stop(); }
}
