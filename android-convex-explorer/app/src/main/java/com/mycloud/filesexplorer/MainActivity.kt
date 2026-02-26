package com.mycloud.filesexplorer

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val client = ConvexClient()
    private lateinit var adapter: FileAdapter
    private var allFiles: List<FileItem> = emptyList()
    private var currentFolder: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = FileAdapter(
            files = emptyList(),
            onFolderClick = { folderName -> 
                currentFolder = if (currentFolder == null) folderName else "$currentFolder/$folderName"
                updateDisplay()
            },
            onFileClick = { file -> openFile(file) },
            onDownload = { file -> downloadFile(file) },
            onDelete = { file -> deleteFile(file) }
        )
        recyclerView.adapter = adapter

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentFolder != null) {
                    val lastSlash = currentFolder!!.lastIndexOf('/')
                    currentFolder = if (lastSlash == -1) null else currentFolder!!.substring(0, lastSlash)
                    updateDisplay()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        loadFiles()
    }

    private fun loadFiles() {
        lifecycleScope.launch {
            try {
                allFiles = client.listFiles()
                updateDisplay()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun updateDisplay() {
        val displayItems = mutableListOf<FileItem>()
        val folders = mutableSetOf<String>()

        allFiles.forEach { file ->
            val group = file.group ?: ""
            if (currentFolder == null) {
                if (group.isEmpty()) {
                    displayItems.add(file)
                } else {
                    val rootFolder = group.split("/")[0]
                    folders.add(rootFolder)
                }
            } else {
                if (group == currentFolder) {
                    displayItems.add(file)
                } else if (group.startsWith("$currentFolder/")) {
                    val subPath = group.substring(currentFolder!!.length + 1)
                    val nextFolder = subPath.split("/")[0]
                    folders.add(nextFolder)
                }
            }
        }

        val finalItems = mutableListOf<FileItem>()
        // Add folders as virtual FileItems
        folders.sorted().forEach { folderName ->
            finalItems.add(FileItem(
                _id = "folder_$folderName",
                filename = folderName,
                fileType = "folder",
                fileSize = 0,
                storageId = null,
                url = null,
                group = currentFolder
            ))
        }
        finalItems.addAll(displayItems.sortedBy { it.filename })
        
        adapter.updateFiles(finalItems)
        findViewById<android.widget.TextView>(R.id.breadcrumbText).text = currentFolder ?: "Root"
    }

    private fun openFile(file: FileItem) {
        lifecycleScope.launch {
            try {
                val url = file.url ?: if (!file.storageId.isNullOrEmpty()) {
                    client.getFileUrl(file.storageId)
                } else null

                if (url.isNullOrEmpty()) {
                    Toast.makeText(this@MainActivity, "File URL not available", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Toast.makeText(this@MainActivity, "Preparing file...", Toast.LENGTH_SHORT).show()

                // Download to temporary cache file
                val cacheFile = withContext(Dispatchers.IO) {
                    val outputFile = File(cacheDir, file.filename)
                    URL(url).openStream().use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    outputFile
                }

                val mimeType = getMimeType(file.filename, file.fileType)
                val contentUri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    cacheFile
                )

                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(contentUri, mimeType)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                // Set explicit package for APKs
                if (mimeType == "application/vnd.android.package-archive") {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = Intent.createChooser(intent, "Open with")
                startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getMimeType(filename: String, type: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            else -> if (type.contains("/")) type else "application/octet-stream"
        }
    }

    private fun downloadFile(file: FileItem) {
        lifecycleScope.launch {
            try {
                val url = file.url ?: if (!file.storageId.isNullOrEmpty()) {
                    client.getFileUrl(file.storageId)
                } else null

                if (url.isNullOrEmpty()) {
                    Toast.makeText(this@MainActivity, "Download URL not available", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(file.filename)
                    .setDescription("Downloading file...")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file.filename)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)

                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.enqueue(request)
                Toast.makeText(this@MainActivity, "Download started", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteFile(file: FileItem) {
        lifecycleScope.launch {
            try {
                client.deleteFile(file._id)
                Toast.makeText(this@MainActivity, "Deleted ${file.filename}", Toast.LENGTH_SHORT).show()
                loadFiles()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }
}