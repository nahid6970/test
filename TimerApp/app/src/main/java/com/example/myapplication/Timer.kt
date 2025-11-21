package com.example.myapplication

data class Timer(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val totalMillis: Long,
    var startTimeMillis: Long = 0L,  // When timer was started
    var pausedAtMillis: Long = totalMillis,  // Remaining time when paused
    var isRunning: Boolean = false,
    var isPaused: Boolean = false
) {
    fun getRemainingMillis(): Long {
        return if (isRunning && startTimeMillis > 0) {
            val elapsed = System.currentTimeMillis() - startTimeMillis
            val remaining = pausedAtMillis - elapsed
            maxOf(0L, remaining)
        } else {
            pausedAtMillis
        }
    }
    
    fun getFormattedTime(): String {
        val remainingMillis = getRemainingMillis()
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
    
    fun isFinished(): Boolean = getRemainingMillis() <= 0
    
    fun start() {
        isRunning = true
        isPaused = false
        startTimeMillis = System.currentTimeMillis()
    }
    
    fun pause() {
        if (isRunning) {
            pausedAtMillis = getRemainingMillis()
            isRunning = false
            isPaused = true
            startTimeMillis = 0L
        }
    }
    
    fun reset() {
        pausedAtMillis = totalMillis
        isRunning = false
        isPaused = false
        startTimeMillis = 0L
    }
}
