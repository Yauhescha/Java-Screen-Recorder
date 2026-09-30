package com.example.screenrecorder;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native Windows enumeration for physical monitor pixels and real top-level HWNDs. */
final class WindowsCaptureService {
    private static final int MONITORINFOF_PRIMARY = 0x00000001;
    private static final int MDT_EFFECTIVE_DPI = 0;

    List<DisplayMonitor> listMonitors() {
        if (!isWindows()) return List.of();
        List<DisplayMonitor> result = new ArrayList<>();
        try {
            User32.MonitorEnumProc callback = (hMonitor, hdc, rect, data) -> {
                MONITORINFOEXW info = new MONITORINFOEXW();
                info.cbSize = info.size();
                if (User32.INSTANCE.GetMonitorInfoW(hMonitor, info) != 0) {
                    info.read();
                    int[] dpi = dpiForMonitor(hMonitor);
                    Rectangle bounds = info.rcMonitor.toRectangle();
                    String device = Native.toString(info.szDevice);
                    String id = device.isBlank()
                            ? "display@" + bounds.x + "," + bounds.y
                            : device;
                    String friendly = device.isBlank()
                            ? "Display " + (result.size() + 1)
                            : device;
                    result.add(new DisplayMonitor(
                            id,
                            friendly,
                            bounds,
                            (info.dwFlags & MONITORINFOF_PRIMARY) != 0,
                            dpi[0], dpi[1]));
                }
                return 1;
            };
            User32.INSTANCE.EnumDisplayMonitors(null, null, callback, null);
        } catch (Throwable ignored) {
        }
        result.sort(Comparator
                .comparing(DisplayMonitor::primary).reversed()
                .thenComparingInt(m -> m.bounds().x)
                .thenComparingInt(m -> m.bounds().y));
        return List.copyOf(result);
    }

    List<WindowTarget> listWindows() {
        if (!isWindows()) return List.of();
        Map<Long, WindowTarget> result = new LinkedHashMap<>();
        long currentPid = ProcessHandle.current().pid();
        try {
            User32.WindowEnumProc callback = (hwnd, data) -> {
                try {
                    if (hwnd == null || Pointer.nativeValue(hwnd) == 0) return 1;
                    if (User32.INSTANCE.IsWindowVisible(hwnd) == 0) return 1;
                    if (User32.INSTANCE.IsIconic(hwnd) != 0) return 1;

                    int titleLength = User32.INSTANCE.GetWindowTextLengthW(hwnd);
                    if (titleLength <= 0) return 1;
                    char[] buffer = new char[Math.min(8192, titleLength + 1)];
                    int copied = User32.INSTANCE.GetWindowTextW(hwnd, buffer, buffer.length);
                    if (copied <= 0) return 1;
                    String title = Native.toString(buffer).trim();
                    if (title.isBlank()) return 1;

                    IntByReference pid = new IntByReference();
                    User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
                    if (Integer.toUnsignedLong(pid.getValue()) == currentPid) return 1;

                    RECT rect = new RECT();
                    if (User32.INSTANCE.GetWindowRect(hwnd, rect) == 0) return 1;
                    Rectangle bounds = rect.toRectangle();
                    if (bounds.width < 80 || bounds.height < 50) return 1;

                    long value = Pointer.nativeValue(hwnd);
                    result.put(value, new WindowTarget(value, title, pid.getValue(), bounds));
                } catch (Throwable ignored) {
                }
                return 1;
            };
            User32.INSTANCE.EnumWindows(callback, null);
        } catch (Throwable ignored) {
        }
        List<WindowTarget> windows = new ArrayList<>(result.values());
        windows.sort(Comparator.comparing(WindowTarget::title, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(windows);
    }

    static Rectangle unionBounds(List<DisplayMonitor> monitors) {
        Rectangle union = null;
        for (DisplayMonitor monitor : monitors) {
            Rectangle b = monitor.bounds();
            union = union == null ? new Rectangle(b) : union.union(b);
        }
        return union == null ? new Rectangle(0, 0, 2, 2) : union;
    }

    private static int[] dpiForMonitor(Pointer monitor) {
        try {
            IntByReference x = new IntByReference(96);
            IntByReference y = new IntByReference(96);
            int hr = Shcore.INSTANCE.GetDpiForMonitor(monitor, MDT_EFFECTIVE_DPI, x, y);
            if (hr == 0) return new int[]{x.getValue(), y.getValue()};
        } catch (Throwable ignored) {
        }
        return new int[]{96, 96};
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @Structure.FieldOrder({"left", "top", "right", "bottom"})
    public static class RECT extends Structure {
        public int left;
        public int top;
        public int right;
        public int bottom;

        Rectangle toRectangle() {
            return new Rectangle(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        }
    }

    @Structure.FieldOrder({"cbSize", "rcMonitor", "rcWork", "dwFlags", "szDevice"})
    public static class MONITORINFOEXW extends Structure {
        public int cbSize;
        public RECT rcMonitor = new RECT();
        public RECT rcWork = new RECT();
        public int dwFlags;
        public char[] szDevice = new char[32];
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);

        interface MonitorEnumProc extends StdCallCallback {
            int invoke(Pointer hMonitor, Pointer hdcMonitor, RECT lprcMonitor, Pointer dwData);
        }

        interface WindowEnumProc extends StdCallCallback {
            int invoke(Pointer hWnd, Pointer data);
        }

        int EnumDisplayMonitors(Pointer hdc, RECT clip, MonitorEnumProc callback, Pointer data);
        int GetMonitorInfoW(Pointer hMonitor, MONITORINFOEXW info);

        int EnumWindows(WindowEnumProc callback, Pointer data);
        int IsWindowVisible(Pointer hWnd);
        int IsIconic(Pointer hWnd);
        int GetWindowTextLengthW(Pointer hWnd);
        int GetWindowTextW(Pointer hWnd, char[] buffer, int maxCount);
        int GetWindowRect(Pointer hWnd, RECT rect);
        int GetWindowThreadProcessId(Pointer hWnd, IntByReference processId);
    }

    private interface Shcore extends StdCallLibrary {
        Shcore INSTANCE = Native.load("shcore", Shcore.class);
        int GetDpiForMonitor(Pointer hMonitor, int dpiType, IntByReference dpiX, IntByReference dpiY);
    }
}
