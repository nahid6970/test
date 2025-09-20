#!/usr/bin/env python3
"""
Windows-friendly launcher for FolderSync Server
Handles encoding issues and provides a clean startup experience
"""

import os
import sys
import subprocess

def main():
    # Set UTF-8 encoding for Windows
    if sys.platform.startswith('win'):
        os.environ['PYTHONIOENCODING'] = 'utf-8'
        # Try to set console to UTF-8
        try:
            subprocess.run(['chcp', '65001'], shell=True, capture_output=True)
        except:
            pass
    
    print("=" * 50)
    print("FolderSync Server with Rclone Integration")
    print("=" * 50)
    print()
    
    # Import and run the main server
    try:
        # Add current directory to Python path
        current_dir = os.path.dirname(os.path.abspath(__file__))
        sys.path.insert(0, current_dir)
        
        # Import the server module
        import server
        
        # Run the server
        server.main()
        
    except KeyboardInterrupt:
        print("\nServer stopped by user")
    except ImportError as e:
        print(f"Error importing server module: {e}")
        print("Make sure server.py is in the same directory")
        sys.exit(1)
    except Exception as e:
        print(f"Error starting server: {e}")
        sys.exit(1)

if __name__ == '__main__':
    main()