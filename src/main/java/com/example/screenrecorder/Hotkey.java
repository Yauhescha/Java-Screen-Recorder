package com.example.screenrecorder;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public record Hotkey(int keyCode, int modifiers) {
    public static final int CTRL = 1;
    public static final int ALT = 1 << 1;
    public static final int SHIFT = 1 << 2;
    public static final int WIN = 1 << 3;

    public static Hotkey fromKeyEvent(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_CONTROL || key == KeyEvent.VK_ALT ||
                key == KeyEvent.VK_SHIFT || key == KeyEvent.VK_META) {
            return null;
        }

        int modifiers = 0;
        int ex = e.getModifiersEx();
        if ((ex & KeyEvent.CTRL_DOWN_MASK) != 0) modifiers |= CTRL;
        if ((ex & KeyEvent.ALT_DOWN_MASK) != 0) modifiers |= ALT;
        if ((ex & KeyEvent.SHIFT_DOWN_MASK) != 0) modifiers |= SHIFT;
        if ((ex & KeyEvent.META_DOWN_MASK) != 0) modifiers |= WIN;
        return new Hotkey(key, modifiers);
    }

    public String displayName() {
        List<String> parts = new ArrayList<>();
        if ((modifiers & CTRL) != 0) parts.add("Ctrl");
        if ((modifiers & ALT) != 0) parts.add("Alt");
        if ((modifiers & SHIFT) != 0) parts.add("Shift");
        if ((modifiers & WIN) != 0) parts.add("Win");
        parts.add(KeyEvent.getKeyText(keyCode));
        return String.join("+", parts);
    }

    public String serialize() {
        return keyCode + ":" + modifiers;
    }

    public static Hotkey deserialize(String value, Hotkey fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            String[] split = value.split(":", 2);
            return new Hotkey(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
