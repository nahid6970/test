package com.example.foldersync

import android.content.Context
import android.os.Bundle
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
                        onFinish = { finish() }
                    )
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error creating sync screen: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onFinish: () -> Unit
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
    
    val scope = rememberCoroutineScope()
    
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
                        status = status
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
    status: SyncStatus
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
                        text = "📱 ${folder.androidPath} ↔️ 💻 ${folder.pcPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                when (status.status) {
                    SyncState.COMPLETED -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color.Green
                    )
                    SyncState.ERROR -> Icon(
                        Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color.Red
                    )
                    SyncState.SYNCING -> Text("${(status.progress * 100).roundToInt()}%")
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
                // Update status to scanning
                statuses[index] = statuses[index].copy(
                    status = SyncState.SCANNING,
                    progress = 0f
                )
                onStatusUpdate(statuses.toList())
                
                var totalOperations = 0
                var completedOperations = 0
                var failedOperations = 0
                val failedFiles = mutableListOf<String>()
                
                // Handle Android to PC sync
                if (folder.syncDirection == SyncDirection.ANDROID_TO_PC) {
                    val androidFiles = scanAndroidFolder(context, folder)
                    totalOperations += androidFiles.size
                    
                    if (androidFiles.isNotEmpty()) {
                        statuses[index] = statuses[index].copy(
                            status = SyncState.SYNCING,
                            totalFiles = totalOperations,
                            currentFile = "Syncing Android → PC"
                        )
                        onStatusUpdate(statuses.toList())
                        
                        androidFiles.forEachIndexed { fileIndex, file ->
                            val displayName = if (file.relativePath.isEmpty()) {
                                file.name
                            } else {
                                "${file.relativePath}/${file.name}"
                            }
                            
                            statuses[index] = statuses[index].copy(
                                progress = completedOperations.toFloat() / totalOperations,
                                filesProcessed = completedOperations,
                                currentFile = "📱→💻 $displayName"
                            )
                            onStatusUpdate(statuses.toList())
                            
                            try {
                                uploadFileToServer(context, serverUrl, folder, file)
                                completedOperations++
                                
                                // Handle post-sync actions based on sync mode
                                if (folder.androidToPcMode == SyncMode.COPY_AND_DELETE) {
                                    try {
                                        deleteFileFromAndroid(context, file)
                                        android.util.Log.i("FolderSync", "Successfully uploaded and deleted: ${file.name}")
                                    } catch (e: Exception) {
                                        android.util.Log.w("FolderSync", "Upload succeeded but failed to delete ${file.name}: ${e.message}")
                                    }
                                }
                                
                            } catch (uploadException: Exception) {
                                failedOperations++
                                failedFiles.add("📱→💻 ${file.name}")
                                android.util.Log.e("FolderSync", "Upload failed for ${file.name}: ${uploadException.message}")
                            }
                            
                            delay(100)
                        }
                    }
                }
                
                // Handle PC to Android sync
                if (folder.syncDirection == SyncDirection.PC_TO_ANDROID) {
                    val pcFiles = scanPcFolder(serverUrl, folder)
                    totalOperations += pcFiles.size
                    
                    if (pcFiles.isNotEmpty()) {
                        statuses[index] = statuses[index].copy(
                            status = SyncState.SYNCING,
                            totalFiles = totalOperations,
                            currentFile = "Syncing PC → Android"
                        )
                        onStatusUpdate(statuses.toList())
                        
                        pcFiles.forEachIndexed { fileIndex, file ->
                            statuses[index] = statuses[index].copy(
                                progress = completedOperations.toFloat() / totalOperations,
                                filesProcessed = completedOperations,
                                currentFile = "💻→📱 ${file.path}"
                            )
                            onStatusUpdate(statuses.toList())
                            
                            try {
                                downloadFileFromServer(context, serverUrl, folder, file)
                                completedOperations++
                                
                                // Handle post-sync actions based on sync mode
                                if (folder.pcToAndroidMode == SyncMode.COPY_AND_DELETE) {
                                    try {
                                        deleteFileFromServer(serverUrl, folder, file)
                                        android.util.Log.i("FolderSync", "Successfully downloaded and deleted: ${file.path}")
                                    } catch (e: Exception) {
                                        android.util.Log.w("FolderSync", "Download succeeded but failed to delete ${file.path}: ${e.message}")
                                    }
                                }
                                
                            } catch (downloadException: Exception) {
                                failedOperations++
                                failedFiles.add("💻→📱 ${file.path}")
                                android.util.Log.e("FolderSync", "Download failed for ${file.path}: ${downloadException.message}")
                            }
                            
                            delay(100)
                        }
                    }
                }
                
                // Update final status based on results
                if (totalOperations == 0) {
                    statuses[index] = statuses[index].copy(
                        status = SyncState.COMPLETED,
                        progress = 1f,
                        currentFile = "No files to sync"
                    )
                } else if (failedOperations == 0) {
                    statuses[index] = statuses[index].copy(
                        status = SyncState.COMPLETED,
                        progress = 1f,
                        filesProcessed = completedOperations,
                        currentFile = ""
                    )
                } else if (completedOperations > 0) {
                    statuses[index] = statuses[index].copy(
                        status = SyncState.ERROR,
                        progress = 1f,
                        filesProcessed = completedOperations,
                        currentFile = "",
                        errorMessage = "Failed: ${failedOperations}/${totalOperations} operations. ${failedFiles.take(2).joinToString(", ")}${if (failedFiles.size > 2) "..." else ""}"
                    )
                } else {
                    statuses[index] = statuses[index].copy(
                        status = SyncState.ERROR,
                        progress = 1f,
                        filesProcessed = 0,
                        currentFile = "",
                        errorMessage = "Failed all ${failedOperations} operations"
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
    val relativePath: String = "" // Path relative to the root sync folder
)

suspend fun scanAndroidFolder(context: Context, folder: SyncFolder): List<AndroidFile> {
    val files = mutableListOf<AndroidFile>()
    
    try {
        // If we have a URI, scan that folder
        folder.androidUri?.let { uri ->
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            documentFile?.let { scanDocumentFolder(it, files, "") }
        }
        
        // No test files created - only sync real files
        
    } catch (e: Exception) {
        // Log error but don't create test files
        android.util.Log.w("FolderSync", "Error scanning folder: ${e.message}")
    }
    
    return files
}

fun scanDocumentFolder(documentFile: DocumentFile, files: MutableList<AndroidFile>, currentPath: String) {
    try {
        documentFile.listFiles().forEach { file ->
            try {
                if (file.isFile) {
                    val fileName = file.name
                    val fileSize = file.length()
                    
                    if (!fileName.isNullOrBlank() && file.canRead() && fileSize > 0) {
                        // Include all files regardless of size, with their relative path
                        files.add(AndroidFile(
                            name = fileName,
                            uri = file.uri,
                            size = fileSize,
                            relativePath = currentPath
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

suspend fun uploadFileToServer(context: Context, serverUrl: String, folder: SyncFolder, file: AndroidFile) = withContext(kotlinx.coroutines.Dispatchers.IO) {
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
            
            // Create HTTP client (like the working example)
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
            
            // Create request body from temp file (like the working example)
            val requestBody = tempFile.asRequestBody("application/octet-stream".toMediaType())
            
            // Build the full file path including subdirectories
            val fullFilePath = if (file.relativePath.isEmpty()) {
                actualFileName
            } else {
                "${file.relativePath}/$actualFileName"
            }
            
            // Determine sync behavior based on sync mode
            val syncMode = folder.androidToPcMode
            val handleDuplicates = when (syncMode) {
                SyncMode.COPY_AND_DELETE -> false  // Overwrite duplicates
                SyncMode.MIRROR -> false           // Skip duplicates (handled by server)
                SyncMode.SYNC -> true              // Handle duplicate names intelligently
            }
            
            val multipartBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", actualFileName, requestBody)
                .addFormDataPart("original_filename", fullFilePath) // Include relative path
                .addFormDataPart("folder_path", folder.pcPath)
                .addFormDataPart("handle_duplicates", handleDuplicates.toString())
                .addFormDataPart("sync_mode", syncMode.name)
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
    val hash: String?
)

suspend fun scanPcFolder(serverUrl: String, folder: SyncFolder): List<PcFile> = withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        android.util.Log.i("FolderSync", "Scanning PC folder: '${folder.pcPath}' on server: $serverUrl")
        
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        val requestBody = """{"folder_path": "${folder.pcPath}"}""".toRequestBody("application/json".toMediaType())
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
            
            val pcFiles = mutableListOf<PcFile>()
            for (i in 0 until filesArray.length()) {
                val fileObj = filesArray.getJSONObject(i)
                pcFiles.add(PcFile(
                    path = fileObj.getString("path"),
                    size = fileObj.getLong("size"),
                    modified = fileObj.getDouble("modified"),
                    hash = fileObj.optString("hash", null)
                ))
            }
            
            android.util.Log.i("FolderSync", "Found ${pcFiles.size} files on PC in folder: ${folder.pcPath}")
            return@withContext pcFiles
        }
    } catch (e: Exception) {
        android.util.Log.e("FolderSync", "Error scanning PC folder: ${e.message}")
        throw Exception("Failed to scan PC folder: ${e.message}")
    }
}

suspend fun downloadFileFromServer(context: Context, serverUrl: String, folder: SyncFolder, file: PcFile) = withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        val request = okhttp3.Request.Builder()
            .url("$serverUrl/api/download/${file.path}?folder_path=${folder.pcPath}")
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
            
            // Handle existing files based on sync mode
            val existingFile = currentFolder.findFile(fileName)
            if (existingFile != null && existingFile.exists()) {
                when (folder.pcToAndroidMode) {
                    SyncMode.MIRROR -> {
                        // Skip if file already exists
                        android.util.Log.i("FolderSync", "Skipping existing file: $fileName")
                        return@withContext
                    }
                    SyncMode.COPY_AND_DELETE, SyncMode.SYNC -> {
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

suspend fun deleteFileFromServer(serverUrl: String, folder: SyncFolder, file: PcFile) = withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        val request = okhttp3.Request.Builder()
            .url("$serverUrl/api/delete/${file.path}?folder_path=${folder.pcPath}")
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