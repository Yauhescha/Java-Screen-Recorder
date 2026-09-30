package com.example.screenrecorder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class ScreenCaptureService {
    Path capture(Path outputDirectory, CaptureMode mode, CaptureRegion region,
                 List<DisplayMonitor> monitors, WindowTarget windowTarget) throws Exception {
        Files.createDirectories(outputDirectory);
        Robot robot = new Robot();
        BufferedImage image;

        if (mode == CaptureMode.WINDOW) {
            if (windowTarget == null) throw new IllegalArgumentException("Select a window");
            image = robot.createScreenCapture(windowTarget.bounds());
        } else if (mode == CaptureMode.REGION) {
            image = robot.createScreenCapture(new Rectangle(region.x(), region.y(), region.width(), region.height()));
        } else {
            if (monitors == null || monitors.isEmpty()) throw new IllegalArgumentException("No monitor selected");
            Rectangle union = WindowsCaptureService.unionBounds(monitors);
            image = new BufferedImage(Math.max(1, union.width), Math.max(1, union.height), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, image.getWidth(), image.getHeight());
                for (DisplayMonitor monitor : monitors) {
                    Rectangle b = monitor.bounds();
                    BufferedImage part = robot.createScreenCapture(b);
                    g.drawImage(part, b.x - union.x, b.y - union.y, null);
                }
            } finally {
                g.dispose();
            }
        }

        Path file = OutputNaming.screenshotFile(outputDirectory);
        if (!ImageIO.write(image, "png", file.toFile())) throw new IllegalStateException("PNG writer is unavailable");
        return file;
    }

    static Rectangle virtualDesktopBounds() {
        Rectangle result = null;
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            for (GraphicsConfiguration configuration : device.getConfigurations()) {
                Rectangle bounds = configuration.getBounds();
                result = result == null ? new Rectangle(bounds) : result.union(bounds);
            }
        }
        if (result == null) {
            Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
            result = new Rectangle(0, 0, size.width, size.height);
        }
        return result;
    }
}
