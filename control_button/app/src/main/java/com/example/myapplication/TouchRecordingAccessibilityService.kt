package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class TouchRecordingAccessibilityService : AccessibilityService() {

    private var touchCallback: ((x: Float, y: Float, action: Int, timestamp: Long) -> Unit)? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for touch recording
    }

    override fun onInterrupt() {
        Log.d("TouchRecorder", "Accessibility service interrupted")
    }

    companion object {
        private var instance: TouchRecordingAccessibilityService? = null
        
        fun getInstance(): TouchRecordingAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("TouchRecorder", "Accessibility service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("TouchRecorder", "Accessibility service connected")
        Toast.makeText(this, "✅ Touch Recorder service enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d("TouchRecorder", "Accessibility service destroyed")
    }

    fun setTouchCallback(callback: ((x: Float, y: Float, action: Int, timestamp: Long) -> Unit)?) {
        touchCallback = callback
    }

    fun performTouch(x: Float, y: Float, duration: Long = 100) {
        try {
            val path = Path()
            path.moveTo(x, y)
            
            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(strokeDescription)
            
            val result = dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d("TouchRecorder", "Touch performed at ($x, $y)")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e("TouchRecorder", "Touch cancelled at ($x, $y)")
                }
            }, null)
            
            if (!result) {
                Log.e("TouchRecorder", "Failed to dispatch gesture at ($x, $y)")
            }
        } catch (e: Exception) {
            Log.e("TouchRecorder", "Error performing touch: ${e.message}")
        }
    }
}
