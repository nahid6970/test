package com.mycloud.filesexplorer

data class FileItem(
    val _id: String,
    val filename: String,
    val fileType: String,
    val fileSize: Long,
    val storageId: String,
    val url: String? = null
)