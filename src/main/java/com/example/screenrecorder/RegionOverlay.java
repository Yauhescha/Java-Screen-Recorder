package com.example.screenrecorder;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.util.function.Consumer;

/**
 * Bandicam-like capture frame with a real transparent hole in the middle.
 *
 * The title bar and border live outside the captured rectangle. The center of
 * the native window is removed with Window#setShape(), so applications below
 * the selected region remain fully clickable even while the recording controls
 * on the frame are interactive.
 */
public final class RegionOverlay extends JFrame {
    public enum CaptureState { READY, RECORDING, PAUSED }

    private static final int BORDER = 3;
    private static final int BAR_HEIGHT = 34;
    private static final int EDGE = 9;
    private static final int MIN_W = 160;
    private static final int MIN_H = 90;
    private static final int CONTROL_W = 30;
    private static final int CONTROL_GAP = 5;

    private final Consumer<CaptureRegion> onChange;
    private Consumer<CaptureRegion> onRelocate = r -> {};
    private Runnable onPauseResume = () -> {};
    private Runnable onStop = () -> {};
    private Runnable onCloseFrame = () -> setVisible(false);

    private Point pressScreen;
    private CaptureRegion pressRegion;
    private ResizeMode dragMode = ResizeMode.MOVE;
    private CaptureState captureState = CaptureState.READY;
    private Control pressedControl = Control.NONE;
    private boolean movedDuringDrag;

