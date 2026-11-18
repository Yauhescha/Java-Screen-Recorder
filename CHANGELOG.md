# Changelog

## 0.11.5

- Fixed multi-second frozen frames at the beginning of Desktop Duplication/NVENC recordings by normalizing DDA timestamps from frame sequence numbers.
- Recording now stays in STARTING until FFmpeg has emitted stable encoded video frames (up to 10 seconds), instead of declaring REC while the encoder is still initializing.
- Added asynchronous DDA -> NVENC pre-warm after NVENC detection to move expensive first-session initialization out of the recording start.
- CPU fallback now keeps Desktop Duplication enabled instead of forcing GDI capture.

## 0.11.4
- Fixed visibly jerky recording on laptops that fall back from NVENC to CPU H.264.
- Desktop Duplication capture is now used independently of NVENC and can feed CPU x264 through a D3D11 -> CPU download.
- Removed the redundant output `-r`/CFR conversion that could manufacture duplicate frames.
- Removed the GDI wall-clock timestamp override and preserve capture-source timing.
- CPU x264 now uses real-time presets (`veryfast` at lower FPS, `superfast` at 50+ FPS) plus `zerolatency`.

## 0.11.3

### NVENC / mixed-audio startup reliability

- `Auto (NVENC if available)` now chooses NVENC only after the one-frame hardware probe actually succeeds. Merely having `h264_nvenc` listed in FFmpeg is no longer enough.
- Detects `Cannot load nvcuda.dll` explicitly and reports that the NVIDIA driver/GPU path is unavailable; Auto immediately selects CPU H.264.
- FFmpeg startup is monitored for encoder/capture fatal errors for up to two seconds instead of declaring success after a fixed 350 ms delay.
- If NVENC is selected manually and fails during real startup, the recorder detects the failure before entering a healthy REC state and automatically retries with CPU H.264.
- Encoder failures skip the pointless Desktop-Duplication-to-GDI retry and go directly to CPU fallback. Capture-backend failures still retry GDI normally.
- Verified the `48 kHz stereo system audio + 48 kHz mono microphone -> amix -> AAC` filter graph independently; the reported second-laptop failure was caused by NVENC initialization, not by microphone amplitude or `amix`.

## 0.11.2

### Self-update reliability

- Replaced in-place ZIP replacement as the public update transport with a small external `JavaScreenRecorderUpdater-<version>.exe` bootstrapper.
- `update.json` remains backward compatible with 0.10.x: old builds see the helper EXE as the package, verify its SHA-256, launch it, and exit.
- The helper immediately moves its working directory out of the recorder folder before replacing files. This fixes Windows `Move-Item` / "folder is being used by another application" failures caused by the updater inheriting the recorder directory as its current working directory.
- The helper resolves the current recorder folder from the parent process, with a current-directory fallback for older portable builds.
- Added retry/wait logic for processes and antivirus/file locks.
- Update ZIP is still the normal user-facing download asset. The helper downloads and verifies that ZIP using `payloadUrl` + `payloadSha256` from `update.json`.
- The helper restores the old folder if replacement fails and shows a readable dark error window with technical details.
- New recorder builds also pass the exact update target and parent PID to the helper and launch updater processes from `%TEMP%`.

### Dialog readability

- Increased contrast in the shared dark dialog component.
- Error/message text is forced to white on a dark panel.
- Technical details remain scrollable and monospace.
- Increased default dialog size so long errors are not clipped.
- All application error/confirmation flows continue to use the shared dark dialog implementation rather than `JOptionPane`.

### Release assets

Each release now contains:

- `JavaScreenRecorder-<version>-win-x64.zip` — application package for users.
- `JavaScreenRecorderUpdater-<version>.exe` — small automatic-update bootstrapper.
- `update.json` — update metadata and hashes for both files.

### Build pipeline fix
- Fixed Windows PowerShell release ZIP creation failing on `TrimStart('\\', '/')`.
- `update.json` is now written as UTF-8 without BOM.
