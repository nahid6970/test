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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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
            if (!isReplaying && touchActions.isNotEmpty()) {
                replayActions()
            } else if (touchActions.isEmpty()) {
                Toast.makeText(this, "No actions recorded!", Toast.LENGTH_SHORT).show()
            }
        }

        windowManager.addView(overlayView, params)
        makeDraggable(overlayView!!, params)
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
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        
        startTouchCapture()
    }

    private fun stopRecording() {
        isRecording = false
        recordButton.setImageResource(R.drawable.ic_record)
        replayButton.isEnabled = true
        Toast.makeText(this, "${touchActions.size} actions recorded", Toast.LENGTH_SHORT).show()
    }

    private fun startTouchCapture() {
        // This captures touch events on the overlay itself
        // For system-wide touch capture, you'd need accessibility service
        overlayView?.setOnTouchListener { _, event ->
            if (isRecording && event.action != MotionEvent.ACTION_MOVE) {
                val timestamp = System.currentTimeMillis() - recordingStartTime
                touchActions.add(TouchAction(event.action, event.rawX, event.rawY, timestamp))
            }
            false
        }
    }

    private fun replayActions() {
        isReplaying = true
        recordButton.isEnabled = false
        replayButton.isEnabled = false
        Toast.makeText(this, "Replaying...", Toast.LENGTH_SHORT).show()

        var previousTimestamp = 0L
        
        touchActions.forEach { action ->
            val delay = action.timestamp - previousTimestamp
            previousTimestamp = action.timestamp
            
            handler.postDelayed({
                // Visual feedback for replay
                Toast.makeText(this, "Touch at (${action.x.toInt()}, ${action.y.toInt()})", Toast.LENGTH_SHORT).show()
            }, delay)
        }

        handler.postDelayed({
            isReplaying = false
            recordButton.isEnabled = true
            replayButton.isEnabled = true
            Toast.makeText(this, "Replay completed", Toast.LENGTH_SHORT).show()
        }, previousTimestamp + 100)
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
    }
}
