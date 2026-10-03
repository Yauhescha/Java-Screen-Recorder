# Changelog

## 0.11.1

- Fixed portable self-update extraction for jpackage runtime ZIPs.
- Release ZIPs now contain file entries only, avoiding directory-entry incompatibilities with older updater builds.
- Updater extraction now tolerates directory entries without trailing slashes and reports clear unpack/install errors.
- Added retry logic when replacing the running application directory.
- Replaced remaining Swing JOptionPane dialogs with readable dark themed dialogs.
- Startup, update, recovery, monitor selection, low-disk, exit and library confirmation dialogs now use consistent colors and readable text.
- Update errors now include a friendly summary plus technical details.

## 0.11.0

### Capture / smoothness
- Added automatic Desktop Duplication (`ddagrab`) GPU capture for single-monitor, multi-monitor, and compatible region capture when NVENC is used; multi-monitor composition falls back automatically if a DXGI output cannot be opened.
- Automatic fallback to the previous GDI capture path if Desktop Duplication cannot start.
- Reduced raw screen queues from 1024 frames to 8 frames to avoid stale-frame buffering and multi-gigabyte memory growth.
- Added wall-clock input timestamps and constant-frame-rate output pacing to reduce jerky playback and cursor jumps.
- Reduced NVENC real-time workload (p4/p5, single pass, no look-ahead).
- Reduced webcam/audio input queue sizes and webcam preview allocations.
- Limited bundled JVM heap to 512 MB in the packaged application.

### Resizable region
- The capture frame now has an actual transparent/click-through hole instead of covering the selected desktop area.
- Added Pause/Resume, Stop and Close-frame controls directly to the capture frame while recording.
- The region can be moved while recording. The active FFmpeg segment is safely restarted at the new coordinates and final output is concatenated through the existing crash-safe segment pipeline.
- The capture frame is excluded from Windows capture with `WDA_EXCLUDEFROMCAPTURE` during region recording.
- Region coordinates use the native Win32 window rectangle to account for 125%/150% DPI scaling when calculating the actual capture rectangle.

### Webcam
- Webcam resize now keeps a 16:9 aspect ratio.
- Changing monitor sets/capture bounds no longer makes the preview jump to another monitor; the physical preview position is preserved where possible.
- Moving a region during recording moves the webcam preview with that region while preserving its relative placement in the video.
- Reduced webcam mask/shadow source FPS to the webcam FPS rather than the full recording FPS.

### UI / errors
- Stronger visual differences for normal, hovered, pressed, selected and disabled buttons.
- Custom high-contrast checkbox icons and selected-state text.
- Replaced light Swing error/warning popups with dark application-styled dialogs.
- Microphone startup failures now show a concise user-facing explanation rather than a raw Java Sound PCM format error.

## 0.10.0
- Automatic update channel and release packaging infrastructure.
