package com.example.fitcollector

import android.content.Context
import android.provider.Settings

/**
 * Checks if the "Automatic Date and Time" setting is enabled on the device.
 * This is a common way to ensure the user hasn't manually tampered with their system time.
 */
fun isAutomaticTimeEnabled(context: Context): Boolean {
    return try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME) == 1
    } catch (e: Settings.SettingNotFoundException) {
        // Fallback for older Android versions if necessary, though AUTO_TIME is standard.
        false
    }
}
