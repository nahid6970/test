# Floating Clock Overlay Implementation Guide

This guide explains how to implement a floating real-time clock overlay with root-based time adjustment, as seen in this project.

## 1. Required Permissions
Add these to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

## 2. Manifest Service Declaration
The service must be declared as a foreground service:

```xml
<service
    android:name=".OverlayService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Clock overlay display" />
</service>
```

## 3. Core Components

### A. WindowManager Setup
To display over other apps, use `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` (for API 26+) or `TYPE_PHONE` (for older).

```kotlin
val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    layoutType,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
    PixelFormat.TRANSLUCENT
)
```

### B. Root Time Adjustment (Optional)
If the device is rooted, you can change the system time directly:

```kotlin
private fun runRootCommand(command: String): Boolean {
    return try {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        os.writeBytes("$command\n")
        os.writeBytes("exit\n")
        os.flush()
        process.waitFor() == 0
    } catch (e: Exception) { false }
}

// Example: Set date
// Format: yyyyMMdd.HHmmss
runRootCommand("date -s \"20231027.123000\"")

// Example: Enable automatic time
runRootCommand("settings put global auto_time 1")
```

### C. Dragging Logic
Implement `View.OnTouchListener` to update `params.x` and `params.y` via `windowManager.updateViewLayout`.

## 4. Layout Tips
*   Use a high `elevation` for the root layout.
*   Keep the UI compact to avoid blocking content.
*   Use `background` with a rounded drawable for a modern floating look.

## 5. Implementation Steps
1.  **Request Permission**: Use `Settings.canDrawOverlays(context)` to check and `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` to request.
2.  **Start Service**: Use `startForegroundService` to keep the overlay alive.
3.  **Inflate View**: Use `LayoutInflater` to create the view from XML and add it to `WindowManager`.
4.  **Update Loop**: Use a `Handler` with `postDelayed` for real-time clock updates.
