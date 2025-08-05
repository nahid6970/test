package com.example.pc_file_share

import android.content.Context
import android.net.Uri
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class FileUploadService(private val context: Context) {

    private val client = OkHttpClient()
    private val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun getServerUrl(): String {
        return sharedPreferences.getString("server_url", "http://192.168.1.100:5002") ?: ""
    }

    fun setServerUrl(url: String) {
        sharedPreferences.edit().putString("server_url", url).apply()
    }

    fun uploadFile(uri: Uri, onProgress: (Double) -> Unit, onStatus: (String) -> Unit) {
        val file = File(context.cacheDir, "upload.tmp")
        val inputStream = context.contentResolver.openInputStream(uri)
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url(getServerUrl())
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                onStatus("Upload failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    onStatus("Upload successful")
                } else {
                    onStatus("Upload failed: ${response.message}")
                }
            }
        })
    }
}