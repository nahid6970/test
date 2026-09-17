# 1. Project DNA (Permanent)

Android Kotlin/Jetpack Compose app with a Python Flask sync server, organized around persisted per-folder sync profiles. The app synchronizes selected Android and PC folders in one-way directions while supporting filtering, conflict handling, and progress reporting.

# 2. Latest Implementation

- `app/src/main/java/com/example/foldersync/SyncFolder.kt`: Added persisted `ComparisonMethod` (`FAST` or `FULL_HASH`), defaulting to `FAST`.
- `app/src/main/java/com/example/foldersync/MainActivity.kt`: Added comparison-method controls to Advanced Sync Settings and migration fallback for old profiles.
- `app/src/main/java/com/example/foldersync/SyncActivity.kt`: Added initial full-hash scanning for both directions; matching content is skipped even when timestamps differ. Existing size/date comparison remains the default.

# 3. Critical Context

- `FULL_HASH` is the old, slower method: Android files are hashed locally and PC hashes come from the existing Flask `/api/hash-files` endpoint.
- Hash comparison is used when the folder’s direction mode is `MIRROR`; other sync modes retain their existing behavior.
- The canonical `C:\@delta\ms1\flask\5016_Sync_w_android\sync_server.py` was inspected and not changed because its hash endpoint already supports this feature.

# 4. Pending Task

Build/install the Android app and verify both comparison options with an existing large folder.
