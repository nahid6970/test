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
    // Rclone is now the default sync method
    val rcloneCommand: RcloneCommand = RcloneCommand.SYNC,
    // Individual rclone flags as booleans
    val flagProgress: Boolean = true,
    val flagTransfers4: Boolean = true,
    val flagCheckers8: Boolean = true,
    val flagContimeout60s: Boolean = true,
    val flagTimeout300s: Boolean = true,
    val flagRetries3: Boolean = true,
    val flagIgnoreExisting: Boolean = false,
    val flagTrackRenames: Boolean = false,
    val flagFastList: Boolean = false
) {
    // Helper property to get Uri from String
    val androidUri: Uri?
        get() = androidUriString?.let { Uri.parse(it) }
    
    // Helper property to build rclone flags string
    val rcloneFlags: String
        get() = buildString {
            if (flagProgress) append(" --progress")
            if (flagTransfers4) append(" --transfers=4")
            if (flagCheckers8) append(" --checkers=8")
            if (flagContimeout60s) append(" --contimeout=60s")
            if (flagTimeout300s) append(" --timeout=300s")
            if (flagRetries3) append(" --retries=3")
            if (flagIgnoreExisting) append(" --ignore-existing")
            if (flagTrackRenames) append(" --track-renames")
            if (flagFastList) append(" --fast-list")
        }.trim()
}

enum class SyncDirection {
    ANDROID_TO_PC,
    PC_TO_ANDROID
}

enum class RcloneCommand {
    SYNC,  // Make destination identical to source
    COPY   // Copy files from source to destination
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