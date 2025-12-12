package com.example.myapplication

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    
    private var fileServer: AndroidFileServer? = null
    private var isServerBound = false
    private val serverState = mutableStateOf<AndroidFileServer?>(null)
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AndroidFileServer.LocalBinder
            fileServer = binder.getService()
            serverState.value = fileServer
            isServerBound = true
            android.util.Log.d("MainActivity", "Service connected successfully")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            fileServer = null
            serverState.value = null
            isServerBound = false
            android.util.Log.d("MainActivity", "Service disconnected")
        }
    }
    
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val intent = Intent(this, FolderUploadActivity::class.java).apply {
                putExtra("folder_uri", it.toString())
            }
            startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Start and bind to the file server service
        Intent(this, AndroidFileServer::class.java).also { intent ->
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val currentServer by serverState
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onPickFolder = { folderPickerLauncher.launch(null) },
                        fileServer = currentServer
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isServerBound) {
            unbindService(serviceConnection)
            isServerBound = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onPickFolder: () -> Unit = {},
    fileServer: AndroidFileServer? = null
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("file_share_prefs", Context.MODE_PRIVATE)
    
    var pcServerUrl by remember { 
        mutableStateOf(sharedPrefs.getString("server_url", "http://192.168.1.100:5002") ?: "http://192.168.1.100:5002") 
    }
    var deviceName by remember {
        mutableStateOf(sharedPrefs.getString("device_name", android.os.Build.MODEL) ?: android.os.Build.MODEL)
    }
    var showSettings by remember { mutableStateOf(false) }
    var isServerRunning by remember { mutableStateOf(false) }
    var serverPort by remember { mutableStateOf(8080) }
    var localIp by remember { mutableStateOf<String?>(null) }
    
    // Check server status periodically
    LaunchedEffect(fileServer) {
        while (true) {
            isServerRunning = fileServer?.isRunning() == true
            if (isServerRunning) {
                serverPort = fileServer?.getPort() ?: 8080
            }
            localIp = NetworkUtils.getLocalIpAddress(context)
            delay(1000)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MyShare") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "MyShare",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Share files between Android & PC",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Android Server Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServerRunning) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📱 Android Server",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = when {
                                    fileServer == null -> "Service initializing..."
                                    isServerRunning -> "Running"
                                    else -> "Stopped"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    fileServer == null -> MaterialTheme.colorScheme.outline
                                    isServerRunning -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        
                        Button(
                            onClick = {
                                if (isServerRunning) {
                                    fileServer?.stopServer()
                                    Toast.makeText(context, "Server stopped", Toast.LENGTH_SHORT).show()
                                } else {
                                    val result = fileServer?.startServer(8080)
                                    if (result == true) {
                                        Toast.makeText(context, "Server started on port 8080", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to start server. Port may be in use.", Toast.LENGTH_LONG).show()
                                        android.util.Log.e("MainActivity", "Server start failed")
                                    }
                                }
                            },
                            enabled = fileServer != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServerRunning) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                if (isServerRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = if (isServerRunning) "Stop" else "Start",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isServerRunning) "Stop" else "Start")
                        }
                    }
                    
                    if (isServerRunning && localIp != null) {
                        Divider()
                        
                        Text(
                            text = "Server Address:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "http://$localIp:$serverPort",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Button(
                            onClick = {
                                val intent = Intent(context, QRDisplayActivity::class.java).apply {
                                    putExtra("server_url", "http://$localIp:$serverPort")
                                    putExtra("device_name", deviceName)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Show QR")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Show QR Code")
                        }
                        
                        Text(
                            text = "Other devices can scan this QR to connect",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (!isServerRunning) {
                        Text(
                            text = "Start the server to receive files from other devices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // PC Server Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💻 PC Server",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    
                    Text(
                        text = pcServerUrl,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "Configure in settings • Run: python upload_files.py",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Action Buttons
            Button(
                onClick = onPickFolder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Pick Folder", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload Entire Folder")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "How to Share:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    
                    Text("1. Share files from any app → Select MyShare")
                    Text("2. Choose destination: Android or PC")
                    Text("3. For Android: Scan QR code from receiving device")
                    Text("4. For PC: Ensure Python server is running")
                    Text("5. Files transfer automatically!")
                }
            }
        }
    }
    
    if (showSettings) {
        SettingsDialog(
            currentPcUrl = pcServerUrl,
            currentDeviceName = deviceName,
            onSave = { newPcUrl, newDeviceName ->
                pcServerUrl = newPcUrl
                deviceName = newDeviceName
                sharedPrefs.edit()
                    .putString("server_url", newPcUrl)
                    .putString("device_name", newDeviceName)
                    .apply()
                Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun SettingsDialog(
    currentPcUrl: String,
    currentDeviceName: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempPcUrl by remember { mutableStateOf(currentPcUrl) }
    var tempDeviceName by remember { mutableStateOf(currentDeviceName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Device Name:", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = tempDeviceName,
                    onValueChange = { tempDeviceName = it },
                    label = { Text("Device Name") },
                    placeholder = { Text("My Phone") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("PC Server URL:", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = tempPcUrl,
                    onValueChange = { tempPcUrl = it },
                    label = { Text("PC Server URL") },
                    placeholder = { Text("http://192.168.1.100:5002") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "Example: http://192.168.1.100:5002",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (tempPcUrl.isNotBlank() && tempDeviceName.isNotBlank()) {
                        onSave(tempPcUrl, tempDeviceName)
                        onDismiss()
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
