package com.example.foldersync

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.documentfile.provider.DocumentFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foldersync.ui.theme.FolderSyncTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt



class SyncActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContent {
                FolderSyncTheme {
                    SyncScreen(
                        onFinish = { finish() },
                        activity = this
                    )
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error creating sync screen: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Ensure screen timeout is restored when activity is destroyed
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onFinish: () -> Unit,
    activity: ComponentActivity
) {
    val context = LocalContext.current
    val syncFolders = try {
        loadSyncFolders(context).filter { it.isEnabled }
    } catch (e: Exception) {
        Toast.makeText(context, "Error loading sync folders: ${e.message}", Toast.LENGTH_LONG).show()
        emptyList()
    }
    
    var syncStatuses by remember { 
        mutableStateOf(
            syncFolders.map { folder ->
                SyncStatus(
                    folderId = folder.id,
                    status = SyncState.IDLE
                )
            }
        )
    }
    
    var isRunning by remember { mutableStateOf(false) }
    var overallProgress by remember { mutableStateOf(0f) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    val scope = rememberCoroutineScope()
    
    // Check if keep screen on is enabled
    val sharedPrefs = context.getSharedPreferences("folder_sync_prefs", Context.MODE_PRIVATE)
    val keepScreenOnEnabled = sharedPrefs.getBoolean("keep_screen_on", false)
    
    // Handle keep screen on only during active sync progress
    val hasActiveSync = syncStatuses.any { 
        it.status == SyncState.SCANNING || it.status == SyncState.SYNCING 
    }
    
    LaunchedEffect(hasActiveSync, keepScreenOnEnabled) {
        if (keepScreenOnEnabled && hasActiveSync) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    // Timer to update scanning display every second
    LaunchedEffect(syncStatuses) {
        while (syncStatuses.any { it.status == SyncState.SCANNING }) {
            currentTime = System.currentTimeMillis()
            delay(1000) // Update every second
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Progress") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overall progress
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Overall Progress", fontWeight = FontWeight.Bold)
                        Text("${(overallProgress * 100).roundToInt()}%")
                    }
                    LinearProgressIndicator(
                        progress = { overallProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    val completedFolders = syncStatuses.count { it.status == SyncState.COMPLETED }
                    val errorFolders = syncStatuses.count { it.status == SyncState.ERROR }
                    
                    Text(
                        text = "${completedFolders}/${syncFolders.size} folders completed" + 
                               if (errorFolders > 0) " • $errorFolders errors" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Individual folder progress
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(syncFolders.zip(syncStatuses)) { (folder, status) ->
                    SyncFolderProgressCard(
                        folder = folder,
                        status = status,
                        currentTime = currentTime
                    )
                }
            }
            
            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onFinish,
                    modifier = Modifier.weight(1f),
                    enabled = !isRunning
                ) {
                    Text("Close")
                }
                
                Button(
                    onClick = {
                        if (!isRunning) {
                            isRunning = true
                            scope.launch {
                                startSync(
                                    context = context,
                                    folders = syncFolders,
                                    onStatusUpdate = { updatedStatuses ->
                                        syncStatuses = updatedStatuses
                                        overallProgress = updatedStatuses.map { 
                                            when (it.status) {
                                                SyncState.COMPLETED -> 1f
                                                SyncState.ERROR -> 1f
                                                else -> it.progress
                                            }
                                        }.average().toFloat()
                                    },
                                    onComplete = {
                                        isRunning = false
                                        val errors = syncStatuses.count { it.status == SyncState.ERROR }
                                        val completed = syncStatuses.count { it.status == SyncState.COMPLETED }
                                        
                                        if (errors == 0) {
                                            Toast.makeText(context, "All $completed folders synced successfully!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "$completed folders synced, $errors failed", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isRunning && syncFolders.isNotEmpty()
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Sync")
                }
            }
        }
    }
}

@Composable
fun SyncFolderProgressCard(
    folder: SyncFolder,
    status: SyncStatus,
    currentTime: Long = System.currentTimeMillis()
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status.status) {
                SyncState.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer
                SyncState.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        fontWeight = FontWeight.Medium
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
                    
                    // Sync direction and mode display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Source icon
                        Text(
                            text = when(folder.syncDirection) {
                                SyncDirection.ANDROID_TO_PC -> "📱"
                                SyncDirection.PC_TO_ANDROID -> "💻"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Text(
                            text = " → ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // Sync mode
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Text(
                            text = " → ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // Target icon
                        Text(
                            text = when(folder.syncDirection) {
                                SyncDirection.ANDROID_TO_PC -> "💻"
                                SyncDirection.PC_TO_ANDROID -> "📱"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                when (status.status) {
                    SyncState.COMPLETED -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = Color.Green
                            )
                            if (status.scanStartTime > 0 && status.completionTime > 0) {
                                val scanTime = if (status.syncStartTime > 0) {
                                    (status.syncStartTime - status.scanStartTime) / 1000
                                } else {
                                    (status.completionTime - status.scanStartTime) / 1000
                                }
                                val syncTime = if (status.syncStartTime > 0) {
                                    (status.completionTime - status.syncStartTime) / 1000
                                } else 0
                                
                                Text(
                                    text = "${scanTime}s scan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (syncTime > 0) {
                                    Text(
                                        text = "${syncTime}s sync",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    SyncState.ERROR -> Icon(
                        Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color.Red
                    )
                    SyncState.SYNCING -> Text("${(status.progress * 100).roundToInt()}%")
                    SyncState.SCANNING -> {
                        val elapsedSeconds = if (status.scanStartTime > 0) {
                            (currentTime - status.scanStartTime) / 1000
                        } else 0
                        Text("Scanning (${elapsedSeconds}s)")
                    }
                    else -> Text(status.status.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
            
            if (status.status == SyncState.SYNCING || status.status == SyncState.SCANNING) {
                LinearProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (status.currentFile.isNotEmpty()) {
                    Text(
                        text = "Processing: ${status.currentFile}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (status.totalFiles > 0) {
                    Text(
                        text = "${status.filesProcessed}/${status.totalFiles} files",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            if (status.errorMessage != null) {
                Text(
                    text = "Error: ${status.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            // Show detailed sync summary when completed
            if (status.status == SyncState.COMPLETED && status.syncSummary != null) {
                val summary = status.syncSummary
                val totalOperations = summary.uploadedFiles.size + summary.downloadedFiles.size + 
                                    summary.updatedFiles.size + summary.deletedFiles.size
                
                if (totalOperations > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sync Summary:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (summary.uploadedFiles.isNotEmpty()) {
                        Text(
                            text = "📱→💻 Uploaded (${summary.uploadedFiles.size}):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        summary.uploadedFiles.forEach { file ->
                            Text(
                                text = "  • $file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    if (summary.downloadedFiles.isNotEmpty()) {
                        Text(
                            text = "💻→📱 Downloaded (${summary.downloadedFiles.size}):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        summary.downloadedFiles.forEach { file ->
                            Text(
                                text = "  • $file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    if (summary.updatedFiles.isNotEmpty()) {
                        Text(
                            text = "🔄 Updated (${summary.updatedFiles.size}):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        summary.updatedFiles.forEach { file ->
                            Text(
                                text = "  • $file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    if (summary.deletedFiles.isNotEmpty()) {
                        Text(
                            text = "🗑️ Deleted (${summary.deletedFiles.size}):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        summary.deletedFiles.forEach { file ->
                            Text(
                                text = "  • $file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    if (summary.skippedFiles.isNotEmpty()) {
                        Text(
                            text = "⏭️ Skipped (${summary.skippedFiles.size}):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        // Show first 10 skipped files, then show count if more
                        summary.skippedFiles.take(10).forEach { file ->
                            Text(
                                text = "  • $file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (summary.skippedFiles.size > 10) {
                            Text(
                                text = "  ... and ${summary.skippedFiles.size - 10} more files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }
        }
    }
}

suspend fun startSync(
    context: Context,
    folders: List<SyncFolder>,
    onStatusUpdate: (List<SyncStatus>) -> Unit,
    onComplete: () -> Unit
) {
    val statuses = folders.map { folder ->
        SyncStatus(
            folderId = folder.id,
            status = SyncState.IDLE
        )
    }.toMutableList()
    
    try {
        // Get server URL from preferences
        val sharedPrefs = context.getSharedPreferences("folder_sync_prefs", Context.MODE_PRIVATE)
        val serverUrl = sharedPrefs.getString("server_url", "http://192.168.0.101:5016") ?: "http://192.168.0.101:5016"
        
        folders.forEachIndexed { index, folder ->
            try {
                val scanStartTime = System.currentTimeMillis()
                
                // Update status to scanning
                statuses[index] = statuses[index].copy(
                    status = SyncState.SCANNING,
                    progress = 0f,
                    currentFile = "Scanning Android folder...",
                    scanStartTime = scanStartTime
                )
                onStatusUpdate(statuses.toList())
                
                var totalOperations = 0
                var completedOperations = 0
                var failedOperations = 0
                val failedFiles = mutableListOf<String>()
                
                // Track sync operations for summary
                val uploadedFiles = mutableListOf<String>()
                val downloadedFiles = mutableListOf<String>()
                val updatedFiles = mutableListOf<String>()
                val deletedFiles = mutableListOf<String>()
                val skippedFiles = mutableListOf<String>()
                
                // Handle Android to PC sync
                if (folder.syncDirection == SyncDirection.ANDROID_TO_PC) {
                    val androidFiles = scanAndroidFolder(context, folder)
                    
                    // For Mirror mode, compare with PC files first
                    val allFilesToSync = if (folder.androidToPcMode == SyncMode.MIRROR) {
                        android.util.Log.i("FolderSync", "🔍 Using Mirror mode for Android → PC sync")
                        val pcFiles = scanPcFolder(serverUrl, folder, context)
                        android.util.Log.i("FolderSync", "📊 Found ${androidFiles.size} Android files and ${pcFiles.size} PC files")
                        compareAndFilterFiles(androidFiles, pcFiles, "📱→💻")
                    } else {
                        android.util.Log.i("FolderSync", "📤 Using ${folder.androidToPcMode} mode for Android → PC sync")
                        androidFiles.map { FileToSync(it, null, SyncAction.UPLOAD) }
                    }
                    
                    // Only count files that actually need to be processed (exclude SKIP actions)
                    val filesToSync = allFilesToSync.filter { it.action != SyncAction.SKIP }
                    
                    totalOperations += filesToSync.size
                    
                    // Log skipped files and add to summary
                    allFilesToSync.filter { it.action == SyncAction.SKIP }.forEach { skipped ->
                        val fileName = skipped.androidFile?.name ?: "unknown"
                        skippedFiles.add(fileName)
                        android.util.Log.i("FolderSync", "⏭️ Skipped file: $fileName (already exists or conflict)")
                    }
                    
                    val uploadCount = filesToSync.count { it.action == SyncAction.UPLOAD }
                    val updateCount = filesToSync.count { it.action == SyncAction.UPDATE }
                    val deleteCount = filesToSync.count { it.action == SyncAction.DELETE }
                    android.util.Log.i("FolderSync", "📱→💻 Mirror sync plan: $uploadCount uploads, $updateCount updates, $deleteCount deletions")
                    
                    // Debug: List all DELETE actions
                    filesToSync.filter { it.action == SyncAction.DELETE }.forEach { deleteAction ->
                        android.util.Log.d("FolderSync", "📱→💻 🗑️ DELETE planned: ${deleteAction.pcFile?.path}")
                    }
                    
                    if (filesToSync.isNotEmpty()) {
                        val syncStartTime = System.currentTimeMillis()
                        statuses[index] = statuses[index].copy(
                            status = SyncState.SYNCING,
                            totalFiles = totalOperations,
                            currentFile = "Syncing Android → PC",
                            syncStartTime = syncStartTime
                        )
                        onStatusUpdate(statuses.toList())
                        
                        filesToSync.forEachIndexed { fileIndex, fileToSync ->
                            val displayName = when (fileToSync.action) {
                                SyncAction.DELETE -> "🗑️ ${fileToSync.pcFile?.path ?: "unknown"}"
                                SyncAction.UPDATE -> "🔄 ${fileToSync.androidFile?.name ?: fileToSync.pcFile?.path ?: "unknown"}"
                                else -> {
                                    val androidFile = fileToSync.androidFile
                                    if (androidFile != null) {
                                        if (androidFile.relativePath.isEmpty()) {
                                            androidFile.name
                                        } else {
                                            "${androidFile.relativePath}/${androidFile.name}"
                                        }
                                    } else {
                                        "unknown file"
                                    }
                                }
                            }
                            
                            statuses[index] = statuses[index].copy(
                                progress = completedOperations.toFloat() / totalOperations,
                                filesProcessed = completedOperations,
                                currentFile = "📱→💻 $displayName"
                            )
                            onStatusUpdate(statuses.toList())
                            
                            try {
                                when (fileToSync.action) {
                                    SyncAction.UPLOAD -> {
                                        val androidFile = fileToSync.androidFile
                                        if (androidFile != null) {
                                            uploadFileToServer(context, serverUrl, folder, androidFile, isUpdate = false)
                                            completedOperations++
                                            uploadedFiles.add(androidFile.name)
                                            
                                            // Handle post-sync actions based on sync mode
                                            if (folder.androidToPcMode == SyncMode.COPY_AND_DELETE) {
                                                try {
                                                    deleteFileFromAndroid(context, androidFile)
                                                    android.util.Log.i("FolderSync", "Successfully uploaded and deleted: ${androidFile.name}")
                                                } catch (e: Exception) {
                                                    android.util.Log.w("FolderSync", "Upload succeeded but failed to delete ${androidFile.name}: ${e.message}")
                                                }
                                            }
                                        } else {
                                            android.util.Log.e("FolderSync", "UPLOAD action but androidFile is null")
                                            completedOperations++
                                        }
                                    }
                                    SyncAction.UPDATE -> {
                                        val androidFile = fileToSync.androidFile
                                        if (androidFile != null) {
                                            // Update = Upload new version (will overwrite existing)
                                            uploadFileToServer(context, serverUrl, folder, androidFile, isUpdate = true)
                                            completedOperations++
                                            updatedFiles.add("📱→💻 ${androidFile.name}")
                                            android.util.Log.i("FolderSync", "Successfully updated PC file: ${androidFile.name}")
                                        } else {
                                            android.util.Log.e("FolderSync", "UPDATE action but androidFile is null")
                                            completedOperations++
                                        }
                                    }
                                    SyncAction.DELETE -> {
                                        try {
                                            // Delete file from PC server
                                            deleteFileFromServer(serverUrl, folder, fileToSync.pcFile!!, context)
                                            completedOperations++
                                            deletedFiles.add("💻 ${fileToSync.pcFile.path}")
                                            android.util.Log.i("FolderSync", "Successfully deleted from PC: ${fileToSync.pcFile.path}")
                                        } catch (deleteException: Exception) {
                                            failedOperations++
                                            failedFiles.add("🗑️💻 ${fileToSync.pcFile!!.path}")
                                            android.util.Log.e("FolderSync", "Delete failed for ${fileToSync.pcFile.path}: ${deleteException.message}")
                                        }
                                    }
                                    else -> {
                                        completedOperations++
                                    }
                                }
                                
                            } catch (syncException: Exception) {
                                failedOperations++
                                val fileName = fileToSync.androidFile?.name ?: fileToSync.pcFile?.path ?: "unknown"
                                failedFiles.add("📱→💻 $fileName")
                                android.util.Log.e("FolderSync", "Sync failed for $fileName: ${syncException.message}")
                            }
                            
                            delay(100)
                        }
                    }
                }
                
                // Handle PC to Android sync
                if (folder.syncDirection == SyncDirection.PC_TO_ANDROID) {
                    val pcFiles = scanPcFolder(serverUrl, folder, context)
                    
                    // For Mirror mode, compare with Android files first
                    val allFilesToSync = if (folder.pcToAndroidMode == SyncMode.MIRROR) {
                        android.util.Log.i("FolderSync", "🔍 Using Mirror mode for PC → Android sync")
                        val androidFiles = scanAndroidFolder(context, folder)
                        android.util.Log.i("FolderSync", "📊 Found ${pcFiles.size} PC files and ${androidFiles.size} Android files")
                        compareAndFilterFiles(androidFiles, pcFiles, "💻→📱")
                    } else {
                        android.util.Log.i("FolderSync", "📥 Using ${folder.pcToAndroidMode} mode for PC → Android sync")
                        pcFiles.map { FileToSync(null, it, SyncAction.DOWNLOAD) }
                    }
                    
                    // Only count files that actually need to be processed (exclude SKIP actions)
                    val filesToSync = allFilesToSync.filter { it.action != SyncAction.SKIP }
                    
                    totalOperations += filesToSync.size
                    
                    // Log skipped files and add to summary
                    allFilesToSync.filter { it.action == SyncAction.SKIP }.forEach { skipped ->
                        val fileName = skipped.pcFile?.path ?: "unknown"
                        skippedFiles.add(fileName)
                        android.util.Log.i("FolderSync", "⏭️ Skipped file: $fileName (already exists or conflict)")
                    }
                    
                    val downloadCount = filesToSync.count { it.action == SyncAction.DOWNLOAD }
                    val updateCount = filesToSync.count { it.action == SyncAction.UPDATE }
                    val deleteCount = filesToSync.count { it.action == SyncAction.DELETE }
                    android.util.Log.i("FolderSync", "💻→📱 Mirror sync plan: $downloadCount downloads, $updateCount updates, $deleteCount deletions")
                    
                    if (filesToSync.isNotEmpty()) {
                        val syncStartTime = System.currentTimeMillis()
                        statuses[index] = statuses[index].copy(
                            status = SyncState.SYNCING,
                            totalFiles = totalOperations,
                            currentFile = "Syncing PC → Android",
                            syncStartTime = syncStartTime
                        )
                        onStatusUpdate(statuses.toList())
                        
                        filesToSync.forEachIndexed { fileIndex, fileToSync ->
                            statuses[index] = statuses[index].copy(
                                progress = completedOperations.toFloat() / totalOperations,
                                filesProcessed = completedOperations,
                                currentFile = when (fileToSync.action) {
                                    SyncAction.DELETE -> "💻→📱 🗑️ ${fileToSync.androidFile?.name ?: "unknown"}"
                                    SyncAction.UPDATE -> "💻→📱 🔄 ${fileToSync.pcFile?.path ?: "unknown"}"
                                    else -> "💻→📱 ${fileToSync.pcFile?.path ?: "unknown"}"
                                }
                            )
                            onStatusUpdate(statuses.toList())
                            
                            try {
                                when (fileToSync.action) {
                                    SyncAction.DOWNLOAD -> {
                                        val pcFile = fileToSync.pcFile
                                        if (pcFile != null) {
                                            downloadFileFromServer(context, serverUrl, folder, pcFile, isUpdate = false)
                                            completedOperations++
                                            downloadedFiles.add(pcFile.path)
                                            
                                            // Handle post-sync actions based on sync mode
                                            if (folder.pcToAndroidMode == SyncMode.COPY_AND_DELETE) {
                                                try {
                                                    deleteFileFromServer(serverUrl, folder, pcFile, context)
                                                    android.util.Log.i("FolderSync", "Successfully downloaded and deleted: ${pcFile.path}")
                                                } catch (e: Exception) {
                                                    android.util.Log.w("FolderSync", "Download succeeded but failed to delete ${pcFile.path}: ${e.message}")
                                                }
                                            }
                                        } else {
                                            android.util.Log.e("FolderSync", "DOWNLOAD action but pcFile is null")
                                            completedOperations++
                                        }
                                    }
                                    SyncAction.UPDATE -> {
                                        val pcFile = fileToSync.pcFile
                                        if (pcFile != null) {
                                            // Update = Download new version (will overwrite existing)
                                            downloadFileFromServer(context, serverUrl, folder, pcFile, isUpdate = true)
                                            completedOperations++
                                            updatedFiles.add("💻→📱 ${pcFile.path}")
                                            android.util.Log.i("FolderSync", "Successfully updated Android file: ${pcFile.path}")
                                        } else {
                                            android.util.Log.e("FolderSync", "UPDATE action but pcFile is null")
                                            completedOperations++
                                        }
                                    }
                                    SyncAction.DELETE -> {
                                        try {
                                            // Delete file from Android
                                            val androidFile = fileToSync.androidFile
                                            if (androidFile != null) {
                                                deleteFileFromAndroid(context, androidFile)
                                                completedOperations++
                                                deletedFiles.add("📱 ${androidFile.name}")
                                                android.util.Log.i("FolderSync", "Successfully deleted from Android: ${androidFile.name}")
                                            } else {
                                                android.util.Log.e("FolderSync", "DELETE action but androidFile is null")
                                                completedOperations++
                                            }
                                        } catch (deleteException: Exception) {
                                            failedOperations++
                                            failedFiles.add("🗑️📱 ${fileToSync.androidFile?.name ?: "unknown"}")
                                            android.util.Log.e("FolderSync", "Delete failed for ${fileToSync.androidFile?.name ?: "unknown"}: ${deleteException.message}")
                                        }
                                    }
                                    else -> {
                                        completedOperations++
                                    }
                                }
                                
                            } catch (syncException: Exception) {
                                failedOperations++
                                val fileName = fileToSync.pcFile?.path ?: fileToSync.androidFile?.name ?: "unknown"
                                failedFiles.add("💻→📱 $fileName")
                                android.util.Log.e("FolderSync", "Sync failed for $fileName: ${syncException.message}")
                            }
                            
                            delay(100)
                        }
                    }
                }
                
                // Create sync summary
                val syncSummary = SyncSummary(
                    uploadedFiles = uploadedFiles,
                    downloadedFiles = downloadedFiles,
                    updatedFiles = updatedFiles,
                    deletedFiles = deletedFiles,
                    skippedFiles = skippedFiles,
                    failedFiles = failedFiles
                )
                
                // Update final status based on results
                val completionTime = System.currentTimeMillis()
                if (totalOperations == 0) {
                    statuses[index] = statuses[index].copy(
                        status = SyncState.COMPLETED,
                        progress = 1f,
                        currentFile = "No files to sync",
                        syncSummary = syncSummary,
                        completionTime = completionTime
                    )
                } else if (failedOperations == 0) {
                    statuses[index] = statuses[index].copy(
                        status = SyncState.COMPLETED,
                        progress = 1f,
                        filesProcessed = completedOperations,
                        currentFile = "",
                        syncSummary = syncSummary,
                        completionTime = completionTime
                    )
                } else if (completedOperations > 0) {
                    statuses[index] = statuses[index].copy(
                        status = SyncState.ERROR,
                        progress = 1f,
                        filesProcessed = completedOperations,
                        currentFile = "",
                        errorMessage = "Failed: ${failedOperations}/${totalOperations} operations. ${failedFiles.take(2).joinToString(", ")}${if (failedFiles.size > 2) "..." else ""}",
                        syncSummary = syncSummary,
                        completionTime = completionTime
                    )
                } else {
                    statuses[index] = statuses[index].copy(
                        status = SyncState.ERROR,
                        progress = 1f,
                        filesProcessed = 0,
                        currentFile = "",
                        errorMessage = "Failed all ${failedOperations} operations",
                        syncSummary = syncSummary,
                        completionTime = completionTime
                    )
                }
                onStatusUpdate(statuses.toList())
                
            } catch (e: Exception) {
                statuses[index] = statuses[index].copy(
                    status = SyncState.ERROR,
                    errorMessage = e.message
                )
                onStatusUpdate(statuses.toList())
            }
        }
    } catch (e: Exception) {
        // Handle global errors
        statuses.forEachIndexed { index, status ->
            if (status.status != SyncState.COMPLETED) {
                statuses[index] = status.copy(
                    status = SyncState.ERROR,
                    errorMessage = e.message
                )
            }
        }
        onStatusUpdate(statuses.toList())
    }
    
    onComplete()
}

data class AndroidFile(
    val name: String,
    val uri: android.net.Uri,
    val size: Long,
    val relativePath: String = "", // Path relative to the root sync folder
    val lastModified: Long = 0L // Last modified timestamp
)

suspend fun scanAndroidFolder(context: Context, folder: SyncFolder): List<AndroidFile> = withContext(kotlinx.coroutines.Dispatchers.IO) {
    val files = mutableListOf<AndroidFile>()
    
    try {
        // If we have a URI, scan that folder
        folder.androidUri?.let { uri ->
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            documentFile?.let { 
                android.util.Log.i("FolderSync", "🚀 Starting parallel scan of Android folder: ${folder.androidPath}")
                android.util.Log.i("FolderSync", "🚫 Ignore prefixes: '${folder.ignorePrefixes}'")
                android.util.Log.i("FolderSync", "🚫 Ignore suffixes: '${folder.ignoreSuffixes}'")
                android.util.Log.i("FolderSync", "🚫 Ignore folders: '${folder.ignoreFolders}'")
                android.util.Log.i("FolderSync", "✅ Whitelist prefixes: '${folder.whitelistPrefixes}'")
                android.util.Log.i("FolderSync", "✅ Whitelist suffixes: '${folder.whitelistSuffixes}'")
                android.util.Log.i("FolderSync", "✅ Whitelist folders: '${folder.whitelistFolders}'")
                val startTime = System.currentTimeMillis()
                scanDocumentFolderParallel(it, files, "", folder.ignorePrefixes, folder.ignoreSuffixes, folder.ignoreFolders, folder.whitelistPrefixes, folder.whitelistSuffixes, folder.whitelistFolders)
                val endTime = System.currentTimeMillis()
                android.util.Log.i("FolderSync", "✅ Parallel scan completed in ${endTime - startTime}ms, found ${files.size} files")
            }
        }
        
        // No test files created - only sync real files
        
    } catch (e: Exception) {
        // Log error but don't create test files
        android.util.Log.w("FolderSync", "Error scanning folder: ${e.message}")
    }
    
    files
}

suspend fun scanDocumentFolderParallel(documentFile: DocumentFile, files: MutableList<AndroidFile>, currentPath: String, ignorePrefixes: String, ignoreSuffixes: String, ignoreFolders: String = "", whitelistPrefixes: String = "", whitelistSuffixes: String = "", whitelistFolders: String = ""): Unit = withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val allFiles = documentFile.listFiles()
        android.util.Log.i("FolderSync", "📁 Scanning directory: $currentPath (${allFiles.size} items)")
        
        // Process files with optimized approach - batch processing
        val filesList = mutableListOf<DocumentFile>()
        val dirsList = mutableListOf<DocumentFile>()
        
        // Separate files and directories first
        allFiles.forEach { file ->
            try {
                if (file.isFile && file.canRead()) {
                    filesList.add(file)
                } else if (file.isDirectory && file.canRead()) {
                    val folderName = file.name
                    if (!folderName.isNullOrBlank()) {
                        // Check if folder should be ignored
                        if (shouldIgnoreFolder(folderName, ignoreFolders)) {
                            android.util.Log.d("FolderSync", "🚫 Ignoring folder: $folderName")
                        } else if (!shouldWhitelistFolder(folderName, whitelistFolders)) {
                            android.util.Log.d("FolderSync", "🚫 Folder not in whitelist: $folderName")
                        } else {
                            dirsList.add(file)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("FolderSync", "Skipping item due to error: ${e.message}")
            }
        }
        
        // Process files in smaller batches to reduce memory pressure
        val batchSize = 100
        val batches = filesList.chunked(batchSize)
        
        batches.forEach { batch ->
            val batchResults = mutableListOf<AndroidFile>()
            
            // Process each batch
            batch.forEach { file ->
                try {
                    val fileName = file.name
                    val fileSize = file.length()
                    
                    if (!fileName.isNullOrBlank() && fileSize > 0) {
                        // Check if file should be ignored
                        if (shouldIgnoreFile(fileName, ignorePrefixes, ignoreSuffixes)) {
                            android.util.Log.d("FolderSync", "🚫 Ignoring file: $fileName")
                            return@forEach
                        }
                        
                        // Check if file is in whitelist
                        if (!shouldWhitelistFile(fileName, whitelistPrefixes, whitelistSuffixes)) {
                            android.util.Log.d("FolderSync", "🚫 File not in whitelist: $fileName")
                            return@forEach
                        }
                        
                        // Get last modified time with optimized approach
                        val lastModified = try {
                            file.lastModified()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }
                        
                        batchResults.add(AndroidFile(
                            name = fileName,
                            uri = file.uri,
                            size = fileSize,
                            relativePath = currentPath,
                            lastModified = lastModified
                        ))
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FolderSync", "Error processing file: ${e.message}")
                }
            }
            
            // Add batch results
            synchronized(files) {
                files.addAll(batchResults)
            }
            
            // Small delay to prevent overwhelming the system
            if (batches.size > 1) {
                kotlinx.coroutines.delay(10)
            }
        }
        
        // Process directories recursively
        for (dir: DocumentFile in dirsList) {
            try {
                val folderName: String? = dir.name
                if (!folderName.isNullOrBlank()) {
                    val subPath: String = if (currentPath.isEmpty()) folderName else "$currentPath/$folderName"
                    scanDocumentFolderParallel(dir, files, subPath, ignorePrefixes, ignoreSuffixes, ignoreFolders, whitelistPrefixes, whitelistSuffixes, whitelistFolders)
                }
            } catch (e: Exception) {
                android.util.Log.w("FolderSync", "Error scanning subdirectory: ${e.message}")
            }
        }
        
    } catch (e: Exception) {
        android.util.Log.w("FolderSync", "Error scanning folder: ${e.message}")
    }
}

// Keep the original function for compatibility
fun scanDocumentFolder(documentFile: DocumentFile, files: MutableList<AndroidFile>, currentPath: String) {
    try {
        documentFile.listFiles().forEach { file ->
            try {
                if (file.isFile) {
                    val fileName = file.name
                    val fileSize = file.length()
                    
                    if (!fileName.isNullOrBlank() && file.canRead() && fileSize > 0) {
                        // Get last modified time
                        val lastModified = try {
                            file.lastModified()
                        } catch (e: Exception) {
                            System.currentTimeMillis() // Fallback to current time
                        }
                        
                        // Include all files regardless of size, with their relative path and modification time
                        files.add(AndroidFile(
                            name = fileName,
                            uri = file.uri,
                            size = fileSize,
                            relativePath = currentPath,
                            lastModified = lastModified
                        ))
                    }
                } else if (file.isDirectory && file.canRead()) {
                    val folderName = file.name
                    if (!folderName.isNullOrBlank()) {
                        // Build the relative path for subdirectory
                        val subPath = if (currentPath.isEmpty()) folderName else "$currentPath/$folderName"
                        // Recursively scan subdirectories with updated path
                        scanDocumentFolder(file, files, subPath)
                    }
                }
            } catch (e: Exception) {
                // Skip files that can't be accessed
                android.util.Log.w("FolderSync", "Skipping file due to error: ${e.message}")
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("FolderSync", "Error scanning folder: ${e.message}")
    }
}

fun createTestFile(context: Context, filename: String, content: String): android.net.Uri {
    // Create a temporary file for testing
    val file = java.io.File(context.cacheDir, filename)
    file.writeText(content)
    return android.net.Uri.fromFile(file)
}

suspend fun uploadFileToServer(context: Context, serverUrl: String, folder: SyncFolder, file: AndroidFile, isUpdate: Boolean = false) = withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        // Validate inputs
        if (file.name.isBlank()) {
            throw Exception("File name is empty")
        }
        
        if (folder.pcPath.isBlank()) {
            throw Exception("PC path is empty")
        }
        
        // Get actual file info
        val cursor = context.contentResolver.query(file.uri, null, null, null, null)
        var actualFileName = file.name
        var fileSize = file.size
        
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                
                if (nameIndex >= 0) {
                    actualFileName = it.getString(nameIndex) ?: file.name
                }
                if (sizeIndex >= 0) {
                    fileSize = it.getLong(sizeIndex)
                }
            }
        }
        
        // Create temporary file (like the working example)
        val inputStream = context.contentResolver.openInputStream(file.uri)
            ?: throw Exception("Cannot open file: $actualFileName")
        
        val tempFile = java.io.File(context.cacheDir, actualFileName.replace("/", "_"))
        
        try {
            inputStream.use { input ->
                java.io.FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw Exception("Failed to create temporary file or file is empty")
            }
            
            // Create HTTP client with configurable timeout
            val timeoutSeconds = getTimeoutSeconds(context)
            val clientBuilder = okhttp3.OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
            
            if (timeoutSeconds > 0) {
                clientBuilder
                    .connectTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            }
            
            val client = clientBuilder.build()
            
            // Create request body from temp file (like the working example)
            val requestBody = tempFile.asRequestBody("application/octet-stream".toMediaType())
            
            // Build the full file path including subdirectories
            val fullFilePath = if (file.relativePath.isEmpty()) {
                actualFileName
            } else {
                "${file.relativePath}/$actualFileName"
            }
            
            // Determine sync behavior based on sync mode and update flag
            val syncMode = if (isUpdate) "UPDATE" else folder.androidToPcMode.name
            val handleDuplicates = when {
                isUpdate -> false                  // Updates should overwrite
                folder.androidToPcMode == SyncMode.COPY_AND_DELETE -> false  // Overwrite duplicates
                folder.androidToPcMode == SyncMode.MIRROR -> false           // Skip duplicates (handled by server)
                folder.androidToPcMode == SyncMode.SYNC -> true              // Handle duplicate names intelligently
                else -> false
            }
            
            val multipartBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", actualFileName, requestBody)
                .addFormDataPart("original_filename", fullFilePath) // Include relative path
                .addFormDataPart("folder_path", folder.pcPath)
                .addFormDataPart("handle_duplicates", handleDuplicates.toString())
                .addFormDataPart("sync_mode", syncMode)
                .addFormDataPart("file_size", fileSize.toString())
                .build()
            
            val request = okhttp3.Request.Builder()
                .url("$serverUrl/api/upload")
                .post(multipartBody)
                .addHeader("Connection", "keep-alive")
                .build()
            
            // Execute request
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown server error"
                    throw Exception("Server error: HTTP ${response.code} - $errorBody")
                }
                
                android.util.Log.i("FolderSync", "Successfully uploaded: $actualFileName")
            }
            
        } finally {
            // Clean up temp file
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        
    } catch (e: java.net.SocketTimeoutException) {
        throw Exception("${file.name}: Connection timeout")
    } catch (e: java.net.UnknownHostException) {
        throw Exception("${file.name}: Cannot reach server")
    } catch (e: java.io.IOException) {
        throw Exception("${file.name}: Network error - ${e.message}")
    } catch (e: Exception) {
        val errorMsg = e.message ?: "Unknown error occurred"
        throw Exception("${file.name}: $errorMsg")
    }
}

suspend fun testServerConnection(serverUrl: String) = withContext(kotlinx.coroutines.Dispatchers.IO) {
    val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    val request = okhttp3.Request.Builder()
        .url("$serverUrl/api/health")
        .get()
        .build()
    
    try {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No error details"
            response.close()
            throw Exception("Server returned ${response.code}: ${response.message} - $errorBody")
        }
        response.close()
    } catch (e: java.net.ConnectException) {
        throw Exception("Connection refused - server may not be running or firewall is blocking")
    } catch (e: java.net.UnknownHostException) {
        throw Exception("Cannot resolve host - check IP address")
    } catch (e: java.net.SocketTimeoutException) {
        throw Exception("Connection timeout - server may be unreachable")
    } catch (e: Exception) {
        throw Exception("Connection error: ${e.message ?: e.javaClass.simpleName}")
    }
}



fun isNetworkAvailable(context: Context): Boolean {
    return try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
        networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
        networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    } catch (e: SecurityException) {
        // If we don't have permission, assume network is available and let the actual request fail with a better error
        android.util.Log.w("FolderSync", "Cannot check network state: ${e.message}")
        true
    } catch (e: Exception) {
        // For any other error, assume network is available
        android.util.Log.w("FolderSync", "Error checking network: ${e.message}")
        true
    }
}

suspend fun deleteFileFromAndroid(context: Context, file: AndroidFile) = withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        // Check if this is a test file in cache directory
        if (file.uri.scheme == "file" && file.uri.path?.contains(context.cacheDir.absolutePath) == true) {
            // Delete test files directly
            val javaFile = java.io.File(file.uri.path!!)
            if (javaFile.exists() && javaFile.delete()) {
                android.util.Log.i("FolderSync", "Deleted test file: ${file.name}")
            }
            return@withContext
        }
        
        // For real document files, use DocumentFile
        val documentFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, file.uri)
        if (documentFile != null && documentFile.exists() && documentFile.canWrite()) {
            if (documentFile.delete()) {
                android.util.Log.i("FolderSync", "Successfully deleted file: ${file.name}")
            } else {
                throw Exception("Failed to delete file: ${file.name}")
            }
        } else {
            throw Exception("Cannot access file for deletion: ${file.name}")
        }
    } catch (e: Exception) {
        android.util.Log.e("FolderSync", "Error deleting file ${file.name}: ${e.message}")
        throw e
    }
}

data class PcFile(
    val path: String,
    val size: Long,
    val modified: Double,
    val hash: String?,
    val lastModified: Long = (modified * 1000).toLong() // Convert to milliseconds
)

suspend fun scanPcFolder(serverUrl: String, folder: SyncFolder, context: Context): List<PcFile> = withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        android.util.Log.i("FolderSync", "Scanning PC folder: '${folder.pcPath}' on server: $serverUrl")
        
        val timeoutSeconds = getTimeoutSeconds(context)
        val clientBuilder = okhttp3.OkHttpClient.Builder()
        
        if (timeoutSeconds > 0) {
            clientBuilder
                .connectTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        }
        
        val client = clientBuilder.build()
        
        val escapedPcPath = folder.pcPath.replace("\\", "\\\\")
        val requestBody = """{"folder_path": "$escapedPcPath"}""".toRequestBody("application/json".toMediaType())
        android.util.Log.i("FolderSync", "Request body: {\"folder_path\": \"${folder.pcPath}\"}")
        
        val request = okhttp3.Request.Builder()
            .url("$serverUrl/api/scan")
            .post(requestBody)
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown server error"
                throw Exception("Server error: HTTP ${response.code} - $errorBody")
            }
            
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val jsonResponse = org.json.JSONObject(responseBody)
            val filesArray = jsonResponse.getJSONArray("files")
            
            val allPcFiles = mutableListOf<PcFile>()
            for (i in 0 until filesArray.length()) {
                val fileObj = filesArray.getJSONObject(i)
                allPcFiles.add(PcFile(
                    path = fileObj.getString("path"),
                    size = fileObj.getLong("size"),
                    modified = fileObj.getDouble("modified"),
                    hash = fileObj.optString("hash", "")
                ))
            }
            
            // Filter out ignored files and files in ignored folders, and apply whitelist
            val pcFiles = allPcFiles.filter { pcFile ->
                val fileName = pcFile.path.substringAfterLast("/")
                val filePath = pcFile.path
                
                // Check if file should be ignored
                val shouldIgnoreFile = shouldIgnoreFile(fileName, folder.ignorePrefixes, folder.ignoreSuffixes)
                if (shouldIgnoreFile) {
                    android.util.Log.d("FolderSync", "🚫 Ignoring PC file: ${pcFile.path}")
                    return@filter false
                }
                
                // Check if file is in whitelist
                if (!shouldWhitelistFile(fileName, folder.whitelistPrefixes, folder.whitelistSuffixes)) {
                    android.util.Log.d("FolderSync", "🚫 PC file not in whitelist: ${pcFile.path}")
                    return@filter false
                }
                
                // Check if file is in an ignored folder
                val pathParts = filePath.split("/")
                for (part in pathParts.dropLast(1)) { // Don't check the filename itself
                    if (shouldIgnoreFolder(part, folder.ignoreFolders)) {
                        android.util.Log.d("FolderSync", "🚫 Ignoring PC file in ignored folder: ${pcFile.path} (folder: $part)")
                        return@filter false
                    }
                    
                    // Check if folder is in whitelist
                    if (!shouldWhitelistFolder(part, folder.whitelistFolders)) {
                        android.util.Log.d("FolderSync", "🚫 PC file in folder not in whitelist: ${pcFile.path} (folder: $part)")
                        return@filter false
                    }
                }
                
                true
            }
            
            android.util.Log.i("FolderSync", "Found ${pcFiles.size} files on PC in folder: ${folder.pcPath} (${allPcFiles.size - pcFiles.size} ignored)")
            return@withContext pcFiles
        }
    } catch (e: Exception) {
        android.util.Log.e("FolderSync", "Error scanning PC folder: ${e.message}")
        throw Exception("Failed to scan PC folder: ${e.message}")
    }
}

suspend fun downloadFileFromServer(context: Context, serverUrl: String, folder: SyncFolder, file: PcFile, isUpdate: Boolean = false) = withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val timeoutSeconds = getTimeoutSeconds(context)
        val clientBuilder = okhttp3.OkHttpClient.Builder()
        
        if (timeoutSeconds > 0) {
            clientBuilder
                .connectTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        }
        
        val client = clientBuilder.build()
        
        val request = okhttp3.Request.Builder()
            .url("$serverUrl/api/download/${java.net.URLEncoder.encode(file.path, "UTF-8").replace("+", "%20")}?folder_path=${java.net.URLEncoder.encode(folder.pcPath, "UTF-8")}")
            .get()
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown server error"
                throw Exception("Server error: HTTP ${response.code} - $errorBody")
            }
            
            val responseBody = response.body ?: throw Exception("Empty response body")
            
            // Get Android folder URI
            val androidUri = folder.androidUri ?: throw Exception("Android folder URI not available")
            val androidFolder = DocumentFile.fromTreeUri(context, androidUri) 
                ?: throw Exception("Cannot access Android folder")
            
            // Create subdirectories if needed
            val pathParts = file.path.split("/")
            var currentFolder = androidFolder
            
            // Navigate/create subdirectories
            for (i in 0 until pathParts.size - 1) {
                val dirName = pathParts[i]
                var subFolder = currentFolder.findFile(dirName)
                if (subFolder == null || !subFolder.isDirectory) {
                    subFolder = currentFolder.createDirectory(dirName)
                        ?: throw Exception("Cannot create directory: $dirName")
                }
                currentFolder = subFolder
            }
            
            // Get filename
            val fileName = pathParts.last()
            
            // Handle existing files based on sync mode and update flag
            val existingFile = currentFolder.findFile(fileName)
            if (existingFile != null && existingFile.exists()) {
                when {
                    isUpdate -> {
                        // For updates, always replace the existing file
                        existingFile.delete()
                        android.util.Log.i("FolderSync", "Replacing existing file for update: $fileName")
                    }
                    folder.pcToAndroidMode == SyncMode.MIRROR -> {
                        // Skip if file already exists (shouldn't happen in Mirror mode with proper comparison)
                        android.util.Log.i("FolderSync", "Skipping existing file: $fileName")
                        return@withContext
                    }
                    folder.pcToAndroidMode == SyncMode.COPY_AND_DELETE || folder.pcToAndroidMode == SyncMode.SYNC -> {
                        // Delete existing file to replace it
                        existingFile.delete()
                    }
                }
            }
            
            // Create new file
            val newFile = currentFolder.createFile("application/octet-stream", fileName)
                ?: throw Exception("Cannot create file: $fileName")
            
            // Write file content
            context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                responseBody.byteStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw Exception("Cannot open output stream for: $fileName")
            
            android.util.Log.i("FolderSync", "Successfully downloaded: ${file.path}")
        }
    } catch (e: Exception) {
        android.util.Log.e("FolderSync", "Error downloading file ${file.path}: ${e.message}")
        throw Exception("${file.path}: ${e.message}")
    }
}

suspend fun deleteFileFromServer(serverUrl: String, folder: SyncFolder, file: PcFile, context: Context) = withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val timeoutSeconds = getTimeoutSeconds(context)
        val clientBuilder = okhttp3.OkHttpClient.Builder()
        
        if (timeoutSeconds > 0) {
            clientBuilder
                .connectTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        }
        
        val client = clientBuilder.build()
        
        val request = okhttp3.Request.Builder()
            .url("$serverUrl/api/delete/${java.net.URLEncoder.encode(file.path, "UTF-8").replace("+", "%20")}?folder_path=${java.net.URLEncoder.encode(folder.pcPath, "UTF-8")}")
            .delete()
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown server error"
                throw Exception("Server error: HTTP ${response.code} - $errorBody")
            }
            
            android.util.Log.i("FolderSync", "Successfully deleted from server: ${file.path}")
        }
        
    } catch (e: Exception) {
        android.util.Log.e("FolderSync", "Error deleting file from server ${file.path}: ${e.message}")
        throw e
    }
}

data class FileToSync(
    val androidFile: AndroidFile?,
    val pcFile: PcFile?,
    val action: SyncAction
)

enum class SyncAction {
    UPLOAD,      // Upload Android file to PC
    DOWNLOAD,    // Download PC file to Android
    SKIP,        // Skip due to conflict or already exists
    DELETE,      // Delete file
    UPDATE       // Update file (newer version available)
}

fun compareAndFilterFiles(
    androidFiles: List<AndroidFile>,
    pcFiles: List<PcFile>,
    direction: String
): List<FileToSync> {
    val filesToSync = mutableListOf<FileToSync>()
    
    // Create maps for quick lookup
    val androidFileMap = androidFiles.associateBy { 
        if (it.relativePath.isEmpty()) it.name else "${it.relativePath}/${it.name}"
    }
    val pcFileMap = pcFiles.associateBy { it.path }
    
    when (direction) {
        "📱→💻" -> {
            // Android to PC sync - check which Android files need to be uploaded
            androidFiles.forEach { androidFile ->
                val fullPath = if (androidFile.relativePath.isEmpty()) {
                    androidFile.name
                } else {
                    "${androidFile.relativePath}/${androidFile.name}"
                }
                
                val matchingPcFile = pcFileMap[fullPath]
                
                when {
                    matchingPcFile == null -> {
                        // File doesn't exist on PC, upload it
                        filesToSync.add(FileToSync(androidFile, null, SyncAction.UPLOAD))
                        android.util.Log.i("FolderSync", "📱→💻 ✅ WILL UPLOAD new file: $fullPath")
                    }
                    androidFile.lastModified > matchingPcFile.lastModified -> {
                        // Android file is newer, update PC file
                        filesToSync.add(FileToSync(androidFile, matchingPcFile, SyncAction.UPDATE))
                        android.util.Log.i("FolderSync", "📱→💻 🔄 WILL UPDATE PC file: $fullPath (Android newer)")
                    }
                    androidFile.lastModified < matchingPcFile.lastModified -> {
                        // PC file is newer, skip (or could update Android if bidirectional)
                        android.util.Log.i("FolderSync", "📱→💻 ⏭️ SKIPPING older file: $fullPath (PC file is newer)")
                    }
                    else -> {
                        // Same modification time, skip
                        android.util.Log.i("FolderSync", "📱→💻 ⏭️ SKIPPING identical file: $fullPath (same modification time)")
                    }
                }
            }
            
            // Check for files that exist on PC but not on Android (need to be deleted for true mirroring)
            pcFiles.forEach { pcFile ->
                val matchingAndroidFile = androidFileMap[pcFile.path]
                if (matchingAndroidFile == null) {
                    // File exists on PC but not on Android, delete it from PC
                    filesToSync.add(FileToSync(null, pcFile, SyncAction.DELETE))
                    android.util.Log.w("FolderSync", "📱→💻 🗑️ WILL DELETE from PC: ${pcFile.path} (not in Android source)")
                }
            }
        }
        
        "💻→📱" -> {
            // PC to Android sync - check which PC files need to be downloaded
            pcFiles.forEach { pcFile ->
                val matchingAndroidFile = androidFileMap[pcFile.path]
                
                when {
                    matchingAndroidFile == null -> {
                        // File doesn't exist on Android, download it
                        filesToSync.add(FileToSync(null, pcFile, SyncAction.DOWNLOAD))
                        android.util.Log.i("FolderSync", "💻→📱 ✅ WILL DOWNLOAD new file: ${pcFile.path}")
                    }
                    pcFile.lastModified > matchingAndroidFile.lastModified -> {
                        // PC file is newer, update Android file
                        filesToSync.add(FileToSync(matchingAndroidFile, pcFile, SyncAction.UPDATE))
                        android.util.Log.i("FolderSync", "💻→📱 🔄 WILL UPDATE Android file: ${pcFile.path} (PC newer)")
                    }
                    pcFile.lastModified < matchingAndroidFile.lastModified -> {
                        // Android file is newer, skip (or could update PC if bidirectional)
                        android.util.Log.i("FolderSync", "💻→📱 ⏭️ SKIPPING older file: ${pcFile.path} (Android file is newer)")
                    }
                    else -> {
                        // Same modification time, skip
                        android.util.Log.i("FolderSync", "💻→📱 ⏭️ SKIPPING identical file: ${pcFile.path} (same modification time)")
                    }
                }
            }
            
            // Check for files that exist on Android but not on PC (need to be deleted for true mirroring)
            androidFiles.forEach { androidFile ->
                val fullPath = if (androidFile.relativePath.isEmpty()) {
                    androidFile.name
                } else {
                    "${androidFile.relativePath}/${androidFile.name}"
                }
                val matchingPcFile = pcFileMap[fullPath]
                if (matchingPcFile == null) {
                    // File exists on Android but not on PC, delete it from Android
                    filesToSync.add(FileToSync(androidFile, null, SyncAction.DELETE))
                    android.util.Log.w("FolderSync", "💻→📱 🗑️ WILL DELETE from Android: $fullPath (not in PC source)")
                }
            }
        }
    }
    
    android.util.Log.i("FolderSync", "$direction Mirror mode: ${filesToSync.count { it.action == SyncAction.UPLOAD || it.action == SyncAction.DOWNLOAD || it.action == SyncAction.UPDATE }} files to sync, ${filesToSync.count { it.action == SyncAction.DELETE }} files to delete")
    
    return filesToSync
}

fun shouldIgnoreFile(fileName: String, ignorePrefixes: String, ignoreSuffixes: String): Boolean {
    if (fileName.isBlank()) return true
    
    // Parse prefixes
    val prefixes = ignorePrefixes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    for (prefix in prefixes) {
        if (fileName.startsWith(prefix)) {
            android.util.Log.d("FolderSync", "🚫 Ignoring file '$fileName' - matches prefix '$prefix'")
            return true
        }
    }
    
    // Parse suffixes
    val suffixes = ignoreSuffixes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    for (suffix in suffixes) {
        if (fileName.endsWith(suffix, ignoreCase = true)) {
            android.util.Log.d("FolderSync", "🚫 Ignoring file '$fileName' - matches suffix '$suffix'")
            return true
        }
    }
    
    return false
}

fun shouldIgnoreFolder(folderName: String, ignoreFolders: String): Boolean {
    if (folderName.isBlank() || ignoreFolders.isBlank()) return false
    
    // Parse folder names
    val folders = ignoreFolders.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    for (ignoreFolder in folders) {
        if (folderName.equals(ignoreFolder, ignoreCase = true)) {
            android.util.Log.d("FolderSync", "🚫 Ignoring folder '$folderName' - matches ignore pattern '$ignoreFolder'")
            return true
        }
    }
    
    return false
}

fun shouldWhitelistFile(fileName: String, whitelistPrefixes: String, whitelistSuffixes: String): Boolean {
    // If whitelist is empty, allow all files
    if (whitelistPrefixes.isBlank() && whitelistSuffixes.isBlank()) return true
    
    if (fileName.isBlank()) return false
    
    // Parse prefixes
    val prefixes = whitelistPrefixes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    for (prefix in prefixes) {
        if (fileName.startsWith(prefix)) {
            android.util.Log.d("FolderSync", "✅ Whitelisting file '$fileName' - matches prefix '$prefix'")
            return true
        }
    }
    
    // Parse suffixes
    val suffixes = whitelistSuffixes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    for (suffix in suffixes) {
        if (fileName.endsWith(suffix, ignoreCase = true)) {
            android.util.Log.d("FolderSync", "✅ Whitelisting file '$fileName' - matches suffix '$suffix'")
            return true
        }
    }
    
    // If whitelist has entries but file doesn't match, reject it
    android.util.Log.d("FolderSync", "🚫 Rejecting file '$fileName' - not in whitelist")
    return false
}

fun shouldWhitelistFolder(folderName: String, whitelistFolders: String): Boolean {
    // If whitelist is empty, allow all folders
    if (whitelistFolders.isBlank()) return true
    
    if (folderName.isBlank()) return false
    
    // Parse folder names
    val folders = whitelistFolders.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    for (whitelistFolder in folders) {
        if (folderName.equals(whitelistFolder, ignoreCase = true)) {
            android.util.Log.d("FolderSync", "✅ Whitelisting folder '$folderName' - matches whitelist pattern '$whitelistFolder'")
            return true
        }
    }
    
    // If whitelist has entries but folder doesn't match, reject it
    android.util.Log.d("FolderSync", "🚫 Rejecting folder '$folderName' - not in whitelist")
    return false
}