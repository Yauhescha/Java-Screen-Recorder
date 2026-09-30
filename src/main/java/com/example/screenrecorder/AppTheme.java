package com.example.screenrecorder;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;

final class AppTheme {
    static final Color BG = new Color(18, 21, 27);
    static final Color PANEL = new Color(27, 31, 39);
    static final Color PANEL_ALT = new Color(34, 39, 49);
    static final Color BORDER = new Color(57, 64, 78);
    static final Color TEXT = new Color(236, 239, 244);
    static final Color MUTED = new Color(154, 163, 178);
    static final Color ACCENT = new Color(65, 145, 255);
    static final Color RECORD = new Color(232, 68, 73);
    static final Color GOOD = new Color(71, 196, 129);

    private AppTheme() {}

    static void apply() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
        }

        Font font = new Font("Segoe UI", Font.PLAIN, 13);
        Font bold = font.deriveFont(Font.BOLD);
        Object[] defaults = {
                "control", new ColorUIResource(PANEL),
                "info", new ColorUIResource(PANEL_ALT),
                "nimbusBase", new ColorUIResource(new Color(42, 49, 61)),
                "nimbusBlueGrey", new ColorUIResource(PANEL_ALT),
                "nimbusLightBackground", new ColorUIResource(PANEL_ALT),
                "nimbusSelectionBackground", new ColorUIResource(ACCENT),
                "text", new ColorUIResource(TEXT),
                "textText", new ColorUIResource(TEXT),
                "Label.foreground", TEXT,
                "Label.disabledForeground", new ColorUIResource(TEXT),
                "Label[Disabled].textForeground", new ColorUIResource(TEXT),
                "Panel.background", BG,
                "OptionPane.background", PANEL,
                "OptionPane.messageForeground", TEXT,
                "TextField.background", PANEL_ALT,
                "TextField.foreground", TEXT,
                "TextField.caretForeground", TEXT,
                "TextField.inactiveForeground", new ColorUIResource(TEXT),
                "TextField.disabledForeground", new ColorUIResource(TEXT),
                "TextField[Disabled].textForeground", new ColorUIResource(TEXT),
                "TextField[Disabled].background", new ColorUIResource(PANEL_ALT),
                "TextArea.background", new Color(14, 17, 22),
                "TextArea.foreground", new Color(207, 214, 226),
                "ComboBox.background", PANEL_ALT,
                "ComboBox.foreground", TEXT,
                "ComboBox.disabledForeground", new ColorUIResource(TEXT),
                "ComboBox[Disabled].textForeground", new ColorUIResource(TEXT),
                "ComboBox[Disabled].background", new ColorUIResource(PANEL_ALT),
                "Spinner.background", PANEL_ALT,
                "Spinner.disabledForeground", new ColorUIResource(TEXT),
                "Spinner[Disabled].textForeground", new ColorUIResource(TEXT),
                "Spinner[Disabled].background", new ColorUIResource(PANEL_ALT),
                "CheckBox.background", PANEL,
                "CheckBox.foreground", TEXT,
                "CheckBox.disabledText", TEXT,
                "CheckBox.disabledForeground", new ColorUIResource(TEXT),
                "CheckBox[Enabled].textForeground", new ColorUIResource(TEXT),
                "CheckBox[Disabled].textForeground", new ColorUIResource(TEXT),
                "Button.background", PANEL_ALT,
                "Button.foreground", TEXT,
                "Button.disabledForeground", new ColorUIResource(TEXT),
                "Button.disabledText", new ColorUIResource(TEXT),
                "Button[Disabled].textForeground", new ColorUIResource(TEXT),
                "Button[Disabled].background", new ColorUIResource(PANEL_ALT),
                "TitledBorder.titleColor", MUTED,
                "ToolTip.background", new ColorUIResource(new Color(38, 44, 55)),
                "ToolTip.foreground", new ColorUIResource(Color.WHITE),
                "ToolTip[Enabled].background", new ColorUIResource(new Color(38, 44, 55)),
                "ToolTip[Enabled].textForeground", new ColorUIResource(Color.WHITE),
                "ToolTip[Disabled].textForeground", new ColorUIResource(Color.WHITE),
                "ToolTip.border", BorderFactory.createLineBorder(BORDER),
                "RootPane.background", BG,
                "defaultFont", font,
                "Label.font", font,
                "Button.font", bold,
                "CheckBox.font", font,
                "ComboBox.font", font,
                "TextField.font", font,
                "TextArea.font", font,
                "Spinner.font", font,
                "TabbedPane.background", new ColorUIResource(PANEL_ALT),
                "TabbedPane.foreground", new ColorUIResource(TEXT),
                "TabbedPane.contentAreaColor", new ColorUIResource(BG),
                "TabbedPane.tabAreaBackground", new ColorUIResource(BG),
                "TabbedPane.selected", new ColorUIResource(PANEL),
                "TabbedPane.focus", new ColorUIResource(ACCENT),
                "TabbedPane.font", bold
        };
        UIManager.getDefaults().putDefaults(defaults);
        UIManager.getDefaults().put("ToolTipUI", DarkToolTipUI.class.getName());
    }

    static Border cardBorder(String title) {
        Border line = BorderFactory.createLineBorder(BORDER, 1, true);
        Border titled = BorderFactory.createTitledBorder(line, title,
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13),
                MUTED);
        return BorderFactory.createCompoundBorder(titled, BorderFactory.createEmptyBorder(8, 10, 10, 10));
    }

    static void styleCheckBox(JCheckBox box) {
        box.setOpaque(false);
        box.setForeground(TEXT);
        box.setFont(new Font("Segoe UI", Font.PLAIN, 13));
    }

    static void styleComboBox(JComboBox<?> combo) {
        combo.setForeground(TEXT);
        combo.setBackground(PANEL_ALT);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setOpaque(true);
                label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                if (isSelected) {
                    label.setBackground(ACCENT);
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(PANEL_ALT);
                    label.setForeground(TEXT);
                }
                label.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
                return label;
            }
        });
    }

    static void styleSpinner(JSpinner spinner) {
        spinner.setForeground(TEXT);
        spinner.setBackground(PANEL_ALT);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            JFormattedTextField field = editor.getTextField();
            // Nimbus may paint the formatted editor with a white background even after setBackground().
            // BasicFormattedTextFieldUI respects the explicit dark palette and keeps enabled/disabled
            // numeric values (notably FPS) readable.
            field.setUI(new javax.swing.plaf.basic.BasicFormattedTextFieldUI());
            field.setOpaque(true);
            field.setForeground(TEXT);
            field.setDisabledTextColor(TEXT);
            field.setBackground(PANEL_ALT);
            field.setCaretColor(TEXT);
            field.setSelectionColor(ACCENT);
            field.setSelectedTextColor(Color.WHITE);
            field.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        }
    }

    static void keepDisabledTextReadable(JTextField field) {
        field.setForeground(TEXT);
        field.setDisabledTextColor(TEXT);
        field.setBackground(PANEL_ALT);
    }

    static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setForeground(TEXT);
        button.setBackground(PANEL_ALT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 11, 5, 11)));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                if (button.isEnabled() && !button.getModel().isPressed()) button.setBackground(new Color(47, 55, 68));
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                if (button.isEnabled() && !button.getModel().isPressed()) button.setBackground(PANEL_ALT);
            }
        });
        return button;
    }

    static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(ACCENT);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(86, 161, 255)),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        return button;
    }

    static JButton recordButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBackground(RECORD);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 102, 107)),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        button.setPreferredSize(new Dimension(180, 38));
        return button;
    }
}
