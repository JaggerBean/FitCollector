package com.example.fitcollector

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata

private const val PREFS_NAME = "fitcollector"
private const val KEY_MC_USER = "minecraft_username"
private const val KEY_LAST_CHANGE_DATE = "last_username_change_date"
private const val KEY_QUEUED_USER = "queued_minecraft_username"
private const val KEY_QUEUED_DATE = "queued_date"
private const val KEY_AUTO_SYNC = "auto_sync_enabled"
private const val KEY_BACKGROUND_SYNC = "background_sync_enabled"
private const val KEY_BACKGROUND_SYNC_INTERVAL = "background_sync_interval_minutes"
private const val KEY_SYNC_LOG = "sync_log"
private const val KEY_LAST_STEPS = "last_known_steps"
private const val KEY_LAST_STEPS_DATE = "last_known_steps_date"
private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
private const val KEY_SELECTED_SERVERS = "selected_servers"
private const val KEY_SERVER_KEYS = "server_keys"
private const val KEY_INVITE_CODES = "invite_codes_by_server"
private const val KEY_THEME_MODE = "theme_mode" // "System", "Light", "Dark"
private const val KEY_ALLOWED_SOURCES = "allowed_step_sources"
private const val KEY_INSTALL_TIME = "install_time"
private const val KEY_TRACKED_TIERS = "tracked_tiers_by_server"
private const val KEY_NOTIFY_TIERS = "notify_tiers"
private const val KEY_MILESTONE_NOTIFIED = "milestone_notified"
private const val KEY_ADMIN_PUSH_BY_SERVER = "admin_push_by_server"

private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val CENTRAL_ZONE = ZoneId.of("America/Chicago")

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
    val today = ZonedDateTime.now(CENTRAL_ZONE).toLocalDate().format(dateFormatter)
    
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
    
    val today = ZonedDateTime.now(CENTRAL_ZONE).toLocalDate().format(dateFormatter)
    return lastChange != today
}

fun queueMinecraftUsername(context: Context, username: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val today = ZonedDateTime.now(CENTRAL_ZONE).toLocalDate().format(dateFormatter)
    
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
    
    val today = LocalDate.now(CENTRAL_ZONE).format(dateFormatter)
    
    if (queuedDate != today && canChangeMinecraftUsername(context)) {
        setMinecraftUsername(context, queuedName)
        return queuedName
    }
    return null
}

fun isAutoSyncEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_AUTO_SYNC, true)
}

fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
}

fun isBackgroundSyncEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_BACKGROUND_SYNC, true)
}

fun setBackgroundSyncEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_BACKGROUND_SYNC, enabled).apply()
}

fun getBackgroundSyncIntervalMinutes(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val value = prefs.getInt(KEY_BACKGROUND_SYNC_INTERVAL, 15)
    return when {
        value < 15 -> 15
        value > 120 -> 120
        value % 15 != 0 -> (value / 15) * 15
        else -> value
    }
}

fun setBackgroundSyncIntervalMinutes(context: Context, minutes: Int) {
    val safe = when {
        minutes < 15 -> 15
        minutes > 120 -> 120
        minutes % 15 != 0 -> (minutes / 15) * 15
        else -> minutes
    }
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_BACKGROUND_SYNC_INTERVAL, safe).apply()
}

fun saveLastKnownSteps(context: Context, steps: Long) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val today = ZonedDateTime.now(CENTRAL_ZONE).toLocalDate().format(dateFormatter)
    prefs.edit()
        .putLong(KEY_LAST_STEPS, steps)
        .putString(KEY_LAST_STEPS_DATE, today)
        .apply()
}

fun getLastKnownSteps(context: Context): Long? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val lastDate = prefs.getString(KEY_LAST_STEPS_DATE, null)
    val today = ZonedDateTime.now(CENTRAL_ZONE).toLocalDate().format(dateFormatter)
    
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
    val now = ZonedDateTime.now(CENTRAL_ZONE)
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay(CENTRAL_ZONE)
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

