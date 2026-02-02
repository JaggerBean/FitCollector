package com.jagger.StepCraft

import android.content.Context
import android.provider.Settings
import java.util.UUID

fun getOrCreateDeviceId(context: Context): String {
    // Settings.Secure.ANDROID_ID survives app reinstalls and is unique to the device/app-signing-key pair.
    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    
    if (!androidId.isNullOrBlank()) {
        return "aid-$androidId"
    }

    // Fallback for edge cases where ANDROID_ID might be unavailable
    val prefs = context.getSharedPreferences("fitcollector", Context.MODE_PRIVATE)
    val existing = prefs.getString("device_id", null)
    if (!existing.isNullOrBlank()) return existing

    val id = "dev-" + UUID.randomUUID().toString()
    prefs.edit().putString("device_id", id).apply()
    return id
}
