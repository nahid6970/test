package com.mycloud.filesexplorer

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class ConvexClient {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://good-basilisk-52.convex.cloud"

    suspend fun listFiles(): List<FileItem> {
        val json = gson.toJson(mapOf(
            "path" to "files:list",
            "args" to emptyMap<String, Any>()
        ))
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/api/query")
            .post(body)
            .build()

        return suspendCancellableCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null) {
                        try {
                            val convexResponse = gson.fromJson<ConvexResponse<List<FileItem>>>(
                                body, 
                                object : TypeToken<ConvexResponse<List<FileItem>>>() {}.type
                            )
                            continuation.resume(convexResponse.value ?: emptyList())
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    } else {
                        continuation.resumeWithException(Exception("Failed to list files: ${response.code}"))
                    }
                }
            })
        }
    }

    suspend fun deleteFile(id: String) {
        val json = gson.toJson(mapOf(
            "path" to "files:remove",
            "args" to mapOf("id" to id)
        ))
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/api/mutation")
            .post(body)
            .build()

        return suspendCancellableCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(Exception("Failed to delete file: ${response.code}"))
                    }
                }
            })
        }
    }

    suspend fun getFileUrl(storageId: String): String {
        val json = gson.toJson(mapOf(
            "path" to "files:getUrl",
            "args" to mapOf("storageId" to storageId)
        ))
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/api/query")
            .post(body)
            .build()

        return suspendCancellableCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val bodyString = response.body?.string()
                    if (response.isSuccessful && bodyString != null) {
                        try {
                            val convexResponse = gson.fromJson<ConvexResponse<String>>(
                                bodyString,
                                object : TypeToken<ConvexResponse<String>>() {}.type
                            )
                            continuation.resume(convexResponse.value ?: "")
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    } else {
                        continuation.resumeWithException(Exception("Failed to get URL: ${response.code}"))
                    }
                }
            })
        }
    }

    private data class ConvexResponse<T>(val value: T?)
}