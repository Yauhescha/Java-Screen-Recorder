package com.example.screenrecorder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class OutputNaming {
    private static final DateTimeFormatter RECORDING_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter SCREENSHOT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");

    private OutputNaming() {}

    static Path recordingFile(Path directory, VideoFormat format) {
        return unique(directory,
                "recording_" + LocalDateTime.now().format(RECORDING_FORMAT),
                format.extension());
    }

    static Path screenshotFile(Path directory) {
        return unique(directory,
                "screenshot_" + LocalDateTime.now().format(SCREENSHOT_FORMAT),
                ".png");
    }

    private static Path unique(Path directory, String baseName, String extension) {
        Path candidate = directory.resolve(baseName + extension);
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(baseName + "_" + suffix++ + extension);
        }
        return candidate;
    }
}
