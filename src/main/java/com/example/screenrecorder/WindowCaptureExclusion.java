package com.example.screenrecorder;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;

import java.awt.Window;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Windows 10 2004+ supports WDA_EXCLUDEFROMCAPTURE for top-level windows.
 * It is used for the recorder UI and the local webcam preview so those helper
 * windows remain visible to the user without becoming part of the recording.
 */
final class WindowCaptureExclusion {
    private static final int WDA_NONE = 0x00000000;
    private static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;

    private WindowCaptureExclusion() {}

    static boolean setExcluded(Window window, boolean excluded, Consumer<String> log) {
        return setExcluded(window, excluded, "Recorder window", log);
    }

    static boolean setExcluded(Window window, boolean excluded, String label, Consumer<String> log) {
        if (window == null || !window.isDisplayable()) return false;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return false;

        String subject = label == null || label.isBlank() ? "Window" : label;
        try {
            Pointer hwnd = Native.getComponentPointer(window);
            int result = User32.INSTANCE.SetWindowDisplayAffinity(
                    hwnd,
                    excluded ? WDA_EXCLUDEFROMCAPTURE : WDA_NONE);
            if (result != 0) {
                if (log != null) {
                    log.accept(excluded
                            ? subject + " exclusion enabled (WDA_EXCLUDEFROMCAPTURE)."
                            : subject + " exclusion disabled.");
                }
                return true;
            }
            if (log != null) {
                log.accept(subject + " exclusion is not supported by this Windows/capture path. " +
                        "The recording will continue normally.");
            }
            return false;
        } catch (Throwable e) {
            if (log != null) log.accept(subject + " exclusion failed: " + e.getMessage());
            return false;
        }
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);
        int SetWindowDisplayAffinity(Pointer hWnd, int dwAffinity);
    }
}
