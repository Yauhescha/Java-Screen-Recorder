package com.example.screenrecorder;

@FunctionalInterface
public interface RecordingStatsListener {
    RecordingStatsListener NONE = stats -> {};
    void onStats(RecordingStats stats);
}
