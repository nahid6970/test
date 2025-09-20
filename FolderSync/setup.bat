@echo off
echo Setting up FolderSync Server with Rclone Integration

REM Check if Python is installed
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python is not installed. Please install Python 3.7 or later.
    echo Download from: https://www.python.org/downloads/
    pause
    exit /b 1
)

echo [OK] Python found
python --version

REM Install Python dependencies
echo [INFO] Installing Python dependencies...
pip install -r requirements.txt

if %errorlevel% neq 0 (
    echo [ERROR] Failed to install Python dependencies
    pause
    exit /b 1
)

echo [OK] Python dependencies installed successfully

REM Check if rclone is installed
rclone version >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARN] Rclone not found. Please install rclone manually.
    echo Download from: https://rclone.org/downloads/
    echo Or use: winget install Rclone.Rclone
) else (
    echo [OK] Rclone found
    rclone version | findstr /C:"rclone"
)

REM Create upload directory
set UPLOAD_DIR=%USERPROFILE%\Desktop\SyncFolders
if not exist "%UPLOAD_DIR%" mkdir "%UPLOAD_DIR%"
echo [OK] Upload directory created: %UPLOAD_DIR%

echo.
echo [SUCCESS] Setup completed successfully!
echo.
echo Next steps:
echo 1. Configure rclone (if needed): rclone config
echo 2. Start the server: python server.py
echo 3. Server will be available at: http://localhost:5016
echo.
echo Server options:
echo   --host 0.0.0.0          # Listen on all interfaces
echo   --port 5016             # Port number
echo   --upload-folder PATH    # Custom upload folder
echo   --disable-rclone        # Disable rclone features
echo   --debug                 # Enable debug mode
echo.
echo Update your Android app server URL to: http://YOUR_IP:5016
echo.
pause