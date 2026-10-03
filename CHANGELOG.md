# Changelog

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
