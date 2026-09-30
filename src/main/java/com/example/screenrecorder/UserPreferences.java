package com.example.screenrecorder;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.prefs.Preferences;

final class UserPreferences {
    private static final Preferences PREFS = Preferences.userNodeForPackage(UserPreferences.class);

    private UserPreferences() {}

    static Path outputDirectory() {
        return Path.of(PREFS.get("outputDirectory",
                Path.of(System.getProperty("user.home"), "Videos").toString()));
    }

    static void outputDirectory(Path path) {
        PREFS.put("outputDirectory", path.toAbsolutePath().toString());
    }

    static boolean showLog() { return PREFS.getBoolean("showLog", false); }
    static void showLog(boolean value) { PREFS.putBoolean("showLog", value); }

    static boolean autoUpdateCheck() { return PREFS.getBoolean("autoUpdateCheck", true); }
    static void autoUpdateCheck(boolean value) { PREFS.putBoolean("autoUpdateCheck", value); }

    static long lastUpdateCheckEpochMillis() { return PREFS.getLong("lastUpdateCheckEpochMillis", 0L); }
    static void lastUpdateCheckEpochMillis(long value) { PREFS.putLong("lastUpdateCheckEpochMillis", Math.max(0L, value)); }

    static int uiLayoutVersion() { return PREFS.getInt("uiLayoutVersion", 0); }
    static void uiLayoutVersion(int value) { PREFS.putInt("uiLayoutVersion", value); }

    static VideoFormat videoFormat() {
        try { return VideoFormat.valueOf(PREFS.get("videoFormat", VideoFormat.MP4.name())); }
        catch (Exception e) { return VideoFormat.MP4; }
    }
    static void videoFormat(VideoFormat value) { PREFS.put("videoFormat", value.name()); }

    static boolean separateAudioTracks() { return PREFS.getBoolean("separateAudioTracks", false); }
    static void separateAudioTracks(boolean value) { PREFS.putBoolean("separateAudioTracks", value); }

    static boolean excludeRecorderWindow() { return PREFS.getBoolean("excludeRecorderWindow", true); }
    static void excludeRecorderWindow(boolean value) { PREFS.putBoolean("excludeRecorderWindow", value); }

    static boolean mouseClickEffects() { return PREFS.getBoolean("mouseClickEffects", false); }
    static void mouseClickEffects(boolean value) { PREFS.putBoolean("mouseClickEffects", value); }

    static CaptureMode captureMode() {
        try { return CaptureMode.valueOf(PREFS.get("captureMode", CaptureMode.FULL_SCREEN.name())); }
        catch (Exception e) { return CaptureMode.FULL_SCREEN; }
    }
    static void captureMode(CaptureMode value) { PREFS.put("captureMode", value.name()); }


