# 1. Project DNA (Permanent)

Android Kotlin/Jetpack Compose app with a Python Flask sync server and persisted per-folder sync profiles. It synchronizes selected Android/PC folders with filtering, conflict handling, and progress reporting.

# 2. Latest Implementation

- `app/src/main/java/com/example/foldersync/SyncFolder.kt`: Added persisted `ComparisonMethod` (`FAST` or `FULL_HASH`), defaulting to `FAST`.
- `app/src/main/java/com/example/foldersync/MainActivity.kt`: Added comparison-method controls and smooth single-line horizontally scrolling marquee rows for long Android/PC paths.
- `app/src/main/java/com/example/foldersync/SyncActivity.kt`: Added initial full-hash scanning for both directions; matching content is skipped even when timestamps differ. Existing size/date comparison remains the default. Sync Progress now auto-scrolls when the active folder changes.
- `.gitignore`: Added Android/Gradle generated-folder and APK/AAB rules.
- `smoke_test_marquee.html`: Added a standalone marquee visual test.

# 3. Critical Context

- `FULL_HASH` is the old, slower method: Android files are hashed locally and PC hashes come from the existing Flask `/api/hash-files` endpoint.
- Hash comparison is used when the folder’s direction mode is `MIRROR`; other sync modes retain their existing behavior.
- The canonical `C:\@delta\ms1\flask\5016_Sync_w_android\sync_server.py` was inspected and not changed because its hash endpoint already supports this feature.
- No explicit bandwidth cap was found; transfers are sequential, uploads stage files in cache, and downloads write through SAF/DocumentFile.
- `./gradlew.bat assembleDebug --offline --no-daemon` succeeds; generated APK is `app/build/outputs/apk/debug/app-debug.apk`.
- Auto-scroll is keyed only to the active folder index, avoiding repeated scroll animations during file-level progress updates.

# 4. Pending Task

Install the APK and verify marquee/active-folder scrolling; then test full-hash sync on a large existing folder.
