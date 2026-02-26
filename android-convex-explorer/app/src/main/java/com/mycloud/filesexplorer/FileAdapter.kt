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
        val iconContainer: View = view.findViewById(R.id.iconContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.fileName.text = file.filename
        
        val type = if (file.fileType == "folder") "folder" else file.fileType
        val iconInfo = getIconInfo(type, file.filename)
        
        holder.fileIcon.setImageResource(iconInfo.iconRes)
        holder.fileIcon.setColorFilter(android.graphics.Color.parseColor(iconInfo.color))
        holder.iconContainer.background.setTint(android.graphics.Color.parseColor(iconInfo.bgColor))

        if (file.fileType == "folder") {
            holder.fileInfo.visibility = View.GONE
        } else {
            holder.fileInfo.visibility = View.VISIBLE
            holder.fileInfo.text = formatFileSize(file.fileSize)
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

    data class IconInfo(val iconRes: Int, val color: String, val bgColor: String)

    private fun getIconInfo(type: String, filename: String): IconInfo {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when {
            type == "folder" -> IconInfo(R.drawable.ic_folder, "#FFB300", "#FFF8E1") // Amber
            type.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> 
                IconInfo(R.drawable.ic_image, "#2E7D32", "#E8F5E9") // Green
            type.startsWith("video/") || ext in listOf("mp4", "mkv", "mov", "avi") -> 
                IconInfo(R.drawable.ic_video, "#1565C0", "#E3F2FD") // Blue
            type.contains("pdf", ignoreCase = true) || ext == "pdf" -> 
                IconInfo(R.drawable.ic_pdf, "#C62828", "#FFEBEE") // Red
            ext == "apk" -> IconInfo(R.drawable.ic_android, "#3DDC84", "#E8FAF0") // Android Green
            ext in listOf("zip", "rar", "7z", "tar", "gz") -> 
                IconInfo(R.drawable.ic_archive, "#EF6C00", "#FFF3E0") // Orange
            else -> IconInfo(R.drawable.ic_file, "#455A64", "#ECEFF1") // Grey
        }
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