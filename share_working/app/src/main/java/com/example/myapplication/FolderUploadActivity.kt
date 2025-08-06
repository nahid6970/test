package com.example.myapplication

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt

class FolderUploadActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val folderUriString = intent.getStringExtra("folder_uri")
        if (folderUriString == null) {
            Toast.makeText(this, "No folder selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        val folderUri = Uri.parse(folderUriString)
        val files = getFilesFromFolder(folderUri)
        
        setContent {
            MyApplicationTheme {
                FolderUploadScreen(
                    files = files,
                    onCancel = { finish() }
                )
            }
        }
    }
    
    private fun getFilesFromFolder(folderUri: Uri): List<SharedFile> {
        val files = mutableListOf<SharedFile>()
        
        try {
            val documentFile = DocumentFile.fromTreeUri(this, folderUri)
            if (documentFile?.isDirectory == true) {
                val folderName = documentFile.name ?: "folder"
                scanDirectory(documentFile, folderName, files)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading folder: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        return files
    }
    
    private fun scanDirectory(directory: DocumentFile, basePath: String, files: MutableList<SharedFile>) {
        directory.listFiles().forEach { file ->
            if (file.isDirectory) {
                val subPath = "$basePath/${file.name}"
                scanDirectory(file, subPath, files)
            } else if (file.isFile) {
                val fileName = file.name ?: "unknown_file"
                val fullPath = "$basePath/$fileName"
                files.add(SharedFile(
                    uri = file.uri,
                    fileName = fullPath,
                    fileSize = file.length()
                ))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderUploadScreen(
    files: List<SharedFile>,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("file_share_prefs", Context.MODE_PRIVATE)
    
    var serverUrl by remember { 
        mutableStateOf(sharedPrefs.getString("server_url", "http://192.168.1.100:5002") ?: "http://192.168.1.100:5002") 
    }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf<List<UploadProgress>>(emptyList()) }
    var overallProgress by remember { mutableStateOf(0f) }
    var totalUploadSpeed by remember { mutableStateOf("0 KB/s") }
    
    val scope = rememberCoroutineScope()
    val totalSize = files.sumOf { it.fileSize }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Folder") }
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
                    Text(
                        text = "Folder Upload",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("📁 ${files.size} files found")
                    Text("📊 Total size: ${formatFileSize(totalSize)}")
                    Text("🏗️ Directory structure will be preserved")
                }
            }
            
            if (isUploading) {
                // Overall progress
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Overall Progress", fontWeight = FontWeight.Bold)
                            Text("${(overallProgress * 100).roundToInt()}%")
                        }
                        LinearProgressIndicator(
                            progress = overallProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Upload Speed: $totalUploadSpeed",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isUploading && uploadProgress.isNotEmpty()) {
                    itemsIndexed(uploadProgress) { index, progress ->
                        FolderFileProgressCard(progress = progress)
                    }
                } else {
                    itemsIndexed(files) { index, file ->
                        FolderFileInfoCard(file = file)
                    }
                }
            }
            
            if (!isUploading) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                )
                
                Text(
                    text = "Server URL is loaded from settings. Change it in the main app if needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !isUploading
                ) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = {
                        if (serverUrl.isNotBlank() && files.isNotEmpty()) {
                            isUploading = true
                            uploadProgress = files.mapIndexed { index, file ->
                                UploadProgress(
                                    fileIndex = index,
                                    fileName = file.fileName,
                                    totalBytes = file.fileSize,
                                    status = UploadStatus.PENDING
                                )
                            }
                            
                            scope.launch {
                                try {
                                    // Check server connection first
                                    Toast.makeText(context, "Checking server connection...", Toast.LENGTH_SHORT).show()
                                    
                                    if (!checkServerConnection(serverUrl)) {
                                        Toast.makeText(context, "Cannot connect to server. Check IP address and ensure server is running.", Toast.LENGTH_LONG).show()
                                        isUploading = false
                                        return@launch
                                    }
                                    
                                    Toast.makeText(context, "Server connected! Starting folder upload...", Toast.LENGTH_SHORT).show()
                                    
                                    uploadFilesWithProgress(
                                        context = context,
                                        files = files,
                                        serverUrl = serverUrl,
                                        onProgressUpdate = { updatedProgress ->
                                            uploadProgress = updatedProgress
                                            overallProgress = updatedProgress.map { it.progress }.average().toFloat()
                                            
                                            // Calculate total upload speed (only from actively uploading files)
                                            val activeUploads = updatedProgress.filter { 
                                                it.status == UploadStatus.UPLOADING && 
                                                !it.uploadSpeed.contains("Retrying") &&
                                                !it.uploadSpeed.contains("timeout")
                                            }
                                            val totalSpeed = activeUploads.sumOf { parseSpeed(it.uploadSpeed) }
                                            totalUploadSpeed = if (totalSpeed > 0) formatSpeed(totalSpeed) else "Calculating..."
                                        }
                                    )
                                    
                                    val completedFiles = uploadProgress.count { it.status == UploadStatus.COMPLETED }
                                    val errorFiles = uploadProgress.count { it.status == UploadStatus.ERROR }
                                    
                                    if (errorFiles == 0) {
                                        Toast.makeText(context, "Folder uploaded successfully! $completedFiles files transferred.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Folder upload completed: $completedFiles files uploaded, $errorFiles failed", Toast.LENGTH_LONG).show()
                                    }
                                    
                                    delay(2000)
                                    onCancel()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    isUploading = false
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please check server URL", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isUploading && serverUrl.isNotBlank() && files.isNotEmpty()
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Upload Folder")
                    }
                }
            }
        }
    }
}

@Composable
fun FolderFileInfoCard(file: SharedFile) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            val pathParts = file.fileName.split("/")
            val fileName = pathParts.last()
            val directory = pathParts.dropLast(1).joinToString("/")
            
            Text(
                text = fileName,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "📁 $directory",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = formatFileSize(file.fileSize),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FolderFileProgressCard(progress: UploadProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (progress.status) {
                UploadStatus.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer
                UploadStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
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
                    val pathParts = progress.fileName.split("/")
                    val fileName = pathParts.last()
                    val directory = pathParts.dropLast(1).joinToString("/")
                    
                    Text(
                        text = fileName,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "📁 $directory",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                when (progress.status) {
                    UploadStatus.COMPLETED -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color.Green
                    )
                    UploadStatus.ERROR -> Icon(
                        Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color.Red
                    )
                    UploadStatus.UPLOADING -> Text("${(progress.progress * 100).roundToInt()}%")
                    UploadStatus.PENDING -> Text("Pending")
                }
            }
            
            if (progress.status == UploadStatus.UPLOADING || progress.status == UploadStatus.COMPLETED) {
                LinearProgressIndicator(
                    progress = progress.progress,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${formatFileSize(progress.uploadedBytes)} / ${formatFileSize(progress.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (progress.status == UploadStatus.UPLOADING) {
                        Text(
                            text = progress.uploadSpeed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}