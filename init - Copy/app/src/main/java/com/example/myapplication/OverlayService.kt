package com.example.myapplication

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button

class OverlayService : Service() {
    
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var isListening = false
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onCreate() {
        super.onCreate()
        
        createOverlay()
    }
    
    private fun createOverlay() {
        // Create the overlay view
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        
        // Set up the window manager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Set up layout parameters
        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        }
        
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100
        
        // Add the view to the window
        windowManager.addView(overlayView, params)
        
        // Set up button click listeners
        val recordButton = overlayView.findViewById<Button>(R.id.recordButton)
        val replayButton = overlayView.findViewById<Button>(R.id.replayButton)
        val closeButton = overlayView.findViewById<Button>(R.id.closeButton)
        
        recordButton.setOnClickListener {
            // Toggle recording touches
            if (TouchRecordingManager.isRecording()) {
                TouchRecordingManager.stopRecording()
                stopGlobalTouchListener()
                recordButton.text = "Record"
            } else {
                TouchRecordingManager.startRecording()
                startGlobalTouchListener()
                recordButton.text = "Stop Recording"
            }
        }
        
        replayButton.setOnClickListener {
            // Replay recorded touches
            TouchRecordingManager.replayTouches()
        }
        
        closeButton.setOnClickListener {
            // Close the overlay
            stopSelf()
        }
    }
    
    private fun startGlobalTouchListener() {
        if (!isListening) {
            val intent = Intent(this, GlobalTouchListenerService::class.java)
            startService(intent)
            isListening = true
        }
    }
    
    private fun stopGlobalTouchListener() {
        if (isListening) {
            val intent = Intent(this, GlobalTouchListenerService::class.java)
            stopService(intent)
            isListening = false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        stopGlobalTouchListener()
    }
}