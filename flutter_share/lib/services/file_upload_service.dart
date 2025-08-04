import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class FileUploadService {
  static const String _serverUrlKey = 'server_url';
  static const String _defaultServerUrl = 'http://192.168.1.100:5002';

  Future<String> getServerUrl() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_serverUrlKey) ?? _defaultServerUrl;
  }

  Future<void> setServerUrl(String url) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_serverUrlKey, url);
  }

  Future<UploadResult> uploadFile(File file, {Function(double)? onProgress}) async {
    try {
      final serverUrl = await getServerUrl();
      final uri = Uri.parse(serverUrl);
      
      final request = http.MultipartRequest('POST', uri);
      final multipartFile = await http.MultipartFile.fromPath('file', file.path);
      request.files.add(multipartFile);

      final streamedResponse = await request.send();
      final response = await http.Response.fromStream(streamedResponse);

      if (response.statusCode == 200) {
        return UploadResult(success: true, message: 'File uploaded successfully');
      } else {
        return UploadResult(
          success: false, 
          message: 'Upload failed with status: ${response.statusCode}'
        );
      }
    } catch (e) {
      return UploadResult(
        success: false, 
        message: 'Upload error: ${e.toString()}'
      );
    }
  }

  Future<List<UploadResult>> uploadMultipleFiles(
    List<File> files, {
    Function(int current, int total, String fileName)? onFileProgress,
    Function(double)? onOverallProgress,
  }) async {
    List<UploadResult> results = [];
    
    for (int i = 0; i < files.length; i++) {
      final file = files[i];
      onFileProgress?.call(i + 1, files.length, file.path.split('/').last);
      
      final result = await uploadFile(file);
      results.add(result);
      
      onOverallProgress?.call((i + 1) / files.length);
    }
    
    return results;
  }

  Future<bool> testConnection() async {
    try {
      final serverUrl = await getServerUrl();
      final response = await http.get(
        Uri.parse(serverUrl),
        headers: {'Accept': 'text/html'},
      ).timeout(Duration(seconds: 5));
      
      return response.statusCode == 200;
    } catch (e) {
      return false;
    }
  }
}

class UploadResult {
  final bool success;
  final String message;

  UploadResult({required this.success, required this.message});
}