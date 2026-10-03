package com.example.screenrecorder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class MonitorSelectionDialog {
    private MonitorSelectionDialog() {}

    static List<DisplayMonitor> show(Component parent, List<DisplayMonitor> monitors, List<DisplayMonitor> selected) {
        if (monitors == null || monitors.isEmpty()) {
            DarkDialogs.warning(parent, "Select monitors", "No Windows monitors were detected.");
            return selected == null ? List.of() : selected;
        }

        Set<String> selectedIds = new HashSet<>();
        if (selected != null) selected.forEach(m -> selectedIds.add(m.id()));

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(AppTheme.PANEL);
        list.setBorder(new EmptyBorder(6, 6, 6, 6));
        List<JCheckBox> boxes = new ArrayList<>();

        for (DisplayMonitor monitor : monitors) {
            JCheckBox box = new JCheckBox(monitor.toString(), selectedIds.contains(monitor.id()));
            AppTheme.styleCheckBox(box);
            box.putClientProperty("monitor", monitor);
            box.setBorder(new EmptyBorder(5, 2, 5, 2));
            boxes.add(box);
            list.add(box);
        }

        JLabel hint = new JLabel("Selected monitors are recorded together in their real Windows layout.");
        hint.setForeground(AppTheme.MUTED);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(AppTheme.PANEL);
        panel.add(hint, BorderLayout.NORTH);
        panel.add(list, BorderLayout.CENTER);

        int answer = DarkDialogs.componentOptions(
                parent, "Select monitor(s)", panel, AppTheme.ACCENT, "Use selected", "Cancel");
        if (answer != 0) return selected == null ? List.of() : selected;

        List<DisplayMonitor> result = new ArrayList<>();
        for (JCheckBox box : boxes) {
            if (box.isSelected()) result.add((DisplayMonitor) box.getClientProperty("monitor"));
        }
        if (result.isEmpty()) {
            DarkDialogs.warning(parent, "Select monitors", "Select at least one monitor.");
            return selected == null ? List.of() : selected;
        }
        return List.copyOf(result);
    }
}
