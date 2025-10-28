package com.example.myapplication

import android.os.Handler
import android.os.Looper
import android.util.Log

data class TouchEvent(
    val x: Float,
    val y: Float,
    val timestamp: Long
)

object TouchRecordingManager {
    private val tag = "TouchRecordingManager"
    private val recordedTouches = mutableListOf<TouchEvent>()
    private var isRecording = false
    private var startTime: Long = 0
    
    fun startRecording() {
        isRecording = true
        recordedTouches.clear()
        startTime = System.currentTimeMillis()
        Log.d(tag, "Started recording touches")
    }
    
    fun stopRecording() {
        isRecording = false
        Log.d(tag, "Stopped recording touches. Recorded ${recordedTouches.size} touches")
    }
    
    fun recordTouch(x: Float, y: Float) {
        if (isRecording) {
            val timestamp = System.currentTimeMillis() - startTime
            recordedTouches.add(TouchEvent(x, y, timestamp))
            Log.d(tag, "Recorded touch at ($x, $y) with timestamp $timestamp")
        }
    }
    
    fun replayTouches() {
        if (recordedTouches.isEmpty()) {
            Log.d(tag, "No touches to replay")
            return
        }
        
        Log.d(tag, "Replaying ${recordedTouches.size} touches")
        val handler = Handler(Looper.getMainLooper())
        
        // Add a small delay before starting replay to give user time to switch to target app
        handler.postDelayed({
            // Replay touches with exact timing
            for ((index, touch) in recordedTouches.withIndex()) {
                handler.postDelayed({
                    Log.d(tag, "Replaying touch $index at (${touch.x}, ${touch.y}) with delay ${touch.timestamp}")
                    // Dispatch the touch event using accessibility service
                    AccessibilityTouchDispatcher.dispatchGesture(touch.x, touch.y)
                }, touch.timestamp)
            }
        }, 2000) // 2 second delay before starting replay
    }
    
    fun isRecording(): Boolean {
        return isRecording
    }
    
    fun getRecordedTouches(): List<TouchEvent> {
        return recordedTouches.toList()
    }
}