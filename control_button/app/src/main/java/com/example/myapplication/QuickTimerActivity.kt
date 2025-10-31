package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class QuickTimerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_quick_timer)
        
        // Make the dialog larger
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        val daysInput = findViewById<EditText>(R.id.daysInput)
        val hoursInput = findViewById<EditText>(R.id.hoursInput)
        val minutesInput = findViewById<EditText>(R.id.minutesInput)
        val speedInput = findViewById<EditText>(R.id.speedInput)
        val labelInput = findViewById<EditText>(R.id.labelInput)
        val startButton = findViewById<Button>(R.id.startButton)

        startButton.setOnClickListener {
            val days = daysInput.text.toString().toIntOrNull() ?: 0
            val hours = hoursInput.text.toString().toIntOrNull() ?: 0
            val minutes = minutesInput.text.toString().toIntOrNull() ?: 0
            val speed = speedInput.text.toString().toIntOrNull() ?: 1
            val label = labelInput.text.toString().trim()

            if (days == 0 && hours == 0 && minutes == 0) {
                Toast.makeText(this, "Please enter a valid time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (speed < 1) {
                Toast.makeText(this, "Speed must be at least 1", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setGoogleClockTimer(days, hours, minutes, speed, label)
        }
    }

    private fun setGoogleClockTimer(days: Int, hours: Int, minutes: Int, speed: Int, label: String) {
        // Calculate total time in milliseconds
        val totalMillis = (days * 24 * 60 * 60 * 1000L) +
                (hours * 60 * 60 * 1000L) +
                (minutes * 60 * 1000L)

        // Calculate actual time considering speed multiplier
        val actualSeconds = (totalMillis / 1000 / speed).toInt()

        if (actualSeconds > 0) {
            val timerLabel = if (label.isNotEmpty()) label else "Quick Timer"
            
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, actualSeconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, timerLabel)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(packageManager) != null) {
                try {
                    startActivity(intent)
                    Toast.makeText(this, "Setting ${actualSeconds}s timer: $timerLabel", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "No clock app found to set timer", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
