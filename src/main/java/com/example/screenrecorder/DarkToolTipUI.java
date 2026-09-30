package com.example.screenrecorder;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicToolTipUI;
import java.awt.*;

/** Forces application tooltips to use the recorder dark palette regardless of Nimbus state painters. */
public final class DarkToolTipUI extends BasicToolTipUI {
    public static ComponentUI createUI(JComponent component) {
        return new DarkToolTipUI();
    }

    @Override
    public void installUI(JComponent component) {
        super.installUI(component);
        component.setOpaque(true);
        component.setBackground(new Color(38, 44, 55));
        component.setForeground(Color.WHITE);
        Border border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                BorderFactory.createEmptyBorder(5, 7, 5, 7));
        component.setBorder(border);
    }
}
