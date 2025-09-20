#!/usr/bin/env python3
"""
FolderSync Server with Rclone Integration
Supports both legacy file upload and rclone-based sync operations
"""

import os
import sys
import json
import shutil
import subprocess
import tempfile
import logging
from pathlib import Path
from datetime import datetime
from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
from werkzeug.utils import secure_filename
import hashlib

# Fix Windows encoding issues
if sys.platform.startswith('win'):
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.detach())
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.detach())

# Configure logging with proper encoding
log_handlers = [
    logging.FileHandler('foldersync.log', encoding='utf-8'),
]

# Add console handler with proper encoding for Windows
if sys.platform.startswith('win'):
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setStream(sys.stdout)
else:
    console_handler = logging.StreamHandler(sys.stdout)

log_handlers.append(console_handler)

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=log_handlers
)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# Configuration
CONFIG = {
    'UPLOAD_FOLDER': os.path.expanduser('~/Desktop/SyncFolders'),
    'MAX_CONTENT_LENGTH': 500 * 1024 * 1024,  # 500MB max file size
    'ALLOWED_EXTENSIONS': None,  # Allow all file types
    'RCLONE_TIMEOUT': 300,  # 5 minutes timeout for rclone operations
    'ENABLE_RCLONE': True,  # Enable rclone functionality
    'RCLONE_CONFIG_PATH': os.path.expanduser('~/.config/rclone/rclone.conf')
}

app.config['MAX_CONTENT_LENGTH'] = CONFIG['MAX_CONTENT_LENGTH']

# Ensure upload directory exists
os.makedirs(CONFIG['UPLOAD_FOLDER'], exist_ok=True)

def is_rclone_available():
    """Check if rclone is installed and available"""
    try:
        result = subprocess.run(['rclone', 'version'], 
                              capture_output=True, text=True, timeout=10)
        return result.returncode == 0
    except (subprocess.TimeoutExpired, FileNotFoundError):
        return False

def sanitize_filename(filename):
    """Sanitize filename for safe storage"""
    # Remove path separators and other dangerous characters
    filename = secure_filename(filename)
    # Replace spaces with underscores if needed
    # filename = filename.replace(' ', '_')
    return filename

def resolve_target_directory(folder_path, base_upload_folder):
    """Resolve target directory handling both absolute and relative paths"""
    if not folder_path or folder_path == 'default':
        return os.path.join(base_upload_folder, 'default')
    
    # Check if it's an absolute path
    if os.path.isabs(folder_path):
        # Validate that the absolute path is safe (not trying to access system directories)
        normalized_path = os.path.normpath(folder_path)
        
        # Basic security check - don't allow access to system directories
        dangerous_paths = [
            'C:\\Windows', 'C:\\Program Files', 'C:\\Program Files (x86)',
            '/etc', '/usr', '/bin', '/sbin', '/root', '/sys', '/proc'
        ]
        
        for dangerous in dangerous_paths:
            if normalized_path.lower().startswith(dangerous.lower()):
                logger.warning(f"Blocked access to system directory: {normalized_path}")
                # Fall back to relative path in upload folder
                safe_name = secure_filename(os.path.basename(folder_path))
                return os.path.join(base_upload_folder, safe_name)
        
        logger.info(f"Using absolute path: {normalized_path}")
        return normalized_path
    else:
        # Relative path - sanitize and place in upload folder
        safe_folder = secure_filename(folder_path)
        target_dir = os.path.join(base_upload_folder, safe_folder)
        logger.info(f"Using relative path: {target_dir}")
        return target_dir

def get_file_hash(filepath):
    """Calculate MD5 hash of a file"""
    hash_md5 = hashlib.md5()
    try:
        with open(filepath, "rb") as f:
            for chunk in iter(lambda: f.read(4096), b""):
                hash_md5.update(chunk)
        return hash_md5.hexdigest()
    except Exception as e:
        logger.error(f"Error calculating hash for {filepath}: {e}")
        return None

def handle_duplicate_file(target_path, handle_duplicates=True):
    """Handle duplicate files based on settings"""
    if not os.path.exists(target_path):
        return target_path
    
    if not handle_duplicates:
        # Overwrite existing file
        return target_path
    
    # Create duplicate folder structure
    base_dir = os.path.dirname(target_path)
    filename = os.path.basename(target_path)
    name, ext = os.path.splitext(filename)
    
    counter = 1
    while True:
        dup_folder = os.path.join(base_dir, f"dup{counter}")
        os.makedirs(dup_folder, exist_ok=True)
        new_path = os.path.join(dup_folder, filename)
        
        if not os.path.exists(new_path):
            return new_path
        
        counter += 1
        if counter > 100:  # Safety limit
            raise Exception("Too many duplicate files")

