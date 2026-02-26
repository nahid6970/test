package com.convexfiles

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FileAdapter(
    private var files: List<FileItem>,
    private val onDelete: (FileItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.icon)
        val filename: TextView = view.findViewById(R.id.filename)
        val fileInfo: TextView = view.findViewById(R.id.fileInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        holder.filename.text = file.filename
        holder.fileInfo.text = formatSize(file.fileSize)
        holder.icon.text = getIcon(file.fileType)
        
        holder.itemView.setOnClickListener {
            file.url?.let { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                holder.itemView.context.startActivity(intent)
            }
        }
        
        holder.itemView.setOnLongClickListener {
            onDelete(file)
            true
        }
    }

    override fun getItemCount() = files.size

    fun updateFiles(newFiles: List<FileItem>) {
        files = newFiles
        notifyDataSetChanged()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

    private fun getIcon(type: String): String = when {
        type.contains("image") -> "🖼️"
        type.contains("video") -> "🎥"
        type.contains("audio") -> "🎵"
        type.contains("pdf") -> "📕"
        else -> "📄"
    }
}
