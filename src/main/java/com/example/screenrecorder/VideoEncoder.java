package com.example.screenrecorder;

public enum VideoEncoder {
    AUTO("Auto (NVENC if available)"),
    NVIDIA_NVENC("NVIDIA NVENC (GPU)"),
    CPU_X264("H.264 CPU (libx264)");

    private final String label;

    VideoEncoder(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
