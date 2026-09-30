package com.example.screenrecorder;

/** Receives normalized 0..1 peak levels from the live PCM capture paths. */
public interface AudioLevelListener {
    AudioLevelListener NONE = new AudioLevelListener() {};

    default void onSystemLevel(double level) {}
    default void onMicrophoneLevel(double level) {}
}
