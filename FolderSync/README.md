# Folder Sync App

A comprehensive Android app for synchronizing folders between your Android device and PC.

## Features

### 📱 Android App
- **Folder Management**: Add, edit, and delete sync folder pairs
- **Flexible Sync Directions**: 
  - Android to PC
  - PC to Android  
  - Bidirectional sync
- **Real-time Progress**: Live sync progress with file-by-file tracking
- **Enable/Disable Folders**: Toggle individual folders on/off
- **Persistent Settings**: Server URL and folder configurations saved automatically

### 🖥️ Python Server (sync_server.py)
- **RESTful API**: Complete API for folder scanning, file upload/download
- **Directory Management**: Automatic folder creation and organization
- **File Integrity**: MD5 hash verification for file changes
- **Concurrent Operations**: Multi-threaded server for simultaneous syncs
- **Progress Tracking**: Real-time sync status and progress reporting

## Setup Instructions

### 1. Python Server Setup
```bash
cd FolderSync
python sync_server.py
```
- Server runs on port **5013**
- Creates sync folders in `~/Desktop/SyncFolders/`
- Access web interface at `http://localhost:5013`

### 2. Android App Installation
1. Build the APK: `./gradlew assembleDebug`
2. Install on your Android device
3. Configure server IP in settings (e.g., `http://192.168.1.100:5013`)

### 3. Folder Configuration
1. Open the app and tap the settings icon
2. Set your PC's IP address
3. Tap the + button to add sync folders
4. Configure:
   - **Folder Name**: Display name
   - **Android Path**: Path on your device
   - **PC Path**: Relative path in sync folder
   - **Sync Direction**: Choose sync behavior

### 4. Synchronization
1. Enable desired folders with toggle switches
2. Tap the sync button (floating action button)
3. Monitor real-time progress for each folder
4. View completion status and any errors

## API Endpoints

- `GET /api/folders` - List available PC folders
- `POST /api/scan` - Scan folder contents
- `POST /api/upload` - Upload file to PC
- `GET /api/download/<filename>` - Download file from PC
- `POST /api/sync/start` - Start synchronization
- `GET /api/sync/status/<sync_id>` - Get sync progress
- `GET /api/health` - Server health check

## Example Folder Setup

**Android Path**: `/storage/emulated/0/DCIM/Camera`
**PC Path**: `Photos/Camera`
**Result**: Camera photos sync to `~/Desktop/SyncFolders/Photos/Camera/`

## Network Requirements

- Both devices on same WiFi network
- Android device can reach PC's IP address
- Port 5013 accessible (check firewall settings)

## File Handling

- **Filename Preservation**: Spaces and special characters maintained
- **Duplicate Handling**: Automatic renaming (file (1).jpg, file (2).jpg)
- **Directory Structure**: Full folder hierarchy preserved
- **Large File Support**: Up to 500MB per file
- **Resume Capability**: Failed uploads automatically retry

## Sync Directions Explained

- **ANDROID_TO_PC**: Only upload new/changed files from Android to PC
- **PC_TO_ANDROID**: Only download new/changed files from PC to Android
- **BOTH**: Full bidirectional sync (most common choice)

## Troubleshooting

1. **Connection Issues**: Verify IP address and ensure both devices on same network
2. **Permission Errors**: Grant storage permissions to Android app
3. **Sync Failures**: Check server logs for detailed error messages
4. **Large Files**: Increase timeout settings for very large files

## Security Notes

- Server only accepts connections from local network
- No authentication required (local network only)
- Files stored in user's desktop folder
- No data transmitted outside local network