package com.example.screenrecorder;

import javax.swing.*;
import java.awt.*;

/** Compact dark-theme audio peak meter. */
final class AudioLevelMeter extends JComponent {
    private volatile double level;

    AudioLevelMeter() {
        setPreferredSize(new Dimension(150, 10));
        setMinimumSize(new Dimension(70, 10));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        setToolTipText("Live peak level while recording");
    }

    void setLevel(double value) {
        level = Math.max(0.0, Math.min(1.0, value));
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(new Color(12, 15, 20));
        g2.fillRoundRect(0, 0, w, h, h, h);
        int fill = (int) Math.round((w - 2) * level);
        if (fill > 0) {
            Color c = level > 0.92 ? AppTheme.RECORD : (level > 0.72 ? new Color(255, 183, 77) : AppTheme.GOOD);
            g2.setColor(c);
            g2.fillRoundRect(1, 1, fill, Math.max(1, h - 2), h - 2, h - 2);
        }
        g2.setColor(AppTheme.BORDER);
        g2.drawRoundRect(0, 0, Math.max(0, w - 1), Math.max(0, h - 1), h, h);
        g2.dispose();
    }
}
