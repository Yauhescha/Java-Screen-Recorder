package com.example.screenrecorder;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class ScreenRecorderApp {
    private ScreenRecorderApp() {}

    public static void main(String[] args) {
        List<String> startupLog = new ArrayList<>();
        // Must happen before AWT creates HWNDs. Monitor capture then uses physical Win32 pixels
        // consistently even when different displays use 100/125/150/200% scaling.
        DpiAwareness.enablePerMonitorV2(startupLog::add);

        final String ffmpegPath;
        try {
            ffmpegPath = FfmpegBootstrap.resolve(startupLog::add);
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    null,
                    e.getMessage(),
                    "Java Screen Recorder - startup error",
                    JOptionPane.ERROR_MESSAGE
            ));
            return;
        }

        SwingUtilities.invokeLater(() -> {
            AppTheme.apply();
            RecorderFrame frame = new RecorderFrame(ffmpegPath);
            var icon = AppIcon.load();
            if (icon != null) frame.setIconImage(icon);
            startupLog.forEach(frame::appendStartupLog);
            frame.setVisible(true);
            WindowsWindowStyler.apply(frame);
            frame.checkForUpdatesAtStartup();
        });
    }
}
