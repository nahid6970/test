package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TimerInputActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timer_input)

        val daysInput = findViewById<EditText>(R.id.daysInput)
        val hoursInput = findViewById<EditText>(R.id.hoursInput)
        val minutesInput = findViewById<EditText>(R.id.minutesInput)
        val startButton = findViewById<Button>(R.id.startButton)
        val cancelButton = findViewById<Button>(R.id.cancelButton)

        val speedInput = findViewById<EditText>(R.id.speedInput)

        startButton.setOnClickListener {
            val days = daysInput.text.toString().toIntOrNull() ?: 0
            val hours = hoursInput.text.toString().toIntOrNull() ?: 0
            val minutes = minutesInput.text.toString().toIntOrNull() ?: 0
            val speed = speedInput.text.toString().toIntOrNull() ?: 1

            if (days == 0 && hours == 0 && minutes == 0) {
                Toast.makeText(this, "Please enter a valid time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (speed < 1) {
                Toast.makeText(this, "Speed must be at least 1", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            OverlayService.getInstance()?.setSpeed(speed)
            OverlayService.getInstance()?.setTimer(days, hours, minutes)
            Toast.makeText(this, "Timer started at ${speed}x speed!", Toast.LENGTH_SHORT).show()
            finish()
        }

        cancelButton.setOnClickListener {
            finish()
        }
    }
}
