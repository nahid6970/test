package com.example.myshare

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myshare.ui.theme.MyShareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyShareTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val serverUrl = remember { mutableStateOf("") }

    // Load current setting
    LaunchedEffect(Unit) {
        val sharedPref = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        serverUrl.value = sharedPref.getString("serverUrl", "http://192.168.1.100:5002") ?: ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MyShare",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Share files instantly by using the share menu in any app. Files will be automatically uploaded to your configured server.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Server Configuration",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "Enter your server URL where files will be uploaded:",
                    style = MaterialTheme.typography.bodySmall
                )
                
                TextField(
                    value = serverUrl.value,
                    onValueChange = { serverUrl.value = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.100:5002") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Button(
                    onClick = { 
                        val sharedPref = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        with(sharedPref.edit()) {
                            putString("serverUrl", serverUrl.value)
                            apply()
                        }
                        Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Save Settings")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "How to use:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text("1. Configure your server URL above", style = MaterialTheme.typography.bodySmall)
                Text("2. Go to any app (Gallery, Files, etc.)", style = MaterialTheme.typography.bodySmall)
                Text("3. Select files and tap Share", style = MaterialTheme.typography.bodySmall)
                Text("4. Choose MyShare from the share menu", style = MaterialTheme.typography.bodySmall)
                Text("5. Files will upload automatically and open your server webpage", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MyShareTheme {
        MainScreen()
    }
}