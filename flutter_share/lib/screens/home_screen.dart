import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'package:receive_sharing_intent/receive_sharing_intent.dart';
import 'dart:io';
import '../services/file_upload_service.dart';

class HomeScreen extends StatefulWidget {
  final GlobalKey<_HomeScreenState> key = GlobalKey<_HomeScreenState>();

  HomeScreen() : super(key: key);

  void handleSharedFiles(List<SharedMediaFile> files) {
    key.currentState?._handleSharedFiles(files);
  }

  @override
  _HomeScreenState createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final FileUploadService _uploadService = FileUploadService();
  bool _isUploading = false;
  double _uploadProgress = 0.0;
  String _statusMessage = '';
  List<File> _selectedFiles = [];
  String _currentFileName = '';
  int _currentFileIndex = 0;
  int _totalFiles = 0;

  void _handleSharedFiles(List<SharedMediaFile> sharedFiles) {
    final files = sharedFiles
        .where((file) => file.path.isNotEmpty)
        .map((file) => File(file.path))
        .toList();
    
    if (files.isNotEmpty) {
      setState(() {
        _selectedFiles = files;
      });
      _uploadFiles();
    }
  }

  Future<void> _pickFiles() async {
    try {
      FilePickerResult? result = await FilePicker.platform.pickFiles(
        allowMultiple: true,
        type: FileType.any,
      );

      if (result != null) {
        setState(() {
          _selectedFiles = result.paths
              .where((path) => path != null)
              .map((path) => File(path!))
              .toList();
        });
      }
    } catch (e) {
      _showSnackBar('Error picking files: ${e.toString()}', isError: true);
    }
  }

  Future<void> _uploadFiles() async {
    if (_selectedFiles.isEmpty) {
      _showSnackBar('No files selected', isError: true);
      return;
    }

    setState(() {
      _isUploading = true;
      _uploadProgress = 0.0;
      _statusMessage = 'Starting upload...';
      _totalFiles = _selectedFiles.length;
    });

    final results = await _uploadService.uploadMultipleFiles(
      _selectedFiles,
      onFileProgress: (current, total, fileName) {
        setState(() {
          _currentFileIndex = current;
          _currentFileName = fileName;
          _statusMessage = 'Uploading $fileName ($current/$total)';
        });
      },
      onOverallProgress: (progress) {
        setState(() {
          _uploadProgress = progress;
        });
      },
    );

    final successCount = results.where((r) => r.success).length;
    final failCount = results.length - successCount;

    setState(() {
      _isUploading = false;
      _uploadProgress = 1.0;
      if (failCount == 0) {
        _statusMessage = 'All $successCount files uploaded successfully!';
      } else {
        _statusMessage = '$successCount uploaded, $failCount failed';
      }
    });

    if (failCount == 0) {
      _showSnackBar('All files uploaded successfully!');
      setState(() {
        _selectedFiles.clear();
      });
    } else {
      _showSnackBar('Some files failed to upload', isError: true);
    }
  }

  Future<void> _testConnection() async {
    _showSnackBar('Testing connection...');
    final isConnected = await _uploadService.testConnection();
    
    if (isConnected) {
      _showSnackBar('Connection successful!');
    } else {
      _showSnackBar('Connection failed. Check server URL in settings.', isError: true);
    }
  }

  void _showSnackBar(String message, {bool isError = false}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: isError ? Colors.red : Colors.green,
        duration: Duration(seconds: 3),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('PC File Share'),
        backgroundColor: Colors.green,
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            icon: Icon(Icons.wifi),
            onPressed: _testConnection,
            tooltip: 'Test Connection',
          ),
        ],
      ),
      body: Padding(
        padding: EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Card(
              child: Padding(
                padding: EdgeInsets.all(16.0),
                child: Column(
                  children: [
                    Icon(
                      Icons.cloud_upload,
                      size: 64,
                      color: Colors.green,
                    ),
                    SizedBox(height: 16),
                    Text(
                      'Share files to your PC',
                      style: Theme.of(context).textTheme.headlineSmall,
                      textAlign: TextAlign.center,
                    ),
                    SizedBox(height: 8),
                    Text(
                      'Select files or share from other apps',
                      style: Theme.of(context).textTheme.bodyMedium,
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            ),
            SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: _isUploading ? null : _pickFiles,
              icon: Icon(Icons.folder_open),
              label: Text('Select Files'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.green,
                foregroundColor: Colors.white,
                padding: EdgeInsets.symmetric(vertical: 16),
              ),
            ),
            SizedBox(height: 16),
            if (_selectedFiles.isNotEmpty) ...[
              Card(
                child: Padding(
                  padding: EdgeInsets.all(16.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Selected Files (${_selectedFiles.length}):',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      SizedBox(height: 8),
                      Container(
                        height: 120,
                        child: ListView.builder(
                          itemCount: _selectedFiles.length,
                          itemBuilder: (context, index) {
                            final file = _selectedFiles[index];
                            final fileName = file.path.split('/').last;
                            return ListTile(
                              dense: true,
                              leading: Icon(Icons.insert_drive_file),
                              title: Text(fileName),
                              subtitle: Text(_getFileSize(file)),
                            );
                          },
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              SizedBox(height: 16),
              ElevatedButton.icon(
                onPressed: _isUploading ? null : _uploadFiles,
                icon: _isUploading 
                    ? SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                        ),
                      )
                    : Icon(Icons.upload),
                label: Text(_isUploading ? 'Uploading...' : 'Upload Files'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blue,
                  foregroundColor: Colors.white,
                  padding: EdgeInsets.symmetric(vertical: 16),
                ),
              ),
            ],
            if (_isUploading || _statusMessage.isNotEmpty) ...[
              SizedBox(height: 24),
              Card(
                child: Padding(
                  padding: EdgeInsets.all(16.0),
                  child: Column(
                    children: [
                      if (_isUploading) ...[
                        LinearProgressIndicator(
                          value: _uploadProgress,
                          backgroundColor: Colors.grey[300],
                          valueColor: AlwaysStoppedAnimation<Color>(Colors.green),
                        ),
                        SizedBox(height: 16),
                      ],
                      Text(
                        _statusMessage,
                        style: Theme.of(context).textTheme.bodyMedium,
                        textAlign: TextAlign.center,
                      ),
                      if (_isUploading && _totalFiles > 0) ...[
                        SizedBox(height: 8),
                        Text(
                          'Progress: $_currentFileIndex/$_totalFiles files',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _getFileSize(File file) {
    try {
      final bytes = file.lengthSync();
      if (bytes < 1024) return '$bytes B';
      if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
      if (bytes < 1024 * 1024 * 1024) return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
      return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
    } catch (e) {
      return 'Unknown size';
    }
  }
}