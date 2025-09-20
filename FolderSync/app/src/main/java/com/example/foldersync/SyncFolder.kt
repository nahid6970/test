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
    // Rclone integration
    val useRclone: Boolean = false,
    val rcloneFlags: String = "--progress --transfers=4 --checkers=8 --contimeout=60s --timeout=300s --retries=3",
    // Legacy sync options (kept for backward compatibility)
    val deleteAfterTransfer: Boolean = false,
    val moveDuplicatesToFolder: Boolean = true,
    val skipExistingFiles: Boolean = true,
    // PC to Android sync options
    val pcToAndroidDeleteAfterTransfer: Boolean = false,
    val pcToAndroidMoveDuplicatesToFolder: Boolean = true,
    val pcToAndroidSkipExistingFiles: Boolean = true
) {
    // Helper property to get Uri from String
    val androidUri: Uri?
        get() = androidUriString?.let { Uri.parse(it) }
}

enum class SyncDirection {
    ANDROID_TO_PC,
    PC_TO_ANDROID
}



data class SyncStatus(
    val folderId: String,
    val status: SyncState,
    val progress: Float = 0f,
    val currentFile: String = "",
    val filesProcessed: Int = 0,
    val totalFiles: Int = 0,
    val errorMessage: String? = null
)

enum class SyncState {
    IDLE,
    SCANNING,
    SYNCING,
    COMPLETED,
    ERROR
}