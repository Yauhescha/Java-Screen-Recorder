package com.example.screenrecorder;

/** Live FFmpeg encoding statistics for the current recording session. */
public record RecordingStats(long frames, double fps, long droppedFrames) {
    public static final RecordingStats ZERO = new RecordingStats(0, 0.0, 0);
}
