package com.example.screenrecorder;

/** Webcam rectangle relative to the captured video's top-left corner. */
public record WebcamPlacement(int x, int y, int width, int height) {
    public WebcamPlacement evenSized() {
        return new WebcamPlacement(x, y, Math.max(2, width & ~1), Math.max(2, height & ~1));
    }

    public WebcamPlacement clampTo(int captureWidth, int captureHeight) {
        int w = Math.min(Math.max(96, width), Math.max(96, captureWidth));
        int h = Math.min(Math.max(72, height), Math.max(72, captureHeight));
        w &= ~1;
        h &= ~1;
        int nx = Math.max(0, Math.min(x, Math.max(0, captureWidth - w)));
        int ny = Math.max(0, Math.min(y, Math.max(0, captureHeight - h)));
        return new WebcamPlacement(nx, ny, w, h);
    }

    public static WebcamPlacement defaultFor(int captureWidth, int captureHeight) {
        int w = Math.min(360, Math.max(160, captureWidth / 4));
        int h = Math.max(120, (w * 9 / 16));
        if (h > captureHeight / 2) {
            h = Math.max(120, captureHeight / 3);
            w = h * 16 / 9;
        }
        w &= ~1;
        h &= ~1;
        int margin = Math.max(12, Math.min(24, Math.min(captureWidth, captureHeight) / 30));
        return new WebcamPlacement(
                Math.max(0, captureWidth - w - margin),
                Math.max(0, captureHeight - h - margin),
                w, h).clampTo(captureWidth, captureHeight);
    }
}
