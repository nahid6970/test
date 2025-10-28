package com.example.myapplication

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: ComposeView
    private var touchAccessibilityService: TouchAccessibilityService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TouchAccessibilityService.LocalBinder
            touchAccessibilityService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            touchAccessibilityService = null
            isBound = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = ComposeView(this).apply {
            setContent {
                val isRecording = remember { mutableStateOf(false) }
                Row {
                    Button(onClick = {
                        if (isBound) {
                            if (isRecording.value) {
                                touchAccessibilityService?.stopRecording()
                            } else {
                                touchAccessibilityService?.startRecording()
                            }
                            isRecording.value = !isRecording.value
                        }
                    }) {
                        Text(if (isRecording.value) "Stop" else "Record")
                    }
                    Button(onClick = { 
                        if (isBound) {
                            touchAccessibilityService?.replayTouches()
                        }
                    }) {
                        Text("Replay")
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        windowManager.addView(floatingView, params)

        Intent(this, TouchAccessibilityService::class.java).also { intent ->
            bindService(intent, connection, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(floatingView)
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}