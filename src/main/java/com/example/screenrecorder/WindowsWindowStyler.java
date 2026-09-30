package com.example.screenrecorder;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.awt.*;

final class WindowsWindowStyler {
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19;
    private static final int DWMWA_BORDER_COLOR = 34;
    private static final int DWMWA_CAPTION_COLOR = 35;
    private static final int DWMWA_TEXT_COLOR = 36;

    private WindowsWindowStyler() {}

    static void apply(Window window) {
        if (!isWindows() || window == null || !window.isDisplayable()) return;

        try {
            Pointer hwnd = Native.getWindowPointer(window);
            if (hwnd == null) return;

            IntByReference enabled = new IntByReference(1);
            int result = DwmApi.INSTANCE.DwmSetWindowAttribute(
                    hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, enabled, Integer.BYTES);
            if (result != 0) {
                DwmApi.INSTANCE.DwmSetWindowAttribute(
                        hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, enabled, Integer.BYTES);
            }

            setColor(hwnd, DWMWA_CAPTION_COLOR, AppTheme.BG);
            setColor(hwnd, DWMWA_BORDER_COLOR, AppTheme.BORDER);
            setColor(hwnd, DWMWA_TEXT_COLOR, AppTheme.TEXT);
        } catch (Throwable ignored) {
            // Older Windows versions may not support these DWM attributes.
        }
    }

    private static void setColor(Pointer hwnd, int attribute, Color color) {
        int colorRef = color.getRed() | (color.getGreen() << 8) | (color.getBlue() << 16);
        DwmApi.INSTANCE.DwmSetWindowAttribute(
                hwnd, attribute, new IntByReference(colorRef), Integer.BYTES);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private interface DwmApi extends Library {
        DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);

        int DwmSetWindowAttribute(Pointer hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);
    }
}
