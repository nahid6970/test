package com.gemini.fileshare

import android.content.Context
import android.net.Uri
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

        return withContext(Dispatchers.IO) {
            try {
                val client = HttpClient(CIO)
                val response = client.post(serverUrl) {
                    setBody(MultiPartFormDataContent(
                        formData {
                            append("file", applicationContext.contentResolver.openInputStream(fileUri)!!.readBytes(), Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"${fileUri.lastPathSegment}\"")
                            })
                        }
                    ))
                }
                client.close()

                if (response.status.value in 200..299) {
                    Result.success()
                } else {
                    Result.failure()
                }
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }
}