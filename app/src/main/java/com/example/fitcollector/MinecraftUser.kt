package com.example.fitcollector

import android.content.Context

private const val PREFS_NAME = "fitcollector"
private const val KEY_MC_USER = "minecraft_username"

fun getMinecraftUsername(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_MC_USER, "") ?: ""
}

fun setMinecraftUsername(context: Context, username: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_MC_USER, username).apply()
}
