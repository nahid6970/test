package com.example.myapplication

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileOutputStream

class AndroidFileServer : Service() {
    
    private var httpServer: FileHttpServer? = null
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): AndroidFileServer = this@AndroidFileServer
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("AndroidFileServer", "Service created")
    }
    
    fun startServer(port: Int = 8080): Boolean {
        return try {
            if (httpServer != null) {
                stopServer()
            }
            
            httpServer = FileHttpServer(port, applicationContext)
            httpServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            
            val isAlive = httpServer?.isAlive == true
            val listeningPort = httpServer?.listeningPort ?: 0
            
            Log.d("AndroidFileServer", "Server started - Alive: $isAlive, Port: $listeningPort")
            
            if (!isAlive) {
                Log.e("AndroidFileServer", "Server started but not alive!")
                return false
            }
            
            true
        } catch (e: Exception) {
            Log.e("AndroidFileServer", "Failed to start server: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }
    
    fun stopServer() {
        httpServer?.stop()
        httpServer = null
        Log.d("AndroidFileServer", "Server stopped")
    }
    
    fun isRunning(): Boolean {
        return httpServer?.isAlive == true
    }
    
    fun getPort(): Int {
        return httpServer?.listeningPort ?: 0
    }
    
    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
    
    private inner class FileHttpServer(port: Int, private val context: android.content.Context) : NanoHTTPD(port) {
        
        private val uploadDir: File = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MyShareReceived"
        ).apply {
            if (!exists()) {
                mkdirs()
            }
            Log.d("FileHttpServer", "Upload directory: ${absolutePath}")
        }
        
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method
            Log.d("FileHttpServer", "Request: $method $uri from ${session.remoteIpAddress}")
            
            return when {
                uri == "/" && method == Method.GET -> {
                    serveHomePage()
                }
                uri == "/" && method == Method.POST -> {
                    handleFileUpload(session)
                }
                uri.startsWith("/files/") -> {
                    serveFile(uri.substring(7))
                }
                else -> {
                    Log.w("FileHttpServer", "Not found: $method $uri")
                    newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
                }
            }
        }
        
        private fun serveHomePage(): Response {
            val files = uploadDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>MyShare - Android Server</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
                        .container { max-width: 800px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; }
                        h1 { color: #4CAF50; }
                        .file-list { list-style: none; padding: 0; }
                        .file-item { padding: 10px; border-bottom: 1px solid #eee; }
                        .file-item a { color: #007BFF; text-decoration: none; }
                        .status { padding: 10px; background: #e8f5e9; border-radius: 4px; margin: 10px 0; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>📱 MyShare Android Server</h1>
                        <div class="status">
                            <strong>Status:</strong> Server is running<br>
                            <strong>Files Location:</strong> Downloads/MyShareReceived
                        </div>
                        <h2>Received Files (${files.size})</h2>
                        <ul class="file-list">
                            ${files.joinToString("") { file ->
                                "<li class=\"file-item\"><a href=\"/files/${file.name}\">${file.name}</a> (${formatFileSize(file.length())})</li>"
                            }}
                            ${if (files.isEmpty()) "<li class=\"file-item\">No files received yet</li>" else ""}
                        </ul>
                    </div>
                </body>
                </html>
            """.trimIndent()
            
            return newFixedLengthResponse(Response.Status.OK, "text/html", html)
        }
        
        private fun handleFileUpload(session: IHTTPSession): Response {
            return try {
                val files = mutableMapOf<String, String>()
                session.parseBody(files)
                
                val fileItem = files["file"]
                if (fileItem != null) {
                    val tempFile = File(fileItem)
                    val originalFilename = session.parameters["original_filename"]?.firstOrNull() 
                        ?: tempFile.name
                    
                    // Handle directory structure
                    val targetFile = File(uploadDir, originalFilename)
                    targetFile.parentFile?.mkdirs()
                    
                    // Handle duplicates
                    val finalFile = getUniqueFile(targetFile)
                    tempFile.copyTo(finalFile, overwrite = false)
                    tempFile.delete()
                    
                    Log.d("FileHttpServer", "File uploaded: ${finalFile.name}")
                    newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "File uploaded successfully")
                } else {
                    newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No file in request")
                }
            } catch (e: Exception) {
                Log.e("FileHttpServer", "Upload error", e)
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Upload failed: ${e.message}")
            }
        }
        
        private fun serveFile(filename: String): Response {
            val file = File(uploadDir, filename)
            return if (file.exists() && file.isFile) {
                newChunkedResponse(Response.Status.OK, getMimeType(filename), file.inputStream())
            } else {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
            }
        }
        
        private fun getUniqueFile(file: File): File {
            if (!file.exists()) return file
            
            val name = file.nameWithoutExtension
            val extension = file.extension
            var counter = 1
            var newFile = file
            
            while (newFile.exists()) {
                newFile = File(file.parent, "$name ($counter).$extension")
                counter++
            }
            
            return newFile
        }
        
        private fun getMimeType(filename: String): String {
            return when (filename.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "pdf" -> "application/pdf"
                "mp4" -> "video/mp4"
                "mp3" -> "audio/mpeg"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }
        }
        
        private fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
                else -> "${size / (1024 * 1024 * 1024)} GB"
            }
        }
    }
}
