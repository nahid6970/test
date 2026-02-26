package com.mycloud.filesexplorer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

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
            onDelete = { file -> deleteFile(file) },
            onFolderClick = { folderName -> 
                currentFolder = if (currentFolder == null) folderName else "$currentFolder/$folderName"
                updateDisplay()
            },
            onFileClick = { file -> openFile(file) }
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
        supportActionBar?.title = currentFolder ?: "My Files"
    }

    private fun openFile(file: FileItem) {
        val url = file.url ?: return
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse(url), file.fileType)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        
        val chooser = Intent.createChooser(intent, "Open with")
        try {
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
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