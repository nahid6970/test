@echo off
REM Set UTF-8 encoding for Windows console
chcp 65001 >nul 2>&1

REM Set environment variable to force UTF-8 encoding
set PYTHONIOENCODING=utf-8

echo Starting FolderSync Server...
echo.

REM Start the server with UTF-8 support
python server.py %*

pause