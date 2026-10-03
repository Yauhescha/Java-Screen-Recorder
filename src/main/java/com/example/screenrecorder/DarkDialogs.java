package com.example.screenrecorder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consistent modal dialogs for the dark application theme.
 * Avoids platform/Nimbus JOptionPane color combinations that can make text unreadable.
 */
final class DarkDialogs {
    static final int CLOSED = -1;

    private DarkDialogs() {}

    static void info(Component owner, String title, String message) {
        showMessage(owner, title, message, AppTheme.ACCENT);
    }

    static void warning(Component owner, String title, String message) {
        showMessage(owner, title, message, new Color(255, 183, 77));
    }

    static void error(Component owner, String title, String message) {
        showMessage(owner, title, message, AppTheme.RECORD);
    }

    static void error(Component owner, String title, String message, String details) {
        JTextArea summary = messageArea(message, 4, 58);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(summary, BorderLayout.NORTH);

        if (details != null && !details.isBlank()) {
            JTextArea technical = messageArea(details, 5, 58);
            technical.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JScrollPane scroll = new JScrollPane(technical);
            scroll.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(AppTheme.BORDER),
                    "Technical details",
                    javax.swing.border.TitledBorder.LEFT,
                    javax.swing.border.TitledBorder.TOP,
                    new Font("Segoe UI", Font.BOLD, 12),
                    AppTheme.MUTED));
            scroll.setPreferredSize(new Dimension(620, 135));
            body.add(scroll, BorderLayout.CENTER);
        }

        show(owner, title, body, AppTheme.RECORD, "OK");
    }

    static int confirmYesNo(Component owner, String title, String message, String yesText, String noText) {
        return show(owner, title, messagePanel(message), new Color(255, 183, 77), yesText, noText);
    }

    static int confirmYesNoCancel(Component owner, String title, String message,
                                  String yesText, String noText, String cancelText) {
        return show(owner, title, messagePanel(message), AppTheme.ACCENT, yesText, noText, cancelText);
    }

    static int options(Component owner, String title, String message, Color accent, String... options) {
        return show(owner, title, messagePanel(message), accent, options);
    }

    static int componentOptions(Component owner, String title, JComponent body, Color accent, String... options) {
        return show(owner, title, body, accent, options);
    }

    private static void showMessage(Component owner, String title, String message, Color accent) {
        show(owner, title, messagePanel(message), accent, "OK");
    }

    private static JPanel messagePanel(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(messageArea(message, 5, 58), BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea messageArea(String message, int rows, int columns) {
        JTextArea text = new JTextArea(message == null ? "" : message, rows, columns);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(true);
        text.setBackground(new Color(22, 26, 33));
        text.setForeground(Color.WHITE);
        text.setCaretColor(AppTheme.TEXT);
        text.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        text.setBorder(new EmptyBorder(10, 12, 10, 12));
        return text;
    }

    private static int show(Component ownerComponent, String title, JComponent body, Color accent, String... options) {
        Window owner = ownerComponent == null ? null : SwingUtilities.getWindowAncestor(ownerComponent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        AtomicInteger result = new AtomicInteger(CLOSED);

        JPanel root = new JPanel(new BorderLayout(12, 14));
        root.setBackground(AppTheme.BG);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        JLabel heading = new JLabel(title == null ? "" : title);
        heading.setForeground(accent == null ? AppTheme.TEXT : accent);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.setBackground(new Color(22, 26, 33));
        bodyWrap.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                new EmptyBorder(2, 2, 2, 2)));
        bodyWrap.add(body, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton defaultButton = null;

        for (int i = options.length - 1; i >= 0; i--) {
            final int index = i;
            String label = options[i];
            JButton button = i == 0 ? AppTheme.primaryButton(label) : AppTheme.button(label);
            button.addActionListener(e -> {
                result.set(index);
                dialog.dispose();
            });
            buttons.add(button, 0);
            if (i == 0) defaultButton = button;
        }

        root.add(heading, BorderLayout.NORTH);
        root.add(bodyWrap, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        if (defaultButton != null) dialog.getRootPane().setDefaultButton(defaultButton);
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        forceReadableColors(body);
        dialog.pack();
        int width = Math.max(520, Math.min(760, dialog.getWidth()));
        int height = Math.max(190, Math.min(560, dialog.getHeight()));
        dialog.setSize(width, height);
        dialog.setMinimumSize(new Dimension(440, 160));
        dialog.setLocationRelativeTo(owner);

        dialog.addNotify();
        WindowsWindowStyler.apply(dialog);
        var icon = AppIcon.load();
        if (icon != null) dialog.setIconImage(icon);

        dialog.setVisible(true);
        return result.get();
    }
    private static void forceReadableColors(Component component) {
        if (component instanceof JTextArea area) {
            area.setForeground(Color.WHITE);
            area.setBackground(new Color(22, 26, 33));
            area.setCaretColor(Color.WHITE);
        } else if (component instanceof JTextField field) {
            field.setForeground(Color.WHITE);
            field.setBackground(new Color(22, 26, 33));
            field.setCaretColor(Color.WHITE);
        } else if (component instanceof JLabel label) {
            label.setForeground(AppTheme.TEXT);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) forceReadableColors(child);
        }
    }

}
