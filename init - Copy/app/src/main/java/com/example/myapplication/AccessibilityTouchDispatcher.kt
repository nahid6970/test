package com.example.myapplication

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log

object AccessibilityTouchDispatcher {
    private const val TAG = "AccessibilityTouchDispatcher"
    private var accessibilityService: TouchReplayAccessibilityService? = null
    
    fun setAccessibilityService(service: TouchReplayAccessibilityService) {
        accessibilityService = service
        Log.d(TAG, "Accessibility service set")
    }
    
    fun dispatchGesture(x: Float, y: Float) {
        val service = accessibilityService ?: run {
            Log.e(TAG, "Accessibility service not available")
            return
        }
        
        try {
            val path = Path()
            path.moveTo(x, y)
            
            val gestureBuilder = GestureDescription.Builder()
            // Increase the duration to make the touch more noticeable
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, 200)
            gestureBuilder.addStroke(strokeDescription)
            
            val gesture = gestureBuilder.build()
            
            // Simple dispatch without callback for now
            service.dispatchGesture(gesture, null, null)
            
            Log.d(TAG, "Dispatched gesture at ($x, $y)")
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching gesture at ($x, $y)", e)
        }
    }
    
    fun clearAccessibilityService() {
        accessibilityService = null
        Log.d(TAG, "Accessibility service cleared")
    }
}