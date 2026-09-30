package com.example.screenrecorder;

public enum WebcamShape {
    RECTANGLE("Rectangle"),
    ROUNDED("Rounded"),
    CIRCLE("Circle");

    private final String label;
    WebcamShape(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
