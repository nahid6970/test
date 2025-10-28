package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Binder
import android.content.Intent
import android.os.IBinder

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }
import android.view.accessibility.AccessibilityEvent

class TouchAccessibilityService : AccessibilityService() {

    private val recordedEvents = mutableListOf<Pair<Float, Float>>()
    private var isRecording = false
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): TouchAccessibilityService = this@TouchAccessibilityService
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (isRecording && event?.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END) {
            recordedEvents.add(Pair(event.source.getBoundsInScreen(null).centerX().toFloat(), event.source.getBoundsInScreen(null).centerY().toFloat()))
        }
    }

    override fun onInterrupt() {}

    fun startRecording() {
        isRecording = true
        recordedEvents.clear()
    }

    fun stopRecording() {
        isRecording = false
    }

    fun replayTouches() {
        if (recordedEvents.isNotEmpty()) {
            val path = Path()
            path.moveTo(recordedEvents[0].first, recordedEvents[0].second)
            val gestureBuilder = GestureDescription.Builder()
            for (i in 0 until recordedEvents.size) {
                path.moveTo(recordedEvents[i].first, recordedEvents[i].second)
                gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, (i * 200).toLong(), 100))
            }
            dispatchGesture(gestureBuilder.build(), null, null)
        }
    }
}