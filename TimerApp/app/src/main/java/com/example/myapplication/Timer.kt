package com.example.myapplication

data class Timer(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val totalMillis: Long,
    var remainingMillis: Long = totalMillis,
    var isRunning: Boolean = false,
    var isPaused: Boolean = false
) {
    fun getFormattedTime(): String {
        val seconds = (remainingMillis / 1000) % 60
        val minutes = (remainingMillis / (1000 * 60)) % 60
        val hours = (remainingMillis / (1000 * 60 * 60)) % 24
        val days = remainingMillis / (1000 * 60 * 60 * 24)
        
        return when {
            days > 0 -> String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds)
            hours > 0 -> String.format("%02dh %02dm %02ds", hours, minutes, seconds)
            else -> String.format("%02dm %02ds", minutes, seconds)
        }
    }
    
    fun isFinished(): Boolean = remainingMillis <= 0
}
