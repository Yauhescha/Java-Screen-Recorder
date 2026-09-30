package com.example.screenrecorder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;

final class DarkColorPickerDialog extends JDialog {
    private static final Color[] PRESETS = {
            new Color(255, 215, 64), new Color(255, 167, 38), new Color(239, 83, 80),
            new Color(236, 64, 122), new Color(171, 71, 188), new Color(126, 87, 194),
            new Color(66, 165, 245), new Color(38, 198, 218), new Color(38, 166, 154),
            new Color(102, 187, 106), new Color(156, 204, 101), new Color(238, 238, 238)
    };

    private final JSlider red = new JSlider(0, 255);
    private final JSlider green = new JSlider(0, 255);
    private final JSlider blue = new JSlider(0, 255);
    private final JLabel redValue = valueLabel();
    private final JLabel greenValue = valueLabel();
    private final JLabel blueValue = valueLabel();
    private final JTextField hex = new JTextField(8);
    private final JPanel preview = new JPanel();
    private Color selected;
    private boolean updating;

    private DarkColorPickerDialog(Window owner, Color initial) {
        super(owner, "Mouse highlight color", ModalityType.APPLICATION_MODAL);
        selected = initial != null ? initial : Color.YELLOW;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        Image icon = AppIcon.load();
        if (icon != null) setIconImage(icon);
        setContentPane(buildUi());
        bind();
        setColor(selected);
        pack();
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                WindowsWindowStyler.apply(DarkColorPickerDialog.this);
            }
        });
    }

    static Color showDialog(Window owner, Color initial) {
        DarkColorPickerDialog dialog = new DarkColorPickerDialog(owner, initial);
        dialog.setVisible(true);
        return dialog.selected;
    }

    private JPanel buildUi() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(AppTheme.BG);
        root.setBorder(new EmptyBorder(16, 16, 14, 16));

        JPanel swatches = new JPanel(new GridLayout(2, 6, 8, 8));
        swatches.setOpaque(false);
        for (Color preset : PRESETS) {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(40, 30));
            button.setBackground(preset);
            button.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
            button.setFocusPainted(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addActionListener(e -> setColor(preset));
            swatches.add(button);
        }

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBackground(AppTheme.PANEL);
        controls.setBorder(AppTheme.cardBorder("CUSTOM COLOR"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        addSliderRow(controls, c, 0, "Red", red, redValue);
        addSliderRow(controls, c, 1, "Green", green, greenValue);
        addSliderRow(controls, c, 2, "Blue", blue, blueValue);

        JLabel hexLabel = new JLabel("Hex");
        hexLabel.setForeground(AppTheme.MUTED);
        c.gridx = 0; c.gridy = 3; c.weightx = 0;
        controls.add(hexLabel, c);
        c.gridx = 1; c.weightx = 1;
        hex.setBackground(AppTheme.PANEL_ALT);
        hex.setForeground(AppTheme.TEXT);
        hex.setCaretColor(AppTheme.TEXT);
        controls.add(hex, c);

        preview.setPreferredSize(new Dimension(54, 34));
        preview.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
        c.gridx = 2; c.weightx = 0;
        controls.add(preview, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = AppTheme.button("Cancel");
        JButton apply = AppTheme.button("Apply");
        apply.setBackground(AppTheme.ACCENT);
        apply.setForeground(Color.WHITE);
        cancel.addActionListener(e -> {
            selected = null;
            dispose();
        });
        apply.addActionListener(e -> {
            applyHexIfValid();
            selected = currentColor();
            dispose();
        });
        buttons.add(cancel);
        buttons.add(apply);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        JLabel caption = new JLabel("Choose the color shown around the mouse pointer while recording.");
        caption.setForeground(AppTheme.MUTED);
        top.add(caption, BorderLayout.NORTH);
        top.add(swatches, BorderLayout.CENTER);

        root.add(top, BorderLayout.NORTH);
        root.add(controls, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private void bind() {
        ChangeListener listener = e -> {
            if (updating) return;
            updateFromSliders();
        };
        red.addChangeListener(listener);
        green.addChangeListener(listener);
        blue.addChangeListener(listener);
        hex.addActionListener(e -> applyHexIfValid());
    }

    private void addSliderRow(JPanel panel, GridBagConstraints c, int y, String label,
                              JSlider slider, JLabel valueLabel) {
        slider.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setForeground(AppTheme.MUTED);
        c.gridx = 0; c.gridy = y; c.weightx = 0;
        panel.add(l, c);
        c.gridx = 1; c.weightx = 1;
        panel.add(slider, c);
        c.gridx = 2; c.weightx = 0;
        panel.add(valueLabel, c);
    }

    private void setColor(Color color) {
        updating = true;
        red.setValue(color.getRed());
        green.setValue(color.getGreen());
        blue.setValue(color.getBlue());
        updating = false;
        updateFromSliders();
    }

    private void updateFromSliders() {
        Color color = currentColor();
        redValue.setText(Integer.toString(red.getValue()));
        greenValue.setText(Integer.toString(green.getValue()));
        blueValue.setText(Integer.toString(blue.getValue()));
        preview.setBackground(color);
        hex.setText(String.format(Locale.ROOT, "#%02X%02X%02X",
                color.getRed(), color.getGreen(), color.getBlue()));
    }

    private void applyHexIfValid() {
        String value = hex.getText().trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("(?i)[0-9a-f]{6}")) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        setColor(new Color(Integer.parseInt(value, 16)));
    }

    private Color currentColor() {
        return new Color(red.getValue(), green.getValue(), blue.getValue());
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("0");
        label.setForeground(AppTheme.TEXT);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        label.setPreferredSize(new Dimension(32, 20));
        return label;
    }
}
