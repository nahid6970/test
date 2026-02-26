package com.convexfiles

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ConvexClient(private val url: String) {
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun listFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val json = JsonObject().apply {
            addProperty("path", "files:list")
            add("args", JsonObject())
        }
        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        client.newCall(request).execute().use { response ->
            val result = gson.fromJson(response.body?.string(), JsonObject::class.java)
            result.getAsJsonArray("value").map { 
                val obj = it.asJsonObject
                FileItem(
                    id = obj.get("_id").asString,
                    filename = obj.get("filename").asString,
                    fileType = obj.get("fileType").asString,
                    fileSize = obj.get("fileSize").asLong,
                    storageId = obj.get("storageId")?.asString,
                    url = obj.get("url")?.asString
                )
            }
        }
    }

    suspend fun deleteFile(id: String) = withContext(Dispatchers.IO) {
        val json = JsonObject().apply {
            addProperty("path", "files:remove")
            add("args", JsonObject().apply { addProperty("id", id) })
        }
        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().close()
    }
}
