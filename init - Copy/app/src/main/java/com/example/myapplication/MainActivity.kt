package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val startOverlayButton = findViewById<Button>(R.id.startOverlayButton)
        val stopOverlayButton = findViewById<Button>(R.id.stopOverlayButton)
        val accessibilitySettingsButton = findViewById<Button>(R.id.accessibilitySettingsButton)
        
        startOverlayButton.setOnClickListener {
            requestOverlayPermission()
        }
        
        stopOverlayButton.setOnClickListener {
            // Stop overlay service
            val intent = Intent(this, OverlayService::class.java)
            stopService(intent)
        }
        
        accessibilitySettingsButton.setOnClickListener {
            // Open accessibility settings
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }
    
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
            } else {
                startOverlayService()
            }
        } else {
            startOverlayService()
        }
    }
    
    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        startService(intent)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    startOverlayService()
                }
            }
        }
    }
}