# Java Screen Recorder 0.11.1


## 0.11.1 updater compatibility fix

- Release ZIPs are generated with file entries only so older 0.10.x/0.11.0 updaters can unpack the bundled jpackage runtime correctly.
- The in-app updater also has a tolerant ZIP extractor for future releases.
- All application message/confirmation/error dialogs use the dark theme with readable foreground colors.
- Failed update replacement is retried and, on future failures, the restored application shows the technical reason on next launch.

Windows screen recorder written in Java 17/Swing with FFmpeg, WASAPI and Windows-native capture helpers.

## Main features

- Full desktop, one/multiple monitors, resizable region and selected-window capture.
- Mixed-DPI / multi-monitor handling.
- H.264 CPU and NVIDIA NVENC encoding.
- MP4 / MKV / MOV output.
- System audio + microphone, mixed or separate audio tracks.
- Webcam overlay with live preview, drag/resize, mirror, shapes, border and shadow.
- Cursor visibility, highlight and click effects where supported by the capture mode.
- Global hotkeys, tray icon, media library and crash recovery.
- Free-space warnings, dropped-frame statistics and device disconnect handling.
- Self-contained Windows application with bundled JRE and embedded FFmpeg fallback.
- GitHub Releases ZIP auto-update channel.

## Normal local build

Requires JDK 17+, Maven and PowerShell:

```bat
build-portable.bat
```

Result:

```text
dist\JavaScreenRecorder\JavaScreenRecorder.exe
```

The JRE is bundled. End users do not need Java installed.

## Release ZIP

For the package that should be uploaded to GitHub Releases:

```bat
build-release.bat
```

This creates:

```text
release-out\JavaScreenRecorder-0.10.0-win-x64.zip
release-out\update.json
```

The ZIP contains the complete `JavaScreenRecorder` app image, including the private JRE. FFmpeg/FFprobe remain embedded in the application and are extracted automatically when required.

## Automatic updates from GitHub

The application is already configured to check:

```text
https://github.com/Yauhescha/Java-Screen-Recorder/releases/latest/download/update.json
```

The public release should contain both:

```text
JavaScreenRecorder-0.10.0-win-x64.zip
update.json
```

`update.json` looks like:

```json
{
  "version": "0.10.0",
  "packageUrl": "https://github.com/Yauhescha/Java-Screen-Recorder/releases/download/v0.10.0/JavaScreenRecorder-0.10.0-win-x64.zip",
  "packageType": "zip",
  "sha256": "...",
  "entryExe": "JavaScreenRecorder/JavaScreenRecorder.exe",
  "notes": "Release notes"
}
```

The updater downloads the ZIP, verifies SHA-256, exits the recorder, replaces the portable application directory and starts the new version. If replacement fails, the updater restores the previous directory.

## GitHub Actions release flow

`.github/workflows/build-windows.yml` runs on every push to `main`.

Every commit:

1. reads the version from `pom.xml`;
2. builds the self-contained Windows app;
3. creates the release ZIP;
4. calculates SHA-256;
5. generates the matching `update.json`;
6. uploads ZIP + JSON as a GitHub Actions artifact.

Every push to `main` also creates or updates GitHub Release `v<version from pom.xml>` and uploads the ZIP plus `update.json`. The workflow synchronizes `AppVersion.java` from `pom.xml`, so `pom.xml` is the release version source of truth. To publish a new user-visible update, bump `<version>` in `pom.xml` and push to `main`. Installed copies discover the latest manifest through the stable `releases/latest/download/update.json` URL.

## Code signing

A certificate is **not required** for the program, GitHub Releases, or automatic updates to function.

Without a trusted Authenticode certificate Windows may display **Unknown publisher** / SmartScreen warnings, especially on fresh downloads. Signing is therefore recommended for a commercial/public release, but it is optional technically.

If you later obtain a certificate, local builds can use:

```bat
set JSR_SIGN_PFX=C:\certs\code-signing.pfx
set JSR_SIGN_PFX_PASSWORD=password
build-signed-release.bat
```

GitHub Actions can use these repository secrets:

```text
JSR_SIGN_PFX_BASE64
JSR_SIGN_PFX_PASSWORD
```

If the secrets are absent, CI simply publishes an unsigned ZIP.

## Optional installer

The older `jpackage + WiX` installer pipeline is still available through `build-windows.bat`, but it is no longer required for the GitHub ZIP release/update flow.

## FFmpeg licensing

Review `THIRD_PARTY_NOTICES.txt` and the license of the exact FFmpeg build before public or commercial redistribution.

## 0.11.0 performance and capture changes

When NVIDIA NVENC is selected, monitor capture and regions fully contained on a monitor now prefer FFmpeg Desktop Duplication (`ddagrab`). A single-monitor/region path can keep frames on the GPU through NVENC. Multiple monitors use one Desktop Duplication input per output and then compose them; if Desktop Duplication cannot start on a particular PC/session, the recorder automatically retries with the compatible GDI capture backend.

The screen input queue is intentionally kept very small. A raw 2560x1600 BGRA frame is roughly 16 MiB, so large FFmpeg packet queues are inappropriate for real-time screen recording and can cause very high RAM use and delayed/stale frames.

For a resizable region, the capture frame can be moved during recording. The recorder closes the current recoverable MKV segment and starts another at the new coordinates, then losslessly finalizes the segments together on Stop.

## 0.11.2 update bootstrapper

Public releases contain the normal portable application ZIP plus a small updater helper EXE. Users still download the ZIP manually. Automatic updates use the helper because it runs outside the recorder's application directory and can safely replace that directory after the old process exits.

This also provides a compatibility bridge for 0.10.x releases: the old application understands the `packageUrl`, `packageType` and `sha256` fields and therefore can launch the new external helper without first being manually upgraded.

A release contains:

```text
JavaScreenRecorder-0.11.2-win-x64.zip
JavaScreenRecorderUpdater-0.11.2.exe
update.json
```

The manifest points `packageUrl` at the updater helper and contains `payloadUrl` / `payloadSha256` for the actual application ZIP.
