package com.mycloud.filesexplorer

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.RecyclerView

class FileAdapter(
    private var files: List<FileItem>,
    private val onFolderClick: (String) -> Unit,
    private val onFileClick: (FileItem) -> Unit,
    private val onDownload: (FileItem) -> Unit,
    private val onDelete: (FileItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.fileName)
        val fileInfo: TextView = view.findViewById(R.id.fileInfo)
        val fileIcon: ImageView = view.findViewById(R.id.fileIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.fileName.text = file.filename
        
        if (file.fileType == "folder") {
            holder.fileInfo.visibility = View.GONE
            holder.fileIcon.setImageResource(R.drawable.ic_folder)
        } else {
            holder.fileInfo.visibility = View.VISIBLE
            holder.fileInfo.text = formatFileSize(file.fileSize)
            holder.fileIcon.setImageResource(getFileIcon(file.fileType))
        }

        holder.itemView.setOnClickListener {
            if (file.fileType == "folder") {
                onFolderClick(file.filename)
            } else {
                onFileClick(file)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (file.fileType != "folder") {
                val options = arrayOf("Download", "Delete")
                MaterialAlertDialogBuilder(holder.itemView.context)
                    .setTitle(file.filename)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> onDownload(file)
                            1 -> onDelete(file)
                        }
                    }
                    .show()
            }
            true
        }
    }

    override fun getItemCount() = files.size

    fun updateFiles(newFiles: List<FileItem>) {
        files = newFiles
        notifyDataSetChanged()
    }

    private fun getFileIcon(type: String): Int {
        return R.drawable.ic_file
    }

    private fun formatFileSize(size: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var digitGroups = 0
        var s = size.toDouble()
        while (s >= 1024 && digitGroups < units.size - 1) {
            s /= 1024
            digitGroups++
        }
        return String.format("%.2f %s", s, units[digitGroups])
    }
}