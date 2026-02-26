# Convex Files Android App Setup

## Project Info
- **Convex Backend**: `C:\@delta\db\@Convex\myFiles`
- **Convex URL**: `https://good-basilisk-52.convex.cloud`
- **Base Template**: `C:\@delta\test\1init`
- **Project Location**: `C:\@delta\test\android-convex-explorer`

This app connects to your Convex myFiles backend to list and delete files with a simple Google Drive-like interface.

---

## Steps to Create from Base Template

### 1. Update app/build.gradle.kts
- Change `namespace` to `"com.convexfiles"`
- Change `applicationId` to `"com.convexfiles"`
- Add dependencies:
  - `androidx.recyclerview:recyclerview:1.3.2`
  - `com.squareup.okhttp3:okhttp:4.12.0`
  - `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`
  - `com.google.code.gson:gson:2.10.1`

### 2. Update AndroidManifest.xml
- Add `package="com.convexfiles"` attribute
- Add `<uses-permission android:name="android.permission.INTERNET" />`
- Change app label to `"My Files"`
- Set theme to `@style/Theme.AppCompat.Light`

### 3. Create Layouts
- **activity_main.xml**: LinearLayout with RecyclerView (id: recyclerView)
- **item_file.xml**: File list item with icon, filename, and file info TextViews

### 4. Create Kotlin Files in java/com/convexfiles/
- **FileItem.kt**: Data class with id, filename, fileType, fileSize, storageId, url
- **ConvexClient.kt**: HTTP client for Convex API (listFiles, deleteFile methods)
- **MainActivity.kt**: Main activity with RecyclerView, loads files on start
- **FileAdapter.kt**: RecyclerView adapter, tap to open, long press to delete

### 5. Fix Gradle Wrapper
Run: `sed -i 's/\r$//' gradlew` to fix line endings for Linux/Colab builds

### 6. Update .gitignore
Add: `!gradle/wrapper/gradle-wrapper.jar` to keep wrapper JAR in git

### 7. Build
```bash
./gradlew assembleDebug
```

## Features
- 📋 List all files from Convex backend
- 👆 Tap to open file in browser
- 🗑️ Long press to delete file
- 🎨 File type icons (images, videos, PDFs, etc.)
- 📏 File size display

## Convex Backend Functions Used
- `files:list` - Get all files
- `files:remove` - Delete file by ID
