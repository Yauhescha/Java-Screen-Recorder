package com.example.screenrecorder;

/** A DirectShow video capture device exposed by FFmpeg on Windows. */
public record WebcamDevice(String name) {
    @Override
    public String toString() {
        return name;
    }
}
