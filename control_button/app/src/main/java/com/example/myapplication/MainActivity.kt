package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQUEST_CODE = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startButton = findViewById<Button>(R.id.recordButton)
        val stopButton = findViewById<Button>(R.id.replayButton)

        startButton.text = "Start Timer Overlay"
        stopButton.text = "Stop Timer Overlay"

        startButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            } else {
                startOverlayService()
            }
        }

        stopButton.setOnClickListener {
            stopOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        startService(intent)
        Toast.makeText(this, "Overlay started! You can minimize the app.", Toast.LENGTH_LONG).show()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        Toast.makeText(this, "Timer overlay stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
