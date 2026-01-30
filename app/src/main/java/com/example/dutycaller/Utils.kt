package com.example.dutycaller

import java.util.Calendar
import java.util.Random

object Utils {
    private val random = Random()

    fun getRandomDelay(min: Int, max: Int): Long {
        if (min >= max) return min.toLong()
        return (min + random.nextInt(max - min + 1)).toLong()
    }
    
    fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60)) % 24
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    fun isTodayAllowed(allowedDays: Set<String>): Boolean {
        val calendar = Calendar.getInstance()
        val day = calendar.get(Calendar.DAY_OF_WEEK).toString() // 1=Sun, 7=Sat
        return allowedDays.contains(day)
    }
    
    fun isCurrentTimeInPauseRange(startStr: String, endStr: String): Boolean {
        try {
            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            
            val startParts = startStr.split(":")
            val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
            
            val endParts = endStr.split(":")
            val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()
            
            if (startMinutes <= endMinutes) {
                // e.g. 09:00 ~ 18:00
                return currentMinutes in startMinutes..endMinutes
            } else {
                // e.g. 21:00 ~ 07:00 (Crosses midnight)
                return currentMinutes >= startMinutes || currentMinutes <= endMinutes
            }
        } catch (e: Exception) {
            return false
        }
    }
}