# GitHub Upload Helper Android — v3.1

Android helper for syncing ZIP project files to GitHub via GitHub REST API.

## v3.1 updates
- Compact, collapsible CMD-style console so it no longer consumes the screen or blocks access to lower controls.
- Persists a project workspace inside the app's private files directory, separated by Owner/Repository.
- Creates the workspace when missing; extracts the selected ZIP into it and keeps it for the next upload.
- Compares incoming files against the existing local workspace using SHA-256 and reports added, changed, unchanged, and removed files. Removed files are not deleted automatically.
- Upload mode sends only files that are new or changed relative to the previous local workspace; users should extract/compare the ZIP before uploading.
- Version name 3.1; Java and Kotlin target JVM 17.

## Security and behavior
- Owner, Repository and Branch are saved locally; the token is not saved in SharedPreferences.
- Fine-grained PAT typically needs Contents: Read and write. Editing `.github/workflows` may require Workflows: Read and write.
- File deletion remains a separate explicitly confirmed action.
- The local workspace is private app storage and is removed if the app's data is cleared or the app is uninstalled.

## Build
- JDK 17
- Gradle 8.9
- Android SDK compileSdk 35
- `gradle assembleDebug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

This source has not been built in this environment; run GitHub Actions or `gradle assembleDebug` to validate the APK.
