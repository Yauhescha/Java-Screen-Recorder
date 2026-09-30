package com.example.screenrecorder;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;

import java.util.Locale;
import java.util.function.Consumer;

/** Enables per-monitor DPI awareness before Swing/AWT creates native windows. */
final class DpiAwareness {
    private DpiAwareness() {}

    static void enablePerMonitorV2(Consumer<String> log) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return;
        try {
            // DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 == (HANDLE)-4
            int ok = User32.INSTANCE.SetProcessDpiAwarenessContext(new Pointer(-4L));
            if (ok != 0) {
                if (log != null) log.accept("Per-monitor DPI awareness v2 enabled.");
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            int ok = User32.INSTANCE.SetProcessDPIAware();
            if (log != null) log.accept(ok != 0
                    ? "Legacy Windows DPI awareness enabled."
                    : "Windows DPI awareness could not be changed (it may already be configured by the JVM)." );
        } catch (Throwable e) {
            if (log != null) log.accept("DPI awareness setup skipped: " + e.getMessage());
        }
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);
        int SetProcessDpiAwarenessContext(Pointer value);
        int SetProcessDPIAware();
    }
}
