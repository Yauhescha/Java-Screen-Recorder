package com.example.screenrecorder;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Lightweight output-drive free-space checker used before and during recording. */
public final class DiskSpaceMonitor {
    public static final long WARNING_BYTES = 2L * 1024 * 1024 * 1024;
    public static final long CRITICAL_BYTES = 512L * 1024 * 1024;

    private DiskSpaceMonitor() {}

    public static long usableBytes(Path directory) {
        try {
            Path dir = directory.toAbsolutePath();
            Files.createDirectories(dir);
            FileStore store = Files.getFileStore(dir);
            return Math.max(0L, store.getUsableSpace());
        } catch (Exception e) {
            return -1L;
        }
    }

    public static String format(long bytes) {
        if (bytes < 0) return "unknown";
        double gb = bytes / (1024d * 1024d * 1024d);
        if (gb >= 1.0) return String.format(Locale.ROOT, "%.1f GB", gb);
        double mb = bytes / (1024d * 1024d);
        return String.format(Locale.ROOT, "%.0f MB", mb);
    }
}
