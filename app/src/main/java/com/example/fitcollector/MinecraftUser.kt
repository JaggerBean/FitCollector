package com.example.fitcollector

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val PREFS_NAME = "fitcollector"
private const val KEY_MC_USER = "minecraft_username"
private const val KEY_LAST_CHANGE_DATE = "last_username_change_date"
private const val KEY_QUEUED_USER = "queued_minecraft_username"
private const val KEY_QUEUED_DATE = "queued_date"
private const val KEY_AUTO_SYNC = "auto_sync_enabled"
private const val KEY_SYNC_LOG = "sync_log"
private const val KEY_LAST_STEPS = "last_known_steps"
private const val KEY_LAST_STEPS_DATE = "last_known_steps_date"
private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
private const val KEY_SELECTED_SERVERS = "selected_servers"
private const val KEY_SERVER_KEYS = "server_keys"

private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

data class SyncLogEntry(
    val timestamp: String,
    val steps: Long,
    val source: String,
    val success: Boolean,
    val message: String
)

fun getMinecraftUsername(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_MC_USER, "") ?: ""
}

fun setMinecraftUsername(context: Context, username: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val today = LocalDate.now().format(dateFormatter)
    
    prefs.edit()
        .putString(KEY_MC_USER, username)
        .putString(KEY_LAST_CHANGE_DATE, today)
        .remove(KEY_QUEUED_USER)
        .remove(KEY_QUEUED_DATE)
        .apply()
}

fun canChangeMinecraftUsername(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val lastChange = prefs.getString(KEY_LAST_CHANGE_DATE, null) ?: return true
    
    val today = LocalDate.now().format(dateFormatter)
    return lastChange != today
}

fun queueMinecraftUsername(context: Context, username: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val today = LocalDate.now().format(dateFormatter)
    
    prefs.edit()
        .putString(KEY_QUEUED_USER, username)
        .putString(KEY_QUEUED_DATE, today)
        .apply()
}

fun cancelQueuedUsername(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .remove(KEY_QUEUED_USER)
        .remove(KEY_QUEUED_DATE)
        .apply()
}

fun getQueuedUsername(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_QUEUED_USER, null)
}

fun applyQueuedUsernameIfPossible(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val queuedName = prefs.getString(KEY_QUEUED_USER, null) ?: return null
    val queuedDate = prefs.getString(KEY_QUEUED_DATE, null) ?: return null
    
    val today = LocalDate.now().format(dateFormatter)
    
    // Only apply if it wasn't queued today AND we can change it today
    if (queuedDate != today && canChangeMinecraftUsername(context)) {
        setMinecraftUsername(context, queuedName)
        return queuedName
    }
    return null
}

fun isAutoSyncEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_AUTO_SYNC, true) // Default to true
}

fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
}

fun saveLastKnownSteps(context: Context, steps: Long) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val today = LocalDate.now().format(dateFormatter)
    prefs.edit()
        .putLong(KEY_LAST_STEPS, steps)
        .putString(KEY_LAST_STEPS_DATE, today)
        .apply()
}

fun getLastKnownSteps(context: Context): Long? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val lastDate = prefs.getString(KEY_LAST_STEPS_DATE, null)
    val today = LocalDate.now().format(dateFormatter)
    
    return if (lastDate == today) {
        prefs.getLong(KEY_LAST_STEPS, 0)
    } else {
        null
    }
}

fun addSyncLogEntry(context: Context, entry: SyncLogEntry) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val gson = Gson()
    val logJson = prefs.getString(KEY_SYNC_LOG, "[]")
    val type = object : TypeToken<MutableList<SyncLogEntry>>() {}.type
    val log: MutableList<SyncLogEntry> = gson.fromJson(logJson, type)
    
    log.add(0, entry)
    if (log.size > 25) {
        log.removeAt(log.size - 1)
    }
    
    prefs.edit().putString(KEY_SYNC_LOG, gson.toJson(log)).apply()
}

fun getSyncLog(context: Context): List<SyncLogEntry> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val gson = Gson()
    val logJson = prefs.getString(KEY_SYNC_LOG, "[]")
    val type = object : TypeToken<List<SyncLogEntry>>() {}.type
    return gson.fromJson(logJson, type)
}

fun getTimeUntilNextChange(): String {
    val now = LocalDateTime.now()
    val midnight = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT)
    val duration = Duration.between(now, midnight)
    
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    
    return when {
        hours > 0 -> "$hours hours and $minutes minutes"
        else -> "$minutes minutes"
    }
}

fun isOnboardingComplete(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
}

fun setOnboardingComplete(context: Context, complete: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
}

fun getSelectedServers(context: Context): List<String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_SELECTED_SERVERS, "[]")
    val type = object : TypeToken<List<String>>() {}.type
    return Gson().fromJson(json, type)
}

fun setSelectedServers(context: Context, servers: List<String>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_SELECTED_SERVERS, Gson().toJson(servers)).apply()
}

fun getServerKeys(context: Context): Map<String, String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_SERVER_KEYS, "{}")
    val type = object : TypeToken<Map<String, String>>() {}.type
    return Gson().fromJson(json, type)
}

fun saveServerKey(context: Context, server: String, key: String) {
    val keys = getServerKeys(context).toMutableMap()
    keys[server] = key
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_SERVER_KEYS, Gson().toJson(keys)).apply()
}
