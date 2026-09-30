package com.example.screenrecorder;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

/** Windows/system tray integration with gray idle and red recording states. */
public final class TrayService implements AutoCloseable {
    private TrayIcon trayIcon;
    private MenuItem recordItem;
    private final Image idleImage = createIcon(false);
    private final Image recordingImage = createIcon(true);

    public boolean install(ActionListener openAction,
                           ActionListener recordAction,
                           ActionListener screenshotAction,
                           ActionListener openFolderAction,
                           ActionListener exitAction) {
        if (!SystemTray.isSupported()) return false;
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open recorder");
            recordItem = new MenuItem("Start recording");
            MenuItem screenshot = new MenuItem("Screenshot");
            MenuItem folder = new MenuItem("Open recordings folder");
            MenuItem exit = new MenuItem("Exit");
            open.addActionListener(openAction);
            recordItem.addActionListener(recordAction);
            screenshot.addActionListener(screenshotAction);
            folder.addActionListener(openFolderAction);
            exit.addActionListener(exitAction);
            menu.add(open);
            menu.addSeparator();
            menu.add(recordItem);
            menu.add(screenshot);
            menu.add(folder);
            menu.addSeparator();
            menu.add(exit);

            trayIcon = new TrayIcon(idleImage, "Java Screen Recorder", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(openAction);
            SystemTray.getSystemTray().add(trayIcon);
            return true;
        } catch (Exception e) {
            trayIcon = null;
            return false;
        }
    }

    public void showWarning(String title, String message) {
        if (trayIcon == null) return;
        trayIcon.displayMessage(title, message, TrayIcon.MessageType.WARNING);
    }

    public void showInfo(String title, String message) {
        if (trayIcon == null) return;
        trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
    }

    public void setRecording(boolean recording) {
        if (trayIcon == null) return;
        trayIcon.setImage(recording ? recordingImage : idleImage);
        trayIcon.setToolTip(recording ? "Java Screen Recorder — recording" : "Java Screen Recorder — ready");
        if (recordItem != null) recordItem.setLabel(recording ? "Stop recording" : "Start recording");
    }

    private static Image createIcon(boolean recording) {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color c = recording ? new Color(232, 68, 73) : new Color(142, 149, 160);
        g.setColor(new Color(20, 23, 29, 230));
        g.fillRoundRect(1, 1, 30, 30, 8, 8);
        g.setColor(c);
        g.fillOval(7, 7, 18, 18);
        g.setColor(new Color(255, 255, 255, 220));
        g.fillRoundRect(12, 12, 8, 8, 2, 2);
        g.dispose();
        return image;
    }

    @Override public void close() {
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }
}
