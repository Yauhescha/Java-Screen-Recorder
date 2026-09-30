package com.example.screenrecorder;

import java.awt.Rectangle;

/** Physical Win32 monitor geometry used directly by gdigrab. */
public record DisplayMonitor(
        String id,
        String name,
        Rectangle bounds,
        boolean primary,
        int dpiX,
        int dpiY
) {
    public DisplayMonitor {
        bounds = new Rectangle(bounds);
    }

    public int scalePercent() {
        int dpi = dpiX > 0 ? dpiX : 96;
        return Math.max(100, Math.round(dpi * 100f / 96f));
    }

    @Override public Rectangle bounds() {
        return new Rectangle(bounds);
    }

    @Override public String toString() {
        return (primary ? "Primary · " : "") + name + " · " + bounds.width + "×" + bounds.height +
                " · " + scalePercent() + "%" + (bounds.x != 0 || bounds.y != 0 ?
                " · (" + bounds.x + ", " + bounds.y + ")" : "");
    }
}