def should_skip_existing_file(source_path, target_path, skip_existing=False):
    """Check if file should be skipped based on mirror mode settings"""
    if not skip_existing:
        return False
    
    if not os.path.exists(target_path):
        return False
    
    try:
        # Compare file sizes
        source_size = os.path.getsize(source_path)
        target_size = os.path.getsize(target_path)
        
        if source_size != target_size:
            return False
        
        # Optionally compare modification times or hashes
        # For now, just compare sizes for performance
        logger.info(f"Skipping existing file: {target_path}")
        return True
        
    except Exception as e:
        logger.error(f"Error comparing files: {e}")
        return False

@app.route('/api/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    rclone_status = is_rclone_available() if CONFIG['ENABLE_RCLONE'] else False
    
    return jsonify({
        'status': 'healthy',
        'timestamp': datetime.now().isoformat(),
        'upload_folder': CONFIG['UPLOAD_FOLDER'],
        'rclone_available': rclone_status,
        'rclone_enabled': CONFIG['ENABLE_RCLONE']
    })

@app.route('/api/upload', methods=['POST'])
def upload_file():
    """Legacy file upload endpoint"""
    try:
        # Check if file is present
        if 'file' not in request.files:
            return jsonify({'error': 'No file provided'}), 400
        
        file = request.files['file']
        if file.filename == '':
            return jsonify({'error': 'No file selected'}), 400
        
        # Get form parameters
        original_filename = request.form.get('original_filename', file.filename)
        folder_path = request.form.get('folder_path', 'default')
        handle_duplicates = request.form.get('handle_duplicates', 'true').lower() == 'true'
        skip_existing = request.form.get('skip_existing', 'false').lower() == 'true'
        delete_after_transfer = request.form.get('delete_after_transfer', 'false').lower() == 'true'
        file_size = request.form.get('file_size', '0')
        
        logger.info(f"Legacy upload: {original_filename} -> {folder_path}")
        
        # Resolve target directory (handles both absolute and relative paths)
        target_dir = resolve_target_directory(folder_path, CONFIG['UPLOAD_FOLDER'])
        original_filename = sanitize_filename(original_filename)
        
        # Handle subdirectories in filename
        if '/' in original_filename:
            subdir_path = os.path.dirname(original_filename)
            filename_only = os.path.basename(original_filename)
            target_dir = os.path.join(target_dir, subdir_path)
            target_filename = filename_only
        else:
            target_filename = original_filename
        
        os.makedirs(target_dir, exist_ok=True)
        
        # Create temporary file first
        temp_path = None
        try:
            with tempfile.NamedTemporaryFile(delete=False, suffix=f'_{target_filename}') as temp_file:
                temp_path = temp_file.name
                file.save(temp_path)
            
            # Determine final target path
            target_path = os.path.join(target_dir, target_filename)
            
            # Check if we should skip existing file
            if should_skip_existing_file(temp_path, target_path, skip_existing):
                os.unlink(temp_path)
                return jsonify({
                    'message': 'File skipped (already exists)',
                    'filename': original_filename,
                    'path': target_path,
                    'skipped': True
                })
            
            # Handle duplicates
            if handle_duplicates and os.path.exists(target_path):
                target_path = handle_duplicate_file(target_path, handle_duplicates)
            
            # Move file to final location
            shutil.move(temp_path, target_path)
            
            # Set file permissions
            os.chmod(target_path, 0o644)
            
            logger.info(f"File uploaded successfully: {target_path}")
            
            return jsonify({
                'message': 'File uploaded successfully',
                'filename': original_filename,
                'path': target_path,
                'size': os.path.getsize(target_path),
                'skipped': False
            })
            
        except Exception as e:
            # Clean up temp file on error
            if temp_path and os.path.exists(temp_path):
                os.unlink(temp_path)
            raise e
            
    except Exception as e:
        logger.error(f"Upload error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/rclone-sync', methods=['POST'])
def rclone_sync():
    """Rclone-based sync endpoint"""
    if not CONFIG['ENABLE_RCLONE']:
        return jsonify({'error': 'Rclone is disabled'}), 400
    
    if not is_rclone_available():
        return jsonify({'error': 'Rclone is not installed or not available'}), 500
    
    try:
        # Check if file is present
        if 'file' not in request.files:
            return jsonify({'error': 'No file provided'}), 400
        
        file = request.files['file']
        if file.filename == '':
            return jsonify({'error': 'No file selected'}), 400
        
        # Get form parameters
        original_filename = request.form.get('original_filename', file.filename)
        folder_path = request.form.get('folder_path', 'default')
        rclone_flags = request.form.get('rclone_flags', '--progress --transfers=4')
        sync_direction = request.form.get('sync_direction', 'ANDROID_TO_PC')
        file_size = request.form.get('file_size', '0')
        
        logger.info(f"Rclone sync: {original_filename} -> {folder_path} with flags: {rclone_flags}")
        
        # Resolve target directory (handles both absolute and relative paths)
        target_dir = resolve_target_directory(folder_path, CONFIG['UPLOAD_FOLDER'])
        original_filename = sanitize_filename(original_filename)
        
        # Create source directory structure
        source_base = os.path.join(CONFIG['UPLOAD_FOLDER'], 'rclone_temp')
        os.makedirs(source_base, exist_ok=True)
        
        # Handle subdirectories in filename
        if '/' in original_filename:
            subdir_path = os.path.dirname(original_filename)
            filename_only = os.path.basename(original_filename)
            source_dir = os.path.join(source_base, subdir_path)
            os.makedirs(source_dir, exist_ok=True)
            source_path = os.path.join(source_dir, filename_only)
        else:
            source_path = os.path.join(source_base, original_filename)
        
        # Save uploaded file to temporary location
        file.save(source_path)
        
        try:
            # Create target directory
            os.makedirs(target_dir, exist_ok=True)
            
            # Parse and validate rclone flags
            rclone_flags = rclone_flags.strip()
            if not rclone_flags:
                rclone_flags = '--progress'
            
            # Build rclone command
            if sync_direction == 'ANDROID_TO_PC':
                # For individual files, use rclone copy
                rclone_cmd = [
                    'rclone', 'copy',
                    source_base,  # Source directory
                    target_dir,   # Target directory
                ] + rclone_flags.split()
            else:
                # PC to Android not implemented yet
                return jsonify({'error': 'PC to Android sync not yet implemented'}), 400
            
            logger.info(f"Executing rclone command: {' '.join(rclone_cmd)}")
            
            # Execute rclone command
            result = subprocess.run(
                rclone_cmd,
                capture_output=True,
                text=True,
                timeout=CONFIG['RCLONE_TIMEOUT'],
                cwd=os.path.dirname(source_path)
            )
            
            # Clean up temporary source file
            try:
                if os.path.exists(source_path):
                    os.unlink(source_path)
                # Clean up empty directories
                if os.path.exists(source_base):
                    shutil.rmtree(source_base, ignore_errors=True)
            except Exception as cleanup_error:
                logger.warning(f"Cleanup error: {cleanup_error}")
            
            if result.returncode == 0:
                logger.info(f"Rclone sync completed successfully: {original_filename}")
                
                # Try to find the synced file for size info
                target_file_path = os.path.join(target_dir, os.path.basename(original_filename))
                file_size_actual = os.path.getsize(target_file_path) if os.path.exists(target_file_path) else 0
                
                return jsonify({
                    'message': 'Rclone sync completed successfully',
                    'filename': original_filename,
                    'path': target_file_path,
                    'size': file_size_actual,
                    'rclone_output': result.stdout,
                    'rclone_flags': rclone_flags
                })
            else:
                error_msg = result.stderr or result.stdout or 'Unknown rclone error'
                logger.error(f"Rclone sync failed: {error_msg}")
                return jsonify({
                    'error': f'Rclone sync failed: {error_msg}',
                    'rclone_output': result.stdout,
                    'rclone_error': result.stderr
                }), 500
                
        except subprocess.TimeoutExpired:
            logger.error(f"Rclone sync timeout for {original_filename}")
            return jsonify({'error': 'Rclone sync timeout'}), 500
            
        except Exception as e:
            # Clean up on error
            try:
                if os.path.exists(source_path):
                    os.unlink(source_path)
                if os.path.exists(source_base):
                    shutil.rmtree(source_base, ignore_errors=True)
            except:
                pass
            raise e
            
    except Exception as e:
        logger.error(f"Rclone sync error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/list-files', methods=['GET'])
def list_files():
    """List files in upload directory"""
    try:
        folder_path = request.args.get('folder', '')
        target_dir = os.path.join(CONFIG['UPLOAD_FOLDER'], folder_path) if folder_path else CONFIG['UPLOAD_FOLDER']
        
        if not os.path.exists(target_dir):
            return jsonify({'files': [], 'folders': []})
        
        files = []
        folders = []
        
        for item in os.listdir(target_dir):
            item_path = os.path.join(target_dir, item)
            if os.path.isfile(item_path):
                files.append({
                    'name': item,
                    'size': os.path.getsize(item_path),
                    'modified': os.path.getmtime(item_path)
                })
            elif os.path.isdir(item_path):
                folders.append(item)
        
        return jsonify({
            'files': files,
            'folders': folders,
            'path': folder_path
        })
        
    except Exception as e:
        logger.error(f"List files error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/download/<path:filename>', methods=['GET'])
def download_file(filename):
    """Download a file from the server"""
    try:
        file_path = os.path.join(CONFIG['UPLOAD_FOLDER'], filename)
        
        if not os.path.exists(file_path) or not os.path.isfile(file_path):
            return jsonify({'error': 'File not found'}), 404
        
        return send_file(file_path, as_attachment=True)
        
    except Exception as e:
        logger.error(f"Download error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/config', methods=['GET'])
def get_config():
    """Get server configuration"""
    return jsonify({
        'upload_folder': CONFIG['UPLOAD_FOLDER'],
        'max_file_size': CONFIG['MAX_CONTENT_LENGTH'],
        'rclone_enabled': CONFIG['ENABLE_RCLONE'],
        'rclone_available': is_rclone_available(),
        'rclone_timeout': CONFIG['RCLONE_TIMEOUT']
    })

@app.route('/api/rclone-remotes', methods=['GET'])
def list_rclone_remotes():
    """List available rclone remotes"""
    if not CONFIG['ENABLE_RCLONE'] or not is_rclone_available():
        return jsonify({'error': 'Rclone not available'}), 400
    
    try:
        result = subprocess.run(['rclone', 'listremotes'], 
                              capture_output=True, text=True, timeout=30)
        
        if result.returncode == 0:
            remotes = [remote.strip(':') for remote in result.stdout.strip().split('\n') if remote.strip()]
            return jsonify({'remotes': remotes})
        else:
            return jsonify({'error': 'Failed to list remotes', 'output': result.stderr}), 500
            
    except Exception as e:
        logger.error(f"List remotes error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.errorhandler(413)
def too_large(e):
    return jsonify({'error': 'File too large'}), 413

@app.errorhandler(404)
def not_found(e):
    return jsonify({'error': 'Endpoint not found'}), 404

@app.errorhandler(500)
def internal_error(e):
    return jsonify({'error': 'Internal server error'}), 500

def main():
    """Main function to start the server"""
    import argparse
    
    parser = argparse.ArgumentParser(description='FolderSync Server with Rclone Integration')
    parser.add_argument('--host', default='0.0.0.0', help='Host to bind to')
    parser.add_argument('--port', type=int, default=5016, help='Port to bind to')
    parser.add_argument('--upload-folder', help='Upload folder path')
    parser.add_argument('--disable-rclone', action='store_true', help='Disable rclone functionality')
    parser.add_argument('--debug', action='store_true', help='Enable debug mode')
    
    args = parser.parse_args()
    
    # Update configuration
    if args.upload_folder:
        CONFIG['UPLOAD_FOLDER'] = os.path.expanduser(args.upload_folder)
        os.makedirs(CONFIG['UPLOAD_FOLDER'], exist_ok=True)
    
    if args.disable_rclone:
        CONFIG['ENABLE_RCLONE'] = False
    
    # Check rclone availability
    if CONFIG['ENABLE_RCLONE']:
        if is_rclone_available():
            logger.info("Rclone is available and enabled")
        else:
            logger.warning("Rclone is enabled but not available. Install rclone for advanced sync features.")
    else:
        logger.info("Rclone is disabled")
    
    logger.info("Starting FolderSync Server")
    logger.info(f"Upload folder: {CONFIG['UPLOAD_FOLDER']}")
    logger.info(f"Server: http://{args.host}:{args.port}")
    logger.info(f"Rclone: {'Enabled' if CONFIG['ENABLE_RCLONE'] else 'Disabled'}")
    
    # Start the server
    app.run(
        host=args.host,
        port=args.port,
        debug=args.debug,
        threaded=True
    )

if __name__ == '__main__':
    main()