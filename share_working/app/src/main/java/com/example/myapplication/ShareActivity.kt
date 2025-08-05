package com.example.myapplication

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
                    files.add(SharedFile(uri, fileName))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris ->
                    uris.forEach { uri ->
                        val fileName = getFileName(uri)
                        files.add(SharedFile(uri, fileName))
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
    val fileName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    files: List<SharedFile>,
    onCancel: () -> Unit
) {
    var serverUrl by remember { mutableStateOf("http://192.168.1.100:5002") }
    var isUploading by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
                text = "Files to Upload",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = file.fileName,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading
            )
            
            Text(
                text = "Make sure your PC is running the Python server and both devices are on the same network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
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
                            scope.launch {
                                try {
                                    files.forEach { sharedFile ->
                                        uploadFileCoroutine(context, sharedFile, serverUrl)
                                    }
                                    Toast.makeText(context, "All files uploaded successfully!", Toast.LENGTH_LONG).show()
                                    onCancel()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    isUploading = false
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please enter server URL", Toast.LENGTH_SHORT).show()
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

private suspend fun uploadFileCoroutine(
    context: android.content.Context,
    sharedFile: SharedFile,
    serverUrl: String
) = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val inputStream = context.contentResolver.openInputStream(sharedFile.uri)
    val tempFile = File(context.cacheDir, sharedFile.fileName)
    
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