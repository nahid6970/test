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
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share

import androidx.compose.material3.*
import androidx.compose.ui.draw.scale
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
import java.text.SimpleDateFormat
import java.io.File

class MainActivity : ComponentActivity() {
    
    private var pendingFolderUri: Uri? = null
    private var showAddFolderDialog by mutableStateOf(false)
    private var pendingImportCallback: ((List<SyncFolder>, String) -> Unit)? = null
    private var pendingExportData: SyncBackup? = null
    
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
    
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { selectedUri ->
            pendingExportData?.let { backup ->
                try {
                    contentResolver.openOutputStream(selectedUri)?.use { outputStream ->
                        outputStream.write(Gson().toJson(backup).toByteArray())
                    }
                    Toast.makeText(this, "Settings exported successfully!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                pendingExportData = null
            }
        }
    }
    
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            pendingImportCallback?.let { callback ->
                try {
                    contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                        val json = inputStream.bufferedReader().use { it.readText() }
                        val backup = Gson().fromJson(json, SyncBackup::class.java)
                        callback(backup.folders, backup.serverUrl)
                        Toast.makeText(this, "Settings imported successfully!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                pendingImportCallback = null
            }
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
                    },
                    onExportSettings = ::exportSyncFolders,
                    onImportSettings = ::importSyncFolders
                )
            }
        }
    }
    
    fun exportSyncFolders(folders: List<SyncFolder>, serverUrl: String) {
        val backup = SyncBackup(
            folders = folders,
            serverUrl = serverUrl,
            exportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            version = "1.0"
        )
        
        pendingExportData = backup
        val fileName = "FolderSync_Backup_${SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())}.json"
        exportLauncher.launch(fileName)
    }
    
    fun importSyncFolders(onImport: (List<SyncFolder>, String) -> Unit) {
        pendingImportCallback = onImport
        importLauncher.launch(arrayOf("application/json"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onPickFolder: () -> Unit,
    pendingFolderUri: Uri?,
    showAddFolderDialog: Boolean,
    onAddFolderDialogDismiss: () -> Unit,
    onExportSettings: (List<SyncFolder>, String) -> Unit,
    onImportSettings: ((List<SyncFolder>, String) -> Unit) -> Unit
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
                    // Add button
                    IconButton(onClick = onPickFolder) {
                        Icon(Icons.Default.Add, contentDescription = "Add Folder")
                    }
                    
                    // Sync button
                    IconButton(
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
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Start Sync")
                    }
                    
                    // Settings button
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
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
            onDismiss = { showSettings = false },
            onExport = {
                onExportSettings(syncFolders, serverUrl)
            },
            onImport = {
                onImportSettings { importedFolders, importedServerUrl ->
                    syncFolders = importedFolders
                    saveSyncFolders(context, importedFolders)
                    if (importedServerUrl.isNotEmpty()) {
                        serverUrl = importedServerUrl
                        sharedPrefs.edit().putString("server_url", importedServerUrl).apply()
                    }
                }
            }
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
            // New improved layout
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Folder Name (prominent)
                Text(
                    text = folder.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Android Path (always first line)
                Text(
                    text = "📱 ${folder.androidPath}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // PC Path (always second line)
                Text(
                    text = "💻 ${folder.pcPath}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Compact: Source → Type → Target
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when(folder.syncDirection) {
                            SyncDirection.ANDROID_TO_PC -> "📱"
                            SyncDirection.PC_TO_ANDROID -> "💻"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = " → ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    
                    Text(
                        text = when(folder.syncDirection) {
                            SyncDirection.ANDROID_TO_PC -> when(folder.androidToPcMode) {
                                SyncMode.COPY_AND_DELETE -> "Copy & Delete"
                                SyncMode.MIRROR -> "Mirror"
                                SyncMode.SYNC -> "Sync"
                            }
                            SyncDirection.PC_TO_ANDROID -> when(folder.pcToAndroidMode) {
                                SyncMode.COPY_AND_DELETE -> "Copy & Delete"
                                SyncMode.MIRROR -> "Mirror"
                                SyncMode.SYNC -> "Sync"
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = " → ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    
                    Text(
                        text = when(folder.syncDirection) {
                            SyncDirection.ANDROID_TO_PC -> "💻"
                            SyncDirection.PC_TO_ANDROID -> "📱"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Bottom row: Enable/Disable + Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Enable/Disable status
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = folder.isEnabled,
                            onCheckedChange = onToggleEnabled,
                            modifier = Modifier.scale(0.8f)
                        )
                        Text(
                            text = if (folder.isEnabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (folder.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Settings button
                        IconButton(
                            onClick = onAdvancedSettings
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Edit button
                        IconButton(
                            onClick = onEdit
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Delete button
                        IconButton(
                            onClick = onDelete
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
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
                
                // Import/Export Section
                Text(
                    text = "Backup & Restore:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Export button
                    OutlinedButton(
                        onClick = onExport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export")
                    }
                    
                    // Import button
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Export your sync folders and settings to a file, or import from a backup.",
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
                    placeholder = { Text("Movies or C:/Movies") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Use relative path (Movies) or absolute path (C:/Movies)",
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

data class SyncBackup(
    val folders: List<SyncFolder>,
    val serverUrl: String,
    val exportDate: String,
    val version: String = "1.0"
)



@Composable
fun AdvancedSettingsDialog(
    folder: SyncFolder,
    onSave: (SyncFolder) -> Unit,
    onDismiss: () -> Unit
) {
    var androidToPcMode by remember { mutableStateOf(folder.androidToPcMode) }
    var pcToAndroidMode by remember { mutableStateOf(folder.pcToAndroidMode) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Advanced Sync Settings") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                
                // Show sync mode options based on folder's sync direction
                when (folder.syncDirection) {
                    SyncDirection.ANDROID_TO_PC -> {
                        Text("📱→💻 Android to PC Mode:", fontWeight = FontWeight.Bold)
                        SyncMode.values().forEach { mode ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = androidToPcMode == mode,
                                    onClick = { androidToPcMode = mode }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = when (mode) {
                                            SyncMode.COPY_AND_DELETE -> "Move files after successful sync"
                                            SyncMode.MIRROR -> "Compare files, skip duplicates"
                                            SyncMode.SYNC -> "Handle duplicate names intelligently"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    SyncDirection.PC_TO_ANDROID -> {
                        Text("💻→📱 PC to Android Mode:", fontWeight = FontWeight.Bold)
                        SyncMode.values().forEach { mode ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = pcToAndroidMode == mode,
                                    onClick = { pcToAndroidMode = mode }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = when (mode) {
                                            SyncMode.COPY_AND_DELETE -> "Move files after successful sync"
                                            SyncMode.MIRROR -> "Compare files, skip duplicates"
                                            SyncMode.SYNC -> "Handle duplicate names intelligently"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedFolder = folder.copy(
                        androidToPcMode = androidToPcMode,
                        pcToAndroidMode = pcToAndroidMode
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