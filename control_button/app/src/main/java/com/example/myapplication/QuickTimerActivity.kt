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
        
        // Make the window background transparent and rounded
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Make the dialog smaller and position at top right
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.4).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.attributes = window?.attributes?.apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            x = 20 // 20px from right
            y = 20 // 20px from top
        }

        val timerInput = findViewById<EditText>(R.id.timerInput)

        // Set up action listener for keyboard's done button
        timerInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                submitTimer()
                true
            } else {
                false
            }
        }
    }

    private fun submitTimer() {
        val timerInput = findViewById<EditText>(R.id.timerInput)
        val input = timerInput.text.toString().trim().lowercase()

        if (input.isEmpty()) {
            Toast.makeText(this, "Please enter timer info", Toast.LENGTH_SHORT).show()
            return
        }

        // Parse the input string
        var speed = 1
        var days = 0
        var hours = 0
        var minutes = 0
        var label = ""

        try {
            // Extract speed (x10, x24, etc.)
            val speedMatch = Regex("x(\\d+)").find(input)
            speed = speedMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

            // Extract time components
            val dayMatch = Regex("(\\d+)d").find(input)
            val hourMatch = Regex("(\\d+)h").find(input)
            val minMatch = Regex("(\\d+)m").find(input)

            days = dayMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            hours = hourMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            minutes = minMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            // Extract label (everything that's not speed or time)
            label = input
                .replace(Regex("x\\d+"), "")
                .replace(Regex("\\d+d"), "")
                .replace(Regex("\\d+h"), "")
                .replace(Regex("\\d+m"), "")
                .trim()

        } catch (e: Exception) {
            Toast.makeText(this, "Error parsing input", Toast.LENGTH_SHORT).show()
            return
        }

        if (days == 0 && hours == 0 && minutes == 0) {
            Toast.makeText(this, "Please enter time (e.g., 1d3h55m)", Toast.LENGTH_SHORT).show()
            return
        }

        if (speed < 1) {
            speed = 1
        }

        setGoogleClockTimer(days, hours, minutes, speed, label)
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
