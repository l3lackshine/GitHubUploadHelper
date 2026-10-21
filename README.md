# GitHub Upload Helper (Android)

Android app starter project that can be built into a debug APK by GitHub Actions.

## Features
- Select a ZIP archive from Android storage.
- Extract and validate ZIP entries in app memory (no shell commands required).
- Show the extracted file list.
- Check GitHub token, repository, and branch.
- Upload all files in the ZIP while preserving their paths (optional destination prefix).
- Thai troubleshooting guide for 401/403/404/409/network errors.

## Build APK on GitHub
1. Create a GitHub repository and upload the contents of this folder, including `.github/workflows`.
2. Open **Actions** and run **Build Android APK**, or push to `main`/`master`.
3. Download artifact `GitHubUploadHelper-debug-apk` from the successful run.
4. Extract the artifact ZIP and install `app-debug.apk`.

## Token
Use a fine-grained Personal Access Token, select the target repository, and grant **Contents: Read and write**. Token is sent directly to `api.github.com` over HTTPS; it is not sent to a developer server. Never commit/share it.

## Limits / limitations
- ZIP is extracted in memory, max 500 files and 150 MB uncompressed.
- Uploads files individually, not as a single atomic commit.
- Existing files are not overwritten automatically yet (GitHub requires existing SHA).
- Large files may exceed GitHub Contents API limits.
- Debug APK is not a Play Store release build.
