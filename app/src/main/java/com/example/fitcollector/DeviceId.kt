package com.example.fitcollector

import android.content.Context
import java.util.UUID

fun getOrCreateDeviceId(context: Context): String {
    val prefs = context.getSharedPreferences("fitcollector", Context.MODE_PRIVATE)
    val existing = prefs.getString("device_id", null)
    if (!existing.isNullOrBlank()) return existing

    val id = "dev-" + UUID.randomUUID().toString()
    prefs.edit().putString("device_id", id).apply()
    return id
}
