import os
import json
import time
import hashlib
from flask import Flask, request, jsonify, send_from_directory
from werkzeug.utils import secure_filename
import threading
from datetime import datetime

app = Flask(__name__)
app.secret_key = 'folder_sync_secret_key'

# Configuration
SYNC_BASE_FOLDER = os.path.expanduser('~/Desktop/SyncFolders')
app.config['SYNC_BASE_FOLDER'] = SYNC_BASE_FOLDER
app.config['MAX_CONTENT_LENGTH'] = None  # No file size limit

# Ensure sync base folder exists
if not os.path.exists(SYNC_BASE_FOLDER):
    os.makedirs(SYNC_BASE_FOLDER)

# Global sync status tracking
sync_status = {}
sync_lock = threading.Lock()

def create_safe_filename(filename):
    """Create a safe filename that preserves spaces and most special characters"""
    # Split path into components to handle directories
    path_parts = filename.split('/')
    safe_parts = []
    
    for part in path_parts:
        if not part:  # Skip empty parts
            continue
            
        # Remove dangerous characters but preserve spaces and common symbols
        import re
        safe_part = re.sub(r'[<>:"|?*\x00-\x1f]', '_', part)
        # Prevent directory traversal
        safe_part = safe_part.replace('..', '_')
        # Remove leading/trailing whitespace and dots
        safe_part = safe_part.strip('. ')
        # Ensure part is not empty
        if not safe_part:
            safe_part = 'unnamed'
            
        safe_parts.append(safe_part)
    
    # Rejoin with forward slashes (works on both Windows and Unix)
    safe_filename = '/'.join(safe_parts) if safe_parts else 'unnamed_file'
    return safe_filename

def get_file_hash(file_path):
    """Calculate MD5 hash of a file"""
    hash_md5 = hashlib.md5()
    try:
        with open(file_path, "rb") as f:
            for chunk in iter(lambda: f.read(4096), b""):
                hash_md5.update(chunk)
        return hash_md5.hexdigest()
    except:
        return None

def scan_directory(directory_path):
    """Scan directory and return file information"""
    files_info = []
    
    if not os.path.exists(directory_path):
        return files_info
    
    for root, dirs, files in os.walk(directory_path):
        for file in files:
            file_path = os.path.join(root, file)
            relative_path = os.path.relpath(file_path, directory_path)
            
            try:
                stat = os.stat(file_path)
                files_info.append({
                    'path': relative_path.replace('\\', '/'),  # Normalize path separators
                    'size': stat.st_size,
                    'modified': stat.st_mtime,
                    'hash': get_file_hash(file_path)
                })
            except:
                continue
    
    return files_info

@app.route('/api/folders', methods=['GET'])
def get_folders():
    """Get list of available PC folders for syncing"""
    try:
        folders = []
        for item in os.listdir(SYNC_BASE_FOLDER):
            item_path = os.path.join(SYNC_BASE_FOLDER, item)
            if os.path.isdir(item_path):
                folders.append({
                    'name': item,
                    'path': item,
                    'file_count': len([f for f in os.listdir(item_path) if os.path.isfile(os.path.join(item_path, f))])
                })
        
        return jsonify({'folders': folders})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/scan', methods=['POST'])
def scan_folder():
    """Scan a PC folder and return file information"""
    try:
        data = request.get_json()
        folder_path = data.get('folder_path', '')
        
        if not folder_path:
            return jsonify({'error': 'folder_path is required'}), 400
        
        full_path = os.path.join(SYNC_BASE_FOLDER, folder_path)
        
        # Security check - ensure path is within sync base folder
        if not os.path.abspath(full_path).startswith(os.path.abspath(SYNC_BASE_FOLDER)):
            return jsonify({'error': 'Invalid folder path'}), 400
        
        files_info = scan_directory(full_path)
        
        return jsonify({
            'folder_path': folder_path,
            'files': files_info,
            'total_files': len(files_info)
        })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/upload', methods=['POST'])
