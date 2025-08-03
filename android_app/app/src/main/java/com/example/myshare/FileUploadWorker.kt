package com.example.myshare

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class FileUploadWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val fileUriString = inputData.getString("fileUri") ?: return Result.failure()
        val serverUrl = inputData.getString("serverUrl") ?: return Result.failure()
        val fileUri = Uri.parse(fileUriString)

        Log.d("FileUploadWorker", "Starting upload for: $fileUriString to $serverUrl")

        return withContext(Dispatchers.IO) {
            try {
                val inputStream = applicationContext.contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    Log.e("FileUploadWorker", "Could not open input stream for URI: $fileUri")
                    return@withContext Result.failure()
                }

                val fileBytes = inputStream.use { it.readBytes() }
                val fileName = getFileName(fileUri) ?: "unknown_file"
                
                Log.d("FileUploadWorker", "File size: ${fileBytes.size} bytes, name: $fileName")

                val client = HttpClient(CIO)
                val response = client.post(serverUrl) {
                    setBody(MultiPartFormDataContent(
                        formData {
                            append("file", fileBytes, Headers.build {
                                val mimeType = applicationContext.contentResolver.getType(fileUri) ?: "application/octet-stream"
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                append(HttpHeaders.ContentType, mimeType)
                            })
                        }
                    ))
                }
                client.close()

                Log.d("FileUploadWorker", "Upload response: ${response.status}")

                if (response.status.value in 200..299) {
                    Log.d("FileUploadWorker", "Upload successful")
                    Result.success()
                } else {
                    Log.e("FileUploadWorker", "Upload failed with status: ${response.status}")
                    Result.failure()
                }
            } catch (e: Exception) {
                Log.e("FileUploadWorker", "Upload failed with exception", e)
                Result.failure()
            }
        }
    }

    

    private fun getFileSize(uri: Uri): Long? {
        var fileSize: Long? = null
        applicationContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }
        return fileSize
    }

    private fun getFileName(uri: Uri): String? {

        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = applicationContext.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
