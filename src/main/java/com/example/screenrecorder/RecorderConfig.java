package com.example.screenrecorder;

import java.nio.file.Path;
import java.util.List;

public record RecorderConfig(
        String ffmpegPath,
        CaptureMode captureMode,
        CaptureRegion region,
        List<DisplayMonitor> captureMonitors,
        WindowTarget windowTarget,
        int qualityPercent,
        int fps,
        boolean recordSystemAudio,
        int systemVolumePercent,
        boolean recordMicrophone,
        MicrophoneDevice microphoneDevice,
        int microphoneVolumePercent,
        boolean microphoneNoiseSuppression,
        boolean microphoneNoiseGate,
        int microphoneNoiseGateDb,
        boolean separateAudioTracks,
        VideoEncoder videoEncoder,
        boolean showCursor,
        boolean windowCaptureNeedsDesktopEffects,
        boolean recordWebcam,
        WebcamDevice webcamDevice,
        String webcamInputUrl,
        int webcamInputWidth,
        int webcamInputHeight,
        int webcamInputFps,
        WebcamPlacement webcamPlacement,
        boolean webcamMirror,
        WebcamShape webcamShape,
        boolean webcamBorder,
        boolean webcamShadow,
        int webcamBorderRgb,
        Path outputFile
) {
    public RecorderConfig {
        captureMonitors = captureMonitors == null ? List.of() : List.copyOf(captureMonitors);
    }

    public RecorderConfig withVideoEncoder(VideoEncoder encoder) {
        return new RecorderConfig(
                ffmpegPath, captureMode, region, captureMonitors, windowTarget, qualityPercent, fps,
                recordSystemAudio, systemVolumePercent, recordMicrophone, microphoneDevice,
                microphoneVolumePercent, microphoneNoiseSuppression, microphoneNoiseGate, microphoneNoiseGateDb,
                separateAudioTracks, encoder, showCursor, windowCaptureNeedsDesktopEffects,
                recordWebcam, webcamDevice, webcamInputUrl, webcamInputWidth, webcamInputHeight, webcamInputFps,
                webcamPlacement, webcamMirror, webcamShape, webcamBorder, webcamShadow, webcamBorderRgb, outputFile);
    }

    public RecorderConfig withOutputFile(Path file) {
        return new RecorderConfig(
                ffmpegPath, captureMode, region, captureMonitors, windowTarget, qualityPercent, fps,
                recordSystemAudio, systemVolumePercent, recordMicrophone, microphoneDevice,
                microphoneVolumePercent, microphoneNoiseSuppression, microphoneNoiseGate, microphoneNoiseGateDb,
                separateAudioTracks, videoEncoder, showCursor, windowCaptureNeedsDesktopEffects,
                recordWebcam, webcamDevice, webcamInputUrl, webcamInputWidth, webcamInputHeight, webcamInputFps,
                webcamPlacement, webcamMirror, webcamShape, webcamBorder, webcamShadow, webcamBorderRgb, file);
    }
}