def upload_file():
    """Upload a file to PC"""
    try:
        if 'file' not in request.files:
            return jsonify({'error': 'No file provided'}), 400
        
        file = request.files['file']
        if file.filename == '':
            return jsonify({'error': 'No file selected'}), 400
        
        # Get folder path, original filename, and duplicate handling preference
        folder_path = request.form.get('folder_path', '')
        original_filename = request.form.get('original_filename', file.filename)
        handle_duplicates = request.form.get('handle_duplicates', 'true').lower() == 'true'
        
        if not folder_path:
            return jsonify({'error': 'folder_path is required'}), 400
        
        # Create safe filename and path
        safe_filename = create_safe_filename(original_filename)
        target_folder = os.path.join(SYNC_BASE_FOLDER, folder_path)
        
        # Ensure target folder exists
        os.makedirs(target_folder, exist_ok=True)
        
        # Handle file path with directories
        file_path = os.path.join(target_folder, safe_filename)
        file_dir = os.path.dirname(file_path)
        if file_dir and file_dir != target_folder:
            os.makedirs(file_dir, exist_ok=True)
        
        # Handle duplicate filenames based on user preference
        if handle_duplicates and os.path.exists(file_path):
            # Find the next available dup folder number
            dup_counter = 1
            while os.path.exists(os.path.join(target_folder, f"dup{dup_counter}")):
                dup_counter += 1
            
            # Create the duplicate folder
            dup_folder = os.path.join(target_folder, f"dup{dup_counter}")
            os.makedirs(dup_folder, exist_ok=True)
            
            # Prepare file names
            existing_file_name = os.path.basename(file_path)
            base_name, extension = os.path.splitext(existing_file_name)
            
            # Move existing file to duplicate folder with "_existing" suffix
            existing_new_name = f"{base_name}_existing{extension}"
            existing_new_path = os.path.join(dup_folder, existing_new_name)
            
            try:
                import shutil
                shutil.move(file_path, existing_new_path)
                print(f"📁 Moved existing file to: dup{dup_counter}/{existing_new_name}")
            except Exception as e:
                print(f"⚠️ Could not move existing file: {e}")
            
            # Set path for new file with "_new" suffix in the same duplicate folder
            new_file_name = f"{base_name}_new{extension}"
            file_path = os.path.join(dup_folder, new_file_name)
            print(f"📁 Will save new file as: dup{dup_counter}/{new_file_name}")
            
        elif not handle_duplicates:
            # Original behavior - add counter to filename
            counter = 1
            base_name, extension = os.path.splitext(safe_filename)
            original_file_path = file_path
            
            while os.path.exists(file_path):
                safe_filename = f"{base_name} ({counter}){extension}"
                file_path = os.path.join(target_folder, safe_filename)
                counter += 1
        
        # Save file
        start_time = time.time()
        file.save(file_path)
        end_time = time.time()
        
        # Get file info
        file_size = os.path.getsize(file_path)
        upload_time = end_time - start_time
        speed = file_size / upload_time if upload_time > 0 else 0
        
        # Determine the final filename for logging
        final_filename = os.path.basename(file_path)
        if handle_duplicates and "dup" in file_path:
            dup_folder_name = os.path.basename(os.path.dirname(file_path))
            print(f"✅ Uploaded to duplicate folder: {dup_folder_name}/{final_filename} ({file_size} bytes) in {upload_time:.2f}s ({speed/1024:.1f} KB/s)")
        else:
            print(f"✅ Uploaded: {final_filename} ({file_size} bytes) in {upload_time:.2f}s ({speed/1024:.1f} KB/s)")
        
        return jsonify({
            'success': True,
            'filename': safe_filename,
            'size': file_size,
            'upload_time': upload_time
        })
    
    except Exception as e:
        print(f"❌ Upload error: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/download/<path:filename>')
def download_file(filename):
    """Download a file from PC"""
    try:
        folder_path = request.args.get('folder_path', '')
        if not folder_path:
            return jsonify({'error': 'folder_path is required'}), 400
        
        target_folder = os.path.join(SYNC_BASE_FOLDER, folder_path)
        
        # Security check
        file_path = os.path.join(target_folder, filename)
        if not os.path.abspath(file_path).startswith(os.path.abspath(SYNC_BASE_FOLDER)):
            return jsonify({'error': 'Invalid file path'}), 400
        
        if not os.path.exists(file_path):
            return jsonify({'error': 'File not found'}), 404
        
        return send_from_directory(target_folder, filename)
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/sync/start', methods=['POST'])
def start_sync():
    """Start synchronization process"""
    try:
        data = request.get_json()
        sync_id = data.get('sync_id', str(int(time.time())))
        folders = data.get('folders', [])
        
        if not folders:
            return jsonify({'error': 'No folders provided'}), 400
        
        with sync_lock:
            sync_status[sync_id] = {
                'status': 'started',
                'folders': folders,
                'progress': 0,
                'current_folder': '',
                'start_time': time.time(),
                'completed_folders': 0,
                'total_folders': len(folders),
                'errors': []
            }
        
        # Start sync in background thread
        threading.Thread(target=perform_sync, args=(sync_id, folders)).start()
        
        return jsonify({
            'sync_id': sync_id,
            'status': 'started',
            'message': f'Sync started for {len(folders)} folders'
        })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/sync/status/<sync_id>')
def get_sync_status(sync_id):
    """Get synchronization status"""
    with sync_lock:
        status = sync_status.get(sync_id, {'status': 'not_found'})
    
    return jsonify(status)

def perform_sync(sync_id, folders):
    """Perform the actual synchronization (background task)"""
    try:
        with sync_lock:
            sync_status[sync_id]['status'] = 'syncing'
        
        for i, folder in enumerate(folders):
            folder_name = folder.get('name', f'folder_{i}')
            
            with sync_lock:
                sync_status[sync_id]['current_folder'] = folder_name
                sync_status[sync_id]['progress'] = i / len(folders)
            
            # Simulate sync work (replace with actual sync logic)
            time.sleep(2)  # Simulate processing time
            
            # Create folder if it doesn't exist
            folder_path = os.path.join(SYNC_BASE_FOLDER, folder_name)
            os.makedirs(folder_path, exist_ok=True)
            
            with sync_lock:
                sync_status[sync_id]['completed_folders'] = i + 1
        
        # Mark as completed
        with sync_lock:
            sync_status[sync_id]['status'] = 'completed'
            sync_status[sync_id]['progress'] = 1.0
            sync_status[sync_id]['end_time'] = time.time()
            sync_status[sync_id]['current_folder'] = ''
        
        print(f"✅ Sync {sync_id} completed successfully")
    
    except Exception as e:
        with sync_lock:
            sync_status[sync_id]['status'] = 'error'
            sync_status[sync_id]['errors'].append(str(e))
        
        print(f"❌ Sync {sync_id} failed: {e}")

@app.route('/api/health')
def health_check():
    """Health check endpoint"""
    return jsonify({
        'status': 'healthy',
        'timestamp': datetime.now().isoformat(),
        'sync_base_folder': SYNC_BASE_FOLDER
    })

if __name__ == "__main__":
    print(f"🚀 Folder Sync Server starting...")
    print(f"📁 Sync base folder: {SYNC_BASE_FOLDER}")
    print(f"🌐 Server will be available at: http://localhost:5016")
    print(f"💡 Make sure your Android device is on the same network")
    
    # Optimized server settings for better performance
    app.run(
        host="0.0.0.0", 
        port=5016, 
        debug=False,  # Disable debug for better performance
        threaded=True,  # Enable threading for concurrent requests
        use_reloader=False  # Disable auto-reloader for stability
    )