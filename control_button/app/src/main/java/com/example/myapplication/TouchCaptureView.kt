package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

class TouchCaptureView(
    context: Context,
    private val onTouchCaptured: (x: Float, y: Float, action: Int) -> Unit
) : View(context) {

    private val paint = Paint().apply {
        color = Color.RED
        alpha = 30
        style = Paint.Style.FILL
    }

    private val touchIndicators = mutableListOf<Pair<Float, Float>>()

    init {
        setBackgroundColor(0x10FF0000) // Slight red tint
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onTouchCaptured(event.x, event.y, MotionEvent.ACTION_DOWN)
                touchIndicators.add(Pair(event.x, event.y))
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                onTouchCaptured(event.x, event.y, MotionEvent.ACTION_UP)
            }
            MotionEvent.ACTION_MOVE -> {
                onTouchCaptured(event.x, event.y, MotionEvent.ACTION_MOVE)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        touchIndicators.forEach { (x, y) ->
            canvas.drawCircle(x, y, 50f, paint)
        }
    }
}
