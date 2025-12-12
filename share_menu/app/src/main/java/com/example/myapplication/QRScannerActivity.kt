package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class QRScannerActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startScanner()
        } else {
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedUrl = result.contents
            
            if (NetworkUtils.isValidUrl(scannedUrl)) {
                // Save the scanned device
                val sharedPrefs = getSharedPreferences("file_share_prefs", MODE_PRIVATE)
                sharedPrefs.edit().putString("android_device_url", scannedUrl).apply()
                
                // Return the URL to the calling activity
                intent.putExtra("scanned_url", scannedUrl)
                setResult(RESULT_OK, intent)
                finish()
            } else {
                Toast.makeText(this, "Invalid QR code. Expected format: http://IP:PORT", Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                QRScannerScreen(
                    onScanClick = { checkCameraPermissionAndScan() },
                    onCancelClick = { finish() }
                )
            }
        }
    }
    
    private fun checkCameraPermissionAndScan() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startScanner()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan the QR code from another device")
            setBeepEnabled(true)
            setOrientationLocked(true)
        }
        scanLauncher.launch(options)
    }
}

@Composable
fun QRScannerScreen(
    onScanClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        onScanClick()
    }
    
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Preparing QR Scanner...",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            CircularProgressIndicator()
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedButton(onClick = onCancelClick) {
                Text("Cancel")
            }
        }
    }
}
