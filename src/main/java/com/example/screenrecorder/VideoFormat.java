package com.example.screenrecorder;

public enum VideoFormat {
    MP4("MP4", ".mp4"),
    MKV("Matroska (MKV)", ".mkv"),
    MOV("QuickTime (MOV)", ".mov");

    private final String label;
    private final String extension;

    VideoFormat(String label, String extension) {
        this.label = label;
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public boolean supportsFastStart() {
        return this == MP4 || this == MOV;
    }

    public static VideoFormat fromPath(java.nio.file.Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        for (VideoFormat value : values()) {
            if (name.endsWith(value.extension)) return value;
        }
        return MP4;
    }

    @Override
    public String toString() {
        return label;
    }
}
