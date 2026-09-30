package com.example.screenrecorder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class RecorderFrame extends JFrame {
    private final JComboBox<CaptureMode> modeBox = new JComboBox<>(CaptureMode.values());
    private final JLabel monitorSummary = new JLabel("Detecting displays...");
    private final JButton selectMonitorsButton = AppTheme.button("Select...");
    private final JComboBox<WindowTarget> windowBox = new JComboBox<>();
    private final JButton refreshWindowsButton = AppTheme.button("Refresh");
    private final JButton regionButton = AppTheme.button("Adjust frame");
    private final JTextField regionField = new JTextField(22);
    private final JSpinner regionWidthSpinner = new JSpinner(new SpinnerNumberModel(1280, 160, 16384, 2));
    private final JSpinner regionHeightSpinner = new JSpinner(new SpinnerNumberModel(720, 90, 16384, 2));
    private final JButton applyRegionSizeButton = AppTheme.button("Apply size");
    private final JSlider qualitySlider = new JSlider(1, 100, 80);
    private final JLabel qualityLabel = new JLabel();
    private final JSpinner fpsSpinner = new JSpinner(new SpinnerNumberModel(60, 1, 240, 1));
    private final JComboBox<VideoEncoder> encoderBox = new JComboBox<>();
    private final JLabel encoderStatus = new JLabel("Checking NVIDIA GPU...");
    private final JCheckBox excludeRecorderWindow = new JCheckBox("Do not record recorder window");

    private final JCheckBox systemAudio = new JCheckBox("Record system audio");
    private final JLabel systemAudioInfo = new JLabel("Windows output / headphones (WASAPI loopback)");
    private final AudioLevelMeter systemAudioMeter = new AudioLevelMeter();
    private final JSlider systemVolume = new JSlider(0, 200, 100);
    private final JLabel systemVolumeLabel = new JLabel("100%");

    private final JCheckBox micAudio = new JCheckBox("Record microphone");
    private final JComboBox<MicrophoneDevice> micBox = new JComboBox<>();
    private final JButton refreshMic = AppTheme.button("Refresh");
    private final AudioLevelMeter microphoneMeter = new AudioLevelMeter();
    private final JLabel microphoneStatusLabel = new JLabel("Ready");
    private final JSlider microphoneVolume = new JSlider(0, 200, 100);
    private final JLabel microphoneVolumeLabel = new JLabel("100%");
    private final JToggleButton muteMicrophoneButton = new JToggleButton("Mute mic");
    private final JCheckBox microphoneNoiseSuppression = new JCheckBox("Mic noise suppression");
    private final JCheckBox microphoneNoiseGate = new JCheckBox("Mic noise gate");
    private final JSpinner microphoneNoiseGateDb = new JSpinner(new SpinnerNumberModel(-45, -70, -10, 1));
    private final JCheckBox separateAudioTracks = new JCheckBox("Separate system / microphone tracks");

    private final JCheckBox recordWebcam = new JCheckBox("Webcam overlay");
    private final JComboBox<WebcamDevice> webcamBox = new JComboBox<>();
    private final JButton refreshWebcams = AppTheme.button("Refresh");
    private final JButton adjustWebcamOverlay = AppTheme.button("Live preview / position");
    private final JLabel webcamPlacementLabel = new JLabel("Not configured");
    private final JLabel webcamStatusLabel = new JLabel("Detecting cameras...");
    private final JCheckBox webcamMirror = new JCheckBox("Mirror webcam");
    private final JComboBox<WebcamShape> webcamShape = new JComboBox<>(WebcamShape.values());
    private final JCheckBox webcamBorder = new JCheckBox("Webcam border");
    private final JCheckBox webcamShadow = new JCheckBox("Webcam shadow");
    private final JCheckBox hideWebcamPreviewWhileRecording = new JCheckBox("Hide webcam preview while recording");
    private final JButton webcamBorderColorButton = AppTheme.button("Border color");

    private final JCheckBox showCursor = new JCheckBox("Show mouse cursor");
    private final JCheckBox highlightCursor = new JCheckBox("Highlight mouse");
    private final JCheckBox mouseClickEffects = new JCheckBox("Mouse click effects");
    private final JButton cursorColorButton = AppTheme.button("Highlight color");

    private final JTextField outputDirectoryField = new JTextField(32);
    private final JButton browseOutputDirectory = AppTheme.button("Choose folder");
    private final JButton openOutputDirectory = AppTheme.button("Open folder");
    private final JComboBox<VideoFormat> videoFormatBox = new JComboBox<>(VideoFormat.values());
    private final JCheckBox showLogToggle = new JCheckBox("Show technical log");
    private final JCheckBox autoUpdateCheck = new JCheckBox("Check for updates automatically");
    private final JButton checkUpdatesButton = AppTheme.button("Check now");
    private final JButton aboutButton = AppTheme.button("About");
    private final JLabel updateStatusLabel = new JLabel("Updates: ready");

    private final JButton start = AppTheme.recordButton("●  START RECORDING");
    private final JButton pause = AppTheme.button("Pause");
    private final JButton stop = AppTheme.button("Stop");
    private final JButton screenshot = AppTheme.button("Screenshot");

    private final JLabel statusLabel = new JLabel("READY");
    private final JLabel timerLabel = new JLabel("00:00:00");
    private final JLabel reliabilityLabel = new JLabel("FPS —  ·  Dropped 0  ·  Disk —");
    private final JTextArea log = new JTextArea(11, 80);
    private JScrollPane logScroll;

    private final RecordingSession session = new RecordingSession();
    private final RecoveryService recoveryService = new RecoveryService();
    private final String ffmpegPath;
    private final FfmpegService ffmpegService = new FfmpegService();
    private final MicrophoneService microphoneService = new MicrophoneService();
    private final WebcamService webcamService = new WebcamService();
    private final WebcamPreviewService webcamPreviewService = new WebcamPreviewService();
    private final TrayService trayService = new TrayService();
    private final ScreenCaptureService screenshotService = new ScreenCaptureService();
    private final WindowsCaptureService captureTargetService = new WindowsCaptureService();
    private final GlobalHotkeyService hotkeyService = new GlobalHotkeyService();
    private final UpdateService updateService = new UpdateService();

    private final HotkeyCaptureField startStopHotkeyField;
    private final HotkeyCaptureField screenshotHotkeyField;
    private final HotkeyCaptureField muteMicrophoneHotkeyField;
    private final MediaLibraryPanel libraryPanel;

    private List<DisplayMonitor> availableMonitors = List.of();
    private List<DisplayMonitor> selectedMonitors = List.of();
    private List<WindowTarget> availableWindows = List.of();

    private CaptureRegion region;
    private RegionOverlay overlay;
    private CursorHighlightOverlay cursorOverlay;
    private WebcamOverlay webcamOverlay;
    private WebcamPlacement webcamPlacement;
    private RecorderConfig activeConfig;
    private boolean busy;
    private volatile boolean nvencAvailable;
    private Color highlightColor;
    private Color webcamBorderColor;
    private boolean updatingRegionControls;
    private boolean recorderWindowExcluded;
    private boolean regionOverlayExcluded;
    private boolean webcamPreviewExcluded;
    private boolean regionRelocationBusy;
    private boolean webcamOverlayVisibleBeforeRecording;

    private final Timer elapsedTimer;
    private final Timer reliabilityTimer;
    private Instant segmentClockStart;
    private long recordedMillis;
    private volatile RecordingStats recordingStats = RecordingStats.ZERO;
    private boolean lowSpaceWarningShown;
    private boolean criticalSpaceStopTriggered;
    private boolean microphoneDisconnectNotified;
    private boolean webcamDisconnectNotified;

    private final AudioLevelListener audioLevels = new AudioLevelListener() {
        @Override public void onSystemLevel(double level) {
            SwingUtilities.invokeLater(() -> systemAudioMeter.setLevel(level));
        }
        @Override public void onMicrophoneLevel(double level) {
            SwingUtilities.invokeLater(() -> microphoneMeter.setLevel(level));
        }
    };

    private final RecordingStatsListener recordingStatsListener = stats ->
            SwingUtilities.invokeLater(() -> {
                recordingStats = stats != null ? stats : RecordingStats.ZERO;
                updateReliabilityStatus();
            });

    private final RecordingHealthListener recordingHealthListener = new RecordingHealthListener() {
        @Override public void onMicrophoneFailure(String message) {
            SwingUtilities.invokeLater(() -> handleMicrophoneFailure(message));
        }

        @Override public void onRecorderFailure(String message) {
            SwingUtilities.invokeLater(() -> {
                append("RECORDER FAILURE: " + message);
                trayService.showWarning("Recording interrupted", "The recording engine stopped. Recovering the saved MKV data now.");
                if (session.isActive() && !busy) stopRecording();
            });
        }
    };

    public RecorderFrame(String ffmpegPath) {
        super(AppVersion.NAME + " " + AppVersion.VERSION);
        this.ffmpegPath = ffmpegPath;
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        region = UserPreferences.savedRegion(defaultRegion());
        highlightColor = UserPreferences.highlightColor();
        webcamBorderColor = UserPreferences.webcamBorderColor();

        modeBox.setSelectedItem(UserPreferences.captureMode());
        refreshMonitors(false);
        refreshWindows(false);
        qualitySlider.setValue(UserPreferences.qualityPercent());
        fpsSpinner.setValue(UserPreferences.fps());
        systemAudio.setSelected(UserPreferences.recordSystemAudio());
        systemVolume.setValue(UserPreferences.systemVolumePercent());
        micAudio.setSelected(UserPreferences.recordMicrophone());
        microphoneVolume.setValue(UserPreferences.microphoneVolumePercent());
        microphoneNoiseSuppression.setSelected(UserPreferences.microphoneNoiseSuppression());
        microphoneNoiseGate.setSelected(UserPreferences.microphoneNoiseGate());
        microphoneNoiseGateDb.setValue(UserPreferences.microphoneNoiseGateDb());
        separateAudioTracks.setSelected(UserPreferences.separateAudioTracks());
        recordWebcam.setSelected(UserPreferences.recordWebcam());
        webcamMirror.setSelected(UserPreferences.webcamMirror());
        webcamShape.setSelectedItem(UserPreferences.webcamShape());
        webcamBorder.setSelected(UserPreferences.webcamBorder());
        webcamShadow.setSelected(UserPreferences.webcamShadow());
        hideWebcamPreviewWhileRecording.setSelected(UserPreferences.hideWebcamPreviewWhileRecording());
        Rectangle initialCaptureBounds = currentCaptureBounds();
        webcamPlacement = UserPreferences.webcamPlacement(initialCaptureBounds.width, initialCaptureBounds.height);
        showCursor.setSelected(UserPreferences.showCursor());
        highlightCursor.setSelected(UserPreferences.highlightCursor());
        mouseClickEffects.setSelected(UserPreferences.mouseClickEffects());
        excludeRecorderWindow.setSelected(UserPreferences.excludeRecorderWindow());
        videoFormatBox.setSelectedItem(UserPreferences.videoFormat());
        showLogToggle.setSelected(UserPreferences.showLog());
        autoUpdateCheck.setSelected(UserPreferences.autoUpdateCheck());

        encoderBox.addItem(VideoEncoder.AUTO);
        encoderBox.addItem(VideoEncoder.CPU_X264);
        if (UserPreferences.videoEncoder() != VideoEncoder.NVIDIA_NVENC) {
            encoderBox.setSelectedItem(UserPreferences.videoEncoder());
        }

        outputDirectoryField.setText(UserPreferences.outputDirectory().toString());
        outputDirectoryField.setEditable(false);
        regionField.setEditable(false);
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setLineWrap(false);

        AppTheme.styleCheckBox(systemAudio);
        AppTheme.styleCheckBox(micAudio);
        AppTheme.styleCheckBox(microphoneNoiseSuppression);
        AppTheme.styleCheckBox(microphoneNoiseGate);
        AppTheme.styleCheckBox(separateAudioTracks);
        AppTheme.styleCheckBox(recordWebcam);
        AppTheme.styleCheckBox(webcamMirror);
        AppTheme.styleCheckBox(webcamBorder);
        AppTheme.styleCheckBox(webcamShadow);
        AppTheme.styleCheckBox(hideWebcamPreviewWhileRecording);
        AppTheme.styleCheckBox(showCursor);
        AppTheme.styleCheckBox(highlightCursor);
        AppTheme.styleCheckBox(mouseClickEffects);
        AppTheme.styleCheckBox(excludeRecorderWindow);
        AppTheme.styleCheckBox(showLogToggle);
        AppTheme.styleCheckBox(autoUpdateCheck);
        updateStatusLabel.setForeground(AppTheme.MUTED);
        AppTheme.styleComboBox(modeBox);
        AppTheme.styleComboBox(windowBox);
        AppTheme.styleComboBox(encoderBox);
        AppTheme.styleComboBox(micBox);
        AppTheme.styleComboBox(webcamBox);
        AppTheme.styleComboBox(webcamShape);
        AppTheme.styleComboBox(videoFormatBox);
        AppTheme.styleSpinner(regionWidthSpinner);
        AppTheme.styleSpinner(regionHeightSpinner);
        AppTheme.styleSpinner(fpsSpinner);
        AppTheme.styleSpinner(microphoneNoiseGateDb);
        AppTheme.keepDisabledTextReadable(regionField);
        AppTheme.keepDisabledTextReadable(outputDirectoryField);
        webcamStatusLabel.setForeground(AppTheme.MUTED);
        separateAudioTracks.setToolTipText("Off: system sound and microphone are mixed. On: they remain separate audio tracks inside the video file.");
        recordWebcam.setToolTipText("Adds the selected DirectShow camera as a movable/resizable overlay in the recorded video.");
        adjustWebcamOverlay.setToolTipText("Opens a live camera preview directly over the capture area. Drag it and resize from the edges/corners.");
        webcamMirror.setToolTipText("Horizontally mirrors the webcam in both preview and the recorded video.");
        webcamShape.setToolTipText("Rectangle, rounded corners, or a circular crop.");
        webcamBorder.setToolTipText("Draws a border around the webcam overlay.");
        webcamShadow.setToolTipText("Adds a subtle drop shadow behind the webcam overlay.");
        hideWebcamPreviewWhileRecording.setToolTipText("Off: keep the local live webcam preview visible while recording. The preview window itself is excluded from capture. On: hide it during recording.");
        excludeRecorderWindow.setToolTipText("Windows 10/11: asks the OS to exclude this recorder window from screen capture where supported.");
        mouseClickEffects.setToolTipText("Draws an animated ring at left/right mouse clicks in the recorded image.");
        highlightCursor.setToolTipText("Draws a translucent halo around the mouse pointer.");
        qualityLabel.setForeground(AppTheme.TEXT);
        encoderStatus.setForeground(AppTheme.MUTED);
        systemVolumeLabel.setForeground(AppTheme.TEXT);
        microphoneVolumeLabel.setForeground(AppTheme.TEXT);
        systemVolume.setToolTipText("System audio volume written to the recording (0-200%).");
        microphoneVolume.setToolTipText("Microphone volume written to the recording (0-200%).");
        microphoneNoiseSuppression.setToolTipText("FFmpeg FFT denoiser (afftdn) applied to the microphone track.");
        microphoneNoiseGate.setToolTipText("Closes the microphone below the selected threshold to reduce room/background noise.");
        microphoneNoiseGateDb.setToolTipText("Noise-gate threshold in dB. More negative = more sensitive.");
        AppTheme.styleToggleButton(muteMicrophoneButton);
        muteMicrophoneButton.setToolTipText("Instantly mute/unmute microphone PCM while recording. The default global hotkey is F10.");

        startStopHotkeyField = new HotkeyCaptureField(
                UserPreferences.startStopHotkey(), this::changeStartStopHotkey);
        screenshotHotkeyField = new HotkeyCaptureField(
                UserPreferences.screenshotHotkey(), this::changeScreenshotHotkey);
        muteMicrophoneHotkeyField = new HotkeyCaptureField(
                UserPreferences.muteMicrophoneHotkey(), this::changeMuteMicrophoneHotkey);

        elapsedTimer = new Timer(250, e -> updateElapsedTime());
        reliabilityTimer = new Timer(1500, e -> updateReliabilityStatus());
        reliabilityTimer.setInitialDelay(0);
        reliabilityTimer.start();
        libraryPanel = new MediaLibraryPanel(this::currentOutputDirectory, ffmpegPath, this::append, this::error);

        setContentPane(buildUi());
        bind();
        configureHotkeys();
        refreshMicrophones();
        refreshWebcams();
        updateRegionText();
        updateWebcamPlacementLabel();
        updateColorButton();
        updateWebcamBorderColorButton();
        updateQualityLabel();
        updateVolumeLabels();
        updateMuteMicrophoneButton();
        updateState();

        int minimumWidth = 900;
        int minimumHeight = 620;
        setMinimumSize(new Dimension(minimumWidth, minimumHeight));
        pack();

        Rectangle preferred;
        if (UserPreferences.uiLayoutVersion() < 1) {
            Rectangle previous = UserPreferences.savedWindowBounds(new Rectangle(120, 80, 980, 700));
            preferred = new Rectangle(previous.x, previous.y, 980, 700);
            UserPreferences.uiLayoutVersion(1);
        } else {
            preferred = UserPreferences.savedWindowBounds(new Rectangle(120, 80, 980, 700));
        }
        preferred.width = Math.max(preferred.width, minimumWidth);
        preferred.height = Math.max(preferred.height, minimumHeight);
        setBounds(ensureVisible(preferred));
        hotkeyService.start();
        boolean trayInstalled = trayService.install(
                e -> SwingUtilities.invokeLater(this::showAndFocus),
                e -> SwingUtilities.invokeLater(() -> { if (busy) return; if (session.isActive()) stopRecording(); else startRecording(); }),
                e -> SwingUtilities.invokeLater(this::takeScreenshot),
                e -> SwingUtilities.invokeLater(this::openOutputDirectory),
                e -> SwingUtilities.invokeLater(this::closeApplication));
        if (!trayInstalled) append("System tray is not available on this desktop.");

        detectNvencAsync();
        SwingUtilities.invokeLater(this::offerRecoveryIfNeeded);
        SwingUtilities.invokeLater(libraryPanel::refresh);
    }

    private JPanel buildUi() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(AppTheme.BG);
        root.setBorder(new EmptyBorder(16, 18, 16, 18));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildTabbedSettings(), BorderLayout.CENTER);
        root.add(buildControls(), BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildTabbedSettings() {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setBackground(AppTheme.PANEL_ALT);
        tabs.setForeground(AppTheme.TEXT);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
        tabs.setFocusable(false);

        tabs.addTab("Capture", buildCaptureTab());
        tabs.addTab("Audio & Webcam", buildAudioWebcamTab());
        tabs.addTab("Output & Hotkeys", buildOutputTab());
        tabs.addTab("Library", libraryPanel);
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == libraryPanel) libraryPanel.refresh();
        });
        return tabs;
    }

    private JComponent buildCaptureTab() {
        JPanel tab = tabPanel();
        JPanel cards = new JPanel(new GridLayout(1, 2, 12, 0));
        cards.setOpaque(false);
        cards.add(buildCaptureCard());
        cards.add(buildCursorCard());
        tab.add(cards, BorderLayout.NORTH);
        return tab;
    }

    private JComponent buildAudioWebcamTab() {
        JPanel tab = tabPanel();
        JPanel cards = new JPanel(new GridLayout(1, 2, 12, 0));
        cards.setOpaque(false);
        cards.add(buildAudioCard());
        cards.add(buildWebcamCard());
        tab.add(cards, BorderLayout.NORTH);
        return tab;
    }

    private JComponent buildOutputTab() {
        JPanel tab = tabPanel();
        tab.add(buildOutputHotkeyCard(), BorderLayout.NORTH);

        logScroll = new JScrollPane(log);
        logScroll.setBorder(AppTheme.cardBorder("TECHNICAL LOG"));
        logScroll.setVisible(showLogToggle.isSelected());
        logScroll.setPreferredSize(new Dimension(100, 210));
        tab.add(logScroll, BorderLayout.CENTER);
        return tab;
    }

    private JPanel tabPanel() {
        JPanel tab = new JPanel(new BorderLayout(12, 12));
        tab.setBackground(AppTheme.BG);
        tab.setBorder(new EmptyBorder(12, 12, 12, 12));
        return tab;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        JLabel title = new JLabel("Java Screen Recorder");
        title.setForeground(AppTheme.TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        JLabel version = new JLabel(AppVersion.VERSION);
        version.setOpaque(true);
        version.setBackground(AppTheme.PANEL_ALT);
        version.setForeground(AppTheme.MUTED);
        version.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                BorderFactory.createEmptyBorder(2, 7, 2, 7)));
        titleRow.add(title);
        titleRow.add(version);
        JLabel subtitle = new JLabel("Local · private · hardware accelerated · MP4 / MKV / MOV");
        subtitle.setForeground(AppTheme.MUTED);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        text.add(titleRow);
        text.add(Box.createVerticalStrut(3));
        text.add(subtitle);

        JPanel status = new JPanel();
        status.setOpaque(false);
        status.setLayout(new BoxLayout(status, BoxLayout.Y_AXIS));
        JPanel statusTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        statusTop.setOpaque(false);
        statusLabel.setForeground(AppTheme.GOOD);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        timerLabel.setForeground(AppTheme.TEXT);
        timerLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        statusTop.add(statusLabel);
        statusTop.add(timerLabel);
        reliabilityLabel.setForeground(AppTheme.MUTED);
        reliabilityLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        reliabilityLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        status.add(statusTop);
        status.add(reliabilityLabel);

        JPanel right = new JPanel(new BorderLayout(10, 0));
        right.setOpaque(false);
        aboutButton.setPreferredSize(new Dimension(78, 30));
        right.add(status, BorderLayout.CENTER);
        right.add(aboutButton, BorderLayout.EAST);

        header.add(text, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCaptureCard() {
        JPanel card = card("CAPTURE & VIDEO");
        GridBagConstraints c = constraints();
        int r = 0;
        row(card, c, r++, "Capture", modeBox, null);
        monitorSummary.setForeground(AppTheme.MUTED);
        monitorSummary.setToolTipText("Physical Win32 monitor pixels are used so mixed-DPI displays record at native resolution.");
        row(card, c, r++, "Monitor(s)", monitorSummary, selectMonitorsButton);
        windowBox.setPrototypeDisplayValue(new WindowTarget(0L, "Application window title...", 0, new Rectangle(0, 0, 1280, 720)));
        windowBox.setPreferredSize(new Dimension(300, 28));
        row(card, c, r++, "Window", windowBox, refreshWindowsButton);
        row(card, c, r++, "Region", regionField, regionButton);

        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sizePanel.setOpaque(false);
        regionWidthSpinner.setPreferredSize(new Dimension(92, 27));
        regionHeightSpinner.setPreferredSize(new Dimension(92, 27));
        JLabel xLabel = new JLabel("×");
        xLabel.setForeground(AppTheme.MUTED);
        sizePanel.add(regionWidthSpinner);
        sizePanel.add(xLabel);
        sizePanel.add(regionHeightSpinner);
        row(card, c, r++, "Size", sizePanel, applyRegionSizeButton);

        JPanel qualityPanel = new JPanel(new BorderLayout(8, 0));
        qualityPanel.setOpaque(false);
        qualitySlider.setMajorTickSpacing(25);
        qualitySlider.setPaintTicks(true);
        qualityLabel.setForeground(AppTheme.TEXT);
        qualityPanel.add(qualitySlider, BorderLayout.CENTER);
        qualityPanel.add(qualityLabel, BorderLayout.EAST);
        row(card, c, r++, "Quality", qualityPanel, null);
        row(card, c, r++, "FPS", fpsSpinner, null);
        row(card, c, r++, "Encoder", encoderBox, null);

        encoderStatus.setForeground(AppTheme.MUTED);
        c.gridx = 1; c.gridy = r++; c.gridwidth = 2; c.weightx = 1;
        card.add(encoderStatus, c);
        c.gridwidth = 1;
        row(card, c, r, "", excludeRecorderWindow, null);
        return card;
    }

    private JPanel buildAudioCard() {
        JPanel card = card("AUDIO");
        GridBagConstraints c = constraints();
        int r = 0;
        row(card, c, r++, "", systemAudio, null);
        systemAudioInfo.setForeground(AppTheme.MUTED);
        row(card, c, r++, "Output", systemAudioInfo, null);
        row(card, c, r++, "System level", systemAudioMeter, null);
        row(card, c, r++, "System volume", sliderWithLabel(systemVolume, systemVolumeLabel), null);

        row(card, c, r++, "", micAudio, muteMicrophoneButton);
        row(card, c, r++, "Microphone", micBox, refreshMic);
        microphoneStatusLabel.setForeground(AppTheme.MUTED);
        row(card, c, r++, "Status", microphoneStatusLabel, null);
        row(card, c, r++, "Mic level", microphoneMeter, null);
        row(card, c, r++, "Mic volume", sliderWithLabel(microphoneVolume, microphoneVolumeLabel), null);
        row(card, c, r++, "", microphoneNoiseSuppression, null);
        row(card, c, r++, "", microphoneNoiseGate, microphoneNoiseGateDb);
        row(card, c, r, "", separateAudioTracks, null);
        return card;
    }

    private JPanel buildWebcamCard() {
        JPanel card = card("WEBCAM");
        GridBagConstraints c = constraints();
        int r = 0;
        row(card, c, r++, "", recordWebcam, null);
        row(card, c, r++, "Webcam", webcamBox, refreshWebcams);
        row(card, c, r++, "Status", webcamStatusLabel, null);
        row(card, c, r++, "Overlay", webcamPlacementLabel, adjustWebcamOverlay);
        row(card, c, r++, "Shape", webcamShape, webcamMirror);
        row(card, c, r++, "", webcamBorder, webcamBorderColorButton);
        row(card, c, r++, "", webcamShadow, null);
        row(card, c, r, "", hideWebcamPreviewWhileRecording, null);
        return card;
    }

    private JPanel buildCursorCard() {
        JPanel card = card("MOUSE & CURSOR");
        GridBagConstraints c = constraints();
        int r = 0;
        row(card, c, r++, "", showCursor, null);
        row(card, c, r++, "", highlightCursor, cursorColorButton);
        row(card, c, r++, "", mouseClickEffects, null);

        JTextArea hint = new JTextArea(
                "Cursor visibility, highlight halo and mouse-click rings are rendered only into the recording and do not change normal Windows pointer behavior.",
                4, 20);
        hint.setEditable(false);
        hint.setFocusable(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setOpaque(false);
        hint.setForeground(AppTheme.MUTED);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        hint.setBorder(new EmptyBorder(10, 6, 4, 6));
        c.gridx = 0; c.gridy = r; c.gridwidth = 3; c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        card.add(hint, c);
        return card;
    }

    private JPanel buildOutputHotkeyCard() {
        JPanel card = card("OUTPUT & HOTKEYS");
        GridBagConstraints c = constraints();
        int r = 0;
        JPanel outputButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        outputButtons.setOpaque(false);
        outputButtons.add(browseOutputDirectory);
        outputButtons.add(openOutputDirectory);
        row(card, c, r++, "Output folder", outputDirectoryField, outputButtons);
        row(card, c, r++, "Video format", videoFormatBox, null);
        row(card, c, r++, "Start / Stop", startStopHotkeyField, null);
        row(card, c, r++, "Screenshot", screenshotHotkeyField, null);
        row(card, c, r++, "Mute microphone", muteMicrophoneHotkeyField, null);

        JPanel updatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        updatePanel.setOpaque(false);
        updatePanel.add(autoUpdateCheck);
        updatePanel.add(checkUpdatesButton);
        row(card, c, r++, "Updates", updatePanel, updateStatusLabel);
        row(card, c, r++, "", showLogToggle, null);

        JTextArea recovery = new JTextArea(
                "Crash recovery: temporary MKV segments are kept while recording and can be restored to the selected format after an unexpected shutdown.",
                2, 20);
        recovery.setEditable(false);
        recovery.setFocusable(false);
        recovery.setLineWrap(true);
        recovery.setWrapStyleWord(true);
        recovery.setOpaque(false);
        recovery.setForeground(AppTheme.MUTED);
        recovery.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        recovery.setBorder(new EmptyBorder(2, 0, 3, 0));
        c.gridx = 0; c.gridy = r; c.gridwidth = 3; c.weightx = 1; c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        card.add(recovery, c);
        return card;
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel(new BorderLayout(10, 0));
        controls.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        pause.setPreferredSize(new Dimension(105, 38));
        stop.setPreferredSize(new Dimension(105, 38));
        screenshot.setPreferredSize(new Dimension(120, 38));
        left.add(start);
        left.add(pause);
        left.add(stop);
        left.add(screenshot);

        JLabel hint = new JLabel(startStopHotkeyField.hotkey().displayName() + " record   ·   " + screenshotHotkeyField.hotkey().displayName() + " screenshot   ·   " + muteMicrophoneHotkeyField.hotkey().displayName() + " mute mic");
        hint.setForeground(AppTheme.MUTED);
        controls.add(left, BorderLayout.WEST);
        controls.add(hint, BorderLayout.EAST);
        return controls;
    }

    private JComponent sliderWithLabel(JSlider slider, JLabel label) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        slider.setOpaque(false);
        panel.add(slider, BorderLayout.CENTER);
        label.setPreferredSize(new Dimension(44, 22));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(label, BorderLayout.EAST);
        return panel;
    }

    private JPanel card(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(AppTheme.PANEL);
        panel.setBorder(AppTheme.cardBorder(title));
        return panel;
    }

    private GridBagConstraints constraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private void row(JPanel p, GridBagConstraints c, int y, String label, Component main, Component extra) {
        c.gridwidth = 1;
        c.gridy = y;
        c.gridx = 0;
        c.weightx = 0;
        JLabel l = new JLabel(label);
        l.setForeground(AppTheme.MUTED);
        p.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        p.add(main, c);
        c.gridx = 2;
        c.weightx = 0;
        p.add(extra != null ? extra : Box.createHorizontalStrut(1), c);
    }

    private void bind() {
        modeBox.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.SELECTED) return;
            CaptureMode mode = (CaptureMode) modeBox.getSelectedItem();
            UserPreferences.captureMode(mode);
            if (mode == CaptureMode.REGION && !session.isActive()) showOverlay();
            else if (overlay != null && !session.isActive()) overlay.setVisible(false);
            if (mode == CaptureMode.FULL_SCREEN || mode == CaptureMode.MONITORS) refreshMonitors(false);
            if (mode == CaptureMode.MONITORS && selectedMonitors.isEmpty()) selectDefaultMonitor();
            if (mode == CaptureMode.WINDOW && windowBox.getItemCount() == 0) refreshWindows(true);
            syncWebcamOverlayToCapture();
            updateMonitorSummary();
            updateState();
        });

        systemAudio.addItemListener(e -> {
            UserPreferences.recordSystemAudio(systemAudio.isSelected());
            updateState();
        });
        systemVolume.addChangeListener(e -> {
            UserPreferences.systemVolumePercent(systemVolume.getValue());
            updateVolumeLabels();
        });
        micAudio.addItemListener(e -> {
            UserPreferences.recordMicrophone(micAudio.isSelected());
            updateState();
        });
        microphoneVolume.addChangeListener(e -> {
            UserPreferences.microphoneVolumePercent(microphoneVolume.getValue());
            updateVolumeLabels();
        });
        microphoneNoiseSuppression.addItemListener(e ->
                UserPreferences.microphoneNoiseSuppression(microphoneNoiseSuppression.isSelected()));
        microphoneNoiseGate.addItemListener(e -> {
            UserPreferences.microphoneNoiseGate(microphoneNoiseGate.isSelected());
            updateState();
        });
        microphoneNoiseGateDb.addChangeListener(e ->
                UserPreferences.microphoneNoiseGateDb((Integer) microphoneNoiseGateDb.getValue()));
        muteMicrophoneButton.addActionListener(e -> toggleMicrophoneMute());
        separateAudioTracks.addItemListener(e ->
                UserPreferences.separateAudioTracks(separateAudioTracks.isSelected()));
        recordWebcam.addItemListener(e -> {
            UserPreferences.recordWebcam(recordWebcam.isSelected());
            if (!recordWebcam.isSelected()) hideWebcamOverlay();
            updateState();
        });
        webcamBox.addActionListener(e -> {
            WebcamDevice selected = (WebcamDevice) webcamBox.getSelectedItem();
            if (selected != null) {
                UserPreferences.webcamName(selected.name());
                if (webcamOverlay != null) webcamOverlay.setDeviceName(selected.name());
                if (webcamOverlay != null && webcamOverlay.isVisible() && !session.isActive()) startWebcamPreview(selected);
            }
        });
        webcamMirror.addItemListener(e -> {
            UserPreferences.webcamMirror(webcamMirror.isSelected());
            applyWebcamPreviewStyle();
        });
        webcamShape.addActionListener(e -> {
            WebcamShape shape = (WebcamShape) webcamShape.getSelectedItem();
            if (shape != null) UserPreferences.webcamShape(shape);
            applyWebcamPreviewStyle();
        });
        webcamBorder.addItemListener(e -> {
            UserPreferences.webcamBorder(webcamBorder.isSelected());
            applyWebcamPreviewStyle();
            updateState();
        });
        webcamShadow.addItemListener(e -> {
            UserPreferences.webcamShadow(webcamShadow.isSelected());
            applyWebcamPreviewStyle();
        });
        hideWebcamPreviewWhileRecording.addItemListener(e ->
                UserPreferences.hideWebcamPreviewWhileRecording(hideWebcamPreviewWhileRecording.isSelected()));
        showCursor.addItemListener(e -> UserPreferences.showCursor(showCursor.isSelected()));
        highlightCursor.addItemListener(e -> {
            UserPreferences.highlightCursor(highlightCursor.isSelected());
            updateState();
        });
        mouseClickEffects.addItemListener(e -> {
            UserPreferences.mouseClickEffects(mouseClickEffects.isSelected());
            updateState();
        });
        excludeRecorderWindow.addItemListener(e ->
                UserPreferences.excludeRecorderWindow(excludeRecorderWindow.isSelected()));
        videoFormatBox.addActionListener(e -> {
            VideoFormat format = (VideoFormat) videoFormatBox.getSelectedItem();
            if (format != null) UserPreferences.videoFormat(format);
        });
        qualitySlider.addChangeListener(e -> {
            updateQualityLabel();
            UserPreferences.qualityPercent(qualitySlider.getValue());
        });
        fpsSpinner.addChangeListener(e -> UserPreferences.fps((Integer) fpsSpinner.getValue()));
        encoderBox.addActionListener(e -> {
            VideoEncoder selected = (VideoEncoder) encoderBox.getSelectedItem();
            if (selected != null) UserPreferences.videoEncoder(selected);
            updateQualityLabel();
        });
        micBox.addActionListener(e -> {
            MicrophoneDevice selected = (MicrophoneDevice) micBox.getSelectedItem();
            if (selected != null) UserPreferences.microphoneId(selected.id());
        });

        selectMonitorsButton.addActionListener(e -> chooseMonitors());
        refreshWindowsButton.addActionListener(e -> refreshWindows(true));
        windowBox.addActionListener(e -> {
            WindowTarget selected = (WindowTarget) windowBox.getSelectedItem();
            if (selected != null) UserPreferences.selectedWindowTitle(selected.title());
            syncWebcamOverlayToCapture();
        });
        regionButton.addActionListener(e -> showOverlay());
        applyRegionSizeButton.addActionListener(e -> applyManualRegionSize());
        bindRegionSpinner(regionWidthSpinner);
        bindRegionSpinner(regionHeightSpinner);
        refreshMic.addActionListener(e -> refreshMicrophones());
        refreshWebcams.addActionListener(e -> refreshWebcams());
        adjustWebcamOverlay.addActionListener(e -> showWebcamOverlay());
        webcamBorderColorButton.addActionListener(e -> chooseWebcamBorderColor());
        browseOutputDirectory.addActionListener(e -> chooseOutputDirectory());
        openOutputDirectory.addActionListener(e -> openOutputDirectory());
        cursorColorButton.addActionListener(e -> chooseCursorColor());
        showLogToggle.addActionListener(e -> setLogVisible(showLogToggle.isSelected()));
        autoUpdateCheck.addItemListener(e -> UserPreferences.autoUpdateCheck(autoUpdateCheck.isSelected()));
        checkUpdatesButton.addActionListener(e -> checkForUpdates(true));
        aboutButton.addActionListener(e -> new AboutDialog(this, ffmpegPath, () -> checkForUpdates(true)).setVisible(true));

        start.addActionListener(e -> startRecording());
        pause.addActionListener(e -> pauseResumeRecording());
        stop.addActionListener(e -> stopRecording());
        screenshot.addActionListener(e -> takeScreenshot());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { closeApplication(); }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentMoved(java.awt.event.ComponentEvent e) { persistWindowBounds(); }
            @Override public void componentResized(java.awt.event.ComponentEvent e) { persistWindowBounds(); }
        });
    }

    private void bindRegionSpinner(JSpinner spinner) {
        spinner.addChangeListener(e -> {
            if (updatingRegionControls || session.isActive()) return;
            if (modeBox.getSelectedItem() == CaptureMode.REGION) applyManualRegionSize();
        });
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().addActionListener(e -> applyManualRegionSize());
        }
    }

    private void configureHotkeys() {
        hotkeyService.setStartStopHotkey(startStopHotkeyField.hotkey());
        hotkeyService.setScreenshotHotkey(screenshotHotkeyField.hotkey());
        hotkeyService.setMuteMicrophoneHotkey(muteMicrophoneHotkeyField.hotkey());
        hotkeyService.setStartStopAction(() -> SwingUtilities.invokeLater(() -> {
            if (busy) return;
            if (session.isActive()) stopRecording(); else startRecording();
        }));
        hotkeyService.setScreenshotAction(() -> SwingUtilities.invokeLater(this::takeScreenshot));
        hotkeyService.setMuteMicrophoneAction(() -> SwingUtilities.invokeLater(this::toggleMicrophoneMute));

        Runnable captureStart = () -> hotkeyService.setSuspended(true);
        Runnable captureEnd = () -> {
            hotkeyService.setSuspended(false);
            hotkeyService.suppressForMillis(500);
        };
        startStopHotkeyField.setCaptureCallbacks(captureStart, captureEnd);
        screenshotHotkeyField.setCaptureCallbacks(captureStart, captureEnd);
        muteMicrophoneHotkeyField.setCaptureCallbacks(captureStart, captureEnd);
    }

    private void changeStartStopHotkey(Hotkey hotkey) {
        if (hotkey.equals(screenshotHotkeyField.hotkey()) || hotkey.equals(muteMicrophoneHotkeyField.hotkey())) {
            Hotkey old = UserPreferences.startStopHotkey();
            startStopHotkeyField.setHotkey(old);
            warning("Start/Stop and Screenshot hotkeys must be different.");
            return;
        }
        UserPreferences.startStopHotkey(hotkey);
        hotkeyService.setStartStopHotkey(hotkey);
        hotkeyService.suppressForMillis(500);
    }

    private void changeScreenshotHotkey(Hotkey hotkey) {
        if (hotkey.equals(startStopHotkeyField.hotkey()) || hotkey.equals(muteMicrophoneHotkeyField.hotkey())) {
            Hotkey old = UserPreferences.screenshotHotkey();
            screenshotHotkeyField.setHotkey(old);
            warning("Start/Stop and Screenshot hotkeys must be different.");
            return;
        }
        UserPreferences.screenshotHotkey(hotkey);
        hotkeyService.setScreenshotHotkey(hotkey);
        hotkeyService.suppressForMillis(500);
    }

    private void changeMuteMicrophoneHotkey(Hotkey hotkey) {
        if (hotkey.equals(startStopHotkeyField.hotkey()) || hotkey.equals(screenshotHotkeyField.hotkey())) {
            Hotkey old = UserPreferences.muteMicrophoneHotkey();
            muteMicrophoneHotkeyField.setHotkey(old);
            warning("Record, Screenshot and Mute microphone hotkeys must be different.");
            return;
        }
        UserPreferences.muteMicrophoneHotkey(hotkey);
        hotkeyService.setMuteMicrophoneHotkey(hotkey);
        hotkeyService.suppressForMillis(500);
    }

    private void toggleMicrophoneMute() {
        if (!session.isActive() || !micAudio.isSelected()) {
            muteMicrophoneButton.setSelected(false);
            updateMuteMicrophoneButton();
            return;
        }
        boolean next = !session.isMicrophoneMuted();
        session.setMicrophoneMuted(next);
        muteMicrophoneButton.setSelected(next);
        updateMuteMicrophoneButton();
    }

    private void updateMuteMicrophoneButton() {
        boolean muted = session.isMicrophoneMuted();
        muteMicrophoneButton.setSelected(muted);
        muteMicrophoneButton.setText(muted ? "Mic muted" : "Mute mic");
        muteMicrophoneButton.setForeground(muted ? AppTheme.RECORD : AppTheme.TEXT);
    }

    private void showOverlay() {
        if (session.isActive()) return;
        if (overlay == null || !overlay.isDisplayable()) {
            overlay = new RegionOverlay(region, r -> {
                region = r;
                UserPreferences.region(r);
                SwingUtilities.invokeLater(() -> {
                    updateRegionText();
                    syncWebcamOverlayToCapture();
                });
            });
            overlay.setRecordingControls(
                    this::pauseResumeRecording,
                    this::stopRecording,
                    () -> overlay.setVisible(false),
                    this::relocateActiveRegion);
        }
        overlay.setCaptureState(RegionOverlay.CaptureState.READY);
        overlay.setRegion(region);
        overlay.setVisible(true);
        overlay.toFront();
    }

    private void relocateActiveRegion(CaptureRegion requested) {
        CaptureMode mode = activeConfig != null ? activeConfig.captureMode() : (CaptureMode) modeBox.getSelectedItem();
        if (mode != CaptureMode.REGION) return;

        CaptureRegion previous = region.evenSized();
        CaptureRegion next = new CaptureRegion(
                requested.x(), requested.y(), previous.width(), previous.height()).evenSized();

        region = next;
        UserPreferences.region(next);
        updateRegionText();
        if (webcamOverlay != null && webcamOverlay.isDisplayable()) {
            webcamOverlay.setCaptureBoundsKeepingPlacement(
                    new Rectangle(next.x(), next.y(), next.width(), next.height()));
            webcamPlacement = webcamOverlay.placement();
            UserPreferences.webcamPlacement(webcamPlacement);
            updateWebcamPlacementLabel();
        }

        if (!session.isActive()) return;
        if (regionRelocationBusy) {
            append("Capture-region move ignored because the previous move is still being applied.");
            return;
        }

        regionRelocationBusy = true;
        append("Moving active capture region to " + next.x() + "," + next.y() + "...");
        runTask(
                () -> {
                    session.relocateRegion(next);
                    return next;
                },
                moved -> {
                    if (activeConfig != null) activeConfig = activeConfig.withRegion(moved);
                    regionRelocationBusy = false;
                    append("Active capture region moved to " + moved.x() + "," + moved.y() + ".");
                },
                ex -> {
                    regionRelocationBusy = false;
                    region = previous;
                    UserPreferences.region(previous);
                    updateRegionText();
                    if (overlay != null) overlay.setRegion(previous);
                    if (webcamOverlay != null && webcamOverlay.isDisplayable()) {
                        webcamOverlay.setCaptureBoundsKeepingPlacement(
                                new Rectangle(previous.x(), previous.y(), previous.width(), previous.height()));
                    }
                    error(new IllegalStateException("Could not move the active recording region. The previous position was restored.", ex));
                });
    }

    private void startRecording() {
        if (busy || session.isActive()) return;

        try {
            Path outputDirectory = Path.of(outputDirectoryField.getText()).toAbsolutePath();
            Files.createDirectories(outputDirectory);
            if (!ensureDiskSpaceBeforeStart(outputDirectory)) return;

            CaptureMode mode = (CaptureMode) modeBox.getSelectedItem();
            if (mode == CaptureMode.FULL_SCREEN || mode == CaptureMode.MONITORS) refreshMonitors(false);
            if (mode == CaptureMode.WINDOW) refreshWindows(false);
            if (mode == CaptureMode.REGION) {
                if (overlay == null || !overlay.isDisplayable()) showOverlay();
                if (overlay != null) {
                    region = overlay.region();
                    UserPreferences.region(region);
                    overlay.setVisible(true);
                    overlay.setCaptureState(RegionOverlay.CaptureState.RECORDING);
                }
            }

            VideoEncoder selectedEncoder = (VideoEncoder) encoderBox.getSelectedItem();
            VideoEncoder resolvedEncoder = selectedEncoder == VideoEncoder.AUTO
                    ? (nvencAvailable ? VideoEncoder.NVIDIA_NVENC : VideoEncoder.CPU_X264)
                    : selectedEncoder;
            if (resolvedEncoder == VideoEncoder.NVIDIA_NVENC && !nvencAvailable) {
                resolvedEncoder = VideoEncoder.CPU_X264;
                append("NVENC is not available; using CPU H.264.");
            }

            VideoFormat videoFormat = (VideoFormat) videoFormatBox.getSelectedItem();
            if (videoFormat == null) videoFormat = VideoFormat.MP4;
            Path outputFile = OutputNaming.recordingFile(outputDirectory, videoFormat);

            List<DisplayMonitor> captureMonitors = captureMonitorsForMode(mode);
            WindowTarget selectedWindow = selectedWindowTarget();
            if (mode == CaptureMode.MONITORS && captureMonitors.isEmpty()) {
                throw new IllegalArgumentException("Select at least one monitor");
            }
            if (mode == CaptureMode.FULL_SCREEN && captureMonitors.isEmpty()) {
                throw new IllegalStateException("No monitors were detected by Windows");
            }
            if (mode == CaptureMode.WINDOW && selectedWindow == null) {
                throw new IllegalArgumentException("Select a window to record");
            }
            Rectangle captureBounds = captureBoundsForMode(mode, captureMonitors, selectedWindow);
            WebcamDevice selectedWebcam = (WebcamDevice) webcamBox.getSelectedItem();
            if (recordWebcam.isSelected() && selectedWebcam == null) {
                throw new IllegalArgumentException("Select a webcam");
            }
            if (webcamOverlay != null && webcamOverlay.isDisplayable()) {
                // Monitor refresh can change the virtual desktop origin (for example when
                // a second display sits to the left). Preserve the physical preview position
                // instead of reusing an old relative X/Y that makes the camera jump.
                webcamOverlay.setCaptureBounds(captureBounds);
                if (webcamOverlay.isVisible()) webcamPlacement = webcamOverlay.placement();
            }
            if (webcamPlacement == null) {
                webcamPlacement = UserPreferences.webcamPlacement(captureBounds.width, captureBounds.height);
            }
            webcamPlacement = webcamPlacement.clampTo(captureBounds.width, captureBounds.height);
            UserPreferences.webcamPlacement(webcamPlacement);

            String webcamInputUrl = null;
            if (recordWebcam.isSelected()) {
                prepareWebcamForRecording(selectedWebcam);
                webcamInputUrl = webcamPreviewService.bridgeUrl();
                if (webcamInputUrl == null || webcamInputUrl.isBlank()) {
                    throw new IllegalStateException("Could not prepare the webcam recording bridge. See technical log.");
                }
            }

            // Keep WINDOW capture bound to the selected HWND so moving the window does not change
            // what is recorded. External mouse halo/click overlays cannot be composited into that HWND
            // capture path, so they are deliberately disabled for WINDOW mode.
            boolean windowCaptureNeedsDesktopEffects = false;
            if (mode == CaptureMode.WINDOW && (highlightCursor.isSelected() || mouseClickEffects.isSelected())) {
                append("Window capture: mouse highlight/click effects are disabled so capture follows the selected window when it moves.");
            }

            boolean fastGpuCapture = resolvedEncoder == VideoEncoder.NVIDIA_NVENC &&
                    mode != CaptureMode.WINDOW &&
                    (mode == CaptureMode.REGION || (!captureMonitors.isEmpty() &&
                            captureMonitors.stream().allMatch(m -> m.dxgiOutputIndex() >= 0)));

            RecorderConfig config = new RecorderConfig(
                    ffmpegPath,
                    mode,
                    region.evenSized(),
                    captureMonitors,
                    selectedWindow,
                    qualitySlider.getValue(),
                    (Integer) fpsSpinner.getValue(),
                    systemAudio.isSelected(),
                    systemVolume.getValue(),
                    micAudio.isSelected(),
                    (MicrophoneDevice) micBox.getSelectedItem(),
                    microphoneVolume.getValue(),
                    microphoneNoiseSuppression.isSelected(),
                    microphoneNoiseGate.isSelected(),
                    (Integer) microphoneNoiseGateDb.getValue(),
                    separateAudioTracks.isSelected(),
                    resolvedEncoder,
                    fastGpuCapture,
                    showCursor.isSelected(),
                    windowCaptureNeedsDesktopEffects,
                    recordWebcam.isSelected(),
                    selectedWebcam,
                    webcamInputUrl,
                    WebcamPreviewService.WIDTH,
                    WebcamPreviewService.HEIGHT,
                    WebcamPreviewService.FPS,
                    webcamPlacement,
                    webcamMirror.isSelected(),
                    (WebcamShape) webcamShape.getSelectedItem(),
                    webcamBorder.isSelected(),
                    webcamShadow.isSelected(),
                    webcamBorderColor.getRGB(),
                    outputFile
            );

            if (config.recordMicrophone() && config.microphoneDevice() == null) {
                throw new IllegalArgumentException("Select a microphone");
            }

            persistAllSettings();
            recordingStats = RecordingStats.ZERO;
            lowSpaceWarningShown = false;
            criticalSpaceStopTriggered = false;
            microphoneDisconnectNotified = false;
            webcamDisconnectNotified = false;
            if (config.recordMicrophone()) {
                microphoneStatusLabel.setText("Starting...");
                microphoneStatusLabel.setForeground(AppTheme.MUTED);
            }
            busy = true;
            statusLabel.setText("STARTING");
            statusLabel.setForeground(AppTheme.ACCENT);
            updateState();
            startCursorEffectsIfEnabled();
            applyRecorderWindowExclusionIfNeeded();

            runTask(
                    () -> {
                        ffmpegService.verify(config.ffmpegPath());
                        if (config.recordWebcam() && !webcamPreviewService.awaitReady(6, java.util.concurrent.TimeUnit.SECONDS)) {
                            throw new IllegalStateException("No webcam frame was received before recording start. Close other camera apps and retry.");
                        }
                        session.start(config, this::append, audioLevels, recordingStatsListener, recordingHealthListener);
                        return config;
                    },
                    cfg -> {
                        activeConfig = cfg;
                        recordedMillis = 0;
                        segmentClockStart = Instant.now();
                        elapsedTimer.start();
                        append("Recording started: " + cfg.outputFile());
                        append("Encoder: " + cfg.videoEncoder());
                        append("Capture backend: " + (cfg.fastGpuCapture()
                                ? "Desktop Duplication (GPU, automatic GDI fallback)"
                                : "GDI/Window capture"));
                        if (cfg.recordMicrophone()) {
                            microphoneStatusLabel.setText("Recording");
                            microphoneStatusLabel.setForeground(AppTheme.GOOD);
                        }
                        updateReliabilityStatus();
                        if (cfg.recordWebcam()) {
                            webcamStatusLabel.setText(hideWebcamPreviewWhileRecording.isSelected()
                                    ? "Recording source active — local preview hidden"
                                    : "Live preview + recording source active");
                            webcamStatusLabel.setForeground(AppTheme.GOOD);
                        }
                        busy = false;
                        updateState();
                    },
                    ex -> {
                        stopCursorEffects();
                        restoreRecorderWindowCapture();
                        restoreWebcamAfterRecording();
                        if (overlay != null) overlay.setCaptureState(RegionOverlay.CaptureState.READY);
                        activeConfig = null;
                        busy = false;
                        updateState();
                        error(ex);
                    });
        } catch (Exception ex) {
            stopCursorEffects();
            restoreRecorderWindowCapture();
            restoreWebcamAfterRecording();
            if (overlay != null) overlay.setCaptureState(RegionOverlay.CaptureState.READY);
            error(ex);
        }
    }

    private void pauseResumeRecording() {
        if (busy || !session.isActive()) return;

        if (session.isRecording()) {
            busy = true;
            accumulateElapsed();
            elapsedTimer.stop();
            stopCursorEffects();
            updateState();
            runTask(
                    () -> { session.pause(); return null; },
                    unused -> {
                        if (overlay != null) overlay.setCaptureState(RegionOverlay.CaptureState.PAUSED);
                        busy = false;
                        updateState();
                    },
                    ex -> {
                        busy = false;
                        updateState();
                        error(ex);
                    });
        } else if (session.isPaused()) {
            if (overlay != null) overlay.setCaptureState(RegionOverlay.CaptureState.RECORDING);
            busy = true;
            updateState();
            startCursorEffectsIfEnabled();
            runTask(
                    () -> { session.resume(); return null; },
                    unused -> {
                        segmentClockStart = Instant.now();
                        elapsedTimer.start();
                        busy = false;
                        updateState();
                    },
                    ex -> {
                        stopCursorEffects();
                        if (overlay != null) overlay.setCaptureState(RegionOverlay.CaptureState.PAUSED);
                        busy = false;
                        updateState();
                        error(ex);
                    });
        }
    }

    private void stopRecording() {
        if (busy || !session.isActive()) return;
        busy = true;
        if (session.isRecording()) accumulateElapsed();
        elapsedTimer.stop();
        stopCursorEffects();
        statusLabel.setText("FINALIZING");
        statusLabel.setForeground(AppTheme.ACCENT);
        updateState();

        runTask(
                session::stop,
                finalFile -> {
                    if (finalFile != null) append("Recording saved: " + finalFile);
                    libraryPanel.refresh();
                    activeConfig = null;
                    restoreRecorderWindowCapture();
                    restoreWebcamAfterRecording();
                    microphoneStatusLabel.setText("Ready");
                    microphoneStatusLabel.setForeground(AppTheme.MUTED);
                    muteMicrophoneButton.setSelected(false);
                    updateMuteMicrophoneButton();
                    busy = false;
                    if (overlay != null) {
                        overlay.setCaptureState(RegionOverlay.CaptureState.READY);
                        overlay.setVisible((CaptureMode) modeBox.getSelectedItem() == CaptureMode.REGION);
                    }
                    updateState();
                },
                ex -> {
                    activeConfig = null;
                    restoreRecorderWindowCapture();
                    restoreWebcamAfterRecording();
                    microphoneStatusLabel.setText("Ready");
                    microphoneStatusLabel.setForeground(AppTheme.MUTED);
                    busy = false;
                    if (overlay != null) overlay.setCaptureState(RegionOverlay.CaptureState.READY);
                    updateState();
                    error(new IllegalStateException(ex.getMessage() +
                            "\n\nThe crash-recovery files were kept. Restart the app to recover them.", ex));
                });
    }

    private void takeScreenshot() {
        if (busy) return;
        try {
            Path outputDirectory = Path.of(outputDirectoryField.getText()).toAbsolutePath();
            CaptureMode mode = activeConfig != null ? activeConfig.captureMode() : (CaptureMode) modeBox.getSelectedItem();
            if (activeConfig == null && (mode == CaptureMode.FULL_SCREEN || mode == CaptureMode.MONITORS)) refreshMonitors(false);
            if (activeConfig == null && mode == CaptureMode.WINDOW) refreshWindows(false);
            CaptureRegion captureRegion = activeConfig != null ? activeConfig.region() : currentRegion();
            List<DisplayMonitor> monitors = activeConfig != null
                    ? activeConfig.captureMonitors() : captureMonitorsForMode(mode);
            WindowTarget selectedWindow = activeConfig != null
                    ? activeConfig.windowTarget() : selectedWindowTarget();

            screenshot.setEnabled(false);
            runTask(
                    () -> screenshotService.capture(outputDirectory, mode, captureRegion, monitors, selectedWindow),
                    file -> {
                        screenshot.setEnabled(true);
                        append("Screenshot saved: " + file);
                        libraryPanel.refresh();
                        Toolkit.getDefaultToolkit().beep();
                    },
                    ex -> {
                        screenshot.setEnabled(true);
                        error(ex);
                    });
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void detectNvencAsync() {
        runTask(
                () -> ffmpegService.detectNvenc(ffmpegPath, this::append),
                status -> {
                    nvencAvailable = status.selectable();
                    VideoEncoder wanted = UserPreferences.videoEncoder();
                    if (status.selectable() && !comboContains(encoderBox, VideoEncoder.NVIDIA_NVENC)) {
                        encoderBox.insertItemAt(VideoEncoder.NVIDIA_NVENC, 1);
                    }
                    if (wanted == VideoEncoder.NVIDIA_NVENC && status.selectable()) encoderBox.setSelectedItem(wanted);
                    else if (wanted != VideoEncoder.NVIDIA_NVENC) encoderBox.setSelectedItem(wanted);

                    if (status.probeSucceeded()) {
                        encoderStatus.setText("● NVENC ready — NVIDIA hardware encoding validated");
                        encoderStatus.setForeground(AppTheme.GOOD);
                    } else if (status.encoderPresent()) {
                        encoderStatus.setText("NVENC found — probe failed; recording will retry it");
                        encoderStatus.setForeground(new Color(255, 183, 77));
                        encoderStatus.setToolTipText(status.detail());
                    } else {
                        encoderStatus.setText("CPU H.264 available — h264_nvenc is missing from FFmpeg");
                        encoderStatus.setForeground(AppTheme.MUTED);
                        encoderStatus.setToolTipText(status.detail());
                    }
                    updateQualityLabel();
                },
                ex -> {
                    nvencAvailable = false;
                    encoderStatus.setText("CPU H.264 available — NVENC check failed");
                    encoderStatus.setForeground(AppTheme.MUTED);
                    append("NVENC check failed: " + ex.getMessage());
                });
    }

    private boolean comboContains(JComboBox<VideoEncoder> combo, VideoEncoder value) {
        for (int i = 0; i < combo.getItemCount(); i++) if (combo.getItemAt(i) == value) return true;
        return false;
    }

    private void offerRecoveryIfNeeded() {
        if (!isDisplayable()) return;
        Path outputDir = UserPreferences.outputDirectory();
        List<RecoveryService.RecoveryCandidate> candidates = recoveryService.scan(outputDir);
        if (candidates.isEmpty()) return;

        StringBuilder message = new StringBuilder("Found unfinished recording data from a previous shutdown:\n\n");
        for (RecoveryService.RecoveryCandidate c : candidates) {
            message.append("• ").append(c).append('\n');
        }
        message.append("\nRecover them to their selected output format now?");

        Object[] options = {"Recover", "Keep for later", "Discard"};
        int choice = JOptionPane.showOptionDialog(
                this, message.toString(), "Recording recovery",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);

        if (choice == 0) {
            busy = true;
            updateState();
            runTask(
                    () -> {
                        List<Path> recovered = new ArrayList<>();
                        for (RecoveryService.RecoveryCandidate candidate : candidates) {
                            recovered.add(recoveryService.recover(ffmpegPath, candidate, this::append));
                        }
                        return recovered;
                    },
                    recovered -> {
                        busy = false;
                        updateState();
                        append("Recovered " + recovered.size() + " unfinished recording(s).");
                        JOptionPane.showMessageDialog(this,
                                "Recovered " + recovered.size() + " recording(s) to:\n" + outputDir,
                                "Recovery complete", JOptionPane.INFORMATION_MESSAGE);
                    },
                    ex -> {
                        busy = false;
                        updateState();
                        error(ex);
                    });
        } else if (choice == 2) {
            for (RecoveryService.RecoveryCandidate candidate : candidates) recoveryService.discard(candidate);
            append("Discarded " + candidates.size() + " recovery session(s).");
        }
    }

    private void startCursorEffectsIfEnabled() {
        CaptureMode mode = activeConfig != null ? activeConfig.captureMode() : (CaptureMode) modeBox.getSelectedItem();
        if (mode == CaptureMode.WINDOW) return;
        boolean highlight = highlightCursor.isSelected();
        boolean clicks = mouseClickEffects.isSelected();
        if (!highlight && !clicks) return;
        if (cursorOverlay == null || !cursorOverlay.isDisplayable()) {
            cursorOverlay = new CursorHighlightOverlay(highlightColor);
        }
        cursorOverlay.setHighlightColor(highlightColor);
        cursorOverlay.setHighlightEnabled(highlight);
        cursorOverlay.setClickEffectsEnabled(clicks);
        cursorOverlay.start();
    }

    private void stopCursorEffects() {
        if (cursorOverlay != null) cursorOverlay.stop();
    }

    private void applyRecorderWindowExclusionIfNeeded() {
        recorderWindowExcluded = false;
        regionOverlayExcluded = false;
        if (excludeRecorderWindow.isSelected()) {
            recorderWindowExcluded = WindowCaptureExclusion.setExcluded(this, true, this::append);
        }
        // The selection border is a helper UI and must never leak into a REGION recording,
        // especially on 125/150% DPI where logical and physical border pixels differ.
        if ((CaptureMode) modeBox.getSelectedItem() == CaptureMode.REGION && overlay != null && overlay.isVisible()) {
            regionOverlayExcluded = WindowCaptureExclusion.setExcluded(
                    overlay, true, "Capture frame", this::append);
        }
    }

    private void restoreRecorderWindowCapture() {
        if (recorderWindowExcluded) {
            WindowCaptureExclusion.setExcluded(this, false, this::append);
            recorderWindowExcluded = false;
        }
        if (regionOverlayExcluded && overlay != null) {
            WindowCaptureExclusion.setExcluded(overlay, false, "Capture frame", this::append);
            regionOverlayExcluded = false;
        }
    }

    private CaptureRegion currentRegion() {
        if (overlay != null && overlay.isDisplayable()) return overlay.region();
        return region.evenSized();
    }

    private void refreshMicrophones() {
        String wantedId = UserPreferences.microphoneId();
        MicrophoneDevice current = (MicrophoneDevice) micBox.getSelectedItem();
        if (current != null) wantedId = current.id();

        List<MicrophoneDevice> list = microphoneService.list();
        micBox.removeAllItems();
        list.forEach(micBox::addItem);

        int selectedIndex = -1;
        for (int i = 0; i < micBox.getItemCount(); i++) {
            if (micBox.getItemAt(i).id().equals(wantedId)) {
                selectedIndex = i;
                break;
            }
        }
        if (selectedIndex < 0 && micBox.getItemCount() > 0) selectedIndex = 0;
        if (selectedIndex >= 0) micBox.setSelectedIndex(selectedIndex);
        MicrophoneDevice selected = (MicrophoneDevice) micBox.getSelectedItem();
        if (selected != null) UserPreferences.microphoneId(selected.id());

        append("Microphones found: " + Math.max(0, list.size() - 1) + " + Windows default");
        if (!session.isActive()) {
            microphoneStatusLabel.setText(list.isEmpty() ? "No microphone detected" : "Ready");
            microphoneStatusLabel.setForeground(list.isEmpty() ? AppTheme.RECORD : AppTheme.MUTED);
        }
    }

    private void refreshWebcams() {
        String wanted = UserPreferences.webcamName();
        WebcamDevice current = (WebcamDevice) webcamBox.getSelectedItem();
        if (current != null) wanted = current.name();
        final String wantedName = wanted;
        refreshWebcams.setEnabled(false);
        webcamStatusLabel.setText("Detecting cameras...");
        webcamStatusLabel.setForeground(AppTheme.MUTED);
        runTask(
                () -> webcamService.listDevices(ffmpegPath, this::append),
                devices -> {
                    webcamBox.removeAllItems();
                    devices.forEach(webcamBox::addItem);
                    int selected = -1;
                    for (int i = 0; i < webcamBox.getItemCount(); i++) {
                        if (webcamBox.getItemAt(i).name().equals(wantedName)) {
                            selected = i;
                            break;
                        }
                    }
                    if (selected < 0 && webcamBox.getItemCount() > 0) selected = 0;
                    if (selected >= 0) webcamBox.setSelectedIndex(selected);
                    WebcamDevice device = (WebcamDevice) webcamBox.getSelectedItem();
                    if (device != null) UserPreferences.webcamName(device.name());
                    append("Webcams found: " + devices.size());
                    if (devices.isEmpty()) {
                        webcamStatusLabel.setText("No camera detected — click Refresh");
                        webcamStatusLabel.setForeground(new Color(235, 173, 72));
                    } else {
                        webcamStatusLabel.setText(devices.size() == 1 ? "1 camera ready" : devices.size() + " cameras ready");
                        webcamStatusLabel.setForeground(AppTheme.GOOD);
                    }
                    if (device != null && webcamOverlay != null && webcamOverlay.isVisible() && !session.isActive()) {
                        startWebcamPreview(device);
                    }
                    updateState();
                },
                ex -> {
                    append("Webcam detection failed: " + ex.getMessage());
                    webcamStatusLabel.setText("Camera detection failed — see technical log");
                    webcamStatusLabel.setForeground(AppTheme.RECORD);
                    updateState();
                });
    }

    private void showWebcamOverlay() {
        if (session.isActive()) return;
        if (!recordWebcam.isSelected()) {
            warning("Enable Webcam overlay first.");
            return;
        }
        WebcamDevice device = (WebcamDevice) webcamBox.getSelectedItem();
        if (device == null) {
            warning("No webcam is selected.");
            return;
        }
        Rectangle capture = currentCaptureBounds();
        if (webcamPlacement == null) webcamPlacement = UserPreferences.webcamPlacement(capture.width, capture.height);
        webcamPlacement = webcamPlacement.clampTo(capture.width, capture.height);
        if (webcamOverlay == null || !webcamOverlay.isDisplayable()) {
            webcamOverlay = new WebcamOverlay(capture, webcamPlacement, placement -> {
                webcamPlacement = placement;
                UserPreferences.webcamPlacement(placement);
                SwingUtilities.invokeLater(this::updateWebcamPlacementLabel);
            });
        } else {
            webcamOverlay.setCaptureBounds(capture);
            webcamOverlay.setPlacement(webcamPlacement);
        }
        webcamOverlay.setDeviceName(device.name());
        applyWebcamPreviewStyle();
        webcamOverlay.clearPreview();
        webcamOverlay.setVisible(true);
        webcamOverlay.toFront();
        webcamStatusLabel.setText("Starting live preview...");
        webcamStatusLabel.setForeground(AppTheme.MUTED);
        startWebcamPreview(device);
        updateWebcamPlacementLabel();
    }

    private void hideWebcamOverlay() {
        webcamPreviewService.stop();
        if (webcamPreviewExcluded && webcamOverlay != null) {
            WindowCaptureExclusion.setExcluded(webcamOverlay, false, "Webcam preview", this::append);
            webcamPreviewExcluded = false;
        }
        if (webcamOverlay != null) {
            webcamOverlay.setLocked(false);
            webcamOverlay.clearPreview();
            webcamOverlay.setVisible(false);
        }
    }

    private void startWebcamPreview(WebcamDevice device) {
        if (device == null) return;
        if (webcamOverlay != null && webcamOverlay.isVisible()) webcamOverlay.clearPreview();
        webcamStatusLabel.setText("Starting camera source...");
        webcamStatusLabel.setForeground(AppTheme.MUTED);
        final boolean[] firstFrame = {true};
        webcamPreviewService.start(ffmpegPath, device, image ->
                SwingUtilities.invokeLater(() -> {
                    if (webcamOverlay != null && webcamOverlay.isVisible()) webcamOverlay.setPreviewImage(image);
                    if (firstFrame[0]) {
                        firstFrame[0] = false;
                        webcamStatusLabel.setText(session.isActive() ? "Live preview + recording source active" : "Live preview active");
                        webcamStatusLabel.setForeground(AppTheme.GOOD);
                    }
                }), this::append, message -> SwingUtilities.invokeLater(() -> handleWebcamFailure(message)));
    }

    private void prepareWebcamForRecording(WebcamDevice device) {
        if (device == null) return;
        webcamOverlayVisibleBeforeRecording = webcamOverlay != null && webcamOverlay.isVisible();

        // The camera is owned by one long-lived source process. Its frames feed both the local
        // preview and the recording FFmpeg through a localhost raw-video bridge.
        if (!hideWebcamPreviewWhileRecording.isSelected() && (webcamOverlay == null || !webcamOverlay.isVisible())) {
            showWebcamOverlay();
        } else {
            startWebcamPreview(device);
        }

        if (webcamOverlay != null) {
            webcamOverlay.setLocked(true);
            if (hideWebcamPreviewWhileRecording.isSelected()) {
                webcamOverlay.setVisible(false);
            } else {
                webcamOverlay.setVisible(true);
                webcamOverlay.toFront();
                webcamPreviewExcluded = WindowCaptureExclusion.setExcluded(
                        webcamOverlay, true, "Webcam preview", this::append);
            }
        }
    }

    private void restoreWebcamAfterRecording() {
        if (webcamOverlay != null) {
            webcamOverlay.setLocked(false);
            if (webcamPreviewExcluded) {
                WindowCaptureExclusion.setExcluded(webcamOverlay, false, "Webcam preview", this::append);
                webcamPreviewExcluded = false;
            }
        }

        boolean shouldShowPreview = recordWebcam.isSelected() &&
                (webcamOverlayVisibleBeforeRecording || !hideWebcamPreviewWhileRecording.isSelected());
        WebcamDevice device = (WebcamDevice) webcamBox.getSelectedItem();
        if (shouldShowPreview && webcamOverlay != null && device != null) {
            webcamOverlay.setVisible(true);
            webcamOverlay.toFront();
            startWebcamPreview(device);
        } else {
            if (webcamOverlay != null) webcamOverlay.setVisible(false);
            webcamPreviewService.stop();
        }
        webcamOverlayVisibleBeforeRecording = false;
    }

    private void applyWebcamPreviewStyle() {
        if (webcamOverlay == null) return;
        webcamOverlay.setMirror(webcamMirror.isSelected());
        webcamOverlay.setShape((WebcamShape) webcamShape.getSelectedItem());
        webcamOverlay.setBorderEnabled(webcamBorder.isSelected());
        webcamOverlay.setShadowEnabled(webcamShadow.isSelected());
        webcamOverlay.setBorderColor(webcamBorderColor);
    }

    private void syncWebcamOverlayToCapture() {
        if (webcamOverlay == null || !webcamOverlay.isDisplayable()) return;
        Rectangle capture = currentCaptureBounds();
        webcamOverlay.setCaptureBounds(capture);
        webcamPlacement = webcamOverlay.placement();
        UserPreferences.webcamPlacement(webcamPlacement);
        updateWebcamPlacementLabel();
    }

    private void updateWebcamPlacementLabel() {
        Rectangle capture = currentCaptureBounds();
        if (webcamPlacement == null) webcamPlacement = UserPreferences.webcamPlacement(capture.width, capture.height);
        webcamPlacement = webcamPlacement.clampTo(capture.width, capture.height);
        webcamPlacementLabel.setText(webcamPlacement.width() + "×" + webcamPlacement.height() +
                " at " + webcamPlacement.x() + "," + webcamPlacement.y());
        webcamPlacementLabel.setForeground(AppTheme.MUTED);
    }

    private Rectangle currentCaptureBounds() {
        CaptureMode mode = (CaptureMode) modeBox.getSelectedItem();
        return captureBoundsForMode(mode, captureMonitorsForMode(mode), selectedWindowTarget());
    }

    private Rectangle captureBoundsForMode(CaptureMode mode, List<DisplayMonitor> monitors, WindowTarget window) {
        if (mode == CaptureMode.REGION) {
            CaptureRegion r = currentRegion().evenSized();
            return new Rectangle(r.x(), r.y(), r.width(), r.height());
        }
        if (mode == CaptureMode.WINDOW && window != null) return window.bounds();
        if (monitors != null && !monitors.isEmpty()) return WindowsCaptureService.unionBounds(monitors);
        Rectangle virtual = new Rectangle();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            virtual = virtual.union(device.getDefaultConfiguration().getBounds());
        }
        return virtual.width > 0 && virtual.height > 0 ? virtual : new Rectangle(0, 0, 1280, 720);
    }

    private List<DisplayMonitor> captureMonitorsForMode(CaptureMode mode) {
        if (mode == CaptureMode.FULL_SCREEN) return availableMonitors;
        if (mode == CaptureMode.MONITORS) return selectedMonitors;
        // REGION itself ignores this list for geometry, but FFmpeg can use it to
        // identify the owning DXGI output for Desktop Duplication GPU capture.
        if (mode == CaptureMode.REGION) return availableMonitors;
        return List.of();
    }

    private WindowTarget selectedWindowTarget() {
        return (WindowTarget) windowBox.getSelectedItem();
    }

    private void refreshMonitors(boolean logResult) {
        List<DisplayMonitor> detected = captureTargetService.listMonitors();
        if (!detected.isEmpty()) availableMonitors = detected;

        List<String> wanted = UserPreferences.selectedMonitorIds();
        List<DisplayMonitor> restored = new ArrayList<>();
        if (!wanted.isEmpty()) {
            for (String id : wanted) {
                for (DisplayMonitor monitor : availableMonitors) {
                    if (monitor.id().equals(id)) {
                        restored.add(monitor);
                        break;
                    }
                }
            }
        }
        if (!restored.isEmpty()) selectedMonitors = List.copyOf(restored);
        else if (selectedMonitors.isEmpty()) selectDefaultMonitor();
        else {
            // Drop monitors that disappeared after docking/undocking.
            List<DisplayMonitor> stillPresent = selectedMonitors.stream()
                    .filter(old -> availableMonitors.stream().anyMatch(now -> now.id().equals(old.id())))
                    .map(old -> availableMonitors.stream().filter(now -> now.id().equals(old.id())).findFirst().orElse(old))
                    .toList();
            selectedMonitors = stillPresent.isEmpty() ? List.of() : stillPresent;
            if (selectedMonitors.isEmpty()) selectDefaultMonitor();
        }
        updateMonitorSummary();
        if (logResult) append("Displays detected: " + availableMonitors.size());
    }

    private void selectDefaultMonitor() {
        DisplayMonitor primary = availableMonitors.stream().filter(DisplayMonitor::primary).findFirst()
                .orElse(availableMonitors.isEmpty() ? null : availableMonitors.get(0));
        selectedMonitors = primary == null ? List.of() : List.of(primary);
    }

    private void chooseMonitors() {
        if (session.isActive() || busy) return;
        refreshMonitors(false);
        List<DisplayMonitor> chosen = MonitorSelectionDialog.show(this, availableMonitors, selectedMonitors);
        if (chosen != null && !chosen.isEmpty()) {
            selectedMonitors = List.copyOf(chosen);
            UserPreferences.selectedMonitorIds(selectedMonitors);
            updateMonitorSummary();
            syncWebcamOverlayToCapture();
        }
    }

    private void updateMonitorSummary() {
        if (availableMonitors.isEmpty()) {
            monitorSummary.setText("No displays detected");
            return;
        }
        CaptureMode mode = (CaptureMode) modeBox.getSelectedItem();
        List<DisplayMonitor> monitors = mode == CaptureMode.FULL_SCREEN ? availableMonitors : selectedMonitors;
        if (monitors.isEmpty()) {
            monitorSummary.setText("None selected");
            return;
        }
        Rectangle union = WindowsCaptureService.unionBounds(monitors);
        if (monitors.size() == 1) {
            DisplayMonitor m = monitors.get(0);
            monitorSummary.setText(m.name() + " · " + m.bounds().width + "×" + m.bounds().height + " @ " + m.scalePercent() + "%");
        } else {
            monitorSummary.setText(monitors.size() + " monitors · " + union.width + "×" + union.height);
        }
    }

    private void refreshWindows(boolean logResult) {
        WindowTarget current = (WindowTarget) windowBox.getSelectedItem();
        long wantedHwnd = current == null ? 0L : current.hwnd();
        String wantedTitle = current == null ? UserPreferences.selectedWindowTitle() : current.title();

        availableWindows = captureTargetService.listWindows();
        windowBox.removeAllItems();
        WindowTarget restoreByHandle = null;
        WindowTarget restoreByTitle = null;
        for (WindowTarget target : availableWindows) {
            windowBox.addItem(target);
            if (wantedHwnd != 0L && target.hwnd() == wantedHwnd) restoreByHandle = target;
            if (restoreByTitle == null && !wantedTitle.isBlank() && target.title().equals(wantedTitle)) {
                restoreByTitle = target;
            }
        }
        WindowTarget restore = restoreByHandle != null ? restoreByHandle : restoreByTitle;
        if (restore != null) windowBox.setSelectedItem(restore);
        else if (wantedTitle.isBlank() && windowBox.getItemCount() > 0) windowBox.setSelectedIndex(0);
        else windowBox.setSelectedItem(null);

        if (logResult) {
            append("Windows detected: " + availableWindows.size());
            if (!wantedTitle.isBlank() && restore == null) {
                append("Previously selected window is no longer available: " + wantedTitle);
            }
            syncWebcamOverlayToCapture();
            updateState();
        }
    }

    private Path currentOutputDirectory() {
        String value = outputDirectoryField.getText();
        if (value == null || value.isBlank()) return UserPreferences.outputDirectory();
        return Path.of(value).toAbsolutePath();
    }

    private void openOutputDirectory() {
        try {
            Path dir = Path.of(outputDirectoryField.getText()).toAbsolutePath();
            Files.createDirectories(dir);
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Opening folders is not supported by this desktop environment.");
            }
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void showAndFocus() {
        if (!isVisible()) setVisible(true);
        if ((getExtendedState() & Frame.ICONIFIED) != 0) setExtendedState(getExtendedState() & ~Frame.ICONIFIED);
        toFront();
        requestFocus();
    }

    private boolean ensureDiskSpaceBeforeStart(Path outputDirectory) {
        long free = DiskSpaceMonitor.usableBytes(outputDirectory);
        if (free < 0) return true;
        if (free < DiskSpaceMonitor.CRITICAL_BYTES) {
            warning("Only " + DiskSpaceMonitor.format(free) + " is free on the output drive.\n" +
                    "Free some disk space before starting a recording.");
            return false;
        }
        if (free < DiskSpaceMonitor.WARNING_BYTES) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Only " + DiskSpaceMonitor.format(free) + " is free on the output drive.\n" +
                            "Long/high-quality recordings may run out of space. Start anyway?",
                    "Low disk space",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            return choice == JOptionPane.YES_OPTION;
        }
        return true;
    }

    private void updateReliabilityStatus() {
        Path dir = currentOutputDirectory();
        long free = DiskSpaceMonitor.usableBytes(dir);
        RecordingStats stats = recordingStats != null ? recordingStats : RecordingStats.ZERO;

        String fpsText = session.isRecording() && stats.fps() > 0.0
                ? String.format(java.util.Locale.ROOT, "%.1f", stats.fps())
                : "—";
        reliabilityLabel.setText("FPS " + fpsText + "  ·  Dropped " + stats.droppedFrames() +
                "  ·  Disk " + DiskSpaceMonitor.format(free));

        if (free >= 0 && free < DiskSpaceMonitor.CRITICAL_BYTES) {
            reliabilityLabel.setForeground(AppTheme.RECORD);
        } else if ((free >= 0 && free < DiskSpaceMonitor.WARNING_BYTES) || stats.droppedFrames() > 0) {
            reliabilityLabel.setForeground(new Color(255, 183, 77));
        } else {
            reliabilityLabel.setForeground(AppTheme.MUTED);
        }

        if (!session.isActive()) {
            lowSpaceWarningShown = false;
            criticalSpaceStopTriggered = false;
            return;
        }

        if (free >= 0 && free < DiskSpaceMonitor.WARNING_BYTES && !lowSpaceWarningShown) {
            lowSpaceWarningShown = true;
            String message = "Only " + DiskSpaceMonitor.format(free) + " remains on the recording drive.";
            append("LOW DISK SPACE: " + message);
            trayService.showWarning("Low disk space", message);
        }

        if (free >= 0 && free < DiskSpaceMonitor.CRITICAL_BYTES && !criticalSpaceStopTriggered) {
            criticalSpaceStopTriggered = true;
            String message = "Recording is being stopped safely because only " +
                    DiskSpaceMonitor.format(free) + " remains on the output drive.";
            append("CRITICAL DISK SPACE: " + message);
            trayService.showWarning("Recording stopped", message);
            if (!busy) stopRecording();
        }
    }

    private void handleMicrophoneFailure(String message) {
        append("MICROPHONE FAILURE: " + message);
        microphoneMeter.setLevel(0.0);
        microphoneStatusLabel.setText(session.isActive()
                ? "Disconnected — recording continues without mic"
                : "Unavailable");
        microphoneStatusLabel.setForeground(AppTheme.RECORD);
        if (session.isActive() && !microphoneDisconnectNotified) {
            microphoneDisconnectNotified = true;
            trayService.showWarning("Microphone disconnected",
                    "Video recording continues. The microphone track stopped at the disconnect point.");
        }
    }

    private void handleWebcamFailure(String message) {
        append("WEBCAM FAILURE: " + message);
        webcamStatusLabel.setText(session.isActive()
                ? "Camera disconnected — recording continues"
                : "Camera unavailable — device may be busy");
        webcamStatusLabel.setForeground(AppTheme.RECORD);
        if (webcamOverlay != null && webcamOverlay.isVisible()) {
            webcamOverlay.setPreviewMessage("CAMERA DISCONNECTED");
        }
        if (session.isActive() && !webcamDisconnectNotified) {
            webcamDisconnectNotified = true;
            trayService.showWarning("Webcam disconnected",
                    "Screen recording continues without the webcam overlay.");
        }
    }

    private void updateState() {
        boolean active = session.isActive();
        boolean idle = !active && !busy;
        CaptureMode mode = (CaptureMode) modeBox.getSelectedItem();

        boolean regionMode = mode == CaptureMode.REGION;
        boolean monitorMode = mode == CaptureMode.MONITORS;
        boolean windowMode = mode == CaptureMode.WINDOW;
        selectMonitorsButton.setEnabled(idle && monitorMode);
        windowBox.setEnabled(idle && windowMode);
        refreshWindowsButton.setEnabled(idle && windowMode);
        regionButton.setEnabled(idle && regionMode);
        regionField.setEnabled(regionMode);
        regionWidthSpinner.setEnabled(idle && regionMode);
        regionHeightSpinner.setEnabled(idle && regionMode);
        applyRegionSizeButton.setEnabled(idle && regionMode);
        modeBox.setEnabled(idle);
        qualitySlider.setEnabled(idle);
        fpsSpinner.setEnabled(idle);
        encoderBox.setEnabled(idle);
        excludeRecorderWindow.setEnabled(idle);
        systemAudio.setEnabled(idle);
        systemVolume.setEnabled(idle && systemAudio.isSelected());
        micAudio.setEnabled(idle);
        micBox.setEnabled(idle && micAudio.isSelected());
        refreshMic.setEnabled(idle);
        microphoneVolume.setEnabled(idle && micAudio.isSelected());
        microphoneNoiseSuppression.setEnabled(idle && micAudio.isSelected());
        microphoneNoiseGate.setEnabled(idle && micAudio.isSelected());
        microphoneNoiseGateDb.setEnabled(idle && micAudio.isSelected() && microphoneNoiseGate.isSelected());
        muteMicrophoneButton.setEnabled(active && !busy && micAudio.isSelected());
        separateAudioTracks.setEnabled(idle && systemAudio.isSelected() && micAudio.isSelected());
        recordWebcam.setEnabled(idle);
        webcamBox.setEnabled(idle && recordWebcam.isSelected());
        refreshWebcams.setEnabled(idle);
        adjustWebcamOverlay.setEnabled(idle && recordWebcam.isSelected() && webcamBox.getSelectedItem() != null);
        webcamMirror.setEnabled(idle && recordWebcam.isSelected());
        webcamShape.setEnabled(idle && recordWebcam.isSelected());
        webcamBorder.setEnabled(idle && recordWebcam.isSelected());
        webcamShadow.setEnabled(idle && recordWebcam.isSelected());
        hideWebcamPreviewWhileRecording.setEnabled(idle && recordWebcam.isSelected());
        webcamBorderColorButton.setEnabled(idle && recordWebcam.isSelected() && webcamBorder.isSelected());
        showCursor.setEnabled(idle);
        boolean cursorEffectsAvailable = !windowMode;
        highlightCursor.setEnabled(idle && cursorEffectsAvailable);
        mouseClickEffects.setEnabled(idle && cursorEffectsAvailable);
        cursorColorButton.setEnabled(idle && cursorEffectsAvailable &&
                (highlightCursor.isSelected() || mouseClickEffects.isSelected()));
        if (windowMode) {
            highlightCursor.setToolTipText("Unavailable in Window mode: the recorder stays attached to the selected window even when it moves.");
            mouseClickEffects.setToolTipText("Unavailable in Window mode: the recorder stays attached to the selected window even when it moves.");
        } else {
            highlightCursor.setToolTipText("Draws a translucent halo around the mouse pointer.");
            mouseClickEffects.setToolTipText("Draws an animated ring at left/right mouse clicks in the recorded image.");
        }
        outputDirectoryField.setEnabled(idle);
        browseOutputDirectory.setEnabled(idle);
        openOutputDirectory.setEnabled(true);
        videoFormatBox.setEnabled(idle);
        startStopHotkeyField.setEnabled(idle);
        screenshotHotkeyField.setEnabled(idle);
        muteMicrophoneHotkeyField.setEnabled(idle);

        start.setEnabled(idle);
        pause.setEnabled(active && !busy);
        stop.setEnabled(active && !busy);
        screenshot.setEnabled(!busy);
        pause.setText(session.isPaused() ? "Resume" : "Pause");

        if (busy) {
            // caller sets a more specific status before updateState when needed
        } else if (session.isRecording()) {
            statusLabel.setText("● REC");
            statusLabel.setForeground(AppTheme.RECORD);
        } else if (session.isPaused()) {
            statusLabel.setText("Ⅱ PAUSED");
            statusLabel.setForeground(new Color(255, 183, 77));
        } else {
            statusLabel.setText("READY");
            statusLabel.setForeground(AppTheme.GOOD);
        }
        if (idle) {
            microphoneStatusLabel.setText(micAudio.isSelected() ? "Ready" : "Disabled");
            microphoneStatusLabel.setForeground(AppTheme.MUTED);
        } else if (session.isRecording() && micAudio.isSelected() && !microphoneDisconnectNotified) {
            microphoneStatusLabel.setText("Recording");
            microphoneStatusLabel.setForeground(AppTheme.GOOD);
        }
        updateMuteMicrophoneButton();
        updateReliabilityStatus();
        trayService.setRecording(session.isRecording());
    }

    private void updateVolumeLabels() {
        systemVolumeLabel.setText(systemVolume.getValue() + "%");
        microphoneVolumeLabel.setText(microphoneVolume.getValue() + "%");
    }

    private void updateQualityLabel() {
        qualityLabel.setForeground(AppTheme.TEXT);
        VideoEncoder selected = (VideoEncoder) encoderBox.getSelectedItem();
        boolean nv = selected == VideoEncoder.NVIDIA_NVENC || (selected == VideoEncoder.AUTO && nvencAvailable);
        int q = qualitySlider.getValue();
        qualityLabel.setText(nv
                ? q + "% (CQ " + FfmpegRecorder.qualityToNvencCq(q) + ")"
                : q + "% (CRF " + FfmpegRecorder.qualityToCrf(q) + ")");
    }

    private void updateRegionText() {
        regionField.setText(region.x() + ", " + region.y() + "   " + region.width() + "×" + region.height());
        updatingRegionControls = true;
        try {
            regionWidthSpinner.setValue(region.width());
            regionHeightSpinner.setValue(region.height());
        } finally {
            updatingRegionControls = false;
        }
    }

    private void applyManualRegionSize() {
        if (updatingRegionControls || session.isActive()) return;
        try {
            regionWidthSpinner.commitEdit();
            regionHeightSpinner.commitEdit();
            int width = ((Number) regionWidthSpinner.getValue()).intValue();
            int height = ((Number) regionHeightSpinner.getValue()).intValue();
            width = Math.max(160, width) & ~1;
            height = Math.max(90, height) & ~1;
            region = new CaptureRegion(region.x(), region.y(), width, height);
            UserPreferences.region(region);
            if (overlay != null && overlay.isDisplayable()) overlay.setRegion(region);
            updateRegionText();
            syncWebcamOverlayToCapture();
        } catch (Exception ex) {
            error(new IllegalArgumentException("Enter a valid capture width and height.", ex));
        }
    }

    private void chooseOutputDirectory() {
        JFileChooser chooser = new JFileChooser(outputDirectoryField.getText());
        chooser.setDialogTitle("Choose recording output folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path directory = chooser.getSelectedFile().toPath().toAbsolutePath();
            outputDirectoryField.setText(directory.toString());
            UserPreferences.outputDirectory(directory);
            libraryPanel.refresh();
        }
    }

    private void chooseCursorColor() {
        Color selected = DarkColorPickerDialog.showDialog(this, highlightColor);
        if (selected == null) return;
        highlightColor = selected;
        UserPreferences.highlightColor(selected);
        updateColorButton();
    }

    private void updateColorButton() {
        cursorColorButton.setBackground(highlightColor);
        int luminance = (highlightColor.getRed() * 299 + highlightColor.getGreen() * 587 + highlightColor.getBlue() * 114) / 1000;
        cursorColorButton.setForeground(luminance > 150 ? Color.BLACK : Color.WHITE);
    }

    private void chooseWebcamBorderColor() {
        Color selected = DarkColorPickerDialog.showDialog(this, webcamBorderColor);
        if (selected == null) return;
        webcamBorderColor = selected;
        UserPreferences.webcamBorderColor(selected);
        updateWebcamBorderColorButton();
        applyWebcamPreviewStyle();
    }

    private void updateWebcamBorderColorButton() {
        webcamBorderColorButton.setBackground(webcamBorderColor);
        int luminance = (webcamBorderColor.getRed() * 299 + webcamBorderColor.getGreen() * 587 + webcamBorderColor.getBlue() * 114) / 1000;
        webcamBorderColorButton.setForeground(luminance > 150 ? Color.BLACK : Color.WHITE);
    }

    private void setLogVisible(boolean visible) {
        UserPreferences.showLog(visible);
        if (logScroll != null) {
            logScroll.setVisible(visible);
            revalidate();
            repaint();
        }
    }

    public void checkForUpdatesAtStartup() {
        if (!UserPreferences.autoUpdateCheck() || !updateService.isConfigured()) return;
        long now = System.currentTimeMillis();
        long last = UserPreferences.lastUpdateCheckEpochMillis();
        if (last > 0 && now - last < java.time.Duration.ofHours(12).toMillis()) return;
        Timer timer = new Timer(1400, e -> checkForUpdates(false));
        timer.setRepeats(false);
        timer.start();
    }

    private void checkForUpdates(boolean manual) {
        if (!updateService.isConfigured()) {
            updateStatusLabel.setText("Updates: source not configured");
            if (manual) warning("Update source is not configured. Set JSR_UPDATE_URL or build with -UpdateManifestUrl.");
            return;
        }
        checkUpdatesButton.setEnabled(false);
        updateStatusLabel.setText("Updates: checking…");
        updateService.check().whenComplete((info, failure) -> SwingUtilities.invokeLater(() -> {
            checkUpdatesButton.setEnabled(!session.isActive());
            UserPreferences.lastUpdateCheckEpochMillis(System.currentTimeMillis());
            if (failure != null) {
                Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                updateStatusLabel.setText("Updates: check failed");
                append("Update check failed: " + cause.getMessage());
                if (manual) warning("Could not check for updates:\n" + cause.getMessage());
                return;
            }
            if (!updateService.isNewer(info.version())) {
                updateStatusLabel.setText("Up to date · " + AppVersion.VERSION);
                if (manual) showInfoDialog("You're up to date", "Java Screen Recorder " + AppVersion.VERSION + " is the latest version available.");
                return;
            }
            updateStatusLabel.setText("Update available · " + info.version());
            showUpdateAvailable(info);
        }));
    }

    private void showUpdateAvailable(UpdateInfo info) {
        JDialog dialog = new JDialog(this, "Update available", true);
        dialog.getContentPane().setBackground(AppTheme.BG);
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(AppTheme.BG);
        root.setBorder(new EmptyBorder(18, 20, 18, 20));
        JLabel title = new JLabel("Java Screen Recorder " + info.version() + " is available");
        title.setForeground(AppTheme.TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        JTextArea notes = new JTextArea(info.notes() == null || info.notes().isBlank() ? "A newer release is available." : info.notes());
        notes.setEditable(false);
        notes.setFocusable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setRows(6);
        notes.setBackground(AppTheme.PANEL_ALT);
        notes.setForeground(AppTheme.TEXT);
        notes.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton later = AppTheme.button("Later");
        JButton install = AppTheme.primaryButton("Download & update");
        later.addActionListener(e -> dialog.dispose());
        install.addActionListener(e -> {
            if (session.isActive() || busy) {
                warning("Stop the current recording before applying an update.");
                return;
            }
            dialog.dispose();
            updateService.downloadAndLaunch(this, info, this::append, this::exitForUpdate);
        });
        buttons.add(later);
        buttons.add(install);
        root.add(title, BorderLayout.NORTH);
        root.add(new JScrollPane(notes), BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setSize(540, 330);
        dialog.setLocationRelativeTo(this);
        WindowsWindowStyler.apply(dialog);
        dialog.setVisible(true);
    }

    private void showInfoDialog(String titleText, String message) {
        JDialog dialog = new JDialog(this, titleText, true);
        JPanel root = new JPanel(new BorderLayout(10, 14));
        root.setBackground(AppTheme.BG);
        root.setBorder(new EmptyBorder(18, 20, 18, 20));
        JLabel messageLabel = new JLabel("<html>" + message.replace("\n", "<br>") + "</html>");
        messageLabel.setForeground(AppTheme.TEXT);
        JButton ok = AppTheme.button("OK");
        ok.addActionListener(e -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        buttons.add(ok);
        root.add(messageLabel, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setSize(430, 170);
        dialog.setLocationRelativeTo(this);
        WindowsWindowStyler.apply(dialog);
        dialog.setVisible(true);
    }

    private void exitForUpdate() {
        persistAllSettings();
        persistWindowBounds();
        disposeResourcesAndExit();
        System.exit(0);
    }

    private void closeApplication() {
        if (busy) return;
        persistAllSettings();
        persistWindowBounds();

        if (!session.isActive()) {
            disposeResourcesAndExit();
            return;
        }

        int result = JOptionPane.showConfirmDialog(
                this,
                "Stop the current recording and save it before exiting?\n" +
                        "Choosing No will intentionally discard this recording.",
                "Exit",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.CANCEL_OPTION || result == JOptionPane.CLOSED_OPTION) return;
        if (result == JOptionPane.NO_OPTION) {
            session.abort();
            disposeResourcesAndExit();
            return;
        }

        busy = true;
        stopCursorEffects();
        elapsedTimer.stop();
        updateState();
        runTask(
                session::stop,
                file -> disposeResourcesAndExit(),
                ex -> {
                    restoreRecorderWindowCapture();
                    busy = false;
                    updateState();
                    error(ex);
                });
    }

    private void disposeResourcesAndExit() {
        restoreRecorderWindowCapture();
        if (webcamPreviewExcluded && webcamOverlay != null) {
            WindowCaptureExclusion.setExcluded(webcamOverlay, false, "Webcam preview", this::append);
            webcamPreviewExcluded = false;
        }
        reliabilityTimer.stop();
        hotkeyService.close();
        if (overlay != null) overlay.dispose();
        if (cursorOverlay != null) cursorOverlay.dispose();
        webcamPreviewService.close();
        if (webcamOverlay != null) webcamOverlay.dispose();
        trayService.close();
        dispose();
    }

    private void persistAllSettings() {
        UserPreferences.captureMode((CaptureMode) modeBox.getSelectedItem());
        UserPreferences.selectedMonitorIds(selectedMonitors);
        WindowTarget selectedWindow = (WindowTarget) windowBox.getSelectedItem();
        if (selectedWindow != null) UserPreferences.selectedWindowTitle(selectedWindow.title());
        UserPreferences.region(currentRegion());
        UserPreferences.qualityPercent(qualitySlider.getValue());
        UserPreferences.fps((Integer) fpsSpinner.getValue());
        UserPreferences.recordSystemAudio(systemAudio.isSelected());
        UserPreferences.systemVolumePercent(systemVolume.getValue());
        UserPreferences.recordMicrophone(micAudio.isSelected());
        UserPreferences.microphoneVolumePercent(microphoneVolume.getValue());
        UserPreferences.microphoneNoiseSuppression(microphoneNoiseSuppression.isSelected());
        UserPreferences.microphoneNoiseGate(microphoneNoiseGate.isSelected());
        UserPreferences.microphoneNoiseGateDb((Integer) microphoneNoiseGateDb.getValue());
        UserPreferences.separateAudioTracks(separateAudioTracks.isSelected());
        UserPreferences.recordWebcam(recordWebcam.isSelected());
        WebcamDevice webcam = (WebcamDevice) webcamBox.getSelectedItem();
        if (webcam != null) UserPreferences.webcamName(webcam.name());
        UserPreferences.webcamPlacement(webcamPlacement);
        UserPreferences.webcamMirror(webcamMirror.isSelected());
        WebcamShape shape = (WebcamShape) webcamShape.getSelectedItem();
        if (shape != null) UserPreferences.webcamShape(shape);
        UserPreferences.webcamBorder(webcamBorder.isSelected());
        UserPreferences.webcamShadow(webcamShadow.isSelected());
        UserPreferences.hideWebcamPreviewWhileRecording(hideWebcamPreviewWhileRecording.isSelected());
        UserPreferences.webcamBorderColor(webcamBorderColor);
        MicrophoneDevice mic = (MicrophoneDevice) micBox.getSelectedItem();
        if (mic != null) UserPreferences.microphoneId(mic.id());
        VideoEncoder enc = (VideoEncoder) encoderBox.getSelectedItem();
        if (enc != null) UserPreferences.videoEncoder(enc);
        UserPreferences.autoUpdateCheck(autoUpdateCheck.isSelected());
        UserPreferences.showCursor(showCursor.isSelected());
        UserPreferences.highlightCursor(highlightCursor.isSelected());
        UserPreferences.mouseClickEffects(mouseClickEffects.isSelected());
        UserPreferences.excludeRecorderWindow(excludeRecorderWindow.isSelected());
        UserPreferences.highlightColor(highlightColor);
        VideoFormat format = (VideoFormat) videoFormatBox.getSelectedItem();
        if (format != null) UserPreferences.videoFormat(format);
        UserPreferences.outputDirectory(Path.of(outputDirectoryField.getText()));
        UserPreferences.showLog(showLogToggle.isSelected());
    }

    private void persistWindowBounds() {
        if (isShowing() && (getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) {
            UserPreferences.windowBounds(getBounds());
        }
    }

    private void updateElapsedTime() {
        long millis = recordedMillis;
        if (session.isRecording() && segmentClockStart != null) {
            millis += Duration.between(segmentClockStart, Instant.now()).toMillis();
        }
        long totalSeconds = Math.max(0, millis / 1000);
        timerLabel.setText(String.format("%02d:%02d:%02d",
                totalSeconds / 3600,
                (totalSeconds % 3600) / 60,
                totalSeconds % 60));
    }

    private void accumulateElapsed() {
        if (segmentClockStart != null) {
            recordedMillis += Math.max(0, Duration.between(segmentClockStart, Instant.now()).toMillis());
            segmentClockStart = null;
            updateElapsedTime();
        }
    }

    private <T> void runTask(Callable<T> callable, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override protected T doInBackground() throws Exception { return callable.call(); }
            @Override protected void done() {
                try { onSuccess.accept(get()); }
                catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    onError.accept(cause);
                }
            }
        };
        worker.execute();
    }

    private static CaptureRegion defaultRegion() {
        Rectangle b = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        int w = Math.min(1280, Math.max(320, b.width - 100));
        int h = Math.min(720, Math.max(180, b.height - 100));
        w &= ~1;
        h &= ~1;
        return new CaptureRegion(b.x + (b.width - w) / 2,
                b.y + (b.height - h) / 2, w, h);
    }

    private static Rectangle ensureVisible(Rectangle requested) {
        Rectangle virtual = new Rectangle();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            virtual = virtual.union(device.getDefaultConfiguration().getBounds());
        }
        if (!virtual.intersects(requested)) {
            return new Rectangle(virtual.x + 60, virtual.y + 60,
                    Math.min(requested.width, virtual.width - 100),
                    Math.min(requested.height, virtual.height - 100));
        }
        return requested;
    }

    public void appendStartupLog(String message) { append(message); }

    private void append(String message) {
        SwingUtilities.invokeLater(() -> {
            log.append(message + System.lineSeparator());
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private void warning(String message) {
        showDarkMessage("Warning", message, new Color(255, 183, 77));
    }

    private void error(Throwable e) {
        String message = friendlyErrorMessage(e);
        append("ERROR: " + message);
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().equals(message)) {
            append("Cause: " + cause.getMessage());
        }
        showDarkMessage("Error", message, AppTheme.RECORD);
    }

    private String friendlyErrorMessage(Throwable e) {
        String raw = e == null ? "Unknown error" : (e.getMessage() == null ? e.toString() : e.getMessage());
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("microphone") && (lower.contains("cannot open") || lower.contains("could not be opened") || lower.contains("line with format"))) {
            return "The selected microphone is unavailable. Close apps that may use it exclusively, click Refresh, " +
                    "then try again or choose another microphone.";
        }
        if (lower.contains("webcam") || lower.contains("camera")) {
            if (lower.contains("could not") || lower.contains("no webcam frame") || lower.contains("unavailable")) {
                return "The webcam could not be opened. Close Windows Camera/Teams/Discord or other apps using it, " +
                        "click Refresh, and try again.";
            }
        }
        return raw;
    }

    private void showDarkMessage(String title, String message, Color accent) {
        JDialog dialog = new JDialog(this, title, true);
        JPanel root = new JPanel(new BorderLayout(12, 14));
        root.setBackground(AppTheme.BG);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        JLabel badge = new JLabel(title.toUpperCase(java.util.Locale.ROOT));
        badge.setForeground(accent);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JTextArea text = new JTextArea(message == null ? "" : message);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(true);
        text.setBackground(AppTheme.PANEL_ALT);
        text.setForeground(AppTheme.TEXT);
        text.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        text.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                new EmptyBorder(10, 12, 10, 12)));

        JButton ok = AppTheme.primaryButton("OK");
        ok.addActionListener(ev -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(ok);

        root.add(badge, BorderLayout.NORTH);
        root.add(text, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setSize(560, 210);
        dialog.setLocationRelativeTo(this);
        WindowsWindowStyler.apply(dialog);
        dialog.setVisible(true);
    }
}