    public RegionOverlay(CaptureRegion initial, Consumer<CaptureRegion> onChange) {
        this.onChange = onChange;
        setUndecorated(true);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));
        setType(Type.UTILITY);
        setFocusableWindowState(false);
        setContentPane(new OverlayPanel());
        setRegion(initial);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { updateWindowShape(); }
            @Override public void componentShown(ComponentEvent e) { updateWindowShape(); }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                pressedControl = controlAt(e.getPoint());
                movedDuringDrag = false;
                if (pressedControl != Control.NONE) {
                    repaint();
                    return;
                }

                pressScreen = e.getLocationOnScreen();
                pressRegion = region();
                if (captureState == CaptureState.READY) {
                    dragMode = detectMode(e.getPoint());
                } else if (e.getY() < BAR_HEIGHT) {
                    // While recording only the top bar can move the region. Resizing
                    // would change the encoded frame dimensions mid-file.
                    dragMode = ResizeMode.MOVE;
                } else {
                    pressScreen = null;
                    pressRegion = null;
                }
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (pressedControl != Control.NONE || pressScreen == null || pressRegion == null) return;

                Point now = e.getLocationOnScreen();
                int dx = now.x - pressScreen.x;
                int dy = now.y - pressScreen.y;
                if (dx == 0 && dy == 0) return;
                movedDuringDrag = true;

                int x = pressRegion.x();
                int y = pressRegion.y();
                int w = pressRegion.width();
                int h = pressRegion.height();

                if (dragMode == ResizeMode.MOVE || captureState != CaptureState.READY) {
                    x += dx;
                    y += dy;
                } else {
                    if (dragMode.west) { x += dx; w -= dx; }
                    if (dragMode.east) w += dx;
                    if (dragMode.north) { y += dy; h -= dy; }
                    if (dragMode.south) h += dy;
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
                if (captureState == CaptureState.READY) onChange.accept(region());
            }

            @Override public void mouseMoved(MouseEvent e) {
                if (controlAt(e.getPoint()) != Control.NONE) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else if (captureState != CaptureState.READY) {
                    setCursor(e.getY() < BAR_HEIGHT
                            ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                            : Cursor.getDefaultCursor());
                } else {
                    setCursor(cursorFor(detectMode(e.getPoint())));
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (pressedControl != Control.NONE) {
                    Control released = controlAt(e.getPoint());
                    Control action = pressedControl == released ? pressedControl : Control.NONE;
                    pressedControl = Control.NONE;
                    repaint();
                    runControl(action);
                    return;
                }

                boolean relocate = captureState != CaptureState.READY && movedDuringDrag;
                pressScreen = null;
                pressRegion = null;
                movedDuringDrag = false;
                CaptureRegion current = region();
                if (captureState == CaptureState.READY) {
                    onChange.accept(current);
                } else if (relocate) {
                    onRelocate.accept(current);
                }
            }
        };

        getContentPane().addMouseListener(mouse);
        getContentPane().addMouseMotionListener(mouse);

        getRootPane().registerKeyboardAction(e -> {
                    if (captureState == CaptureState.READY) setVisible(false);
                }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public void setRecordingControls(Runnable pauseResume, Runnable stop, Runnable closeFrame,
                                     Consumer<CaptureRegion> relocate) {
        this.onPauseResume = pauseResume != null ? pauseResume : () -> {};
        this.onStop = stop != null ? stop : () -> {};
        this.onCloseFrame = closeFrame != null ? closeFrame : () -> setVisible(false);
        this.onRelocate = relocate != null ? relocate : r -> {};
    }

    /** Returns physical screen pixels, not Swing logical coordinates. */
    public CaptureRegion region() {
        Rectangle nativeBounds = nativeWindowBounds();
        if (nativeBounds != null && getWidth() > 0 && getHeight() > 0) {
            double sx = nativeBounds.width / (double) getWidth();
            double sy = nativeBounds.height / (double) getHeight();
            int insetX = Math.max(1, (int) Math.round(BORDER * sx));
            int topInset = Math.max(1, (int) Math.round((BAR_HEIGHT + BORDER) * sy));
            int bottomInset = Math.max(1, (int) Math.round(BORDER * sy));
            int x = nativeBounds.x + insetX;
            int y = nativeBounds.y + topInset;
            int w = Math.max(2, nativeBounds.width - insetX * 2) & ~1;
            int h = Math.max(2, nativeBounds.height - topInset - bottomInset) & ~1;
            return new CaptureRegion(x, y, w, h);
        }

        int w = Math.max(2, getWidth() - BORDER * 2);
        int h = Math.max(2, getHeight() - BAR_HEIGHT - BORDER * 2);
        return new CaptureRegion(getX() + BORDER, getY() + BAR_HEIGHT + BORDER, w & ~1, h & ~1);
    }

    public void setRegion(CaptureRegion region) {
        CaptureRegion r = region.evenSized();
        setBounds(r.x() - BORDER, r.y() - BAR_HEIGHT - BORDER,
                r.width() + BORDER * 2, r.height() + BAR_HEIGHT + BORDER * 2);
        updateWindowShape();
        repaint();
    }

    public void setCaptureState(CaptureState state) {
        this.captureState = state == null ? CaptureState.READY : state;
        repaint();
    }

    public CaptureState captureState() { return captureState; }

    @Override public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) SwingUtilities.invokeLater(this::updateWindowShape);
    }

    private void updateWindowShape() {
        if (!isDisplayable() || getWidth() <= 0 || getHeight() <= 0) return;
        try {
            Area frame = new Area(new Rectangle(0, 0, getWidth(), BAR_HEIGHT + BORDER));
            int captureTop = BAR_HEIGHT + BORDER;
            int captureH = Math.max(1, getHeight() - captureTop - BORDER);
            frame.add(new Area(new Rectangle(0, captureTop, BORDER, captureH)));
            frame.add(new Area(new Rectangle(Math.max(0, getWidth() - BORDER), captureTop, BORDER, captureH)));
            frame.add(new Area(new Rectangle(0, Math.max(0, getHeight() - BORDER), getWidth(), BORDER)));
            setShape(frame);
        } catch (Throwable ignored) {
            // Shape is an enhancement; capture still works if a JVM/desktop implementation rejects it.
        }
    }

    private Control controlAt(Point p) {
        if (captureState == CaptureState.READY || p.y < 0 || p.y >= BAR_HEIGHT) return Control.NONE;
        Rectangle close = controlRect(0);
        Rectangle stop = controlRect(1);
        Rectangle pause = controlRect(2);
        if (close.contains(p)) return Control.CLOSE;
        if (stop.contains(p)) return Control.STOP;
        if (pause.contains(p)) return Control.PAUSE;
        return Control.NONE;
    }

    private Rectangle controlRect(int indexFromRight) {
        int x = getWidth() - 8 - CONTROL_W - indexFromRight * (CONTROL_W + CONTROL_GAP);
        return new Rectangle(x, 4, CONTROL_W, BAR_HEIGHT - 8);
    }

    private void runControl(Control control) {
        switch (control) {
            case PAUSE -> onPauseResume.run();
            case STOP -> onStop.run();
            case CLOSE -> onCloseFrame.run();
            default -> { }
        }
    }

    private ResizeMode detectMode(Point p) {
        int topEdge = BAR_HEIGHT + BORDER;
        int bottomEdge = getHeight() - BORDER;
        int leftEdge = BORDER;
        int rightEdge = getWidth() - BORDER;
        if (p.y < BAR_HEIGHT) return ResizeMode.MOVE;

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

    private Rectangle nativeWindowBounds() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win") || !isDisplayable()) return null;
        try {
            Pointer hwnd = Native.getComponentPointer(this);
            RECT rect = new RECT();
            if (User32.INSTANCE.GetWindowRect(hwnd, rect) != 0) {
                rect.read();
                return new Rectangle(rect.left, rect.top,
                        Math.max(1, rect.right - rect.left), Math.max(1, rect.bottom - rect.top));
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private final class OverlayPanel extends JPanel {
        OverlayPanel() { setOpaque(false); setDoubleBuffered(true); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            CaptureRegion r = region();
            int captureW = Math.max(2, getWidth() - BORDER * 2);
            int captureH = Math.max(2, getHeight() - BAR_HEIGHT - BORDER * 2);
            int captureTop = BAR_HEIGHT + BORDER;

            g2.setColor(new Color(34, 40, 47, 248));
            g2.fillRect(0, 0, getWidth(), BAR_HEIGHT);

            String size = r.width() + "x" + r.height();
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            int baseline = (BAR_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(size, 10, baseline);

            int x = 10 + fm.stringWidth(size) + 10;
            String status = captureState == CaptureState.RECORDING ? "REC" :
                    captureState == CaptureState.PAUSED ? "PAUSE" : "READY";
            Color statusColor = captureState == CaptureState.RECORDING ? new Color(238, 62, 62) :
                    captureState == CaptureState.PAUSED ? new Color(255, 183, 77) : new Color(210, 216, 222);
            if (captureState == CaptureState.RECORDING) {
                g2.setColor(statusColor);
                g2.fillOval(x, baseline - 10, 9, 9);
                x += 15;
            }
            g2.setColor(statusColor);
            g2.drawString(status, x, baseline);

            if (captureState != CaptureState.READY) {
                drawControl(g2, Control.PAUSE, controlRect(2), captureState == CaptureState.PAUSED ? "▶" : "Ⅱ");
                drawControl(g2, Control.STOP, controlRect(1), "■");
                drawControl(g2, Control.CLOSE, controlRect(0), "×");
            }

            g2.setColor(new Color(40, 145, 214));
            g2.fillRect(0, BAR_HEIGHT, getWidth(), BORDER);
            g2.fillRect(0, captureTop, BORDER, captureH);
            g2.fillRect(BORDER + captureW, captureTop, BORDER, captureH);
            g2.fillRect(0, captureTop + captureH, getWidth(), BORDER);
            g2.dispose();
        }

        private void drawControl(Graphics2D g2, Control control, Rectangle rect, String label) {
            g2.setColor(pressedControl == control ? new Color(65, 145, 255) : new Color(48, 55, 66));
            g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 6, 6);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI Symbol", Font.BOLD, 14));
            FontMetrics fm = g2.getFontMetrics();
            int tx = rect.x + (rect.width - fm.stringWidth(label)) / 2;
            int ty = rect.y + (rect.height - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(label, tx, ty);
        }
    }

    @Structure.FieldOrder({"left", "top", "right", "bottom"})
    public static class RECT extends Structure {
        public int left, top, right, bottom;
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);
        int GetWindowRect(Pointer hWnd, RECT rect);
    }

    private enum Control { NONE, PAUSE, STOP, CLOSE }

    private enum ResizeMode {
        MOVE(false, false, false, false), N(true, false, false, false), S(false, true, false, false),
        W(false, false, true, false), E(false, false, false, true), NW(true, false, true, false),
        NE(true, false, false, true), SW(false, true, true, false), SE(false, true, false, true);
        final boolean north, south, west, east;
        ResizeMode(boolean north, boolean south, boolean west, boolean east) {
            this.north = north; this.south = south; this.west = west; this.east = east;
        }
    }
}
