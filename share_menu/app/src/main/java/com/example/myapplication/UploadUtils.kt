package com.example.myapplication

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
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
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.roundToInt

suspend fun uploadFilesWithProgress(
    context: Context,
    files: List<SharedFile>,
    serverUrl: String,
    onProgressUpdate: (List<UploadProgress>) -> Unit
) = withContext(Dispatchers.IO) {
    // Optimized HTTP client with better timeouts and connection pooling
    val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 5, java.util.concurrent.TimeUnit.MINUTES))
        .build()
        
    val progressList = files.mapIndexed { index, file ->
        UploadProgress(
            fileIndex = index,
            fileName = file.fileName,
            totalBytes = file.fileSize,
            status = UploadStatus.PENDING
        )
    }.toMutableList()
    
    files.forEachIndexed { index, file ->
        var retryCount = 0
        val maxRetries = 3
        var uploadSuccessful = false
        
        while (!uploadSuccessful && retryCount <= maxRetries) {
            try {
                // Update status to uploading
                val statusText = if (retryCount > 0) "Retrying ($retryCount/$maxRetries)" else "Uploading"
                progressList[index] = progressList[index].copy(
                    status = UploadStatus.UPLOADING,
                    uploadSpeed = statusText
                )
                withContext(Dispatchers.Main) {
                    onProgressUpdate(progressList.toList())
                }
                
                val startTime = System.currentTimeMillis()
                
                // Create temp file
                val inputStream = context.contentResolver.openInputStream(file.uri)
                val tempFile = File(context.cacheDir, file.fileName.replace("/", "_"))
                
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
                    MainScope().launch {
                        onProgressUpdate(progressList.toList())
                    }
                }
                
                val safeFilename = createSafeFilename(file.fileName)
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", safeFilename, requestBody)
                    .addFormDataPart("original_filename", file.fileName)
                    .build()
                
                val request = Request.Builder()
                    .url(serverUrl)
                    .post(multipartBody)
                    .addHeader("Connection", "keep-alive")
                    .addHeader("Keep-Alive", "timeout=60, max=100")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        progressList[index] = progressList[index].copy(
                            progress = 1f,
                            uploadedBytes = file.fileSize,
                            status = UploadStatus.COMPLETED,
                            uploadSpeed = "Completed"
                        )
                        uploadSuccessful = true
                    } else {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                }
                
                tempFile.delete()
                
            } catch (e: Exception) {
                retryCount++
                val errorMessage = when (e) {
                    is SocketTimeoutException -> "Connection timeout"
                    is UnknownHostException -> "Cannot reach server"
                    is IOException -> "Network error: ${e.message}"
                    else -> "Error: ${e.message}"
                }
                
                if (retryCount > maxRetries) {
                    progressList[index] = progressList[index].copy(
                        status = UploadStatus.ERROR,
                        uploadSpeed = errorMessage
                    )
                    // Don't throw exception, continue with next file
                    break
                } else {
                    // Wait before retry (exponential backoff)
                    val delayMs = (1000 * retryCount).toLong()
                    kotlinx.coroutines.delay(delayMs)
                    
                    progressList[index] = progressList[index].copy(
                        uploadSpeed = "Retrying in ${delayMs/1000}s..."
                    )
                    withContext(Dispatchers.Main) {
                        onProgressUpdate(progressList.toList())
                    }
                }
            }
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

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes / 1024.0).roundToInt()} KB"
        bytes < 1024 * 1024 * 1024 -> "${(bytes / (1024.0 * 1024)).roundToInt()} MB"
        else -> "${(bytes / (1024.0 * 1024 * 1024)).roundToInt()} GB"
    }
}

fun formatSpeed(bytesPerSecond: Double): String {
    return when {
        bytesPerSecond < 1024 -> "${bytesPerSecond.roundToInt()} B/s"
        bytesPerSecond < 1024 * 1024 -> "${(bytesPerSecond / 1024).roundToInt()} KB/s"
        bytesPerSecond < 1024 * 1024 * 1024 -> "${(bytesPerSecond / (1024 * 1024)).roundToInt()} MB/s"
        else -> "${(bytesPerSecond / (1024 * 1024 * 1024)).roundToInt()} GB/s"
    }
}

fun parseSpeed(speedString: String): Double {
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

fun createSafeFilename(originalFilename: String): String {
    // Preserve spaces and most special characters, only remove truly dangerous ones
    return originalFilename
        .replace(Regex("[<>:\"|?*]"), "_") // Remove Windows forbidden characters
        .replace(Regex("[\u0000-\u001f]"), "_") // Remove control characters
        .replace("..", "_") // Prevent directory traversal
        .trim()
        .takeIf { it.isNotEmpty() } ?: "unnamed_file"
}

suspend fun checkServerConnection(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            
        val request = Request.Builder()
            .url(serverUrl)
            .head() // Use HEAD request for faster check
            .build()
            
        client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    } catch (e: Exception) {
        false
    }
}

fun getOptimalChunkSize(fileSize: Long): Int {
    // Adjust chunk size based on file size for better performance
    return when {
        fileSize < 1024 * 1024 -> 8192 // 8KB for small files
        fileSize < 10 * 1024 * 1024 -> 32768 // 32KB for medium files
        fileSize < 100 * 1024 * 1024 -> 65536 // 64KB for large files
        else -> 131072 // 128KB for very large files
    }
}