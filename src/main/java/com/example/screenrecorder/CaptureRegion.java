package com.example.screenrecorder;

public record CaptureRegion(int x, int y, int width, int height) {
    public CaptureRegion {
        if (width < 2 || height < 2) throw new IllegalArgumentException("Invalid capture region");
    }

    public CaptureRegion evenSized() {
        int w = (width & 1) == 0 ? width : width - 1;
        int h = (height & 1) == 0 ? height : height - 1;
        return new CaptureRegion(x, y, Math.max(2, w), Math.max(2, h));
    }
}
