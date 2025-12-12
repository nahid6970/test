package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class ShareActivity : ComponentActivity() {
    
    private val client = OkHttpClient()
    private var sharedFiles: List<SharedFile> = emptyList()
    
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedUrl = result.data?.getStringExtra("scanned_url")
            if (scannedUrl != null) {
                // Proceed with Android upload
                startUpload(scannedUrl, ShareDestination.ANDROID)
            } else {
                Toast.makeText(this, "Failed to get device URL", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedFiles = getSharedFiles()
        
        setContent {
            MyApplicationTheme {
                DestinationSelectionScreen(
                    filesCount = sharedFiles.size,
                    onDestinationSelected = { destination ->
                        when (destination) {
                            ShareDestination.PC -> {
                                val sharedPrefs = getSharedPreferences("file_share_prefs", MODE_PRIVATE)
                                val pcUrl = sharedPrefs.getString("server_url", "http://192.168.1.100:5002") 
                                    ?: "http://192.168.1.100:5002"
                                startUpload(pcUrl, ShareDestination.PC)
                            }
                            ShareDestination.ANDROID -> {
                                // Check if we have a saved Android device URL
                                val sharedPrefs = getSharedPreferences("file_share_prefs", MODE_PRIVATE)
                                val savedAndroidUrl = sharedPrefs.getString("android_device_url", null)
                                
                                if (savedAndroidUrl != null) {
                                    // Ask if they want to use saved device or scan new
                                    showAndroidDeviceChoice(savedAndroidUrl)
                                } else {
                                    // Launch QR scanner
                                    launchQRScanner()
                                }
                            }
                        }
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
    
    private fun showAndroidDeviceChoice(savedUrl: String) {
        setContent {
            MyApplicationTheme {
                AndroidDeviceChoiceDialog(
                    savedUrl = savedUrl,
                    onUseSaved = {
                        startUpload(savedUrl, ShareDestination.ANDROID)
                    },
                    onScanNew = {
                        launchQRScanner()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
    
    private fun launchQRScanner() {
        val intent = Intent(this, QRScannerActivity::class.java)
        qrScannerLauncher.launch(intent)
    }
    
    private fun startUpload(serverUrl: String, destination: ShareDestination) {
        setContent {
            MyApplicationTheme {
                ShareScreen(
                    files = sharedFiles,
                    serverUrl = serverUrl,
                    destination = destination,
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
            val fileName = getFileName(directoryUri)
            val fileSize = getFileSize(directoryUri)
            files.add(SharedFile(directoryUri, fileName, fileSize))
        }
        
        return files
    }
}

enum class ShareDestination {
    PC, ANDROID
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

@Composable
fun DestinationSelectionScreen(
    filesCount: Int,
    onDestinationSelected: (ShareDestination) -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Share $filesCount file${if (filesCount > 1) "s" else ""} to:",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // PC Option
                Button(
                    onClick = { onDestinationSelected(ShareDestination.PC) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "PC",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Share to PC", fontSize = 16.sp)
                    }
                }
                
                // Android Option
                Button(
                    onClick = { onDestinationSelected(ShareDestination.ANDROID) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "Android",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Share to Android", fontSize = 16.sp)
                    }
                }
                
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun AndroidDeviceChoiceDialog(
    savedUrl: String,
    onUseSaved: () -> Unit,
    onScanNew: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select Android Device",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Use previously connected device or scan a new QR code",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Saved device
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Last Connected Device:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = savedUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Button(
                    onClick = onUseSaved,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use This Device")
                }
                
                OutlinedButton(
                    onClick = onScanNew,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Scan")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan New QR Code")
                }
                
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

// Continue with ShareScreen from the original ShareActivity...

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    files: List<SharedFile>,
    serverUrl: String,
    destination: ShareDestination,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf<List<UploadProgress>>(emptyList()) }
    var overallProgress by remember { mutableStateOf(0f) }
    var totalUploadSpeed by remember { mutableStateOf("0 KB/s") }
    
    val scope = rememberCoroutineScope()
    
    val destinationLabel = when (destination) {
        ShareDestination.PC -> "Share to PC"
        ShareDestination.ANDROID -> "Share to Android"
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(destinationLabel) }
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Destination:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = serverUrl,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
                                    Toast.makeText(context, "Checking server connection...", Toast.LENGTH_SHORT).show()
                                    
                                    if (!checkServerConnection(serverUrl)) {
                                        Toast.makeText(context, "Cannot connect to server. Check connection and try again.", Toast.LENGTH_LONG).show()
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
