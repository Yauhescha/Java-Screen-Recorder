# Java Screen Recorder

A Windows desktop screen recorder built with Java, Swing and FFmpeg. Record a monitor, multiple displays, a selected window or a custom region, with system audio, microphone input and a movable webcam overlay.

[Download the latest release](https://github.com/Yauhescha/Java-Screen-Recorder/releases/latest) · [Changelog](CHANGELOG.md) · [Roadmap](ROADMAP.md)

## Features

- Desktop, monitor, region and selected-window capture.
- Mixed-DPI and multi-monitor handling.
- H.264 CPU encoding and NVIDIA NVENC where available.
- MP4, MKV and MOV output.
- System audio and microphone recording, mixed or on separate tracks.
- Webcam overlay with positioning, resizing, mirroring and appearance options.
- Global hotkeys, tray controls and a media library.
- Recovery of interrupted recordings, disk-space checks and recording statistics.
- GitHub Releases update channel with package hash verification.

## Run a release

Download and extract the Windows x64 portable ZIP, then launch `JavaScreenRecorder.exe`. The packaged application includes a Java runtime; a separate Java installation is not required for that distribution.

## Build from source

Use Windows x64 with JDK 17+, Maven and PowerShell available. Run from the repository root:

```bat
build-portable.bat
```

The portable application is created under `dist\JavaScreenRecorder`. For the release package and updater files:

```bat
build-release.bat
```

Release output is written to `release-out`. Use the generated versioned filenames rather than renaming files from an older release. The version in `pom.xml` is currently **0.11.2**.

For a Java-only development build:

```sh
mvn package
```

The resulting executable shaded JAR is in `target/`. Windows capture/audio helpers and FFmpeg are still needed for the corresponding recording features; the portable packaging scripts prepare the application distribution.

## Updates and release maintenance

The update channel reads `update.json` from the latest GitHub release. The 0.11.2 packaging flow uses an external updater helper and a separate portable payload so the running application can exit before its directory is replaced.

The workflow in `.github/workflows/build-windows.yml` builds the Windows distribution and publishes versioned release assets. Keep the application version, generated manifest and uploaded assets consistent.

## Implementation

`src/main/java/com/example/screenrecorder/` contains the Swing UI, recording session management, FFmpeg integration, audio sources, overlays and update support. Native integration uses JNA and the bundled Windows helpers.

Capture capabilities depend on Windows, the selected encoder and device drivers. For FFmpeg redistribution details, see [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt).
