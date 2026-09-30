# Changelog

## 0.10.0 — GitHub ZIP updates

### Portable ZIP updater
- Auto-update now supports the same self-contained ZIP that is published in GitHub Releases; an installer EXE is no longer required.
- Update packages are SHA-256 verified before they are applied.
- The running application exits, a detached Windows updater replaces the application directory, then launches the new version.
- Failed replacement attempts roll back to the previous application directory.
- Legacy `installerUrl` manifests remain readable for compatibility.

### GitHub release channel
- Default update manifest is now `https://github.com/Yauhescha/Java-Screen-Recorder/releases/latest/download/update.json`.
- Added `scripts/build-release-package.ps1` to build the portable ZIP and manifest together.
- `scripts/make-update-manifest.ps1` now reads the project version automatically and generates the GitHub Release package URL + SHA-256.
- GitHub Actions builds ZIP + `update.json` on every commit and stores them as a CI artifact.
- A matching `v<version>` tag automatically publishes both files to GitHub Releases.

### Signing
- Code signing is now optional for normal release ZIPs.
- If signing secrets/certificate are present, the launcher is signed automatically.
- Added `build-signed-release.bat` for releases where a trusted signature is explicitly required.

## 0.9.0 — Release preparation
- Added first updater, jpackage installer pipeline, signing pipeline, About/version information and release UI polish.
