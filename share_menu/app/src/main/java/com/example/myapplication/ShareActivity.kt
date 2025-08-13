package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

class ShareActivity : ComponentActivity() {
    
    private val client = OkHttpClient()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedFiles = getSharedFiles()
        
        setContent {
            MyApplicationTheme {
                ShareScreen(
                    files = sharedFiles,
                    onCancel = { finish() }
                )
            }
        }
    }
    
    private fun getSharedFiles(): List<SharedFile> {
        val files = mutableListOf<SharedFile>()
        
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                    if (isDirectory(uri)) {
                        // If it's a directory, get all files recursively
                        files.addAll(getFilesFromDirectory(uri))
                    } else {
                        val fileName = getFileName(uri)
                        val fileSize = getFileSize(uri)
                        files.add(SharedFile(uri, fileName, fileSize))
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris ->
                    uris.forEach { uri ->
                        if (isDirectory(uri)) {
                            // If it's a directory, get all files recursively
                            files.addAll(getFilesFromDirectory(uri))
                        } else {
                            val fileName = getFileName(uri)
                            val fileSize = getFileSize(uri)
                            files.add(SharedFile(uri, fileName, fileSize))
                        }
                    }
                }
            }
        }
        
        return files
    }
    
    private fun getFileName(uri: Uri): String {
        var fileName = "unknown_file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }
    
    private fun getFileSize(uri: Uri): Long {
        var fileSize = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }
        return fileSize
    }
    
    private fun isDirectory(uri: Uri): Boolean {
        return try {
            val documentFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(this, uri)
            documentFile?.isDirectory == true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getFilesFromDirectory(directoryUri: Uri, basePath: String = ""): List<SharedFile> {
        val files = mutableListOf<SharedFile>()
        
        try {
            val documentFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(this, directoryUri)
            if (documentFile?.isDirectory == true) {
                val directoryName = documentFile.name ?: "folder"
                val currentPath = if (basePath.isEmpty()) directoryName else "$basePath/$directoryName"
                
                documentFile.listFiles().forEach { childFile ->
                    if (childFile.isDirectory) {
                        // Recursively get files from subdirectory
                        files.addAll(getFilesFromDirectory(childFile.uri, currentPath))
                    } else if (childFile.isFile) {
                        val fileName = childFile.name ?: "unknown_file"
                        val fileSize = childFile.length()
                        val fullPath = "$currentPath/$fileName"
                        
                        files.add(SharedFile(
                            uri = childFile.uri,
                            fileName = fullPath,
                            fileSize = fileSize
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            // If directory scanning fails, treat as single file
            val fileName = getFileName(directoryUri)
            val fileSize = getFileSize(directoryUri)
            files.add(SharedFile(directoryUri, fileName, fileSize))
        }
        
        return files
    }
    
    // This function is no longer needed as we handle uploads directly in the Composable
    
    private suspend fun uploadFile(sharedFile: SharedFile, serverUrl: String) = withContext(Dispatchers.IO) {
        val inputStream = contentResolver.openInputStream(sharedFile.uri)
        val tempFile = File(cacheDir, sharedFile.fileName)
        
        inputStream?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        
        val requestBody = tempFile.asRequestBody("application/octet-stream".toMediaType())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", sharedFile.fileName, requestBody)
            .build()
        
        val request = Request.Builder()
            .url(serverUrl)
            .post(multipartBody)
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Upload failed: ${response.code}")
            }
        }
        
        tempFile.delete()
    }
}

data class SharedFile(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long = 0L
)

data class UploadProgress(
    val fileIndex: Int = -1,
    val fileName: String = "",
    val progress: Float = 0f,
    val uploadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val uploadSpeed: String = "0 KB/s",
    val status: UploadStatus = UploadStatus.PENDING
)

enum class UploadStatus {
    PENDING, UPLOADING, COMPLETED, ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share to PC") }
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
            val totalSize = files.sumOf { it.fileSize }
            val hasDirectories = files.any { it.fileName.contains("/") }
            
            Text(
                text = if (hasDirectories) 
                    "Files to Upload (${files.size} files from folders)" 
                else 
                    "Files to Upload (${files.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (hasDirectories) {
                Text(
                    text = "Total size: ${formatFileSize(totalSize)} • Directory structure will be preserved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isUploading) {
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
                        FileProgressCard(progress = progress)
                    }
                } else {
                    itemsIndexed(files) { index, file ->
                        FileInfoCard(file = file, index = index)
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
                                    
                                    Toast.makeText(context, "Server connected! Starting upload...", Toast.LENGTH_SHORT).show()
                                    
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
                                        Toast.makeText(context, "All $completedFiles files uploaded successfully!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "$completedFiles files uploaded, $errorFiles failed", Toast.LENGTH_LONG).show()
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
                        Text("Upload")
                    }
                }
            }
        }
    }
}

@Composable
fun FileInfoCard(file: SharedFile, index: Int) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (file.fileName.contains("/")) {
                // Show directory structure
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
            } else {
                Text(
                    text = file.fileName,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatFileSize(file.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FileProgressCard(progress: UploadProgress) {
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
                    if (progress.fileName.contains("/")) {
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
                    } else {
                        Text(
                            text = progress.fileName,
                            fontWeight = FontWeight.Medium
                        )
                    }
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

// Functions moved to UploadUtils.kt