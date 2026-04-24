package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ClockSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_clock_settings)

        val prefs = getSharedPreferences("ClockOverlayPrefs", MODE_PRIVATE)
        val stepInput = findViewById<EditText>(R.id.stepMinutesInput)
        val saveButton = findViewById<Button>(R.id.saveSettingsButton)
        val cancelButton = findViewById<Button>(R.id.cancelSettingsButton)

        stepInput.setText(prefs.getInt("stepMinutes", 15).toString())

        saveButton.setOnClickListener {
            val stepMinutes = stepInput.text.toString().trim().toIntOrNull()
            if (stepMinutes == null || stepMinutes < 1 || stepMinutes > 1440) {
                Toast.makeText(this, "Enter a step from 1 to 1440 minutes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putInt("stepMinutes", stepMinutes).apply()
            Toast.makeText(this, "Minute step saved: $stepMinutes", Toast.LENGTH_SHORT).show()
            finish()
        }

        cancelButton.setOnClickListener {
            finish()
        }
    }
}
