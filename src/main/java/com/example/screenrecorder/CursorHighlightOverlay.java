package com.example.screenrecorder;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Transparent, click-through overlay used for the optional cursor halo and
 * animated mouse-click feedback. The overlay is intentionally visible to the
 * screen-capture path so the effects become part of the recorded video.
 */
public final class CursorHighlightOverlay extends JWindow {
    private static final int HALO_RADIUS = 24;
    private static final int RIPPLE_MAX_RADIUS = 44;
    private static final long RIPPLE_DURATION_MS = 430;

    private final Rectangle desktopBounds;
    private final Timer timer;
    private final List<ClickRipple> ripples = new ArrayList<>();

    private Color color;
    private Point pointer = new Point(-1000, -1000);
    private boolean highlightEnabled;
    private boolean clickEffectsEnabled;
    private boolean previousLeftDown;
    private boolean previousRightDown;

    public CursorHighlightOverlay(Color color) {
        this.color = color;
        desktopBounds = virtualDesktopBounds();
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        setBackground(new Color(0, 0, 0, 0));
        setBounds(desktopBounds);
        setContentPane(new EffectsPanel());
        timer = new Timer(16, e -> tick());
    }

    public void setHighlightColor(Color color) {
        this.color = color;
        repaint();
    }

    public void setHighlightEnabled(boolean enabled) {
        this.highlightEnabled = enabled;
        repaint();
    }

    public void setClickEffectsEnabled(boolean enabled) {
        this.clickEffectsEnabled = enabled;
        if (!enabled) ripples.clear();
        repaint();
    }

    public void start() {
        if (!highlightEnabled && !clickEffectsEnabled) {
            stop();
            return;
        }
        previousLeftDown = isMouseButtonDown(0x01);
        previousRightDown = isMouseButtonDown(0x02);
        if (!isVisible()) {
            setVisible(true);
            setClickThrough();
        }
        timer.start();
        toFront();
    }

    public void stop() {
        timer.stop();
        ripples.clear();
        setVisible(false);
    }

    @Override
    public void dispose() {
        timer.stop();
        super.dispose();
    }

    private void tick() {
        PointerInfo info = MouseInfo.getPointerInfo();
        Point oldPointer = pointer;
        if (info != null) pointer = info.getLocation();

        long now = System.currentTimeMillis();
        if (clickEffectsEnabled && info != null) {
            boolean leftDown = isMouseButtonDown(0x01);
            boolean rightDown = isMouseButtonDown(0x02);

            if (leftDown && !previousLeftDown) {
                ripples.add(new ClickRipple(new Point(pointer), now, false));
            }
            if (rightDown && !previousRightDown) {
                ripples.add(new ClickRipple(new Point(pointer), now, true));
            }
            previousLeftDown = leftDown;
            previousRightDown = rightDown;
        }

        Rectangle dirty = null;
        if (highlightEnabled) {
            dirty = union(dirty, haloRect(oldPointer));
            dirty = union(dirty, haloRect(pointer));
        }

        Iterator<ClickRipple> iterator = ripples.iterator();
        while (iterator.hasNext()) {
            ClickRipple ripple = iterator.next();
            dirty = union(dirty, rippleRect(ripple.point()));
            if (now - ripple.startedAtMillis() > RIPPLE_DURATION_MS) iterator.remove();
        }

        if (dirty != null) {
            repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }
    }

    private Rectangle haloRect(Point screenPoint) {
        int r = HALO_RADIUS + 4;
        int x = screenPoint.x - desktopBounds.x - r;
        int y = screenPoint.y - desktopBounds.y - r;
        return new Rectangle(x, y, r * 2, r * 2);
    }

    private Rectangle rippleRect(Point screenPoint) {
        int r = RIPPLE_MAX_RADIUS + 5;
        int x = screenPoint.x - desktopBounds.x - r;
        int y = screenPoint.y - desktopBounds.y - r;
        return new Rectangle(x, y, r * 2, r * 2);
    }