fun ensureInstallState(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val stored = prefs.getLong(KEY_INSTALL_TIME, 0L)
    val current = try {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    } catch (_: Exception) {
        0L
    }

    if (current > 0L && stored != current) {
        resetAppStateForFreshInstall(context)
        prefs.edit().putLong(KEY_INSTALL_TIME, current).apply()
    } else if (stored == 0L && current > 0L) {
        prefs.edit().putLong(KEY_INSTALL_TIME, current).apply()
    }
}

fun resetAppStateForFreshInstall(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .remove(KEY_MC_USER)
        .remove(KEY_LAST_CHANGE_DATE)
        .remove(KEY_QUEUED_USER)
        .remove(KEY_QUEUED_DATE)
        .remove(KEY_SELECTED_SERVERS)
        .remove(KEY_SERVER_KEYS)
        .remove(KEY_INVITE_CODES)
        .remove(KEY_ONBOARDING_COMPLETE)
        .remove(KEY_ALLOWED_SOURCES)
        .apply()
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

fun getInviteCodesByServer(context: Context): Map<String, String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_INVITE_CODES, "{}")
    val type = object : TypeToken<Map<String, String>>() {}.type
    return Gson().fromJson(json, type)
}

fun setInviteCodesByServer(context: Context, inviteCodes: Map<String, String>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_INVITE_CODES, Gson().toJson(inviteCodes)).apply()
}

fun removeInviteCodeForServer(context: Context, server: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val codes = getInviteCodesByServer(context).toMutableMap()
    codes.remove(server)
    prefs.edit().putString(KEY_INVITE_CODES, Gson().toJson(codes)).apply()
}

fun getServerKey(context: Context, username: String, server: String): String? {
    val allKeys = getAllServerKeys(context)
    return allKeys["$username:$server"]
}

fun saveServerKey(context: Context, username: String, server: String, key: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val allKeys = getAllServerKeys(context).toMutableMap()
    allKeys["$username:$server"] = key
    prefs.edit().putString(KEY_SERVER_KEYS, Gson().toJson(allKeys)).apply()
}

fun removeServerKey(context: Context, username: String, server: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val allKeys = getAllServerKeys(context).toMutableMap()
    allKeys.remove("$username:$server")
    prefs.edit().putString(KEY_SERVER_KEYS, Gson().toJson(allKeys)).apply()
}

fun clearAllServerKeys(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().remove(KEY_SERVER_KEYS).apply()
}

fun getAllServerKeys(context: Context): Map<String, String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_SERVER_KEYS, "{}")
    val type = object : TypeToken<Map<String, String>>() {}.type
    return Gson().fromJson(json, type)
}

fun getServerKeysForUser(context: Context, username: String): Map<String, String> {
    val all = getAllServerKeys(context)
    val prefix = "$username:"
    return all.filterKeys { it.startsWith(prefix) }
              .mapKeys { it.key.substring(prefix.length) }
}

fun getThemeMode(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_THEME_MODE, "System") ?: "System"
}

fun setThemeMode(context: Context, mode: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_THEME_MODE, mode).apply()
}

fun shouldExcludeManualSteps(context: Context): Boolean {
    return true // Always enabled for integrity
}

fun getAllowedStepSources(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getStringSet(KEY_ALLOWED_SOURCES, emptySet()) ?: emptySet()
}

fun setAllowedStepSources(context: Context, sources: Set<String>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putStringSet(KEY_ALLOWED_SOURCES, sources).apply()
}

fun getAdminPushByServer(context: Context): Map<String, Boolean> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_ADMIN_PUSH_BY_SERVER, "{}")
    val type = object : TypeToken<Map<String, Boolean>>() {}.type
    return Gson().fromJson(json, type)
}

