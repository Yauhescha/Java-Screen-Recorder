package com.example.screenrecorder;

public interface RecordingHealthListener {
    RecordingHealthListener NONE = new RecordingHealthListener() {};

    default void onMicrophoneFailure(String message) {}
    default void onRecorderFailure(String message) {}
}
