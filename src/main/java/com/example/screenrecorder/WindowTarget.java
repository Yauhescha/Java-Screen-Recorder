package com.example.screenrecorder;

import java.awt.Rectangle;

/** A visible top-level Windows window that can be captured by gdigrab via HWND. */
public record WindowTarget(
        long hwnd,
        String title,
        int processId,
        Rectangle bounds
) {
    public WindowTarget {
        bounds = new Rectangle(bounds);
    }

    @Override public Rectangle bounds() {
        return new Rectangle(bounds);
    }

    public String ffmpegHandle() {
        return "0x" + Long.toUnsignedString(hwnd, 16);
    }

    @Override public String toString() {
        String text = title == null ? "" : title.trim();
        if (text.length() > 72) text = text.substring(0, 69) + "...";
        return text + "  [" + bounds.width + "×" + bounds.height + "]";
    }
}
