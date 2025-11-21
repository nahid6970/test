package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object TimerStorage {
    private const val PREFS_NAME = "timer_prefs"
    private const val KEY_TIMERS = "timers"
    private val gson = Gson()
    
    fun saveTimers(context: Context, timers: List<Timer>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(timers)
        prefs.edit().putString(KEY_TIMERS, json).apply()
    }
    
    fun loadTimers(context: Context): MutableList<Timer> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TIMERS, null) ?: return mutableListOf()
        
        val type = object : TypeToken<List<Timer>>() {}.type
        val timers: List<Timer> = gson.fromJson(json, type)
        
        // Reset running state when loading
        return timers.map { it.copy(isRunning = false) }.toMutableList()
    }
}
