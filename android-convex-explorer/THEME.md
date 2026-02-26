# Material 3 Design System: My Files

This document outlines the UI and theming system used in the Android Convex Explorer. It follows **Material 3 (M3)** guidelines for a modern, clean, and accessible interface.

---

## 1. Theme Configuration (`themes.xml`)
The app uses a `NoActionBar` theme to allow for a custom header. It defines specific Material 3 color roles.

### Light Theme
```xml
<style name="Theme.MyFiles" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="colorPrimary">#6750A4</item>
    <item name="colorOnPrimary">#FFFFFF</item>
    <item name="colorPrimaryContainer">#EADDFF</item>
    <item name="colorOnPrimaryContainer">#21005D</item>
    <item name="android:statusBarColor">#FFFFFF</item>
    <item name="android:windowLightStatusBar">true</item>
</style>
```

---

## 2. Component Architecture

### Modern Header (`activity_main.xml`)
Instead of a standard Toolbar, we use a vertical layout for a "Title-first" experience often seen in modern apps like Google Drive or iOS Files.

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="20dp"
    android:paddingTop="24dp"
    android:paddingBottom="16dp">

    <TextView
        android:text="My Files"
        android:textSize="28sp"
        android:textStyle="bold"
        android:textColor="?attr/colorOnSurface" />

    <TextView
        android:id="@+id/breadcrumbText"
        android:text="Root"
        android:textSize="14sp"
        android:textColor="?attr/colorOutline"
        android:layout_marginTop="4dp" />
</LinearLayout>
```

### File Cards (`item_file.xml`)
We use `MaterialCardView` with zero elevation and a subtle stroke for a "flat" but structured M3 look.

```xml
<com.google.android.material.card.MaterialCardView
    app:cardCornerRadius="16dp"
    app:cardElevation="0dp"
    app:strokeWidth="1dp"
    app:strokeColor="?attr/colorOutlineVariant">
    <!-- Content with Icon Container + Text -->
</com.google.android.material.card.MaterialCardView>
```

---

## 3. Dynamic Icon Styling Logic
The core of the "nice" look is the color-coded icons. Each file type has a specific **Primary Color** and a **Light Background Color**.

### Icon Background (`icon_bg.xml`)
```xml
<shape android:shape="oval">
    <solid android:color="#FFFFFF" /> <!-- Tinted dynamically in code -->
</shape>
```

### Style Logic (Kotlin)
This logic maps file extensions/types to specific Material color pairs.

| Type | Icon Color | Background Color |
| :--- | :--- | :--- |
| **Folder** | `#FFB300` (Amber) | `#FFF8E1` |
| **Image** | `#2E7D32` (Green) | `#E8F5E9` |
| **Video** | `#1565C0` (Blue) | `#E3F2FD` |
| **PDF** | `#C62828` (Red) | `#FFEBEE` |
| **APK** | `#3DDC84` (Android) | `#E8FAF0` |
| **Archive**| `#EF6C00` (Orange) | `#FFF3E0` |
| **Default**| `#455A64` (Grey) | `#ECEFF1` |

---

## 4. Interaction UI
*   **Dialogs:** Always use `MaterialAlertDialogBuilder` instead of the standard `AlertDialog`.
*   **Feedback:** Use `MaterialCardView`'s built-in ripple for touch feedback.
*   **Spacing:** Consistent use of 4dp/8dp/16dp/24dp (Material 8dp grid).

---

## Implementation Checklist
1. Add `com.google.android.material:material:1.10.0` or higher.
2. Update `themes.xml` to inherit from `Theme.Material3`.
3. Create `icon_bg.xml` (oval shape).
4. Port the `getIconInfo` helper function for dynamic tinting.
