package com.example.screenrecorder;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

/**
 * Bandicam-like capture frame.
 *
 * The dark top bar and blue border are placed OUTSIDE the captured rectangle,
 * so they remain visible during recording without becoming part of the video.
 * While recording/paused the window becomes mouse-click-through, allowing the
 * user to interact normally with applications inside the selected area.
 */
public final class RegionOverlay extends JFrame {
    public enum CaptureState { READY, RECORDING, PAUSED }

    private static final int BORDER = 3;
    private static final int BAR_HEIGHT = 32;
    private static final int EDGE = 9;
    private static final int MIN_W = 160;
    private static final int MIN_H = 90;

    private final Consumer<CaptureRegion> onChange;
    private Point pressScreen;
    private CaptureRegion pressRegion;
    private ResizeMode dragMode = ResizeMode.MOVE;
    private CaptureState captureState = CaptureState.READY;

    public RegionOverlay(CaptureRegion initial, Consumer<CaptureRegion> onChange) {
        this.onChange = onChange;
        setUndecorated(true);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));
        setType(Type.UTILITY);
        setFocusableWindowState(true);
        setContentPane(new OverlayPanel());
        setRegion(initial);

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (captureState != CaptureState.READY) return;
                pressScreen = e.getLocationOnScreen();
                pressRegion = region();
                dragMode = detectMode(e.getPoint());
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (captureState != CaptureState.READY || pressScreen == null || pressRegion == null) return;

                Point now = e.getLocationOnScreen();
                int dx = now.x - pressScreen.x;
                int dy = now.y - pressScreen.y;
                int x = pressRegion.x();
                int y = pressRegion.y();
                int w = pressRegion.width();
                int h = pressRegion.height();

                if (dragMode == ResizeMode.MOVE) {
                    x += dx;
                    y += dy;
                } else {
                    if (dragMode.west) { x += dx; w -= dx; }
                    if (dragMode.east) { w += dx; }
                    if (dragMode.north) { y += dy; h -= dy; }
                    if (dragMode.south) { h += dy; }

                    if (w < MIN_W) {
                        if (dragMode.west) x -= MIN_W - w;
                        w = MIN_W;
                    }
                    if (h < MIN_H) {
                        if (dragMode.north) y -= MIN_H - h;
                        h = MIN_H;
                    }
                }

                w = Math.max(MIN_W, w & ~1);
                h = Math.max(MIN_H, h & ~1);
                setRegion(new CaptureRegion(x, y, w, h));
                onChange.accept(region());
            }

            @Override public void mouseMoved(MouseEvent e) {
                if (captureState != CaptureState.READY) {
                    setCursor(Cursor.getDefaultCursor());
                    return;
                }
                setCursor(cursorFor(detectMode(e.getPoint())));
            }

            @Override public void mouseReleased(MouseEvent e) {
                pressScreen = null;
                pressRegion = null;
                if (captureState == CaptureState.READY) {
                    onChange.accept(region());
                }
            }
        };

        getContentPane().addMouseListener(mouse);
        getContentPane().addMouseMotionListener(mouse);

        getRootPane().registerKeyboardAction(e -> {
                    if (captureState == CaptureState.READY) setVisible(false);
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public CaptureRegion region() {
        int w = Math.max(2, getWidth() - BORDER * 2);
        int h = Math.max(2, getHeight() - BAR_HEIGHT - BORDER * 2);
        return new CaptureRegion(
                getX() + BORDER,
                getY() + BAR_HEIGHT + BORDER,
                w & ~1,
                h & ~1
        );
    }

    public void setRegion(CaptureRegion region) {
        CaptureRegion r = region.evenSized();
        setBounds(
                r.x() - BORDER,
                r.y() - BAR_HEIGHT - BORDER,
                r.width() + BORDER * 2,
                r.height() + BAR_HEIGHT + BORDER * 2
        );
        repaint();
    }

    public void setCaptureState(CaptureState state) {
        this.captureState = state;
        repaint();
        SwingUtilities.invokeLater(() -> setMouseClickThrough(state != CaptureState.READY));
    }

    public CaptureState captureState() {
        return captureState;
    }

    @Override public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            SwingUtilities.invokeLater(() -> setMouseClickThrough(captureState != CaptureState.READY));
        }
    }

    private ResizeMode detectMode(Point p) {
        int topEdge = BAR_HEIGHT + BORDER;
        int bottomEdge = getHeight() - BORDER;
        int leftEdge = BORDER;
        int rightEdge = getWidth() - BORDER;

        boolean west = Math.abs(p.x - leftEdge) <= EDGE || p.x < leftEdge;
        boolean east = Math.abs(p.x - rightEdge) <= EDGE || p.x > rightEdge;
        boolean north = Math.abs(p.y - topEdge) <= EDGE;
        boolean south = Math.abs(p.y - bottomEdge) <= EDGE || p.y > bottomEdge;

        if (north && west) return ResizeMode.NW;
        if (north && east) return ResizeMode.NE;
        if (south && west) return ResizeMode.SW;
        if (south && east) return ResizeMode.SE;
        if (north) return ResizeMode.N;
        if (south) return ResizeMode.S;
        if (west) return ResizeMode.W;
        if (east) return ResizeMode.E;
        return ResizeMode.MOVE;
    }

    private Cursor cursorFor(ResizeMode mode) {
        return Cursor.getPredefinedCursor(switch (mode) {
            case N -> Cursor.N_RESIZE_CURSOR;
            case S -> Cursor.S_RESIZE_CURSOR;
            case E -> Cursor.E_RESIZE_CURSOR;
            case W -> Cursor.W_RESIZE_CURSOR;
            case NE -> Cursor.NE_RESIZE_CURSOR;
            case NW -> Cursor.NW_RESIZE_CURSOR;
            case SE -> Cursor.SE_RESIZE_CURSOR;
            case SW -> Cursor.SW_RESIZE_CURSOR;
            default -> Cursor.MOVE_CURSOR;
        });
    }

    private void setMouseClickThrough(boolean enabled) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win") || !isDisplayable()) {
            return;
        }
        try {
            Pointer hwnd = Native.getComponentPointer(this);
            int style = User32.INSTANCE.GetWindowLongW(hwnd, User32.GWL_EXSTYLE);
            int next = enabled ? (style | User32.WS_EX_TRANSPARENT) : (style & ~User32.WS_EX_TRANSPARENT);
            if (next != style) {
                User32.INSTANCE.SetWindowLongW(hwnd, User32.GWL_EXSTYLE, next);
            }
        } catch (Throwable ignored) {
            // If click-through cannot be enabled, the frame still remains visual;
            // recording itself is unaffected because border/bar are outside region.
        }
    }

    private final class OverlayPanel extends JPanel {
        OverlayPanel() {
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            CaptureRegion r = region();
            int captureW = r.width();
            int captureH = r.height();
            int captureTop = BAR_HEIGHT + BORDER;

            // Bandicam-like top panel, outside the actual capture rectangle.
            g2.setColor(new Color(34, 40, 47, 245));
            g2.fillRect(0, 0, getWidth(), BAR_HEIGHT);

            String size = captureW + "x" + captureH;
            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            int baseline = (BAR_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(size, 10, baseline);

            int x = 10 + fm.stringWidth(size) + 10;
            g2.setColor(new Color(140, 148, 156));
            g2.drawString("|", x, baseline);
            x += fm.stringWidth("|") + 10;

            String status;
            Color statusColor;
            if (captureState == CaptureState.RECORDING) {
                status = "REC";
                statusColor = new Color(238, 62, 62);
                g2.setColor(statusColor);
                g2.fillOval(x, baseline - 10, 9, 9);
                x += 15;
            } else if (captureState == CaptureState.PAUSED) {
                status = "PAUSE";
                statusColor = new Color(255, 183, 77);
            } else {
                status = "READY";
                statusColor = new Color(210, 216, 222);
            }
            g2.setColor(statusColor);
            g2.drawString(status, x, baseline);

            // Blue border is also outside the capture rectangle.
            g2.setColor(new Color(40, 145, 214));
            g2.fillRect(0, BAR_HEIGHT, getWidth(), BORDER); // top
            g2.fillRect(0, captureTop, BORDER, captureH); // left
            g2.fillRect(BORDER + captureW, captureTop, BORDER, captureH); // right
            g2.fillRect(0, captureTop + captureH, getWidth(), BORDER); // bottom

            g2.dispose();
        }
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);
        int GWL_EXSTYLE = -20;
        int WS_EX_TRANSPARENT = 0x00000020;

        int GetWindowLongW(Pointer hWnd, int nIndex);
        int SetWindowLongW(Pointer hWnd, int nIndex, int dwNewLong);
    }

    private enum ResizeMode {
        MOVE(false, false, false, false),
        N(true, false, false, false),
        S(false, true, false, false),
        W(false, false, true, false),
        E(false, false, false, true),
        NW(true, false, true, false),
        NE(true, false, false, true),
        SW(false, true, true, false),
        SE(false, true, false, true);

        final boolean north;
        final boolean south;
        final boolean west;
        final boolean east;

        ResizeMode(boolean north, boolean south, boolean west, boolean east) {
            this.north = north;
            this.south = south;
            this.west = west;
            this.east = east;
        }
    }
}
