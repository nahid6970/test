# Job Circular Overlay - Mark Complete App

## Project Overview
An Android overlay application that floats on top of job circular apps, allowing users to mark jobs as complete with green checkmarks. Uses OCR to identify and track jobs by their text content (Bengali + English support).

## Problem Statement
- Job circular apps show daily job updates mixed with old jobs
- Difficult to identify which jobs have already been reviewed
- Need a way to mark jobs as "seen/complete" that persists across sessions

## Solution
Android overlay app with:
- Floating transparent overlay on top of job circular app
- OCR-based job identification (Bengali + English)
- Tap to mark jobs complete with green checkmark
- Persistent storage of marked jobs
- Checkmarks follow jobs even if list reorders

## Technical Architecture

### Platform
- **Target**: Android (API 23+)
- **Language**: Kotlin
- **Build System**: Gradle

### Core Components

#### 1. Overlay Service
- `SYSTEM_ALERT_WINDOW` permission for overlay
- Floating window using `WindowManager`
- Transparent background with touch passthrough for unmarked areas
- Intercepts taps only on checkmark positions

#### 2. OCR Engine
- **Library**: Google ML Kit Text Recognition v2
- **Languages**: Bengali (ben) + English (en)
- **Process**:
  - Capture screenshot of visible area
  - Run OCR on job list items
  - Extract job titles and positions
  - Match against stored marked jobs

#### 3. Data Storage
- **Method**: SharedPreferences or Room Database
- **Stored Data**:
  - Job text hash (for matching)
  - Original job text (for verification)
  - Timestamp marked
  - Last seen position (optimization)

#### 4. UI Components
- Checkmark drawable (green circle with white check)
- Semi-transparent overlay canvas
- Settings activity for configuration

### App Flow

```
1. User starts Overlay App
   ↓
2. Grant overlay permission (if needed)
   ↓
3. Service starts, overlay created
   ↓
4. User opens Job Circular app
   ↓
5. Overlay detects screen content
   ↓
6. OCR scans visible jobs
   ↓
7. Match jobs against marked database
   ↓
8. Draw checkmarks on marked jobs
   ↓
9. User taps unmarked job
   ↓
10. OCR identifies job at tap position
    ↓
11. Save to database, draw checkmark
    ↓
12. Repeat 6-11 as user scrolls
```

### Key Features

#### Phase 1 (MVP)
- [x] Overlay permission handling
- [x] Floating overlay window
- [x] Screenshot capture
- [x] OCR text extraction (Bengali + English)
- [x] Tap detection on overlay
- [x] Draw checkmarks at positions
- [x] Persist marked jobs
- [x] Match jobs by text content

#### Phase 2 (Enhancements)
- [ ] Settings: Checkmark size/color
- [ ] Clear all marks option
- [ ] Export/import marked jobs
- [ ] Statistics (jobs marked per day)
- [ ] Multiple app support (not just one job circular app)

### Technical Challenges & Solutions

#### Challenge 1: OCR Performance
- **Issue**: OCR is slow, can't run on every frame
- **Solution**: 
  - Run OCR only when screen changes (scroll detection)
  - Cache OCR results for visible area
  - Use incremental OCR (only new visible items)

#### Challenge 2: Job Matching
- **Issue**: OCR may have slight variations in text
- **Solution**:
  - Use fuzzy string matching (Levenshtein distance)
  - Hash normalized text (remove extra spaces, lowercase)
  - Match threshold: 85% similarity

#### Challenge 3: Overlay Touch Handling
- **Issue**: Need to pass touches through to app below
- **Solution**:
  - Set `FLAG_NOT_TOUCHABLE` by default
  - Only intercept touches on checkmark areas
  - Use `FLAG_NOT_FOCUSABLE` to avoid stealing focus

#### Challenge 4: Bengali Text Recognition
- **Issue**: ML Kit needs proper language model
- **Solution**:
  - Use ML Kit with Bengali language pack
  - Download model on first run
  - Fallback to English if Bengali fails

### File Structure

```
JobCircularOverlay/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/joboverlay/
│   │   │   │   ├── MainActivity.kt          # Entry point, permissions
│   │   │   │   ├── OverlayService.kt        # Foreground service for overlay
│   │   │   │   ├── OverlayView.kt           # Custom view for drawing
│   │   │   │   ├── OcrManager.kt            # OCR processing
│   │   │   │   ├── JobDatabase.kt           # Data persistence
│   │   │   │   └── JobMatcher.kt            # Fuzzy matching logic
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   └── overlay_layout.xml
│   │   │   │   ├── drawable/
│   │   │   │   │   └── ic_checkmark.xml     # Green checkmark vector
│   │   │   │   └── values/
│   │   │   │       ├── strings.xml
│   │   │   │       └── colors.xml
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   └── build.gradle
└── PROJECT.md (this file)
```

