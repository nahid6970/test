# FolderSync Server with Rclone Integration

A powerful Python server that supports both legacy file uploads and advanced rclone-based sync operations for the FolderSync Android app.

## 🚀 Features

### Legacy Sync Support
- ✅ Direct file uploads via HTTP
- ✅ Directory structure preservation
- ✅ Duplicate file handling
- ✅ Skip existing files (mirror mode)
- ✅ Delete after transfer option

### Rclone Integration
- 🚀 **70+ cloud storage providers** (Google Drive, Dropbox, OneDrive, AWS S3, etc.)
- ⚡ **High performance** with parallel transfers
- 🔧 **Extensive customization** with rclone flags
- 📊 **Progress tracking** and detailed logging
- 🔄 **Resume capability** for interrupted transfers
- 🛡️ **Data integrity** with checksum verification

## 📋 Requirements

- **Python 3.7+**
- **pip3** (Python package manager)
- **rclone** (optional, for advanced sync features)

## 🛠️ Installation

### Quick Setup (Recommended)

#### Linux/macOS:
```bash
chmod +x setup.sh
./setup.sh
```

#### Windows:
```cmd
setup.bat
```

### Manual Installation

1. **Install Python dependencies:**
```bash
pip3 install -r requirements.txt
```

2. **Install rclone (optional but recommended):**

**Linux/macOS:**
```bash
curl https://rclone.org/install.sh | sudo bash
```

**Windows:**
```cmd
winget install Rclone.Rclone
```

**Or download from:** https://rclone.org/downloads/

3. **Configure rclone (if using cloud storage):**
```bash
rclone config
```

## 🚀 Usage

### Start the Server

**Linux/macOS:**
```bash
python3 server.py
```

**Windows (Recommended):**
```cmd
python start_server.py
```
*Or use the batch file:*
```cmd
start_server.bat
```

**Advanced options:**
```bash
python3 server.py --host 0.0.0.0 --port 5016 --upload-folder ~/MySync
```

### Server Options

| Option | Default | Description |
|--------|---------|-------------|
| `--host` | `0.0.0.0` | Host to bind to |
| `--port` | `5016` | Port number |
| `--upload-folder` | `~/Desktop/SyncFolders` | Upload directory |
| `--disable-rclone` | `False` | Disable rclone features |
| `--debug` | `False` | Enable debug mode |

### Android App Configuration

Update your Android app server URL to:
```
http://YOUR_SERVER_IP:5016
```

## 🔧 API Endpoints

### Health Check
```
GET /api/health
```
Returns server status and rclone availability.

### Legacy File Upload
```
POST /api/upload
```
**Parameters:**
- `file`: File to upload
- `original_filename`: Original filename with path
- `folder_path`: Target folder
- `handle_duplicates`: Handle duplicate files (true/false)
- `skip_existing`: Skip existing files (true/false)
- `delete_after_transfer`: Delete source after transfer (true/false)

### Rclone Sync
```
POST /api/rclone-sync
```
**Parameters:**
- `file`: File to upload
- `original_filename`: Original filename with path
- `folder_path`: Target folder
- `rclone_flags`: Rclone command flags
- `sync_direction`: ANDROID_TO_PC or PC_TO_ANDROID

### List Files
```
GET /api/list-files?folder=path
```
List files in upload directory.

### Download File
```
GET /api/download/<filename>
```
Download a file from the server.

### Configuration
```
GET /api/config
```
Get server configuration.

### Rclone Remotes
```
GET /api/rclone-remotes
```
List available rclone remotes.

## 🎯 Rclone Configuration Examples

### Popular Rclone Flags

| Flag | Description |
|------|-------------|
| `--progress` | Show transfer progress |
| `--transfers=4` | Number of parallel transfers |
| `--checkers=8` | Number of checkers to run in parallel |
| `--delete-after` | Delete source files after successful transfer |
| `--update` | Skip files that are newer on destination |
| `--checksum` | Use checksums for file comparison |
| `--dry-run` | Test run without actual transfers |
| `--bandwidth=10M` | Limit bandwidth to 10MB/s |
| `--exclude="*.tmp"` | Exclude temporary files |

### Example Configurations

**High Performance:**
```
--progress --transfers=8 --checkers=16 --buffer-size=32M
```

**Bandwidth Limited:**
```
--progress --transfers=2 --bandwidth=5M --timeout=300s
```

**Safe Sync with Verification:**
```
--progress --checksum --update --retries=3 --timeout=600s
```

**Move Files (Delete After Transfer):**
```
--progress --delete-after --transfers=4
```

## 🌐 Cloud Storage Setup

### Google Drive
```bash
rclone config
# Choose: Google Drive
# Follow authentication steps
```

### Dropbox
```bash
rclone config
# Choose: Dropbox
# Follow authentication steps
```

### OneDrive
```bash
rclone config
# Choose: Microsoft OneDrive
# Follow authentication steps
```

### AWS S3
```bash
rclone config
# Choose: Amazon S3
# Enter access key and secret
```

## 📊 Monitoring and Logs

### Log Files
- **Application logs:** `foldersync.log`
- **Console output:** Real-time status and errors

### Health Monitoring
Check server status:
```bash
curl http://localhost:5016/api/health
```

## 🔒 Security Considerations

1. **Network Security:**
   - Run on trusted networks only
   - Use firewall rules to restrict access
   - Consider VPN for remote access

2. **File Permissions:**
   - Server creates files with 644 permissions
   - Upload directory should have appropriate access controls

3. **Input Validation:**
   - Filenames are sanitized automatically
   - File size limits are enforced
   - Path traversal protection included

## 🐛 Troubleshooting

### Common Issues

**"Rclone not found"**
- Install rclone: `curl https://rclone.org/install.sh | sudo bash`
- Check PATH: `which rclone`

**"Permission denied"**
- Check upload directory permissions
- Ensure server has write access

**"Connection refused"**
- Check if server is running: `ps aux | grep server.py`
- Verify port is not blocked: `netstat -tlnp | grep 5016`
- Check firewall settings

**"File too large"**
- Increase `MAX_CONTENT_LENGTH` in server configuration
- Check available disk space

**"Unicode encoding errors" (Windows)**
- Use `python start_server.py` instead of `server.py`
- Or use `start_server.bat`
- Set environment variable: `set PYTHONIOENCODING=utf-8`

### Debug Mode
Run with debug enabled:
```bash
python3 server.py --debug
```

## 🔄 Auto-Start (Linux)

Enable auto-start with systemd:
```bash
systemctl --user enable foldersync.service
systemctl --user start foldersync.service
```

Check status:
```bash
systemctl --user status foldersync.service
```

## 📈 Performance Tips

1. **Use SSD storage** for upload directory
2. **Increase transfers** for faster sync: `--transfers=8`
3. **Use local network** for best performance
4. **Enable rclone** for advanced features
5. **Monitor system resources** during large transfers

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License.

## 🆘 Support

For issues and questions:
1. Check the troubleshooting section
2. Review server logs
3. Test with debug mode enabled
4. Create an issue with detailed information

---

**Happy syncing! 🚀**