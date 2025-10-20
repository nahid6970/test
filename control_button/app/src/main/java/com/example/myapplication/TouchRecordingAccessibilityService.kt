package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class TouchRecordingAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for touch recording
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    companion object {
        private var instance: TouchRecordingAccessibilityService? = null
        
        fun getInstance(): TouchRecordingAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun performTouch(x: Float, y: Float, duration: Long = 50) {
        val path = Path()
        path.moveTo(x, y)
        
        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
        gestureBuilder.addStroke(strokeDescription)
        
        dispatchGesture(gestureBuilder.build(), null, null)
    }
}
