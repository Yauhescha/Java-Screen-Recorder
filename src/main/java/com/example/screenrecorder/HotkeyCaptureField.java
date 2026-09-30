package com.example.screenrecorder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

final class HotkeyCaptureField extends JTextField {
    private Hotkey hotkey;
    private final Consumer<Hotkey> onChange;
    private Runnable onCaptureStart = () -> {};
    private Runnable onCaptureEnd = () -> {};

    HotkeyCaptureField(Hotkey initial, Consumer<Hotkey> onChange) {
        super(14);
        this.hotkey = initial;
        this.onChange = onChange;
        setEditable(false);
        setHorizontalAlignment(SwingConstants.CENTER);
        setToolTipText("Click this field, then press the desired key combination");
        setBackground(AppTheme.PANEL_ALT);
        setForeground(AppTheme.TEXT);
        setDisabledTextColor(AppTheme.TEXT);
        setCaretColor(AppTheme.TEXT);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        refreshText();

        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                setText("Press a key...");
                setForeground(Color.WHITE);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(AppTheme.ACCENT, 2),
                        BorderFactory.createEmptyBorder(3, 7, 3, 7)));
                onCaptureStart.run();
            }

            @Override public void focusLost(FocusEvent e) {
                refreshText();
                setForeground(AppTheme.TEXT);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(AppTheme.BORDER),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)));
                onCaptureEnd.run();
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                Hotkey candidate = Hotkey.fromKeyEvent(e);
                e.consume();
                if (candidate == null) return;
                hotkey = candidate;
                refreshText();
                onChange.accept(candidate);
                onCaptureEnd.run();
                SwingUtilities.invokeLater(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner());
            }
        });
    }

    void setCaptureCallbacks(Runnable start, Runnable end) {
        this.onCaptureStart = start != null ? start : () -> {};
        this.onCaptureEnd = end != null ? end : () -> {};
    }

    Hotkey hotkey() {
        return hotkey;
    }

    void setHotkey(Hotkey hotkey) {
        this.hotkey = hotkey;
        refreshText();
    }

    @Override
    public JToolTip createToolTip() {
        JToolTip tip = super.createToolTip();
        tip.setBackground(new Color(38, 44, 55));
        tip.setForeground(Color.WHITE);
        tip.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.ACCENT),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        return tip;
    }

    private void refreshText() {
        setText(hotkey.displayName());
        setCaretPosition(0);
    }
}
