package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SpeedInputActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speed_input)

        val speedInput = findViewById<EditText>(R.id.speedInput)
        val setButton = findViewById<Button>(R.id.setSpeedButton)
        val normalButton = findViewById<Button>(R.id.normalSpeedButton)
        val cancelButton = findViewById<Button>(R.id.cancelButton)

        setButton.setOnClickListener {
            val speed = speedInput.text.toString().toIntOrNull()
            
            if (speed == null || speed < 1) {
                Toast.makeText(this, "Please enter a valid speed (e.g., 10, 16, 24)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            OverlayService.getInstance()?.setSpeed(speed)
            finish()
        }

        normalButton.setOnClickListener {
            OverlayService.getInstance()?.setSpeed(1)
            finish()
        }

        cancelButton.setOnClickListener {
            finish()
        }
    }
}
