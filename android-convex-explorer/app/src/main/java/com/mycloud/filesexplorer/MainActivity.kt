package com.mycloud.filesexplorer

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val client = ConvexClient()
    private lateinit var adapter: FileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = FileAdapter(emptyList()) { file ->
            deleteFile(file)
        }
        recyclerView.adapter = adapter

        loadFiles()
    }

    private fun loadFiles() {
        lifecycleScope.launch {
            try {
                val files = client.listFiles()
                adapter.updateFiles(files)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
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