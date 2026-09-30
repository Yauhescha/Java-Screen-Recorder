package com.example.screenrecorder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class AboutDialog extends JDialog {
    AboutDialog(Window owner, String ffmpegPath, Runnable checkUpdates) {
        super(owner, "About " + AppVersion.NAME, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(AppTheme.BG);

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBackground(AppTheme.BG);
        root.setBorder(new EmptyBorder(20, 22, 18, 22));

        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        var icon = AppIcon.load();
        if (icon != null) {
            JLabel image = new JLabel(new ImageIcon(icon.getScaledInstance(56, 56, Image.SCALE_SMOOTH)));
            header.add(image, BorderLayout.WEST);
        }
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(AppVersion.NAME);
        name.setForeground(AppTheme.TEXT);
        name.setFont(new Font("Segoe UI", Font.BOLD, 22));
        JLabel version = new JLabel("Version " + AppVersion.VERSION + " · pre-1.0 release candidate");
        version.setForeground(AppTheme.MUTED);
        titles.add(name);
        titles.add(Box.createVerticalStrut(4));
        titles.add(version);
        header.add(titles, BorderLayout.CENTER);

        JTextArea info = new JTextArea();
        info.setEditable(false);
        info.setFocusable(false);
        info.setOpaque(false);
        info.setForeground(AppTheme.TEXT);
        info.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        info.setText("Local screen recorder for Windows\n\n" +
                "Java: " + System.getProperty("java.version") + "\n" +
                "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n" +
                "FFmpeg: " + firstLine(ffmpegPath) + "\n\n" +
                "Recording stays local unless you explicitly use an update server.\n" +
                "FFmpeg and other third-party components retain their respective licenses.");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton updates = AppTheme.button("Check for updates");
        JButton close = AppTheme.button("Close");
        updates.addActionListener(e -> checkUpdates.run());
        close.addActionListener(e -> dispose());
        buttons.add(updates);
        buttons.add(close);

        root.add(header, BorderLayout.NORTH);
        root.add(info, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setSize(Math.max(520, getWidth()), Math.max(330, getHeight()));
        setLocationRelativeTo(owner);
    }

    private static String firstLine(String ffmpegPath) {
        try {
            Process p = new ProcessBuilder(ffmpegPath, "-version").redirectErrorStream(true).start();
            String line;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                line = r.readLine();
            }
            p.waitFor(2, TimeUnit.SECONDS);
            return line == null || line.isBlank() ? ffmpegPath : line.trim();
        } catch (Exception e) {
            return ffmpegPath;
        }
    }
}
