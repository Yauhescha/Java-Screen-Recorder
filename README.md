# Java Screen Recorder 0.10.0

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

This intentionally does **not** publish an update to users on every commit.

To release a version, make sure `pom.xml` and `AppVersion.VERSION` contain the same version, then push a matching tag:

```bash
git tag v0.10.0
git push origin v0.10.0
```

The tag workflow automatically creates the GitHub Release, uploads the ZIP and `update.json`, and marks it latest. Installed copies then discover that manifest through the stable `releases/latest/download/update.json` URL.

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

## GitHub Actions publication behavior

Every push to `main` builds a Windows ZIP plus `update.json` and uploads them as a GitHub Actions artifact. A normal commit does **not** create a public GitHub Release.

To publish an update to users, push a tag matching the version in `pom.xml`, for example:

```bash
git tag v0.10.0
git push origin v0.10.0
```

That tagged run creates/updates the public GitHub Release and uploads both `JavaScreenRecorder-0.10.0-win-x64.zip` and `update.json`. Installed copies use the stable `releases/latest/download/update.json` URL.

Code signing remains optional. If signing secrets are absent, CI now builds an unsigned package instead of failing workflow validation.
