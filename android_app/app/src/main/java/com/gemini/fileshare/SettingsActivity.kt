package com.gemini.fileshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.gemini.fileshare.ui.theme.FileShareTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileShareTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val serverUrl = remember { mutableStateOf("") }

    Column {
        Text(text = "Server URL")
        TextField(
            value = serverUrl.value,
            onValueChange = { serverUrl.value = it },
            label = { Text("http://192.168.1.100:5002") }
        )
        Button(onClick = { 
            val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("serverUrl", serverUrl.value)
                apply()
            }
        }) {
            Text(text = "Save")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    FileShareTheme {
        SettingsScreen()
    }
}