    static java.util.List<String> selectedMonitorIds() {
        String raw = PREFS.get("selectedMonitorIds", "");
        if (raw.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(raw.split("\\n"))
                .map(String::trim).filter(v -> !v.isBlank()).toList();
    }
    static void selectedMonitorIds(java.util.List<DisplayMonitor> monitors) {
        if (monitors == null || monitors.isEmpty()) {
            PREFS.remove("selectedMonitorIds");
            return;
        }
        PREFS.put("selectedMonitorIds", monitors.stream().map(DisplayMonitor::id)
                .collect(java.util.stream.Collectors.joining("\n")));
    }

    static String selectedWindowTitle() { return PREFS.get("selectedWindowTitle", ""); }
    static void selectedWindowTitle(String value) {
        if (value == null || value.isBlank()) PREFS.remove("selectedWindowTitle");
        else PREFS.put("selectedWindowTitle", value);
    }

    static int qualityPercent() { return clamp(PREFS.getInt("qualityPercent", 80), 1, 100); }
    static void qualityPercent(int value) { PREFS.putInt("qualityPercent", clamp(value, 1, 100)); }

    static int fps() { return clamp(PREFS.getInt("fps", 60), 1, 240); }
    static void fps(int value) { PREFS.putInt("fps", clamp(value, 1, 240)); }

    static boolean recordSystemAudio() { return PREFS.getBoolean("recordSystemAudio", true); }
    static void recordSystemAudio(boolean value) { PREFS.putBoolean("recordSystemAudio", value); }

    static int systemVolumePercent() { return clamp(PREFS.getInt("systemVolumePercent", 100), 0, 200); }
    static void systemVolumePercent(int value) { PREFS.putInt("systemVolumePercent", clamp(value, 0, 200)); }

    static boolean recordMicrophone() { return PREFS.getBoolean("recordMicrophone", true); }
    static void recordMicrophone(boolean value) { PREFS.putBoolean("recordMicrophone", value); }

    static int microphoneVolumePercent() { return clamp(PREFS.getInt("microphoneVolumePercent", 100), 0, 200); }
    static void microphoneVolumePercent(int value) { PREFS.putInt("microphoneVolumePercent", clamp(value, 0, 200)); }

    static boolean microphoneNoiseSuppression() { return PREFS.getBoolean("microphoneNoiseSuppression", false); }
    static void microphoneNoiseSuppression(boolean value) { PREFS.putBoolean("microphoneNoiseSuppression", value); }

    static boolean microphoneNoiseGate() { return PREFS.getBoolean("microphoneNoiseGate", false); }
    static void microphoneNoiseGate(boolean value) { PREFS.putBoolean("microphoneNoiseGate", value); }

    static int microphoneNoiseGateDb() { return clamp(PREFS.getInt("microphoneNoiseGateDb", -45), -70, -10); }
    static void microphoneNoiseGateDb(int value) { PREFS.putInt("microphoneNoiseGateDb", clamp(value, -70, -10)); }

    static String microphoneId() { return PREFS.get("microphoneId", "default"); }
    static void microphoneId(String value) { if (value != null) PREFS.put("microphoneId", value); }

    static VideoEncoder videoEncoder() {
        try { return VideoEncoder.valueOf(PREFS.get("videoEncoder", VideoEncoder.AUTO.name())); }
        catch (Exception e) { return VideoEncoder.AUTO; }
    }
    static void videoEncoder(VideoEncoder value) { PREFS.put("videoEncoder", value.name()); }


    static boolean recordWebcam() { return PREFS.getBoolean("recordWebcam", false); }
    static void recordWebcam(boolean value) { PREFS.putBoolean("recordWebcam", value); }

    static boolean hideWebcamPreviewWhileRecording() { return PREFS.getBoolean("hideWebcamPreviewWhileRecording", false); }
    static void hideWebcamPreviewWhileRecording(boolean value) { PREFS.putBoolean("hideWebcamPreviewWhileRecording", value); }

    static String webcamName() { return PREFS.get("webcamName", ""); }
    static void webcamName(String value) { if (value != null) PREFS.put("webcamName", value); }

    static boolean webcamMirror() { return PREFS.getBoolean("webcamMirror", true); }
    static void webcamMirror(boolean value) { PREFS.putBoolean("webcamMirror", value); }

    static WebcamShape webcamShape() {
        try { return WebcamShape.valueOf(PREFS.get("webcamShape", WebcamShape.ROUNDED.name())); }
        catch (Exception e) { return WebcamShape.ROUNDED; }
    }
    static void webcamShape(WebcamShape value) { if (value != null) PREFS.put("webcamShape", value.name()); }

    static boolean webcamBorder() { return PREFS.getBoolean("webcamBorder", true); }
    static void webcamBorder(boolean value) { PREFS.putBoolean("webcamBorder", value); }

    static boolean webcamShadow() { return PREFS.getBoolean("webcamShadow", true); }
    static void webcamShadow(boolean value) { PREFS.putBoolean("webcamShadow", value); }

    static Color webcamBorderColor() {
        int rgb = PREFS.getInt("webcamBorderColor", Color.WHITE.getRGB());
        return new Color(rgb, true);
    }
    static void webcamBorderColor(Color value) { if (value != null) PREFS.putInt("webcamBorderColor", value.getRGB()); }

    static WebcamPlacement webcamPlacement(int captureWidth, int captureHeight) {
        WebcamPlacement fallback = WebcamPlacement.defaultFor(captureWidth, captureHeight);
        int x = PREFS.getInt("webcamX", fallback.x());
        int y = PREFS.getInt("webcamY", fallback.y());
        int w = PREFS.getInt("webcamW", fallback.width());
        int h = PREFS.getInt("webcamH", fallback.height());
        return new WebcamPlacement(x, y, w, h).clampTo(captureWidth, captureHeight);
    }

    static void webcamPlacement(WebcamPlacement value) {
        if (value == null) return;
        PREFS.putInt("webcamX", value.x());
        PREFS.putInt("webcamY", value.y());
        PREFS.putInt("webcamW", value.width());
        PREFS.putInt("webcamH", value.height());
    }

    static boolean showCursor() { return PREFS.getBoolean("showCursor", true); }
    static void showCursor(boolean value) { PREFS.putBoolean("showCursor", value); }

    static boolean highlightCursor() { return PREFS.getBoolean("highlightCursor", false); }
    static void highlightCursor(boolean value) { PREFS.putBoolean("highlightCursor", value); }

    static Color highlightColor() {
        int rgb = PREFS.getInt("highlightColor", new Color(255, 214, 64).getRGB());
        return new Color(rgb, true);
    }
    static void highlightColor(Color value) { PREFS.putInt("highlightColor", value.getRGB()); }

    static CaptureRegion savedRegion(CaptureRegion fallback) {
        int x = PREFS.getInt("regionX", fallback.x());
        int y = PREFS.getInt("regionY", fallback.y());
        int w = Math.max(160, PREFS.getInt("regionW", fallback.width())) & ~1;
        int h = Math.max(90, PREFS.getInt("regionH", fallback.height())) & ~1;
        return new CaptureRegion(x, y, w, h);
    }
    static void region(CaptureRegion region) {
        PREFS.putInt("regionX", region.x());
        PREFS.putInt("regionY", region.y());
        PREFS.putInt("regionW", region.width());
        PREFS.putInt("regionH", region.height());
    }

    static Hotkey startStopHotkey() {
        return Hotkey.deserialize(PREFS.get("startStopHotkey", null),
                new Hotkey(KeyEvent.VK_F12, 0));
    }
    static void startStopHotkey(Hotkey hotkey) { PREFS.put("startStopHotkey", hotkey.serialize()); }

    static Hotkey screenshotHotkey() {
        return Hotkey.deserialize(PREFS.get("screenshotHotkey", null),
                new Hotkey(KeyEvent.VK_F11, 0));
    }
    static void screenshotHotkey(Hotkey hotkey) { PREFS.put("screenshotHotkey", hotkey.serialize()); }

    static Hotkey muteMicrophoneHotkey() {
        return Hotkey.deserialize(PREFS.get("muteMicrophoneHotkey", null),
                new Hotkey(KeyEvent.VK_F10, 0));
    }
    static void muteMicrophoneHotkey(Hotkey hotkey) { PREFS.put("muteMicrophoneHotkey", hotkey.serialize()); }

    static Rectangle savedWindowBounds(Rectangle fallback) {
        int x = PREFS.getInt("windowX", fallback.x);
        int y = PREFS.getInt("windowY", fallback.y);
        int w = Math.max(900, PREFS.getInt("windowW", fallback.width));
        int h = Math.max(620, PREFS.getInt("windowH", fallback.height));
        return new Rectangle(x, y, w, h);
    }
    static void windowBounds(Rectangle b) {
        PREFS.putInt("windowX", b.x);
        PREFS.putInt("windowY", b.y);
        PREFS.putInt("windowW", b.width);
        PREFS.putInt("windowH", b.height);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