    private static Rectangle union(Rectangle a, Rectangle b) {
        if (b == null) return a;
        return a == null ? new Rectangle(b) : a.union(b);
    }

    private boolean isMouseButtonDown(int vk) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return false;
        try {
            return (User32.INSTANCE.GetAsyncKeyState(vk) & 0x8000) != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void setClickThrough() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") || !isDisplayable()) {
            return;
        }
        try {
            Pointer hwnd = Native.getComponentPointer(this);
            int style = User32.INSTANCE.GetWindowLongW(hwnd, User32.GWL_EXSTYLE);
            int next = style | User32.WS_EX_TRANSPARENT | User32.WS_EX_LAYERED |
                    User32.WS_EX_NOACTIVATE | User32.WS_EX_TOOLWINDOW;
            User32.INSTANCE.SetWindowLongW(hwnd, User32.GWL_EXSTYLE, next);
        } catch (Throwable ignored) {
        }
    }

    private static Rectangle virtualDesktopBounds() {
        Rectangle result = new Rectangle();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            for (GraphicsConfiguration cfg : device.getConfigurations()) {
                result = result.union(cfg.getBounds());
            }
        }
        return result;
    }

    private final class EffectsPanel extends JPanel {
        EffectsPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int x = pointer.x - desktopBounds.x;
            int y = pointer.y - desktopBounds.y;
            if (highlightEnabled && x >= -HALO_RADIUS && y >= -HALO_RADIUS &&
                    x <= getWidth() + HALO_RADIUS && y <= getHeight() + HALO_RADIUS) {
                Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), 82);
                Color outline = new Color(color.getRed(), color.getGreen(), color.getBlue(), 215);
                g2.setColor(fill);
                g2.fillOval(x - HALO_RADIUS, y - HALO_RADIUS, HALO_RADIUS * 2, HALO_RADIUS * 2);
                g2.setStroke(new BasicStroke(2.5f));
                g2.setColor(outline);
                g2.drawOval(x - HALO_RADIUS, y - HALO_RADIUS, HALO_RADIUS * 2, HALO_RADIUS * 2);
            }

            long now = System.currentTimeMillis();
            for (ClickRipple ripple : ripples) {
                float progress = Math.min(1f, Math.max(0f,
                        (now - ripple.startedAtMillis()) / (float) RIPPLE_DURATION_MS));
                int radius = 10 + Math.round((RIPPLE_MAX_RADIUS - 10) * progress);
                int alpha = Math.max(0, Math.round(235 * (1f - progress)));
                Point p = ripple.point();
                int rx = p.x - desktopBounds.x;
                int ry = p.y - desktopBounds.y;

                Color base = ripple.rightButton()
                        ? new Color(74, 177, 255)
                        : color;
                g2.setStroke(new BasicStroke(4f - 2f * progress));
                g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
                g2.drawOval(rx - radius, ry - radius, radius * 2, radius * 2);

                if (progress < 0.22f) {
                    int fillAlpha = Math.max(0, Math.round(105 * (1f - progress / 0.22f)));
                    g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), fillAlpha));
                    g2.fillOval(rx - 9, ry - 9, 18, 18);
                }
            }
            g2.dispose();
        }
    }

    private record ClickRipple(Point point, long startedAtMillis, boolean rightButton) {}

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);
        int GWL_EXSTYLE = -20;
        int WS_EX_TRANSPARENT = 0x00000020;
        int WS_EX_LAYERED = 0x00080000;
        int WS_EX_NOACTIVATE = 0x08000000;
        int WS_EX_TOOLWINDOW = 0x00000080;

        int GetWindowLongW(Pointer hWnd, int nIndex);
        int SetWindowLongW(Pointer hWnd, int nIndex, int dwNewLong);
        short GetAsyncKeyState(int vKey);
    }
}
