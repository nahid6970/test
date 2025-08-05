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
                    val fileName = getFileName(uri)
                    val fileSize = getFileSize(uri)
                    files.add(SharedFile(uri, fileName, fileSize))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris ->
                    uris.forEach { uri ->
                        val fileName = getFileName(uri)
                        val fileSize = getFileSize(uri)
                        files.add(SharedFile(uri, fileName, fileSize))
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
            Text(
                text = "Files to Upload (${files.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
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
                                    uploadFilesWithProgress(
                                        context = context,
                                        files = files,
                                        serverUrl = serverUrl,
                                        onProgressUpdate = { updatedProgress ->
                                            uploadProgress = updatedProgress
                                            overallProgress = updatedProgress.map { it.progress }.average().toFloat()
                                            
                                            // Calculate total upload speed
                                            val totalSpeed = updatedProgress
                                                .filter { it.status == UploadStatus.UPLOADING }
                                                .sumOf { parseSpeed(it.uploadSpeed) }
                                            totalUploadSpeed = formatSpeed(totalSpeed)
                                        }
                                    )
                                    Toast.makeText(context, "All files uploaded successfully!", Toast.LENGTH_LONG).show()
                                    delay(1000)
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
                Text(
                    text = progress.fileName,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                
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

private suspend fun uploadFilesWithProgress(
    context: Context,
    files: List<SharedFile>,
    serverUrl: String,
    onProgressUpdate: (List<UploadProgress>) -> Unit
) = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val progressList = files.mapIndexed { index, file ->
        UploadProgress(
            fileIndex = index,
            fileName = file.fileName,
            totalBytes = file.fileSize,
            status = UploadStatus.PENDING
        )
    }.toMutableList()
    
    files.forEachIndexed { index, file ->
        try {
            // Update status to uploading
            progressList[index] = progressList[index].copy(status = UploadStatus.UPLOADING)
            withContext(Dispatchers.Main) {
                onProgressUpdate(progressList.toList())
            }
            
            val startTime = System.currentTimeMillis()
            
            // Create temp file
            val inputStream = context.contentResolver.openInputStream(file.uri)
            val tempFile = File(context.cacheDir, file.fileName)
            
            inputStream?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Create progress tracking request body
            val requestBody = ProgressRequestBody(
                tempFile.asRequestBody("application/octet-stream".toMediaType()),
                file.fileSize
            ) { bytesWritten ->
                val currentTime = System.currentTimeMillis()
                val elapsedTime = (currentTime - startTime) / 1000.0
                val speed = if (elapsedTime > 0) bytesWritten / elapsedTime else 0.0
                
                progressList[index] = progressList[index].copy(
                    progress = bytesWritten.toFloat() / file.fileSize,
                    uploadedBytes = bytesWritten,
                    uploadSpeed = formatSpeed(speed)
                )
                
                // Update UI on main thread
                kotlinx.coroutines.MainScope().launch {
                    onProgressUpdate(progressList.toList())
                }
            }
            
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.fileName, requestBody)
                .build()
            
            val request = Request.Builder()
                .url(serverUrl)
                .post(multipartBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    progressList[index] = progressList[index].copy(
                        progress = 1f,
                        uploadedBytes = file.fileSize,
                        status = UploadStatus.COMPLETED
                    )
                } else {
                    throw IOException("Upload failed: ${response.code}")
                }
            }
            
            tempFile.delete()
            
        } catch (e: Exception) {
            progressList[index] = progressList[index].copy(status = UploadStatus.ERROR)
            throw e
        }
        
        withContext(Dispatchers.Main) {
            onProgressUpdate(progressList.toList())
        }
    }
}

class ProgressRequestBody(
    private val requestBody: RequestBody,
    private val totalBytes: Long,
    private val onProgress: (Long) -> Unit
) : RequestBody() {
    
    override fun contentType() = requestBody.contentType()
    override fun contentLength() = totalBytes
    
    override fun writeTo(sink: BufferedSink) {
        val progressSink = ProgressSink(sink, totalBytes, onProgress)
        val bufferedSink = progressSink.buffer()
        requestBody.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}

class ProgressSink(
    private val sink: Sink,
    private val totalBytes: Long,
    private val onProgress: (Long) -> Unit
) : ForwardingSink(sink) {
    
    private var bytesWritten = 0L
    
    override fun write(source: okio.Buffer, byteCount: Long) {
        super.write(source, byteCount)
        bytesWritten += byteCount
        onProgress(bytesWritten)
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes / 1024.0).roundToInt()} KB"
        bytes < 1024 * 1024 * 1024 -> "${(bytes / (1024.0 * 1024)).roundToInt()} MB"
        else -> "${(bytes / (1024.0 * 1024 * 1024)).roundToInt()} GB"
    }
}

private fun formatSpeed(bytesPerSecond: Double): String {
    return when {
        bytesPerSecond < 1024 -> "${bytesPerSecond.roundToInt()} B/s"
        bytesPerSecond < 1024 * 1024 -> "${(bytesPerSecond / 1024).roundToInt()} KB/s"
        bytesPerSecond < 1024 * 1024 * 1024 -> "${(bytesPerSecond / (1024 * 1024)).roundToInt()} MB/s"
        else -> "${(bytesPerSecond / (1024 * 1024 * 1024)).roundToInt()} GB/s"
    }
}

private fun parseSpeed(speedString: String): Double {
    val parts = speedString.split(" ")
    if (parts.size != 2) return 0.0
    
    val value = parts[0].toDoubleOrNull() ?: return 0.0
    return when (parts[1]) {
        "B/s" -> value
        "KB/s" -> value * 1024
        "MB/s" -> value * 1024 * 1024
        "GB/s" -> value * 1024 * 1024 * 1024
        else -> 0.0
    }
}