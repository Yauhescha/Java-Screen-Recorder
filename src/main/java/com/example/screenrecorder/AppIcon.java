package com.example.screenrecorder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

final class AppIcon {
    private AppIcon() {}

    static Image load() {
        try (InputStream in = AppIcon.class.getResourceAsStream("/icons/app-icon.png")) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (IOException ignored) {
            return null;
        }
    }
}
