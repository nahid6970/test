package com.example.myapplication

object TimeParser {
    fun parseTimeInput(input: String): Long {
        var totalMillis = 0L
        val regex = Regex("(\\d+)([dhms])")
        val matches = regex.findAll(input.lowercase())
        
        for (match in matches) {
            val value = match.groupValues[1].toLongOrNull() ?: continue
            val unit = match.groupValues[2]
            
            totalMillis += when (unit) {
                "d" -> value * 24 * 60 * 60 * 1000
                "h" -> value * 60 * 60 * 1000
                "m" -> value * 60 * 1000
                "s" -> value * 1000
                else -> 0
            }
        }
        
        return totalMillis
    }
    
    fun formatInputPreview(input: String): String {
        val millis = parseTimeInput(input)
        if (millis == 0L) return "Invalid input"
        
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60)) % 24
        val days = millis / (1000 * 60 * 60 * 24)
        
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0) parts.add("${seconds}s")
        
        return parts.joinToString(" ")
    }
}
