package com.example.screenrecorder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class MediaLibraryPanel extends JPanel {
    private static final Set<String> VIDEO_EXT = Set.of("mp4", "mkv", "mov");
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final Supplier<Path> outputDirectory;
    private final Consumer<String> log;
    private final Consumer<Throwable> error;
    private final MediaProbeService probe;
    private final LibraryModel model = new LibraryModel();
    private final JTable table = new JTable(model);
    private final JLabel summary = new JLabel("No files");
    private final JButton open = AppTheme.button("Open / Play");
    private final JButton rename = AppTheme.button("Rename");
    private final JButton delete = AppTheme.button("Delete");
    private final JButton refresh = AppTheme.button("Refresh");
    private final JButton openFolder = AppTheme.button("Open folder");
    private volatile SwingWorker<List<MediaItem>, Void> loader;

    MediaLibraryPanel(Supplier<Path> outputDirectory, String ffmpegPath,
                      Consumer<String> log, Consumer<Throwable> error) {
        super(new BorderLayout(10, 10));
        this.outputDirectory = outputDirectory;
        this.log = log;
        this.error = error;
        this.probe = new MediaProbeService(ffmpegPath);
        setBackground(AppTheme.BG);
        setBorder(new EmptyBorder(12, 12, 12, 12));
        buildUi();
        bind();
    }

    private void buildUi() {
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        JLabel title = new JLabel("RECORDINGS & SCREENSHOTS");
        title.setForeground(AppTheme.MUTED);
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        summary.setForeground(AppTheme.MUTED);
        top.add(title, BorderLayout.WEST);
        top.add(summary, BorderLayout.EAST);

        table.setBackground(AppTheme.PANEL);
        table.setForeground(AppTheme.TEXT);
        table.setSelectionBackground(AppTheme.ACCENT);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(AppTheme.BORDER);
        table.setRowHeight(27);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setBackground(AppTheme.PANEL_ALT);
        table.getTableHeader().setForeground(AppTheme.TEXT);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(75);
        table.getColumnModel().getColumn(1).setPreferredWidth(400);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(95);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
        scroll.getViewport().setBackground(AppTheme.PANEL);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(open);
        buttons.add(rename);
        buttons.add(delete);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(refresh);
        buttons.add(openFolder);

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        updateButtons();
    }

    private void bind() {
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) openSelected();
            }
        });
        open.addActionListener(e -> openSelected());
        rename.addActionListener(e -> renameSelected());
        delete.addActionListener(e -> deleteSelected());
        refresh.addActionListener(e -> refresh());
        openFolder.addActionListener(e -> openFolder());
    }

    void refresh() {
        SwingWorker<List<MediaItem>, Void> old = loader;
        if (old != null && !old.isDone()) old.cancel(true);
        summary.setText("Scanning...");
        refresh.setEnabled(false);
        loader = new SwingWorker<>() {
            @Override protected List<MediaItem> doInBackground() throws Exception {
                Path dir = outputDirectory.get().toAbsolutePath();
                Files.createDirectories(dir);
                List<Path> files;
                try (var stream = Files.list(dir)) {
                    files = stream.filter(Files::isRegularFile)
                            .filter(MediaLibraryPanel::isSupported)
                            .sorted(Comparator.comparingLong(MediaLibraryPanel::modifiedMillis).reversed())
                            .toList();
                }
                List<MediaItem> result = new ArrayList<>();
                for (Path path : files) {
                    if (isCancelled()) break;
                    String ext = extension(path);
                    boolean video = VIDEO_EXT.contains(ext);
                    long size = Files.size(path);
                    FileTime modified = Files.getLastModifiedTime(path);
                    double duration = video ? probe.durationSeconds(path) : -1;
                    result.add(new MediaItem(path, video ? "Video" : "Screenshot", size,
                            modified.toInstant(), duration));
                }
                return result;
            }

            @Override protected void done() {
                refresh.setEnabled(true);
                try {
                    model.setItems(get());
                    summary.setText(model.getRowCount() + " files · " + formatSize(model.totalSize()));
                } catch (Exception e) {
                    if (!isCancelled()) error.accept(e.getCause() != null ? e.getCause() : e);
                }
                updateButtons();
            }
        };
        loader.execute();
    }

    private void openSelected() {
        MediaItem item = selected();
        if (item == null) return;
        try {
            if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Desktop file opening is not supported.");
            Desktop.getDesktop().open(item.path().toFile());
        } catch (Exception e) { error.accept(e); }
    }

    private void renameSelected() {
        MediaItem item = selected();
        if (item == null) return;
        String original = item.path().getFileName().toString();
        String ext = extensionWithDot(item.path());
        String base = ext.isEmpty() ? original : original.substring(0, original.length() - ext.length());
        String entered = DarkTextInputDialog.show(
                this,
                "Rename",
                "New file name (extension will be preserved):",
                base);
        if (entered == null) return;
        String name = entered.trim();
        if (name.isBlank() || containsInvalidWindowsFileNameChar(name)) {
            error.accept(new IllegalArgumentException("File name is empty or contains Windows-invalid characters."));
            return;
        }
        try {
            Path target = item.path().resolveSibling(name + ext);
            if (Files.exists(target)) throw new IllegalArgumentException("A file with this name already exists.");
            Files.move(item.path(), target, StandardCopyOption.ATOMIC_MOVE);
            log.accept("Renamed: " + item.path().getFileName() + " -> " + target.getFileName());
            refresh();
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Path target = item.path().resolveSibling(name + ext);
                Files.move(item.path(), target);
                refresh();
            } catch (Exception ex) { error.accept(ex); }
        } catch (Exception e) { error.accept(e); }
    }

    private void deleteSelected() {
        MediaItem item = selected();
        if (item == null) return;
        int result = JOptionPane.showConfirmDialog(this,
                "Delete this file permanently?\n\n" + item.path().getFileName(),
                "Delete recording", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) return;
        try {
            Files.deleteIfExists(item.path());
            log.accept("Deleted: " + item.path());
            refresh();
        } catch (Exception e) { error.accept(e); }
    }

    private void openFolder() {
        try {
            Path dir = outputDirectory.get().toAbsolutePath();
            Files.createDirectories(dir);
            if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Desktop folder opening is not supported.");
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception e) { error.accept(e); }
    }

    private MediaItem selected() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        return model.item(table.convertRowIndexToModel(row));
    }

    private void updateButtons() {
        boolean selected = selected() != null;
        open.setEnabled(selected);
        rename.setEnabled(selected);
        delete.setEnabled(selected);
    }

    private static boolean containsInvalidWindowsFileNameChar(String name) {
        String invalid = "<>:\"/\\|?*";
        for (int i = 0; i < name.length(); i++) {
            if (invalid.indexOf(name.charAt(i)) >= 0 || name.charAt(i) < 32) return true;
        }
        return name.endsWith(".") || name.endsWith(" ");
    }

    private static boolean isSupported(Path path) {
        String ext = extension(path);
        return VIDEO_EXT.contains(ext) || IMAGE_EXT.contains(ext);
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String extensionWithDot(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    private static long modifiedMillis(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (Exception e) { return 0; }
    }

    private static String formatDuration(double seconds) {
        if (seconds < 0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) return "—";
        long total = Math.round(seconds);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        do { value /= 1024.0; unit++; } while (value >= 1024 && unit < units.length - 1);
        return new DecimalFormat(value >= 100 ? "0" : value >= 10 ? "0.0" : "0.00").format(value) + " " + units[unit];
    }

    private record MediaItem(Path path, String type, long size, Instant modified, double durationSeconds) {}

    private static final class LibraryModel extends AbstractTableModel {
        private final String[] columns = {"Type", "Name", "Date", "Size", "Duration"};
        private List<MediaItem> items = List.of();

        void setItems(List<MediaItem> value) {
            items = value == null ? List.of() : List.copyOf(value);
            fireTableDataChanged();
        }

        MediaItem item(int row) { return row >= 0 && row < items.size() ? items.get(row) : null; }
        long totalSize() { return items.stream().mapToLong(MediaItem::size).sum(); }
        @Override public int getRowCount() { return items.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            MediaItem i = items.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> i.type();
                case 1 -> i.path().getFileName().toString();
                case 2 -> DATE.format(i.modified());
                case 3 -> formatSize(i.size());
                case 4 -> formatDuration(i.durationSeconds());
                default -> "";
            };
        }
    }
}
