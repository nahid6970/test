package com.example.foldersync

import android.net.Uri

data class SyncFolder(
    val id: String,
    val name: String,
    val androidPath: String,
    val androidUriString: String?, // Changed from Uri to String for Gson compatibility
    val pcPath: String,
    val isEnabled: Boolean = true,
    val lastSyncTime: Long = 0L,
    val syncDirection: SyncDirection = SyncDirection.ANDROID_TO_PC,
    val androidToPcMode: SyncMode = SyncMode.COPY_AND_DELETE,
    val pcToAndroidMode: SyncMode = SyncMode.COPY_AND_DELETE,
    val ignorePrefixes: String = "",
    val ignoreSuffixes: String = "",
    val ignoreFolders: String = ""
) {
    // Helper property to get Uri from String
    val androidUri: Uri?
        get() = androidUriString?.let { Uri.parse(it) }
}

enum class SyncDirection {
    ANDROID_TO_PC,
    PC_TO_ANDROID
}

enum class SyncMode {
    COPY_AND_DELETE,  // Move files after successful sync
    MIRROR,           // Compare files, don't copy duplicates
    SYNC              // Like mirror but handle duplicate names differently
}

data class SyncStatus(
    val folderId: String,
    val status: SyncState,
    val progress: Float = 0f,
    val currentFile: String = "",
    val filesProcessed: Int = 0,
    val totalFiles: Int = 0,
    val errorMessage: String? = null,
    val syncSummary: SyncSummary? = null
)

data class SyncSummary(
    val uploadedFiles: List<String> = emptyList(),
    val downloadedFiles: List<String> = emptyList(),
    val updatedFiles: List<String> = emptyList(),
    val deletedFiles: List<String> = emptyList(),
    val skippedFiles: List<String> = emptyList(),
    val failedFiles: List<String> = emptyList()
)

enum class SyncState {
    IDLE,
    SCANNING,
    SYNCING,
    COMPLETED,
    ERROR
}