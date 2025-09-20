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
        mutableStateOf(sharedPrefs.getString("server_url", "http://192.168.0.101:5016") ?: "http://192.168.0.101:5016") 
    }
    var syncFolders by remember { mutableStateOf(loadSyncFolders(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<SyncFolder?>(null) }
    var advancedSettingsFolder by remember { mutableStateOf<SyncFolder?>(null) }
    
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
                            },
                            onAdvancedSettings = { advancedSettingsFolder = folder }
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
    
    // Advanced Settings Dialog
    if (advancedSettingsFolder != null) {
        AdvancedSettingsDialog(
            folder = advancedSettingsFolder!!,
            onSave = { updatedFolder ->
                syncFolders = syncFolders.map { 
                    if (it.id == updatedFolder.id) updatedFolder else it 
                }
                saveSyncFolders(context, syncFolders)
                advancedSettingsFolder = null
            },
            onDismiss = { advancedSettingsFolder = null }
        )
    }
}

@Composable
fun SyncFolderCard(
    folder: SyncFolder,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAdvancedSettings: () -> Unit
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
                        text = "Direction: ${when(folder.syncDirection) {
                            SyncDirection.ANDROID_TO_PC -> "Android to PC"
                            SyncDirection.PC_TO_ANDROID -> "PC to Android"
                        }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Show rclone sync information
                    val directionIcon = when(folder.syncDirection) {
                        SyncDirection.ANDROID_TO_PC -> "📱→💻"
                        SyncDirection.PC_TO_ANDROID -> "💻→📱"
                    }
                    val commandText = when(folder.rcloneCommand) {
                        RcloneCommand.SYNC -> "sync"
                        RcloneCommand.COPY -> "copy"
                    }
                    val flagsText = folder.rcloneFlags.take(40) + if (folder.rcloneFlags.length > 40) "..." else ""
                    val syncOptionsText = "$directionIcon 🚀 rclone $commandText $flagsText"
                    
                    Text(
                        text = syncOptionsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = folder.isEnabled,
                        onCheckedChange = onToggleEnabled
                    )
                    
                    IconButton(onClick = onAdvancedSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Advanced Settings")
                    }
                    
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
        title = { Text("Settings") },
        text = {
            Column {
                Text("Server Configuration:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tempUrl,
                    onValueChange = { tempUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.0.101:5016") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Example: http://192.168.0.101:5016",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Note: Sync modes and duplicate handling are now configured per folder.",
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
    var direction by remember { mutableStateOf(existingFolder?.syncDirection ?: SyncDirection.ANDROID_TO_PC) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingFolder != null) "Edit Sync Folder" else "Add Sync Folder") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = androidPath,
                    onValueChange = { androidPath = it },
                    label = { Text("Android Path") },
                    placeholder = { Text("/storage/emulated/0/DCIM/Camera") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = pcPath,
                    onValueChange = { pcPath = it },
                    label = { Text("PC Path") },
                    placeholder = { Text("Movies or C:/test or D:/MyFiles") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Relative: Movies → ~/Desktop/SyncFolders/Movies\nAbsolute: C:/test → C:/test",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Sync Direction:", fontWeight = FontWeight.Bold)
                SyncDirection.values().forEach { dir ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = direction == dir,
                            onClick = { direction = dir }
                        )
                        Text(when(dir) {
                            SyncDirection.ANDROID_TO_PC -> "Android to PC"
                            SyncDirection.PC_TO_ANDROID -> "PC to Android"
                        })
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Use the settings icon after creating the folder to configure sync modes (Copy & Delete, Mirror, Sync)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
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

@Composable
fun AdvancedSettingsDialog(
    folder: SyncFolder,
    onSave: (SyncFolder) -> Unit,
    onDismiss: () -> Unit
) {
    // State for rclone command
    var rcloneCommand by remember { mutableStateOf(folder.rcloneCommand) }
    
    // State for individual flags
    var flagProgress by remember { mutableStateOf(folder.flagProgress) }
    var flagTransfers4 by remember { mutableStateOf(folder.flagTransfers4) }
    var flagCheckers8 by remember { mutableStateOf(folder.flagCheckers8) }
    var flagContimeout60s by remember { mutableStateOf(folder.flagContimeout60s) }
    var flagTimeout300s by remember { mutableStateOf(folder.flagTimeout300s) }
    var flagRetries3 by remember { mutableStateOf(folder.flagRetries3) }
    var flagIgnoreExisting by remember { mutableStateOf(folder.flagIgnoreExisting) }
    var flagTrackRenames by remember { mutableStateOf(folder.flagTrackRenames) }
    var flagFastList by remember { mutableStateOf(folder.flagFastList) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rclone Sync Settings") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Folder: ${folder.name}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "Direction: ${when(folder.syncDirection) {
                                SyncDirection.ANDROID_TO_PC -> "Android to PC"
                                SyncDirection.PC_TO_ANDROID -> "PC to Android"
                            }}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Rclone Command Selection
                        Text("Rclone Command:", fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = rcloneCommand == RcloneCommand.SYNC,
                                    onClick = { rcloneCommand = RcloneCommand.SYNC }
                                )
                                Column {
                                    Text("Sync", fontWeight = FontWeight.Medium)
                                    Text(
                                        "Make destination identical",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = rcloneCommand == RcloneCommand.COPY,
                                    onClick = { rcloneCommand = RcloneCommand.COPY }
                                )
                                Column {
                                    Text("Copy", fontWeight = FontWeight.Medium)
                                    Text(
                                        "Copy files to destination",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Rclone Flags
                        Text("Rclone Flags:", fontWeight = FontWeight.Bold)
                    }
                }
                
                // Performance flags
                item {
                    FlagCheckbox(
                        checked = flagProgress,
                        onCheckedChange = { flagProgress = it },
                        title = "--progress",
                        description = "Show transfer progress"
                    )
                }
                
                item {
                    FlagCheckbox(
                        checked = flagTransfers4,
                        onCheckedChange = { flagTransfers4 = it },
                        title = "--transfers=4",
                        description = "Number of parallel transfers"
                    )
                }
                
                item {
                    FlagCheckbox(
                        checked = flagCheckers8,
                        onCheckedChange = { flagCheckers8 = it },
                        title = "--checkers=8",
                        description = "Number of checkers to run in parallel"
                    )
                }
                
                // Timeout flags
                item {
                    FlagCheckbox(
                        checked = flagContimeout60s,
                        onCheckedChange = { flagContimeout60s = it },
                        title = "--contimeout=60s",
                        description = "Connection timeout"
                    )
                }
                
                item {
                    FlagCheckbox(
                        checked = flagTimeout300s,
                        onCheckedChange = { flagTimeout300s = it },
                        title = "--timeout=300s",
                        description = "IO idle timeout"
                    )
                }
                
                item {
                    FlagCheckbox(
                        checked = flagRetries3,
                        onCheckedChange = { flagRetries3 = it },
                        title = "--retries=3",
                        description = "Retry operations on failure"
                    )
                }
                
                // Advanced flags
                item {
                    FlagCheckbox(
                        checked = flagIgnoreExisting,
                        onCheckedChange = { flagIgnoreExisting = it },
                        title = "--ignore-existing",
                        description = "Skip files that exist on destination"
                    )
                }
                
                item {
                    FlagCheckbox(
                        checked = flagTrackRenames,
                        onCheckedChange = { flagTrackRenames = it },
                        title = "--track-renames",
                        description = "Track file renames and do server-side moves"
                    )
                }
                
                item {
                    FlagCheckbox(
                        checked = flagFastList,
                        onCheckedChange = { flagFastList = it },
                        title = "--fast-list",
                        description = "Use recursive list if available"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedFolder = folder.copy(
                        rcloneCommand = rcloneCommand,
                        flagProgress = flagProgress,
                        flagTransfers4 = flagTransfers4,
                        flagCheckers8 = flagCheckers8,
                        flagContimeout60s = flagContimeout60s,
                        flagTimeout300s = flagTimeout300s,
                        flagRetries3 = flagRetries3,
                        flagIgnoreExisting = flagIgnoreExisting,
                        flagTrackRenames = flagTrackRenames,
                        flagFastList = flagFastList
                    )
                    onSave(updatedFolder)
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
fun FlagCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}