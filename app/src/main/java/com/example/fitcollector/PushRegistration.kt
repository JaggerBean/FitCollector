package com.example.fitcollector

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

suspend fun syncAndroidPushRegistrations(
    context: Context,
    forcedToken: String? = null
) {
    val username = getMinecraftUsername(context)
    val servers = getSelectedServers(context)
    if (username.isBlank() || servers.isEmpty()) return

    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val api = buildApi(BASE_URL, GLOBAL_API_KEY)
    val deviceId = getOrCreateDeviceId(context)

    if (!notificationsEnabled) {
        servers.forEach { server ->
            val key = getServerKey(context, username, server) ?: return@forEach
            try {
                api.unregisterPushDevice(
                    PushTokenUnregisterPayload(
                        device_id = deviceId,
                        player_api_key = key,
                        platform = "android"
                    )
                )
            } catch (_: Exception) {}
        }
        return
    }

    val token = (forcedToken ?: runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull())
        ?.trim()
        .orEmpty()
    if (token.isBlank()) return

    val previousToken = getFcmToken(context)
    if (!previousToken.isNullOrBlank() && previousToken != token) {
        servers.forEach { server ->
            val key = getServerKey(context, username, server) ?: return@forEach
            try {
                api.unregisterPushDevice(
                    PushTokenUnregisterPayload(
                        device_id = deviceId,
                        player_api_key = key,
                        platform = "android",
                        apns_token = previousToken
                    )
                )
            } catch (_: Exception) {}
        }
    }

    servers.forEach { server ->
        val key = getServerKey(context, username, server) ?: return@forEach
        val adminPushEnabled = isAdminPushEnabledForServer(context, server)

        try {
            if (adminPushEnabled) {
                api.registerPushDevice(
                    PushTokenRegisterPayload(
                        device_id = deviceId,
                        player_api_key = key,
                        apns_token = token,
                        sandbox = false,
                        platform = "android"
                    )
                )
            } else {
                api.unregisterPushDevice(
                    PushTokenUnregisterPayload(
                        device_id = deviceId,
                        player_api_key = key,
                        platform = "android"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("PushRegistration", "Push token sync failed for $server: ${e.message}")
        }
    }

    setFcmToken(context, token)
}
