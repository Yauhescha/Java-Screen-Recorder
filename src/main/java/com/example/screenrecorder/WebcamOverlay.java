package com.example.screenrecorder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/** Movable/resizable live webcam preview positioned directly over the capture rectangle. */
public final class WebcamOverlay extends JWindow {
    private static final int EDGE = 9;
    private static final int MIN_W = 96;
    private static final int MIN_H = 54;
    private static final double ASPECT = 16.0 / 9.0;

    private final Consumer<WebcamPlacement> onChange;
    private Rectangle captureBounds;
    private Point pressScreen;
    private Rectangle pressBounds;
    private ResizeMode dragMode = ResizeMode.MOVE;
    private String deviceName = "Webcam";
    private volatile BufferedImage previewImage;
    private volatile String previewMessage = "STARTING CAMERA...";
    private volatile boolean mirror;
    private volatile WebcamShape shape = WebcamShape.ROUNDED;
    private volatile boolean borderEnabled = true;
    private volatile boolean shadowEnabled = true;
    private volatile Color borderColor = Color.WHITE;
    private volatile boolean locked;

    public WebcamOverlay(Rectangle captureBounds, WebcamPlacement placement, Consumer<WebcamPlacement> onChange) {
        this.captureBounds = new Rectangle(captureBounds);
        this.onChange = onChange;
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        setBackground(new Color(0, 0, 0, 0));
        setType(Window.Type.UTILITY);
        setContentPane(new OverlayPanel());
        setPlacement(placement);

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (locked) return;
                pressScreen = e.getLocationOnScreen();
                pressBounds = getBounds();
                dragMode = detectMode(e.getPoint());
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (locked || pressScreen == null || pressBounds == null) return;
                Point now = e.getLocationOnScreen();
                int dx = now.x - pressScreen.x;
                int dy = now.y - pressScreen.y;
                int x = pressBounds.x, y = pressBounds.y, w = pressBounds.width, h = pressBounds.height;
                if (dragMode == ResizeMode.MOVE) {
                    x += dx;
                    y += dy;
                } else {
                    int right = pressBounds.x + pressBounds.width;
                    int bottom = pressBounds.y + pressBounds.height;
                    if (dragMode.west) w = pressBounds.width - dx;
                    if (dragMode.east) w = pressBounds.width + dx;
                    if (dragMode.north) h = pressBounds.height - dy;
                    if (dragMode.south) h = pressBounds.height + dy;

                    double widthChange = Math.abs(w - pressBounds.width) / (double) Math.max(1, pressBounds.width);
                    double heightChange = Math.abs(h - pressBounds.height) / (double) Math.max(1, pressBounds.height);
                    if (dragMode.west || dragMode.east) {
                        if ((dragMode.north || dragMode.south) && heightChange > widthChange) {
                            h = Math.max(MIN_H, h);
                            w = (int) Math.round(h * ASPECT);
                        } else {
                            w = Math.max(MIN_W, w);
                            h = (int) Math.round(w / ASPECT);
                        }
                    } else {
                        h = Math.max(MIN_H, h);
                        w = (int) Math.round(h * ASPECT);
                    }

                    w = Math.max(MIN_W, w) & ~1;
                    h = Math.max(MIN_H, h) & ~1;
                    if (dragMode.west) x = right - w;
                    if (dragMode.north) y = bottom - h;
                }
                Rectangle next = clampAspectAbsolute(new Rectangle(x, y, w, h));
                setBounds(next);
                onChange.accept(placement());
            }
            @Override public void mouseMoved(MouseEvent e) {
                setCursor(locked ? Cursor.getDefaultCursor() : cursorFor(detectMode(e.getPoint())));
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (locked) return;
                pressScreen = null; pressBounds = null; onChange.accept(placement());
            }
        };
        getContentPane().addMouseListener(mouse);
        getContentPane().addMouseMotionListener(mouse);
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName == null || deviceName.isBlank() ? "Webcam" : deviceName; repaint();
    }
    public void setPreviewImage(BufferedImage image) { this.previewImage = image; repaint(); }
    public void clearPreview() { this.previewImage = null; this.previewMessage = "STARTING CAMERA..."; repaint(); }

    public void setPreviewMessage(String message) {
        this.previewMessage = message == null || message.isBlank() ? "STARTING CAMERA..." : message;
        repaint();
    }
    public void setMirror(boolean mirror) { this.mirror = mirror; repaint(); }
    public void setShape(WebcamShape shape) { this.shape = shape == null ? WebcamShape.ROUNDED : shape; repaint(); }
    public void setBorderEnabled(boolean value) { this.borderEnabled = value; repaint(); }
    public void setShadowEnabled(boolean value) { this.shadowEnabled = value; repaint(); }
    public void setBorderColor(Color color) { this.borderColor = color == null ? Color.WHITE : color; repaint(); }

    public void setLocked(boolean locked) {
        this.locked = locked;
        setCursor(locked ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        repaint();
    }

    /**
     * Changes the captured desktop bounds while keeping the preview at the same
     * physical screen position whenever possible. This prevents the camera from
     * jumping to another monitor when the selected monitor set changes.
     */
    public void setCaptureBounds(Rectangle captureBounds) {
        Rectangle absolute = getBounds();
        this.captureBounds = new Rectangle(captureBounds);
        setBounds(clampAspectAbsolute(absolute));
        repaint();
    }

    /** Moves the preview together with a moving capture region, preserving its position inside the video. */
    public void setCaptureBoundsKeepingPlacement(Rectangle captureBounds) {
        WebcamPlacement current = placement();
        this.captureBounds = new Rectangle(captureBounds);
        setPlacement(current);
    }
    public WebcamPlacement placement() {
        Rectangle b = getBounds();
        return new WebcamPlacement(b.x - captureBounds.x, b.y - captureBounds.y, b.width, b.height)
                .clampTo(captureBounds.width, captureBounds.height);
    }
    public void setPlacement(WebcamPlacement placement) {
        WebcamPlacement p = normalizePlacement(placement);
        setBounds(captureBounds.x + p.x(), captureBounds.y + p.y(), p.width(), p.height());
        repaint();
    }

    private Shape contentShape(int inset) {
        int w = Math.max(2, getWidth() - inset * 2);
        int h = Math.max(2, getHeight() - inset * 2);
        return switch (shape) {
            case RECTANGLE -> new Rectangle(inset, inset, w, h);
            case ROUNDED -> new RoundRectangle2D.Double(inset, inset, w, h,
                    Math.max(18, Math.min(w, h) * 0.18), Math.max(18, Math.min(w, h) * 0.18));
            case CIRCLE -> {
                int d = Math.max(2, Math.min(w, h));
                yield new Ellipse2D.Double((getWidth() - d) / 2.0, (getHeight() - d) / 2.0, d, d);
            }
        };
    }

    private WebcamPlacement normalizePlacement(WebcamPlacement requested) {
        int maxW = Math.max(MIN_W, captureBounds.width);
        int maxH = Math.max(MIN_H, captureBounds.height);
        int w = Math.max(MIN_W, requested.width());
        int h = (int) Math.round(w / ASPECT);
        if (h > maxH) {
            h = maxH;
            w = (int) Math.round(h * ASPECT);
        }
        if (w > maxW) {
            w = maxW;
            h = (int) Math.round(w / ASPECT);
        }
        w = Math.max(2, w & ~1);
        h = Math.max(2, h & ~1);
        int x = Math.max(0, Math.min(requested.x(), Math.max(0, captureBounds.width - w)));
        int y = Math.max(0, Math.min(requested.y(), Math.max(0, captureBounds.height - h)));
        return new WebcamPlacement(x, y, w, h);
    }

    private Rectangle clampAspectAbsolute(Rectangle requested) {
        int maxW = Math.max(MIN_W, captureBounds.width);
        int maxH = Math.max(MIN_H, captureBounds.height);
        int w = Math.max(MIN_W, requested.width);
        int h = (int) Math.round(w / ASPECT);
        if (h > maxH) {
            h = maxH;
            w = (int) Math.round(h * ASPECT);
        }
        if (w > maxW) {
            w = maxW;
            h = (int) Math.round(w / ASPECT);
        }
        w = Math.max(2, w & ~1);
        h = Math.max(2, h & ~1);
        int x = Math.max(captureBounds.x, Math.min(requested.x, captureBounds.x + captureBounds.width - w));
        int y = Math.max(captureBounds.y, Math.min(requested.y, captureBounds.y + captureBounds.height - h));
        return new Rectangle(x, y, w, h);
    }
    private ResizeMode detectMode(Point p) {
        boolean west = p.x <= EDGE, east = p.x >= getWidth() - EDGE;
        boolean north = p.y <= EDGE, south = p.y >= getHeight() - EDGE;
        if (north && west) return ResizeMode.NW; if (north && east) return ResizeMode.NE;
        if (south && west) return ResizeMode.SW; if (south && east) return ResizeMode.SE;
        if (north) return ResizeMode.N; if (south) return ResizeMode.S;
        if (west) return ResizeMode.W; if (east) return ResizeMode.E; return ResizeMode.MOVE;
    }
    private Cursor cursorFor(ResizeMode mode) {
        return Cursor.getPredefinedCursor(switch (mode) {
            case N -> Cursor.N_RESIZE_CURSOR; case S -> Cursor.S_RESIZE_CURSOR;
            case E -> Cursor.E_RESIZE_CURSOR; case W -> Cursor.W_RESIZE_CURSOR;
            case NE -> Cursor.NE_RESIZE_CURSOR; case NW -> Cursor.NW_RESIZE_CURSOR;
            case SE -> Cursor.SE_RESIZE_CURSOR; case SW -> Cursor.SW_RESIZE_CURSOR;
            default -> Cursor.MOVE_CURSOR;
        });
    }

    private final class OverlayPanel extends JPanel {
        OverlayPanel() { setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape content = contentShape(6);
            if (shadowEnabled) {
                g2.translate(4, 4);
                g2.setColor(new Color(0, 0, 0, 105));
                g2.fill(content);
                g2.translate(-4, -4);
            }
            Shape oldClip = g2.getClip();
            g2.clip(content);
            BufferedImage image = previewImage;
            if (image != null) {
                Rectangle b = content.getBounds();
                if (mirror) {
                    g2.drawImage(image, b.x + b.width, b.y, -b.width, b.height, null);
                } else {
                    g2.drawImage(image, b.x, b.y, b.width, b.height, null);
                }
            } else {
                g2.setColor(new Color(8, 12, 18, 220));
                g2.fill(content);
            }
            g2.setClip(oldClip);
            if (borderEnabled) {
                g2.setStroke(new BasicStroke(4f));
                g2.setColor(borderColor);
                g2.draw(content);
            } else {
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(new Color(65, 145, 255));
                g2.draw(content);
            }

            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            String text = previewImage == null ? previewMessage : getWidth() + "×" + getHeight() + (locked ? "  •  REC" : "");
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(text);
            int tx = Math.max(5, (getWidth() - tw) / 2);
            int ty = getHeight() - 14;
            g2.setColor(new Color(0, 0, 0, 170));
            g2.fillRoundRect(tx - 6, ty - fm.getAscent(), tw + 12, fm.getHeight() + 2, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawString(text, tx, ty);
            g2.dispose();
        }
    }

    private enum ResizeMode {
        MOVE(false,false,false,false), N(true,false,false,false), S(false,true,false,false),
        W(false,false,true,false), E(false,false,false,true), NW(true,false,true,false),
        NE(true,false,false,true), SW(false,true,true,false), SE(false,true,false,true);
        final boolean north,south,west,east;
        ResizeMode(boolean n, boolean s, boolean w, boolean e) { north=n; south=s; west=w; east=e; }
    }
}
