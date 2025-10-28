package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.AlarmClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var timerText: TextView
    private lateinit var addButton: Button
    private lateinit var speedButton: Button
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var targetTimeMillis = 0L
    private var remainingMillis = 0L
    private var isTimerRunning = false
    private var speedMultiplier = 1
    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdateTime = 0L

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "timer_overlay_channel"
        private var instance: OverlayService? = null
        
        fun getInstance(): OverlayService? = instance
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TimerOverlay::WakeLock"
        ).apply {
            acquire()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the timer overlay running"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Countdown Timer")
            .setContentText("Timer overlay is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
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
        val closeButton = overlayView!!.findViewById<Button>(R.id.closeButton)

        addButton.setOnClickListener {
            if (isTimerRunning) {
                stopTimer()
            } else {
                showTimerInputDialog()
            }
        }

        speedButton.setOnClickListener {
            // Speed is now set in timer input dialog
            Toast.makeText(this, "Current speed: ${speedMultiplier}x", Toast.LENGTH_SHORT).show()
        }

        closeButton.setOnClickListener {
            stopSelf()
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
        
        setGoogleClockTimer()
        runTimer()
    }

    private fun setGoogleClockTimer() {
        // Calculate actual time considering speed multiplier
        val actualSeconds = (remainingMillis / 1000 / speedMultiplier).toInt()
        
        if (actualSeconds > 0) {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, actualSeconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Countdown Timer")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Show UI to confirm
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Check if there's an app that can handle this intent
            if (intent.resolveActivity(packageManager) != null) {
                try {
                    startActivity(intent)
                    Toast.makeText(this, "Setting ${actualSeconds}s timer in Clock app", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "No clock app found to set timer", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun stopTimer() {
        isTimerRunning = false
        targetTimeMillis = 0L
        remainingMillis = 0L
        speedMultiplier = 1
        handler.removeCallbacksAndMessages(null)
        updateDisplay()
        Toast.makeText(this, "Timer stopped!", Toast.LENGTH_SHORT).show()
    }

    private fun updateDisplay() {
        if (targetTimeMillis == 0L) {
            timerText.text = "Tap + to set timer"
            speedButton.visibility = View.GONE
            addButton.text = "+"
        } else {
            val totalSeconds = (remainingMillis / 1000).toInt()
            val days = totalSeconds / 86400
            val hours = (totalSeconds % 86400) / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            timerText.text = String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds)
            speedButton.visibility = View.VISIBLE
            speedButton.text = if (speedMultiplier == 1) "Speed" else "${speedMultiplier}x"
            addButton.text = "■"
        }
    }

    fun setTimer(days: Int, hours: Int, minutes: Int) {
        startTimer(days, hours, minutes)
    }

    fun setSpeed(speed: Int) {
        speedMultiplier = speed
        updateDisplay()
        
        // Update Google Clock timer with new speed
        if (isTimerRunning) {
            setGoogleClockTimer()
        }
        
        Toast.makeText(this, "Speed set to ${speed}x", Toast.LENGTH_SHORT).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        return START_STICKY
    }

    override fun onTaskRemoved(intent: Intent?) {
        super.onTaskRemoved(intent)
        // Restart service if task is removed
        val restartIntent = Intent(applicationContext, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartIntent)
        } else {
            applicationContext.startService(restartIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isTimerRunning = false
        handler.removeCallbacksAndMessages(null)
        overlayView?.let { 
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Already removed
            }
        }
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        instance = null
    }
}
