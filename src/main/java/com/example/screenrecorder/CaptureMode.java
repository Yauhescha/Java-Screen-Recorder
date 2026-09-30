package com.example.screenrecorder;

public enum CaptureMode {
    FULL_SCREEN("All monitors"),
    MONITORS("Selected monitor(s)"),
    REGION("Resizable region"),
    WINDOW("Window");

    private final String label;
    CaptureMode(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
