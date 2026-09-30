package com.example.screenrecorder;

public record MicrophoneDevice(String id, String label) {
    @Override public String toString() { return label; }
}
