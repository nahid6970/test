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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var timerText: TextView
    private lateinit var addButton: Button
    private lateinit var speedButton: Button
    
    private var targetTimeMillis = 0L
    private var remainingMillis = 0L
    private var isTimerRunning = false
    private var speedMultiplier = 1
    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdateTime = 0L

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
        timerText = overlayView!!.findViewById(R.id.timerText)
        addButton = overlayView!!.findViewById(R.id.addButton)
        speedButton = overlayView!!.findViewById(R.id.speedButton)

        addButton.setOnClickListener {
            if (isTimerRunning) {
                resetTimer()
            } else {
                showTimerInputDialog()
            }
        }

        speedButton.setOnClickListener {
            showSpeedDialog()
        }

        windowManager.addView(overlayView, params)
        makeDraggable(overlayView!!, params)
        updateDisplay()
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

    private fun showTimerInputDialog() {
        val intent = Intent(this, TimerInputActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun showSpeedDialog() {
        val intent = Intent(this, SpeedInputActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun startTimer(days: Int, hours: Int, minutes: Int) {
        val totalMillis = (days * 24 * 60 * 60 * 1000L) + 
                         (hours * 60 * 60 * 1000L) + 
                         (minutes * 60 * 1000L)
        
        targetTimeMillis = totalMillis
        remainingMillis = totalMillis
        isTimerRunning = true
        lastUpdateTime = System.currentTimeMillis()
        
        runTimer()
    }

    private fun runTimer() {
        handler.post(object : Runnable {
            override fun run() {
                if (isTimerRunning && remainingMillis > 0) {
                    val currentTime = System.currentTimeMillis()
                    val elapsed = (currentTime - lastUpdateTime) * speedMultiplier
                    lastUpdateTime = currentTime
                    
                    remainingMillis -= elapsed
                    
                    if (remainingMillis <= 0) {
                        remainingMillis = 0
                        isTimerRunning = false
                        Toast.makeText(this@OverlayService, "⏰ Timer finished!", Toast.LENGTH_LONG).show()
                    }
                    
                    updateDisplay()
                    handler.postDelayed(this, 100)
                }
            }
        })
    }

    private fun resetTimer() {
        remainingMillis = targetTimeMillis
        lastUpdateTime = System.currentTimeMillis()
        isTimerRunning = true
        runTimer()
        Toast.makeText(this, "Timer reset!", Toast.LENGTH_SHORT).show()
    }

    private fun updateDisplay() {
        if (targetTimeMillis == 0L) {
            timerText.text = "Tap + to set timer"
            speedButton.visibility = View.GONE
        } else {
            val totalSeconds = (remainingMillis / 1000).toInt()
            val days = totalSeconds / 86400
            val hours = (totalSeconds % 86400) / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            timerText.text = String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds)
            speedButton.visibility = View.VISIBLE
            speedButton.text = if (speedMultiplier == 1) "Speed" else "${speedMultiplier}x"
        }
    }

    fun setTimer(days: Int, hours: Int, minutes: Int) {
        startTimer(days, hours, minutes)
    }

    fun setSpeed(speed: Int) {
        speedMultiplier = speed
        updateDisplay()
        Toast.makeText(this, "Speed set to ${speed}x", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        isTimerRunning = false
        handler.removeCallbacksAndMessages(null)
        overlayView?.let { windowManager.removeView(it) }
    }

    companion object {
        private var instance: OverlayService? = null
        
        fun getInstance(): OverlayService? = instance
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        return START_STICKY
    }
}
