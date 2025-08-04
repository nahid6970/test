import 'package:flutter/material.dart';
import '../services/file_upload_service.dart';

class SettingsScreen extends StatefulWidget {
  @override
  _SettingsScreenState createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final FileUploadService _uploadService = FileUploadService();
  final TextEditingController _urlController = TextEditingController();
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _loadServerUrl();
  }

  Future<void> _loadServerUrl() async {
    final url = await _uploadService.getServerUrl();
    setState(() {
      _urlController.text = url;
    });
  }

  Future<void> _saveServerUrl() async {
    if (_urlController.text.trim().isEmpty) {
      _showSnackBar('Please enter a server URL', isError: true);
      return;
    }

    String url = _urlController.text.trim();
    
    // Add http:// if no protocol specified
    if (!url.startsWith('http://') && !url.startsWith('https://')) {
      url = 'http://$url';
    }

    // Remove trailing slash
    if (url.endsWith('/')) {
      url = url.substring(0, url.length - 1);
    }

    await _uploadService.setServerUrl(url);
    _showSnackBar('Server URL saved successfully!');
    
    setState(() {
      _urlController.text = url;
    });
  }

  Future<void> _testConnection() async {
    setState(() {
      _isLoading = true;
    });

    await _saveServerUrl(); // Save first, then test
    
    final isConnected = await _uploadService.testConnection();
    
    setState(() {
      _isLoading = false;
    });

    if (isConnected) {
      _showSnackBar('Connection successful!');
    } else {
      _showSnackBar('Connection failed. Please check the URL and ensure your PC server is running.', isError: true);
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

  void _showHelpDialog() {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text('Setup Help'),
          content: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'To use this app, you need to:',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
                SizedBox(height: 12),
                Text('1. Make sure your PC and phone are on the same WiFi network'),
                SizedBox(height: 8),
                Text('2. Run the upload_files.py script on your PC'),
                SizedBox(height: 8),
                Text('3. Find your PC\'s IP address:'),
                SizedBox(height: 4),
                Text('   • Windows: Open Command Prompt, type "ipconfig"'),
                Text('   • Look for "IPv4 Address" under your WiFi adapter'),
                SizedBox(height: 8),
                Text('4. Enter your PC\'s IP address with port 5002'),
                SizedBox(height: 4),
                Text('   • Example: 192.168.1.100:5002'),
                SizedBox(height: 12),
                Text(
                  'The default port is 5002 as configured in your Python script.',
                  style: TextStyle(fontStyle: FontStyle.italic),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: Text('Got it!'),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Settings'),
        backgroundColor: Colors.green,
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            icon: Icon(Icons.help_outline),
            onPressed: _showHelpDialog,
            tooltip: 'Help',
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
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'PC Server Configuration',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    SizedBox(height: 16),
                    TextField(
                      controller: _urlController,
                      decoration: InputDecoration(
                        labelText: 'Server URL',
                        hintText: 'e.g., 192.168.1.100:5002',
                        prefixIcon: Icon(Icons.computer),
                        border: OutlineInputBorder(),
                        helperText: 'Enter your PC\'s IP address with port 5002',
                      ),
                      keyboardType: TextInputType.url,
                    ),
                    SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(
                          child: ElevatedButton.icon(
                            onPressed: _saveServerUrl,
                            icon: Icon(Icons.save),
                            label: Text('Save'),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: Colors.green,
                              foregroundColor: Colors.white,
                            ),
                          ),
                        ),
                        SizedBox(width: 12),
                        Expanded(
                          child: ElevatedButton.icon(
                            onPressed: _isLoading ? null : _testConnection,
                            icon: _isLoading 
                                ? SizedBox(
                                    width: 20,
                                    height: 20,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                      valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                                    ),
                                  )
                                : Icon(Icons.wifi),
                            label: Text(_isLoading ? 'Testing...' : 'Test'),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: Colors.blue,
                              foregroundColor: Colors.white,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            SizedBox(height: 24),
            Card(
              child: Padding(
                padding: EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'How to Use',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    SizedBox(height: 12),
                    _buildInstructionItem(
                      Icons.share,
                      'Share from any app',
                      'Use the share button in any app and select "PC File Share"',
                    ),
                    _buildInstructionItem(
                      Icons.folder_open,
                      'Select files manually',
                      'Use the "Select Files" button on the home screen',
                    ),
                    _buildInstructionItem(
                      Icons.cloud_upload,
                      'Automatic upload',
                      'Files are automatically uploaded to your PC\'s ShareFolder',
                    ),
                  ],
                ),
              ),
            ),
            SizedBox(height: 24),
            Card(
              child: Padding(
                padding: EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'About',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    SizedBox(height: 12),
                    Text('PC File Share v1.0.0'),
                    SizedBox(height: 4),
                    Text(
                      'Share files seamlessly between your Android device and PC',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInstructionItem(IconData icon, String title, String description) {
    return Padding(
      padding: EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: Colors.green, size: 24),
          SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
                SizedBox(height: 4),
                Text(
                  description,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }
}