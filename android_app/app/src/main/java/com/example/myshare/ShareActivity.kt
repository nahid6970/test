package com.example.myshare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.myshare.ui.theme.MyShareTheme

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

        setContent {
            MyShareTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (uris != null) {
                        ShareScreen(
                            uris = uris,
                            onSettingsClick = {
                                val intent = Intent(this@ShareActivity, SettingsActivity::class.java)
                                startActivity(intent)
                            },
                            onUploadClick = { fileUris ->
                                val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
                                val serverUrl = sharedPref.getString("serverUrl", "http://192.168.1.100:5002")

                                if (!serverUrl.isNullOrBlank()) {
                                    Toast.makeText(this@ShareActivity, "Starting upload of ${fileUris.size} file(s)...", Toast.LENGTH_SHORT).show()

                                    val workManager = WorkManager.getInstance(applicationContext)
                                    for (uri in fileUris) {
                                        val data = Data.Builder()
                                            .putString("fileUri", uri.toString())
                                            .putString("serverUrl", serverUrl)
                                            .build()

                                        val uploadWorkRequest = OneTimeWorkRequestBuilder<FileUploadWorker>()
                                            .setInputData(data)
                                            .build()

                                        workManager.enqueue(uploadWorkRequest)
                                    }
                                    
                                    // Close the activity after starting uploads
                                    finish()
                                } else {
                                    Toast.makeText(this@ShareActivity, "Please set server URL in settings", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShareScreen(uris: List<Uri>, onSettingsClick: () -> Unit, onUploadClick: (List<Uri>) -> Unit) {
    Column {
        Text(text = "Selected files:")
        for (uri in uris) {
            Text(text = uri.toString())
        }
        Button(onClick = onSettingsClick) {
            Text(text = "Settings")
        }
        Button(onClick = { onUploadClick(uris) }) {
            Text(text = "Upload")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ShareScreenPreview() {
    MyShareTheme {
        ShareScreen(
            uris = listOf(Uri.parse("file://test.txt")),
            onSettingsClick = {},
            onUploadClick = {}
        )
    }
}