fun isAdminPushEnabledForServer(context: Context, server: String): Boolean {
    return getAdminPushByServer(context)[server] ?: true
}

fun setAdminPushEnabledForServer(context: Context, server: String, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val current = getAdminPushByServer(context).toMutableMap()
    current[server] = enabled
    prefs.edit().putString(KEY_ADMIN_PUSH_BY_SERVER, Gson().toJson(current)).apply()
}

fun getTrackedTiersByServer(context: Context): Map<String, Long> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_TRACKED_TIERS, "{}")
    val type = object : TypeToken<Map<String, Long>>() {}.type
    return Gson().fromJson(json, type)
}

fun setTrackedTierForServer(context: Context, server: String, minSteps: Long?) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val current = getTrackedTiersByServer(context).toMutableMap()
    if (minSteps == null) current.remove(server) else current[server] = minSteps
    prefs.edit().putString(KEY_TRACKED_TIERS, Gson().toJson(current)).apply()
}

fun makeTierKey(server: String, minSteps: Long): String = "$server|$minSteps"

fun getNotificationTierKeys(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getStringSet(KEY_NOTIFY_TIERS, emptySet()) ?: emptySet()
}

fun isNotificationTierEnabled(context: Context, server: String, minSteps: Long): Boolean {
    return getNotificationTierKeys(context).contains(makeTierKey(server, minSteps))
}

fun setNotificationTierEnabled(context: Context, server: String, minSteps: Long, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val current = getNotificationTierKeys(context).toMutableSet()
    val key = makeTierKey(server, minSteps)
    if (enabled) current.add(key) else current.remove(key)
    prefs.edit().putStringSet(KEY_NOTIFY_TIERS, current).apply()
}

fun getNotifiedMilestones(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getStringSet(KEY_MILESTONE_NOTIFIED, emptySet()) ?: emptySet()
}

fun hasMilestoneNotified(context: Context, server: String, minSteps: Long, day: String): Boolean {
    val key = "$day|${makeTierKey(server, minSteps)}"
    return getNotifiedMilestones(context).contains(key)
}

fun markMilestoneNotified(context: Context, server: String, minSteps: Long, day: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val current = getNotifiedMilestones(context).toMutableSet()
    current.add("$day|${makeTierKey(server, minSteps)}")
    prefs.edit().putStringSet(KEY_MILESTONE_NOTIFIED, current).apply()
}

fun pruneMilestoneNotifications(context: Context, day: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val current = getNotifiedMilestones(context).filter { it.startsWith("$day|") }.toSet()
    prefs.edit().putStringSet(KEY_MILESTONE_NOTIFIED, current).apply()
}

/**
 * Shared logic to determine if a specific record should be counted.
 * Rejects manual entries and suspicious "Active Session" records which are often manual workouts.
 * LEGITIMATE "Unknown" methods are allowed as they often come from wearable hardware.
 */
fun isRecordValid(record: StepsRecord, allowedSources: Set<String>): Boolean {
    val recordingMethod = record.metadata.recordingMethod
    val sourceApp = record.metadata.dataOrigin.packageName
    
    // 1. Source Check
    if (allowedSources.isNotEmpty() && !allowedSources.contains(sourceApp)) {
        return false
    }
    
    // 2. Strict Manual Exclusion
    return when {
        // Explicit manual entry
        recordingMethod == Metadata.RECORDING_METHOD_MANUAL_ENTRY -> false
        
        // "Active Session" is often used by apps like Samsung Health or Google Fit 
        // when a user manually adds a "Workout" after the fact.
        recordingMethod == Metadata.RECORDING_METHOD_ACTIVELY_RECORDED -> false
        
        // We previously suspected Google Fit's "Unknown" entries, but the user confirmed
        // that legitimate hardware records can appear as Unknown.
        // We will allow UNKNOWN (0) while still rejecting explicit MANUAL (2) and ACTIVE (3).
        
        else -> true
    }
}
