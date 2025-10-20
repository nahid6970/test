package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class TouchRecorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val touchActions = mutableListOf<TouchAction>()
    private var isRecording = false
    private var recordingStartTime = 0L
    private var currentTouchX = 0f
    private var currentTouchY = 0f
    private var isTouching = false
    
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        alpha = 150
    }

    fun startRecording() {
        isRecording = true
        touchActions.clear()
        recordingStartTime = System.currentTimeMillis()
    }

    fun stopRecording(): List<TouchAction> {
        isRecording = false
        return touchActions.toList()
    }

    fun isRecording(): Boolean = isRecording

    override fun onTouchEvent(event: MotionEvent): Boolean {
        currentTouchX = event.x
        currentTouchY = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isTouching = true
                if (isRecording) {
                    val timestamp = System.currentTimeMillis() - recordingStartTime
                    touchActions.add(TouchAction(MotionEvent.ACTION_DOWN, event.x, event.y, timestamp))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isRecording) {
                    val timestamp = System.currentTimeMillis() - recordingStartTime
                    touchActions.add(TouchAction(MotionEvent.ACTION_MOVE, event.x, event.y, timestamp))
                }
            }
            MotionEvent.ACTION_UP -> {
                isTouching = false
                if (isRecording) {
                    val timestamp = System.currentTimeMillis() - recordingStartTime
                    touchActions.add(TouchAction(MotionEvent.ACTION_UP, event.x, event.y, timestamp))
                }
            }
        }
        
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isTouching) {
            canvas.drawCircle(currentTouchX, currentTouchY, 50f, paint)
        }
    }
}
