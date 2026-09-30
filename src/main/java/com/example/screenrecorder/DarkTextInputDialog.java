package com.example.screenrecorder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Small modal text-input dialog matching the recorder theme. */
final class DarkTextInputDialog extends JDialog {
    private final JTextField field;
    private String result;

    private DarkTextInputDialog(Window owner, String title, String prompt, String initialValue) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(AppTheme.BG);
        root.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel label = new JLabel(prompt);
        label.setForeground(AppTheme.TEXT);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        field = new JTextField(initialValue == null ? "" : initialValue, 36);
        field.setBackground(AppTheme.PANEL_ALT);
        field.setForeground(AppTheme.TEXT);
        field.setCaretColor(AppTheme.TEXT);
        field.setSelectionColor(AppTheme.ACCENT);
        field.setSelectedTextColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)));

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(label, BorderLayout.NORTH);
        center.add(field, BorderLayout.CENTER);

        JButton ok = AppTheme.button("Rename");
        ok.setBackground(AppTheme.ACCENT);
        ok.setForeground(Color.WHITE);
        JButton cancel = AppTheme.button("Cancel");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(ok);

        root.add(center, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);

        ok.addActionListener(e -> {
            result = field.getText();
            dispose();
        });
        cancel.addActionListener(e -> dispose());
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);

        pack();
        setMinimumSize(new Dimension(470, getHeight()));
        setLocationRelativeTo(owner);
        addNotify();
        WindowsWindowStyler.apply(this);
    }

    static String show(Component ownerComponent, String title, String prompt, String initialValue) {
        Window owner = ownerComponent == null ? null : SwingUtilities.getWindowAncestor(ownerComponent);
        DarkTextInputDialog dialog = new DarkTextInputDialog(owner, title, prompt, initialValue);
        SwingUtilities.invokeLater(() -> {
            dialog.field.requestFocusInWindow();
            dialog.field.selectAll();
        });
        dialog.setVisible(true);
        return dialog.result;
    }
}
