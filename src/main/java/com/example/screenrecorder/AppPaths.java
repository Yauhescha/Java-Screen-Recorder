package com.example.screenrecorder;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Resolves the directory containing the native jpackage launcher on Windows. */
public final class AppPaths {
    private AppPaths() {
    }

    private interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        int GetModuleFileNameW(Pointer hModule, char[] lpFilename, int nSize);
    }

    public static Path applicationDirectory() {
        Path nativeExecutable = windowsProcessExecutable();
        if (nativeExecutable != null) {
            String fileName = nativeExecutable.getFileName().toString().toLowerCase(Locale.ROOT);
            // jpackage keeps the JVM inside the native launcher process, so this is normally
            // JavaScreenRecorder.exe. During development it may instead be java.exe/javaw.exe.
            if (!fileName.equals("java.exe") && !fileName.equals("javaw.exe")) {
                return nativeExecutable.getParent();
            }
        }

        // Development / java -jar fallback.
        try {
            URI uri = ScreenRecorderApp.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path location = Path.of(uri).toAbsolutePath().normalize();
            if (Files.isRegularFile(location)) {
                return location.getParent();
            }
        } catch (Exception ignored) {
        }

        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private static Path windowsProcessExecutable() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return null;
        }

        try {
            char[] buffer = new char[32768];
            int len = Kernel32.INSTANCE.GetModuleFileNameW(Pointer.NULL, buffer, buffer.length);
            if (len <= 0) {
                return null;
            }
            return Path.of(new String(buffer, 0, len)).toAbsolutePath().normalize();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