### Dependencies

```gradle
dependencies {
    // ML Kit for OCR
    implementation 'com.google.mlkit:text-recognition:16.0.0'
    implementation 'com.google.mlkit:text-recognition-bengali:16.0.0'
    
    // Coroutines for async operations
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // Room for database (optional, can use SharedPreferences)
    implementation 'androidx.room:room-runtime:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'
    
    // Fuzzy string matching
    implementation 'me.xdrop:fuzzywuzzy:1.4.0'
}
```

### Permissions Required

```xml
<!-- Overlay permission -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Screenshot capture -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Optional: Internet for ML Kit model download -->
<uses-permission android:name="android.permission.INTERNET" />
```

### Data Model

```kotlin
data class MarkedJob(
    val id: String,              // Hash of normalized text
    val jobText: String,         // Original job title
    val markedAt: Long,          // Timestamp
    val lastSeenY: Int = 0       // Last Y position (optimization)
)
```

### OCR Processing Logic

```
1. Capture screenshot of current screen
2. Pass to ML Kit Text Recognition
3. Get TextBlocks with bounding boxes
4. For each TextBlock:
   - Extract text content
   - Normalize (trim, lowercase, remove extra spaces)
   - Generate hash
   - Check if hash exists in marked database
   - If exists: Draw checkmark at bounding box position
5. On user tap:
   - Find TextBlock at tap coordinates
   - Extract and normalize text
   - Save to database
   - Draw checkmark
```

### Performance Optimizations

1. **Lazy OCR**: Only run when screen content changes
2. **Viewport Culling**: Only process visible text blocks
3. **Result Caching**: Cache OCR results for 2-3 seconds
4. **Incremental Updates**: Only re-scan changed regions
5. **Background Processing**: Run OCR on background thread
6. **Bitmap Downscaling**: Reduce screenshot resolution for faster OCR

### Testing Strategy

1. **Unit Tests**:
   - JobMatcher fuzzy matching logic
   - Text normalization functions
   - Hash generation consistency

2. **Integration Tests**:
   - OCR accuracy with sample Bengali text
   - Database save/retrieve operations
   - Overlay touch event handling

3. **Manual Tests**:
   - Test with actual job circular app
   - Verify checkmarks persist after app restart
   - Test with scrolling and list reordering
   - Verify Bengali text recognition accuracy

### Known Limitations

1. **OCR Accuracy**: May misread some Bengali characters
2. **Performance**: OCR adds 200-500ms delay per scan
3. **Battery**: Continuous overlay uses more battery
4. **App-Specific**: May need adjustments for different job apps
5. **Scroll Detection**: May miss very fast scrolls

### Future Enhancements

1. **Smart Notifications**: Alert when new unmarked jobs appear
2. **Job Categories**: Group marks by job type/deadline
3. **Sync**: Cloud sync marked jobs across devices
4. **Widgets**: Quick toggle overlay on/off
5. **Accessibility**: Voice commands to mark jobs
6. **ML Improvements**: Train custom model for job circular format

### Build Instructions

```bash
# Clone repository
git clone <repo-url>
cd JobCircularOverlay

# Build debug APK
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk

# Or build and install
./gradlew installDebug
```

### Usage Instructions

1. Install and open the app
2. Grant "Display over other apps" permission
3. Tap "Start Overlay Service"
4. Open your job circular app
5. Tap on any job to mark it complete
6. Green checkmark appears on marked jobs
7. Checkmarks persist even after closing apps

### Troubleshooting

**Issue**: Checkmarks not appearing
- Solution: Ensure overlay permission is granted in Settings

**Issue**: Wrong jobs getting marked
- Solution: Improve OCR accuracy by ensuring good screen brightness

**Issue**: App crashes on start
- Solution: Check if ML Kit models are downloaded (requires internet on first run)

**Issue**: Checkmarks in wrong positions after scroll
- Solution: This is expected during scroll; they reposition after OCR completes

### License
MIT License (or specify your preferred license)

### Contributors
- Initial development: [Your Name]

### Version History
- v1.0.0 (TBD): Initial release with core features
  - Overlay functionality
  - OCR-based job tracking
  - Bengali + English support
  - Persistent storage

---

## Development Notes

### Current Status
- [ ] Project setup
- [ ] Basic overlay implementation
- [ ] OCR integration
- [ ] Database implementation
- [ ] Job matching logic
- [ ] UI polish
- [ ] Testing
- [ ] Release build

### Next Steps
1. Create Android project structure
2. Implement overlay service with permissions
3. Integrate ML Kit OCR
4. Build job matching and storage
5. Add checkmark drawing logic
6. Test with real job circular app
7. Optimize performance
8. Polish UI and add settings

---

**Last Updated**: 2026-03-13
