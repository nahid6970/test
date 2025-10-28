package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class TouchReplayAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "TouchReplayAccessibilityService"
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle accessibility events for this service
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
        AccessibilityTouchDispatcher.clearAccessibilityService()
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        AccessibilityTouchDispatcher.setAccessibilityService(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility service destroyed")
        AccessibilityTouchDispatcher.clearAccessibilityService()
    }
}