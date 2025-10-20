package com.example.myapplication

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var recordingOverlay: View? = null
    private lateinit var recordButton: ImageButton
    private lateinit var replayButton: ImageButton
    
    private val touchActions = mutableListOf<TouchAction>()
    private var isRecording = false
    private var recordingStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var isReplaying = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
    }

    private fun createOverlay() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_buttons, null)
        recordButton = overlayView!!.findViewById(R.id.recordButton)
        replayButton = overlayView!!.findViewById(R.id.replayButton)

        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        replayButton.setOnClickListener {
            Toast.makeText(this, "Replay button clicked! Actions: ${touchActions.size}", Toast.LENGTH_SHORT).show()
            if (!isReplaying && touchActions.isNotEmpty()) {
                replayActions()
            } else if (touchActions.isEmpty()) {
                Toast.makeText(this, "❌ No touches recorded! Tap record first.", Toast.LENGTH_LONG).show()
            } else if (isReplaying) {
                Toast.makeText(this, "Already replaying...", Toast.LENGTH_SHORT).show()
            }
        }

        windowManager.addView(overlayView, params)
        makeDraggable(overlayView!!, params)
        setupManualTouchCapture()
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun startRecording() {
        isRecording = true
        touchActions.clear()
        recordingStartTime = System.currentTimeMillis()
        recordButton.setImageResource(R.drawable.ic_stop)
        replayButton.isEnabled = false
        replayButton.alpha = 0.5f
        
        createRecordingOverlay()
        Toast.makeText(this, "🔴 Recording - Tap anywhere on screen", Toast.LENGTH_LONG).show()
    }

    private fun stopRecording() {
        isRecording = false
        recordButton.setImageResource(R.drawable.ic_record)
        replayButton.isEnabled = true
        replayButton.alpha = 1.0f
        
        removeRecordingOverlay()
        val uniqueTouches = touchActions.count { it.action == MotionEvent.ACTION_DOWN }
        Toast.makeText(this, "⏹️ Stopped - $uniqueTouches touches recorded", Toast.LENGTH_LONG).show()
    }

    private fun createRecordingOverlay() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Create a fullscreen overlay BEHIND the control buttons
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        recordingOverlay = View(this).apply {
            setBackgroundColor(0x10FF0000) // Slight red tint to show recording
            setOnTouchListener { _, event ->
                if (isRecording) {
                    val timestamp = System.currentTimeMillis() - recordingStartTime
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            touchActions.add(TouchAction(MotionEvent.ACTION_DOWN, event.rawX, event.rawY, timestamp))
                        }
                        MotionEvent.ACTION_UP -> {
                            touchActions.add(TouchAction(MotionEvent.ACTION_UP, event.rawX, event.rawY, timestamp))
                        }
                        MotionEvent.ACTION_MOVE -> {
                            touchActions.add(TouchAction(MotionEvent.ACTION_MOVE, event.rawX, event.rawY, timestamp))
                        }
                    }
                }
                false // Don't consume - let touches pass through
            }
        }

        try {
            // Add recording overlay first (behind)
            windowManager.addView(recordingOverlay, params)
            // Then re-add control buttons on top
            windowManager.removeView(overlayView)
            val controlParams = overlayView!!.layoutParams as WindowManager.LayoutParams
            windowManager.addView(overlayView, controlParams)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeRecordingOverlay() {
        recordingOverlay?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Already removed
            }
            recordingOverlay = null
        }
    }

    private fun setupManualTouchCapture() {
        // Accessibility service will capture touches system-wide
    }

    private fun replayActions() {
        if (!TouchRecordingAccessibilityService.isServiceEnabled()) {
            Toast.makeText(this, "⚠️ Enable Accessibility Service in Settings!", Toast.LENGTH_LONG).show()
            return
        }

        if (touchActions.isEmpty()) {
            Toast.makeText(this, "No touches recorded!", Toast.LENGTH_SHORT).show()
            return
        }

        isReplaying = true
        recordButton.isEnabled = false
        replayButton.isEnabled = false
        recordButton.alpha = 0.5f
        replayButton.alpha = 0.5f
        
        Toast.makeText(this, "▶️ Replaying ${touchActions.size} touches...", Toast.LENGTH_LONG).show()

        var previousTimestamp = 0L
        val accessibilityService = TouchRecordingAccessibilityService.getInstance()
        var actionCount = 0
        
        touchActions.forEach { action ->
            val delay = action.timestamp - previousTimestamp
            previousTimestamp = action.timestamp
            
            handler.postDelayed({
                when (action.action) {
                    MotionEvent.ACTION_DOWN -> {
                        accessibilityService?.performTouch(action.x, action.y, 100)
                        actionCount++
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Skip moves for now, just do taps
                    }
                }
            }, delay)
        }

        // Show completion message
        handler.postDelayed({
            isReplaying = false
            recordButton.isEnabled = true
            replayButton.isEnabled = true
            recordButton.alpha = 1.0f
            replayButton.alpha = 1.0f
            Toast.makeText(this, "✅ Replay finished! ($actionCount touches)", Toast.LENGTH_LONG).show()
        }, previousTimestamp + 500)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeRecordingOverlay()
        overlayView?.let { windowManager.removeView(it) }
    }
}
