package com.example.myshare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    listOf(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
                } else {
                    @Suppress("DEPRECATION")
                    listOf(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }?.filterNotNull()

        if (uris != null && uris.isNotEmpty()) {
            val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val serverUrl = sharedPref.getString("serverUrl", "http://192.168.1.100:5002")

            if (!serverUrl.isNullOrBlank()) {
                // Show upload toast
                Toast.makeText(this, "Uploading ${uris.size} file(s)...", Toast.LENGTH_SHORT).show()

                // Start upload work
                val workManager = WorkManager.getInstance(applicationContext)
                for (uri in uris) {
                    val data = Data.Builder()
                        .putString("fileUri", uri.toString())
                        .putString("serverUrl", serverUrl)
                        .build()

                    val uploadWorkRequest = OneTimeWorkRequestBuilder<FileUploadWorker>()
                        .setInputData(data)
                        .build()

                    workManager.enqueue(uploadWorkRequest)
                }

                // Open the server webpage
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl))
                try {
                    startActivity(webIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open webpage: $serverUrl", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Please configure server URL in MyShare app", Toast.LENGTH_LONG).show()
                
                // Open main app to configure settings
                val mainIntent = Intent(this, MainActivity::class.java)
                startActivity(mainIntent)
            }
        } else {
            Toast.makeText(this, "No files to share", Toast.LENGTH_SHORT).show()
        }

        // Always finish this activity
        finish()
    }
}

