package com.example.myapplication

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.util.Log

class GlobalTouchListenerService : Service() {
    
    companion object {
        private const val TAG = "GlobalTouchListener"
    }
    
    private lateinit var windowManager: WindowManager
    private lateinit var touchView: View
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onCreate() {
        super.onCreate()
        
        createTouchOverlay()
    }
    
    private fun createTouchOverlay() {
        // Create a transparent view that covers the entire screen
        touchView = object : View(this) {
            override fun onTouchEvent(event: MotionEvent?): Boolean {
                return handleTouch(event)
            }
        }
        
        // Set up the window manager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Set up layout parameters to cover the entire screen
        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                1, // Minimal size
                1, // Minimal size
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSPARENT
            )
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams(
                1, // Minimal size
                1, // Minimal size
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSPARENT
            )
        }
        
        // Position the view off-screen
        params.gravity = Gravity.TOP or Gravity.START
        params.x = -1
        params.y = -1
        
        // Add the view to the window
        try {
            windowManager.addView(touchView, params)
            Log.d(TAG, "Global touch listener created")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating global touch listener", e)
        }
    }
    
    private fun handleTouch(event: MotionEvent?): Boolean {
        event?.let {
            when (it.action) {
                MotionEvent.ACTION_OUTSIDE -> {
                    if (TouchRecordingManager.isRecording()) {
                        // Record the touch coordinates
                        TouchRecordingManager.recordTouch(it.x, it.y)
                        // Return false to allow the touch to be handled by the underlying app
                        return false
                    }
                }
            }
        }
        // Return false to allow touches to pass through to underlying apps
        return false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::touchView.isInitialized) {
                windowManager.removeView(touchView)
            }
            Log.d(TAG, "Global touch listener destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying global touch listener", e)
        }
    }
}