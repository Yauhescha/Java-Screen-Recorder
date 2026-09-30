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
    static final Color ACCENT_DARK = new Color(42, 105, 190);
    static final Color RECORD = new Color(232, 68, 73);
    static final Color GOOD = new Color(71, 196, 129);
    static final Color DISABLED_BG = new Color(30, 34, 42);
    static final Color DISABLED_TEXT = new Color(177, 184, 196);

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
                "Label.disabledForeground", new ColorUIResource(DISABLED_TEXT),
                "Label[Disabled].textForeground", new ColorUIResource(DISABLED_TEXT),
                "Panel.background", BG,
                "OptionPane.background", PANEL,
                "OptionPane.messageForeground", TEXT,
                "TextField.background", PANEL_ALT,
                "TextField.foreground", TEXT,
                "TextField.caretForeground", TEXT,
                "TextField.inactiveForeground", new ColorUIResource(DISABLED_TEXT),
                "TextField.disabledForeground", new ColorUIResource(DISABLED_TEXT),
                "TextField[Disabled].textForeground", new ColorUIResource(DISABLED_TEXT),
                "TextField[Disabled].background", new ColorUIResource(DISABLED_BG),
                "TextArea.background", new Color(14, 17, 22),
                "TextArea.foreground", new Color(207, 214, 226),
                "ComboBox.background", PANEL_ALT,
                "ComboBox.foreground", TEXT,
                "ComboBox.disabledForeground", new ColorUIResource(DISABLED_TEXT),
                "ComboBox[Disabled].textForeground", new ColorUIResource(DISABLED_TEXT),
                "ComboBox[Disabled].background", new ColorUIResource(DISABLED_BG),
                "Spinner.background", PANEL_ALT,
                "Spinner.disabledForeground", new ColorUIResource(DISABLED_TEXT),
                "Spinner[Disabled].textForeground", new ColorUIResource(DISABLED_TEXT),
                "Spinner[Disabled].background", new ColorUIResource(DISABLED_BG),
                "CheckBox.background", PANEL,
                "CheckBox.foreground", TEXT,
                "CheckBox.disabledText", DISABLED_TEXT,
                "CheckBox.disabledForeground", new ColorUIResource(DISABLED_TEXT),
                "CheckBox[Enabled].textForeground", new ColorUIResource(TEXT),
                "CheckBox[Disabled].textForeground", new ColorUIResource(DISABLED_TEXT),
                "Button.background", PANEL_ALT,
                "Button.foreground", TEXT,
                "Button.disabledForeground", new ColorUIResource(DISABLED_TEXT),
                "Button.disabledText", new ColorUIResource(DISABLED_TEXT),
                "Button[Disabled].textForeground", new ColorUIResource(DISABLED_TEXT),
                "Button[Disabled].background", new ColorUIResource(DISABLED_BG),
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
        box.setIcon(new CheckIcon(false, false));
        box.setSelectedIcon(new CheckIcon(true, false));
        box.setDisabledIcon(new CheckIcon(false, true));
        box.setDisabledSelectedIcon(new CheckIcon(true, true));
        box.setIconTextGap(7);
        box.addItemListener(e -> updateCheckBoxState(box));
        box.addPropertyChangeListener("enabled", e -> updateCheckBoxState(box));
        updateCheckBoxState(box);
    }

    private static void updateCheckBoxState(JCheckBox box) {
        if (!box.isEnabled()) {
            box.setForeground(DISABLED_TEXT);
            box.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        } else if (box.isSelected()) {
            box.setForeground(new Color(143, 195, 255));
            box.setFont(new Font("Segoe UI", Font.BOLD, 13));
        } else {
            box.setForeground(TEXT);
            box.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        }
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
                if (!combo.isEnabled()) {
                    label.setBackground(DISABLED_BG);
                    label.setForeground(DISABLED_TEXT);
                } else if (isSelected) {
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
            field.setUI(new javax.swing.plaf.basic.BasicFormattedTextFieldUI());
            field.setOpaque(true);
            field.setForeground(TEXT);
            field.setDisabledTextColor(DISABLED_TEXT);
            field.setBackground(PANEL_ALT);
            field.setCaretColor(TEXT);
            field.setSelectionColor(ACCENT);
            field.setSelectedTextColor(Color.WHITE);
            field.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            spinner.addPropertyChangeListener("enabled", e -> {
                field.setBackground(spinner.isEnabled() ? PANEL_ALT : DISABLED_BG);
                field.setForeground(spinner.isEnabled() ? TEXT : DISABLED_TEXT);
            });
        }
    }

    static void keepDisabledTextReadable(JTextField field) {
        field.setForeground(TEXT);
        field.setDisabledTextColor(DISABLED_TEXT);
        field.setBackground(PANEL_ALT);
        field.addPropertyChangeListener("enabled", e ->
                field.setBackground(field.isEnabled() ? PANEL_ALT : DISABLED_BG));
    }

    static JButton button(String text) {
        JButton button = new JButton(text);
        configureButton(button, false, false);
        return button;
    }

    static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        configureButton(button, true, false);
        return button;
    }

    static JButton recordButton(String text) {
        JButton button = new JButton(text);
        configureButton(button, false, true);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setPreferredSize(new Dimension(180, 38));
        return button;
    }

    static void styleToggleButton(AbstractButton button) {
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setForeground(TEXT);
        button.setBackground(PANEL_ALT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 11, 5, 11)));
        button.getModel().addChangeListener(e -> updateButtonColors(button, false, false));
        updateButtonColors(button, false, false);
    }

    private static void configureButton(AbstractButton button, boolean primary, boolean record) {
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setForeground(Color.WHITE);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? new Color(86, 161, 255) :
                        record ? new Color(255, 102, 107) : BORDER),
                BorderFactory.createEmptyBorder(5, 11, 5, 11)));
        button.getModel().addChangeListener(e -> updateButtonColors(button, primary, record));
        button.addPropertyChangeListener("enabled", e -> updateButtonColors(button, primary, record));
        updateButtonColors(button, primary, record);
    }

    private static void updateButtonColors(AbstractButton button, boolean primary, boolean record) {
        ButtonModel model = button.getModel();
        if (!button.isEnabled()) {
            button.setBackground(DISABLED_BG);
            button.setForeground(DISABLED_TEXT);
            return;
        }
        if (model.isPressed() || model.isSelected()) {
            button.setBackground(record ? new Color(181, 45, 50) : ACCENT_DARK);
            button.setForeground(Color.WHITE);
        } else if (model.isRollover()) {
            button.setBackground(record ? new Color(245, 82, 88) : primary ? new Color(79, 158, 255) : new Color(49, 58, 72));
            button.setForeground(Color.WHITE);
        } else {
            button.setBackground(record ? RECORD : primary ? ACCENT : PANEL_ALT);
            button.setForeground(TEXT);
        }
    }

    private static final class CheckIcon implements Icon {
        private final boolean selected;
        private final boolean disabled;

        private CheckIcon(boolean selected, boolean disabled) {
            this.selected = selected;
            this.disabled = disabled;
        }

        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = disabled ? new Color(45, 50, 60) : selected ? ACCENT : new Color(31, 36, 45);
            Color outline = disabled ? new Color(76, 82, 94) : selected ? new Color(112, 181, 255) : new Color(91, 100, 116);
            g2.setColor(fill);
            g2.fillRoundRect(x, y, 15, 15, 4, 4);
            g2.setColor(outline);
            g2.drawRoundRect(x, y, 15, 15, 4, 4);
            if (selected) {
                g2.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(disabled ? new Color(180, 184, 192) : Color.WHITE);
                g2.drawLine(x + 3, y + 8, x + 6, y + 11);
                g2.drawLine(x + 6, y + 11, x + 12, y + 4);
            }
            g2.dispose();
        }
    }
}
