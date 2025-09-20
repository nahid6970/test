#!/bin/bash

# FolderSync Server Setup Script

echo "🚀 Setting up FolderSync Server with Rclone Integration"

# Check if Python 3 is installed
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 is not installed. Please install Python 3.7 or later."
    exit 1
fi

echo "✅ Python 3 found: $(python3 --version)"

# Check if pip is installed
if ! command -v pip3 &> /dev/null; then
    echo "❌ pip3 is not installed. Please install pip3."
    exit 1
fi

echo "✅ pip3 found"

# Install Python dependencies
echo "📦 Installing Python dependencies..."
pip3 install -r requirements.txt

if [ $? -eq 0 ]; then
    echo "✅ Python dependencies installed successfully"
else
    echo "❌ Failed to install Python dependencies"
    exit 1
fi

# Check if rclone is installed
if command -v rclone &> /dev/null; then
    echo "✅ Rclone found: $(rclone version | head -n 1)"
else
    echo "⚠️  Rclone not found. Installing rclone..."
    
    # Install rclone based on OS
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        curl https://rclone.org/install.sh | sudo bash
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        if command -v brew &> /dev/null; then
            brew install rclone
        else
            curl https://rclone.org/install.sh | sudo bash
        fi
    elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
        # Windows
        echo "Please install rclone manually from https://rclone.org/downloads/"
        echo "Or use: winget install Rclone.Rclone"
    else
        echo "Unknown OS. Please install rclone manually from https://rclone.org/downloads/"
    fi
fi

# Create upload directory
UPLOAD_DIR="$HOME/Desktop/SyncFolders"
mkdir -p "$UPLOAD_DIR"
echo "✅ Upload directory created: $UPLOAD_DIR"

# Create systemd service file (Linux only)
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    echo "📝 Creating systemd service file..."
    
    SERVICE_FILE="$HOME/.config/systemd/user/foldersync.service"
    mkdir -p "$(dirname "$SERVICE_FILE")"
    
    cat > "$SERVICE_FILE" << EOF
[Unit]
Description=FolderSync Server
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$(pwd)
ExecStart=$(which python3) $(pwd)/server.py --host 0.0.0.0 --port 5016
Restart=always
RestartSec=10

[Install]
WantedBy=default.target
EOF

    echo "✅ Systemd service file created: $SERVICE_FILE"
    echo "To enable auto-start: systemctl --user enable foldersync.service"
    echo "To start service: systemctl --user start foldersync.service"
fi

echo ""
echo "🎉 Setup completed successfully!"
echo ""
echo "📋 Next steps:"
echo "1. Configure rclone (if needed): rclone config"
echo "2. Start the server: python3 server.py"
echo "3. Server will be available at: http://localhost:5016"
echo ""
echo "🔧 Server options:"
echo "  --host 0.0.0.0          # Listen on all interfaces"
echo "  --port 5016             # Port number"
echo "  --upload-folder PATH    # Custom upload folder"
echo "  --disable-rclone        # Disable rclone features"
echo "  --debug                 # Enable debug mode"
echo ""
echo "📱 Update your Android app server URL to: http://YOUR_IP:5016"