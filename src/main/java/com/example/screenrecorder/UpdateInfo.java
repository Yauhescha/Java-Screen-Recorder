package com.example.screenrecorder;

public record UpdateInfo(
        String version,
        String packageUrl,
        String packageType,
        String sha256,
        String notes,
        String entryExe
) {}
