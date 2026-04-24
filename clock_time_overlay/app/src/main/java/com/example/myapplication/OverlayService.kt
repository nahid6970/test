package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.DataOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var clockText: TextView
    private lateinit var stepText: TextView
    private lateinit var addButton: Button
    private lateinit var autoButton: Button
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    private val helperFormat = SimpleDateFormat("EEE, dd MMM yyyy  hh:mm a", Locale.getDefault())
    private val rootDateFormat = SimpleDateFormat("yyyyMMdd.HHmmss", Locale.getDefault())
    private val prefs by lazy { getSharedPreferences("ClockOverlayPrefs", MODE_PRIVATE) }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "clock_overlay_channel"
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
                "Clock Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the clock overlay running"
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
            .setContentTitle("Clock Time Overlay")
            .setContentText("Overlay is active")
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
        clockText = overlayView!!.findViewById(R.id.clockText)
        stepText = overlayView!!.findViewById(R.id.stepText)
        addButton = overlayView!!.findViewById(R.id.addButton)
        autoButton = overlayView!!.findViewById(R.id.autoButton)
        val closeButton = overlayView!!.findViewById<Button>(R.id.closeButton)

        addButton.setOnClickListener {
            handleTimeAdvance()
        }

        autoButton.setOnClickListener {
            handleTimeAuto()
        }

        closeButton.setOnClickListener {
            stopSelf()
        }

        windowManager.addView(overlayView, params)
        makeDraggable(overlayView!!, params)
        startClockUpdates()
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

    private fun startClockUpdates() {
        handler.post(object : Runnable {
            override fun run() {
                updateDisplay()
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun updateDisplay() {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.MINUTE, getStepMinutes())
        }

        clockText.text = clockFormat.format(now.time)
        stepText.text = "Step ${getStepMinutes()}m | Next: ${helperFormat.format(target.time)}"
        addButton.text = "+${getStepMinutes()}"
        autoButton.text = "Auto"
    }

    private fun getStepMinutes(): Int {
        return prefs.getInt("stepMinutes", 15)
    }

    private fun handleTimeAdvance() {
        val target = Calendar.getInstance().apply {
            add(Calendar.MINUTE, getStepMinutes())
        }
        val targetText = helperFormat.format(target.time)
        val rootFormatText = rootDateFormat.format(target.time)

        // Try root first
        if (runRootCommand("date -s \"$rootFormatText\"")) {
            Toast.makeText(this, "Time advanced to $targetText (Root)", Toast.LENGTH_SHORT).show()
        } else {
            // Fallback to manual
            copyToClipboard("Manual clock target", targetText)
            val intent = Intent(Settings.ACTION_DATE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(this, "Root failed. Set manually to $targetText. Copied.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleTimeAuto() {
        // Try root first to turn on automatic time
        if (runRootCommand("settings put global auto_time 1 && settings put global auto_time_zone 1")) {
            Toast.makeText(this, "Automatic time enabled (Root)", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(Settings.ACTION_DATE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(this, "Root failed. Enable 'Automatic date & time' in settings.", Toast.LENGTH_LONG).show()
        }
    }

    private fun runRootCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun copyToClipboard(label: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(intent: Intent?) {
        super.onTaskRemoved(intent)
        val restartIntent = Intent(applicationContext, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartIntent)
        } else {
            applicationContext.startService(restartIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        overlayView?.let { 
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }
}
