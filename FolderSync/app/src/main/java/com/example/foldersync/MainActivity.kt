package com.example.foldersync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foldersync.ui.theme.FolderSyncTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class MainActivity : ComponentActivity() {
    
    private var pendingFolderUri: Uri? = null
    private var showAddFolderDialog by mutableStateOf(false)
    
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { selectedUri ->
            // Take persistent permission
            contentResolver.takePersistableUriPermission(
                selectedUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            
            // Add to pending folder creation
            pendingFolderUri = selectedUri
            showAddFolderDialog = true
            
            // Debug: Show what was selected
            Toast.makeText(this, "Selected: ${selectedUri.path}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FolderSyncTheme {
                MainScreen(
                    onPickFolder = { folderPickerLauncher.launch(null) },
                    pendingFolderUri = pendingFolderUri,
                    showAddFolderDialog = showAddFolderDialog,
                    onAddFolderDialogDismiss = { 
                        showAddFolderDialog = false
                        pendingFolderUri = null
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onPickFolder: () -> Unit,
    pendingFolderUri: Uri?,
    showAddFolderDialog: Boolean,
    onAddFolderDialogDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("folder_sync_prefs", Context.MODE_PRIVATE)
    
    var serverUrl by remember { 
        mutableStateOf(sharedPrefs.getString("server_url", "http://192.168.1.100:5013") ?: "http://192.168.1.100:5013") 
    }
    var syncFolders by remember { mutableStateOf(loadSyncFolders(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<SyncFolder?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folder Sync") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            Column {
                FloatingActionButton(
                    onClick = onPickFolder,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Folder")
                }
                
                FloatingActionButton(
                    onClick = {
                        try {
                            if (syncFolders.any { it.isEnabled }) {
                                val intent = Intent(context, SyncActivity::class.java)
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "No folders enabled for sync", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error starting sync: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Start Sync")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (syncFolders.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No sync folders configured",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap the + button to add your first sync folder",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Sync folders list
                Text(
                    text = "Sync Folders (${syncFolders.count { it.isEnabled }} enabled)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(syncFolders) { folder ->
                        SyncFolderCard(
                            folder = folder,
                            onToggleEnabled = { enabled ->
                                syncFolders = syncFolders.map { 
                                    if (it.id == folder.id) it.copy(isEnabled = enabled) else it 
                                }
                                saveSyncFolders(context, syncFolders)
                            },
                            onEdit = { editingFolder = folder },
                            onDelete = {
                                syncFolders = syncFolders.filter { it.id != folder.id }
                                saveSyncFolders(context, syncFolders)
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Settings Dialog
    if (showSettings) {
        SettingsDialog(
            currentUrl = serverUrl,
            onUrlChange = { newUrl ->
                serverUrl = newUrl
                sharedPrefs.edit().putString("server_url", newUrl).apply()
                Toast.makeText(context, "Server URL saved", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSettings = false }
        )
    }
    
    // Add/Edit Folder Dialog
    if (showAddFolderDialog || editingFolder != null) {
        AddFolderDialog(
            existingFolder = editingFolder,
            pendingFolderUri = pendingFolderUri,
            onSave = { name, androidPath, pcPath, direction ->
                val folder = if (editingFolder != null) {
                    editingFolder!!.copy(
                        name = name,
                        androidPath = androidPath,
                        pcPath = pcPath,
                        syncDirection = direction
                    )
                } else {
                    SyncFolder(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        androidPath = androidPath,
                        androidUriString = pendingFolderUri?.toString(),
                        pcPath = pcPath,
                        syncDirection = direction
                    )
                }
                
                syncFolders = if (editingFolder != null) {
                    syncFolders.map { if (it.id == folder.id) folder else it }
                } else {
                    syncFolders + folder
                }
                
                saveSyncFolders(context, syncFolders)
                onAddFolderDialogDismiss()
                editingFolder = null
            },
            onDismiss = { 
                onAddFolderDialogDismiss()
                editingFolder = null
            }
        )
    }
}

@Composable
fun SyncFolderCard(
    folder: SyncFolder,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "📱 ${folder.androidPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "💻 ${folder.pcPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Direction: ${folder.syncDirection.name.replace("_", " ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = folder.isEnabled,
                        onCheckedChange = onToggleEnabled
                    )
                    
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempUrl by remember { mutableStateOf(currentUrl) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Settings") },
        text = {
            Column {
                Text("Enter your PC's IP address and port:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tempUrl,
                    onValueChange = { tempUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.100:5013") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Example: http://192.168.1.100:5013",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (tempUrl.isNotBlank()) {
                        onUrlChange(tempUrl)
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

@Composable
fun AddFolderDialog(
    existingFolder: SyncFolder?,
    pendingFolderUri: Uri?,
    onSave: (String, String, String, SyncDirection) -> Unit,
    onDismiss: () -> Unit
) {
    // Auto-populate fields when a folder is selected
    val initialAndroidPath = existingFolder?.androidPath ?: pendingFolderUri?.let { uri ->
        // Extract a readable path from the URI
        uri.path?.substringAfterLast("/") ?: uri.toString()
    } ?: ""
    
    val initialName = existingFolder?.name ?: pendingFolderUri?.let { uri ->
        // Use the folder name as the default sync folder name
        uri.path?.substringAfterLast("/") ?: "New Folder"
    } ?: ""
    
    var name by remember { mutableStateOf(initialName) }
    var androidPath by remember { mutableStateOf(initialAndroidPath) }
    var pcPath by remember { mutableStateOf(existingFolder?.pcPath ?: "") }
    var direction by remember { mutableStateOf(existingFolder?.syncDirection ?: SyncDirection.BOTH) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingFolder != null) "Edit Sync Folder" else "Add Sync Folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = androidPath,
                    onValueChange = { androidPath = it },
                    label = { Text("Android Path") },
                    placeholder = { Text("/storage/emulated/0/DCIM/Camera") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = pcPath,
                    onValueChange = { pcPath = it },
                    label = { Text("PC Path") },
                    placeholder = { Text("Movies or C:/Movies") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Use relative path (Movies) or absolute path (C:/Movies)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Sync Direction:")
                SyncDirection.values().forEach { dir ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = direction == dir,
                            onClick = { direction = dir }
                        )
                        Text(dir.name.replace("_", " "))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && androidPath.isNotBlank() && pcPath.isNotBlank()) {
                        onSave(name, androidPath, pcPath, direction)
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

fun loadSyncFolders(context: Context): List<SyncFolder> {
    val sharedPrefs = context.getSharedPreferences("folder_sync_prefs", Context.MODE_PRIVATE)
    val json = sharedPrefs.getString("sync_folders", "[]") ?: "[]"
    
    return try {
        val type = object : TypeToken<List<SyncFolder>>() {}.type
        Gson().fromJson(json, type) ?: emptyList()
    } catch (e: Exception) {
        // If loading fails (e.g., due to format change), clear the data and start fresh
        sharedPrefs.edit().remove("sync_folders").apply()
        emptyList()
    }
}

fun saveSyncFolders(context: Context, folders: List<SyncFolder>) {
    val sharedPrefs = context.getSharedPreferences("folder_sync_prefs", Context.MODE_PRIVATE)
    val json = Gson().toJson(folders)
    sharedPrefs.edit().putString("sync_folders", json).apply()
}