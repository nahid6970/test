package com.gemini.fileshare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gemini.fileshare.ui.theme.FileShareTheme

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOf(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        }?.filterNotNull()

        setContent {
            FileShareTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (uris != null) {
                        ShareScreen(uris = uris)
                    }
                }
            }
        }
    }
}

@Composable
fun ShareScreen(uris: List<Uri>) {
    Column {
        Text(text = "Selected files:")
        for (uri in uris) {
            Text(text = uri.toString())
        }
        Button(onClick = { 
            val intent = Intent(applicationContext, SettingsActivity::class.java)
            startActivity(intent)
        }) {
            Text(text = "Settings")
        }
        Button(onClick = { 
            val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val serverUrl = sharedPref.getString("serverUrl", "http://192.168.1.100:5002")

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
        }) {
            Text(text = "Upload")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ShareScreenPreview() {
    FileShareTheme {
        ShareScreen(uris = listOf(Uri.parse("file://test.txt")))
    }
}