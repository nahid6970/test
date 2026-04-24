package com.example.myapplication

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class OverlayTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, OverlayService::class.java)
        
        // Use DirectLaunchActivity logic or just toggle the service
        // Since we need to check permissions, let's just start the service and let it handle UI if needed
        // Or better yet, call DirectLaunchActivity if permissions are missing
        
        if (qsTile.state == Tile.STATE_ACTIVE) {
            stopService(intent)
        } else {
            val launchIntent = Intent(this, DirectLaunchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(launchIntent)
        }
        
        updateTileState()
    }

    private fun updateTileState() {
        // We can't easily check if service is running here, so we assume based on toggle
        // In a real app, you'd check if the service is active or use a shared state
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.updateTile()
    }
}
