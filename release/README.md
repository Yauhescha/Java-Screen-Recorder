# GitHub Releases update channel

The application is preconfigured to read the stable manifest URL:

`https://github.com/Yauhescha/Java-Screen-Recorder/releases/latest/download/update.json`

Public releases contain two assets:

- `JavaScreenRecorder-<version>-win-x64.zip` — the self-contained app, JRE included.
- `update.json` — version, ZIP URL and SHA-256.

The GitHub Actions workflow runs on every push to `main`. Every commit builds the Windows ZIP and generates the matching `update.json`, then stores both as an Actions artifact. This does **not** notify end users.

To publish an update to users, bump the version in `pom.xml` and `AppVersion.java`, commit it, then push a matching tag, for example:

```text
git tag v0.10.0
git push origin v0.10.0
```

The tag build automatically creates/updates the GitHub Release and uploads the ZIP plus `update.json`. Because the application reads `releases/latest/download/update.json`, no server and no manual SHA-256 editing are required.

Code signing is optional. Without a certificate the application and updater still work, but Windows may show an Unknown Publisher / SmartScreen warning. If `JSR_SIGN_PFX_BASE64` and `JSR_SIGN_PFX_PASSWORD` GitHub secrets are configured, the launcher in the ZIP is signed during CI.
