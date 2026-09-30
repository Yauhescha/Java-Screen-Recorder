package com.example.screenrecorder;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight Windows global hotkey listener based on GetAsyncKeyState.
 * It works even while the recorder is minimized or another application has focus.
 */
final class GlobalHotkeyService implements AutoCloseable {
    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);
        short GetAsyncKeyState(int vKey);
    }

    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean suspended;
    private volatile long suspendedUntilNanos;

    private volatile Hotkey startStopHotkey = new Hotkey(KeyEvent.VK_F12, 0);
    private volatile Hotkey screenshotHotkey = new Hotkey(KeyEvent.VK_F11, 0);
    private volatile Hotkey muteMicrophoneHotkey = new Hotkey(KeyEvent.VK_F10, 0);
    private volatile Runnable startStopAction = () -> {};
    private volatile Runnable screenshotAction = () -> {};
    private volatile Runnable muteMicrophoneAction = () -> {};

    private Thread thread;

    void setStartStopHotkey(Hotkey hotkey) {
        startStopHotkey = hotkey;
    }

    void setScreenshotHotkey(Hotkey hotkey) {
        screenshotHotkey = hotkey;
    }

    void setMuteMicrophoneHotkey(Hotkey hotkey) {
        muteMicrophoneHotkey = hotkey;
    }

    void setStartStopAction(Runnable action) {
        startStopAction = action != null ? action : () -> {};
    }

    void setScreenshotAction(Runnable action) {
        screenshotAction = action != null ? action : () -> {};
    }

    void setMuteMicrophoneAction(Runnable action) {
        muteMicrophoneAction = action != null ? action : () -> {};
    }

    void setSuspended(boolean value) {
        suspended = value;
    }

    void suppressForMillis(long millis) {
        suspendedUntilNanos = Math.max(suspendedUntilNanos,
                System.nanoTime() + millis * 1_000_000L);
    }

    void start() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        thread = new Thread(this::loop, "global-hotkey-listener");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop() {
        boolean previousStartStop = false;
        boolean previousScreenshot = false;
        boolean previousMuteMicrophone = false;

        while (running.get()) {
            try {
                Hotkey ss = startStopHotkey;
                Hotkey shot = screenshotHotkey;
                Hotkey mute = muteMicrophoneHotkey;
                boolean ssDown = isPressed(ss);
                boolean shotDown = isPressed(shot);
                boolean muteDown = isPressed(mute);
                boolean blocked = suspended || System.nanoTime() < suspendedUntilNanos;

                if (!blocked) {
                    if (ssDown && !previousStartStop) {
                        safeRun(startStopAction);
                    }
                    if (shotDown && !previousScreenshot) {
                        safeRun(screenshotAction);
                    }
                    if (muteDown && !previousMuteMicrophone) {
                        safeRun(muteMicrophoneAction);
                    }
                }

                // Always track the physical state while suspended. This prevents
                // the key used to configure a hotkey from immediately firing it.
                previousStartStop = ssDown;
                previousScreenshot = shotDown;
                previousMuteMicrophone = muteDown;
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ignored) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void safeRun(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable ignored) {
        }
    }

    private static boolean isPressed(Hotkey hotkey) {
        if (hotkey == null) return false;
        if (!isDown(toWindowsVk(hotkey.keyCode()))) return false;
        int modifiers = hotkey.modifiers();
        if ((modifiers & Hotkey.CTRL) != 0 && !isDown(0x11)) return false;
        if ((modifiers & Hotkey.ALT) != 0 && !isDown(0x12)) return false;
        if ((modifiers & Hotkey.SHIFT) != 0 && !isDown(0x10)) return false;
        if ((modifiers & Hotkey.WIN) != 0 && !(isDown(0x5B) || isDown(0x5C))) return false;
        return true;
    }

    private static boolean isDown(int virtualKey) {
        return (User32.INSTANCE.GetAsyncKeyState(virtualKey) & 0x8000) != 0;
    }

    private static int toWindowsVk(int javaKeyCode) {
        return switch (javaKeyCode) {
            case KeyEvent.VK_ENTER -> 0x0D;
            case KeyEvent.VK_INSERT -> 0x2D;
            case KeyEvent.VK_DELETE -> 0x2E;
            case KeyEvent.VK_PRINTSCREEN -> 0x2C;
            default -> javaKeyCode;
        };
    }

    @Override public void close() {
        running.set(false);
        Thread t = thread;
        if (t != null) t.interrupt();
    }
}
