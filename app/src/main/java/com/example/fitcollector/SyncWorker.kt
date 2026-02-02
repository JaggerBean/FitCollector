package com.example.fitcollector

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import retrofit2.HttpException
import com.google.gson.JsonParser

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val dayFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun parseErrorMessage(e: Throwable): String {
        if (e is HttpException) {
            try {
                val errorBody = e.response()?.errorBody()?.string()
                if (!errorBody.isNullOrBlank()) {
                    val json = JsonParser.parseString(errorBody).asJsonObject
                    return when {
                        json.has("message") -> json.get("message").asString
                        json.has("error") -> json.get("error").asString
                        else -> errorBody
                    }
                }
            } catch (_: Exception) {}
            return "HTTP ${e.code()}: ${e.message()}"
        }
        return e.message ?: "Unknown error"
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        Log.d("SyncWorker", "Background sync started...")
        
        // 1. Check if auto-sync is enabled
        if (!isAutoSyncEnabled(context)) {
            Log.d("SyncWorker", "Auto-sync disabled, skipping.")
            return Result.success()
        }

        // 2. Check and apply queued username if midnight has passed
        val mcUsername = applyQueuedUsernameIfPossible(context) ?: getMinecraftUsername(context)
        val selectedServers = getSelectedServers(context)
        val deviceId = getOrCreateDeviceId(context)
        
        if (mcUsername.isBlank() || selectedServers.isEmpty()) {
            Log.e("SyncWorker", "Missing credentials or no servers selected, failing.")
            return Result.failure()
        }

        // 3. Check Health Connect availability
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            Log.e("SyncWorker", "Health Connect unavailable.")
            return Result.retry()
        }

        val client = HealthConnectClient.getOrCreate(context)
        val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))

        // 4. Check Permissions
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(permissions)) {
            Log.e("SyncWorker", "Permissions not granted.")
            return Result.failure()
        }

        return try {
            // 5. Read steps using aggregate for better accuracy
            val deviceZone = ZoneId.systemDefault()
            val nowZoned = ZonedDateTime.now(deviceZone)
            val start = nowZoned.toLocalDate().atStartOfDay(deviceZone).toInstant()
            val now = Instant.now()
            
            val aggregateResponse = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, now)
                )
            )
            val totalSteps = aggregateResponse[StepsRecord.COUNT_TOTAL] ?: 0L
            
            saveLastKnownSteps(context, totalSteps)

            // 6. Sync to backend for each server
            val dayStr = nowZoned.format(dayFormatter)
            val timestampStr = now.toString()
            val nowStr = nowZoned.format(logTimeFormatter)

            var successCount = 0
            val successServers = mutableListOf<String>()
            val errorGroups = mutableMapOf<String, MutableList<String>>() // error -> list of servers
            val removedServers = mutableSetOf<String>()

            val globalApi = buildApi(BASE_URL, GLOBAL_API_KEY)
            val inviteCodes = getInviteCodesByServer(context)

            selectedServers.forEach { server ->
                suspend fun getOrRecoverKey(): String? {
                    val inviteCode = inviteCodes[server]
                    var key = getServerKey(context, mcUsername, server)
                    if (key != null) return key

                    try {
                        val resp = globalApi.recoverKey(RegisterPayload(mcUsername, deviceId, server))
                        saveServerKey(context, mcUsername, server, resp.player_api_key)
                        return resp.player_api_key
                    } catch (e: HttpException) {
                        if (e.code() == 404) {
                            try {
                                val resp = globalApi.register(RegisterPayload(mcUsername, deviceId, server, inviteCode))
                                saveServerKey(context, mcUsername, server, resp.player_api_key)
                                return resp.player_api_key
                            } catch (e2: HttpException) {
                                if (e2.code() == 404) throw e2
                            } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {}

                    try {
                        val resp = globalApi.register(RegisterPayload(mcUsername, deviceId, server, inviteCode))
                        saveServerKey(context, mcUsername, server, resp.player_api_key)
                        return resp.player_api_key
                    } catch (e: HttpException) {
                        if (e.code() == 404) throw e
                    } catch (e: Exception) {}

                    return null
                }

                suspend fun performIngest(key: String): Boolean {
                    globalApi.ingest(IngestPayload(
                        minecraft_username = mcUsername,
                        device_id = deviceId,
                        steps_today = totalSteps,
                        player_api_key = key,
                        day = dayStr,
                        source = "health_connect",
                        timestamp = timestampStr
                    ))
                    return true
                }

                try {
                    val key = getOrRecoverKey()
                    if (key == null) {
                        errorGroups.getOrPut("Could not get API key") { mutableListOf() }.add(server)
                        return@forEach
                    }

                    try {
                        if (performIngest(key)) {
                            successCount++
                            successServers.add(server)
                        }
                    } catch (e: HttpException) {
                        if (e.code() == 404) {
                            throw e
                        }
                        if (e.code() == 401) {
                            try {
                                val resp = globalApi.recoverKey(RegisterPayload(mcUsername, deviceId, server))
                                saveServerKey(context, mcUsername, server, resp.player_api_key)
                                val newKey = resp.player_api_key
                                if (newKey != key) {
                                    if (performIngest(newKey)) {
                                        successCount++
                                        successServers.add(server)
                                        return@forEach
                                    }
                                }
                            } catch (e2: HttpException) {
                                if (e2.code() == 404) {
                                    val inviteCode = inviteCodes[server]
                                    try {
                                        val resp = globalApi.register(RegisterPayload(mcUsername, deviceId, server, inviteCode))
                                        saveServerKey(context, mcUsername, server, resp.player_api_key)
                                        val newKey = resp.player_api_key
                                        if (performIngest(newKey)) {
                                            successCount++
                                            successServers.add(server)
                                            return@forEach
                                        }
                                    } catch (e3: HttpException) {
                                        if (e3.code() == 404) throw e3
                                    } catch (_: Exception) {}
                                }
                            } catch (e2: Exception) {}
                        }
                        throw e
                    }
                } catch (e: Exception) {
                    if (e is HttpException && e.code() == 404) {
                        handleServerRemoval(context, mcUsername, server, removedServers)
                        return@forEach
                    }
                    val errMsg = parseErrorMessage(e)
                    errorGroups.getOrPut(errMsg) { mutableListOf() }.add(server)
                }
            }

            val successMsg = if (successServers.isNotEmpty()) "Synced to ${successServers.joinToString(", ")}" else null
            val errorMsg = if (errorGroups.isNotEmpty()) {
                errorGroups.entries.joinToString(" | ") { (err, srvs) ->
                    "Failed for ${srvs.joinToString(", ")}: $err"
                }
            } else null
            val removalMsg = if (removedServers.isNotEmpty()) {
                "Removed from app: ${removedServers.joinToString(", ")}" 
            } else null

            val logMessage = when {
                successMsg != null && errorMsg != null && removalMsg != null -> "Partial: $successMsg | $errorMsg | $removalMsg"
                successMsg != null && errorMsg != null -> "Partial: $successMsg | $errorMsg"
                successMsg != null && removalMsg != null -> "Partial: $successMsg | $removalMsg"
                successMsg != null -> successMsg
                removalMsg != null && errorMsg != null -> "Partial: $errorMsg | $removalMsg"
                removalMsg != null -> removalMsg
                else -> errorMsg ?: "Unknown failure"
            }

            addSyncLogEntry(context, SyncLogEntry(
                timestamp = nowStr,
                steps = totalSteps,
                source = "Background",
                success = successServers.isNotEmpty(),
                message = logMessage
            ))

            try {
                val currentServers = getSelectedServers(context)
                syncAndroidPushRegistrations(context)
                checkMilestoneNotifications(context, mcUsername, deviceId, currentServers, totalSteps, dayStr)
            } catch (e: Exception) {
                Log.e("SyncWorker", "Notification sync failed: ${e.message}")
            }

            if (successCount == 0 && errorGroups.isNotEmpty()) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed: ${e.message}")
            val nowStr = ZonedDateTime.now().format(logTimeFormatter)
            addSyncLogEntry(context, SyncLogEntry(
                timestamp = nowStr,
                steps = 0,
                source = "Background",
                success = false,
                message = "BG Sync Critical Failure: ${parseErrorMessage(e)}"
            ))
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "StepCraftSyncWork"
        private const val PUSH_CHANNEL_ID = "stepcraft_push"
        private const val SYSTEM_CHANNEL_ID = "stepcraft_system"

        fun schedule(context: Context) {
            if (!isBackgroundSyncEnabled(context)) {
                cancel(context)
                Log.d("SyncWorker", "Background sync disabled; worker canceled.")
                return
            }

            val intervalMinutes = getBackgroundSyncIntervalMinutes(context).toLong()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
            Log.d("SyncWorker", "Worker scheduled for $intervalMinutes minute intervals.")
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .build()
            WorkManager.getInstance(context).enqueue(request)
            Log.d("SyncWorker", "One-time worker triggered for immediate testing.")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d("SyncWorker", "Worker canceled.")
        }
    }

    private suspend fun checkMilestoneNotifications(
        context: Context,
        mcUsername: String,
        deviceId: String,
        servers: List<String>,
        stepsToday: Long,
        day: String
    ) {
        val notifyKeys = getNotificationTierKeys(context)
        if (notifyKeys.isEmpty()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val api = buildApi(BASE_URL, GLOBAL_API_KEY)
        createPushChannel(context)
        pruneMilestoneNotifications(context, day)

        servers.forEach { server ->
            val key = getServerKey(context, mcUsername, server)
            if (key.isNullOrBlank()) return@forEach
            try {
                val resp = api.getPlayerRewards(deviceId, server, key)
                resp.tiers.forEach { tier ->
                    val shouldNotify = notifyKeys.contains(makeTierKey(server, tier.min_steps))
                    if (!shouldNotify) return@forEach

                    if (stepsToday >= tier.min_steps && !hasMilestoneNotified(context, server, tier.min_steps, day)) {
                        val label = if (tier.label.isNotBlank()) tier.label else "Milestone"
                        val message = "You reached $label (${tier.min_steps} steps) on $server."
                        showPushNotification(context, server, message)
                        markMilestoneNotified(context, server, tier.min_steps, day)
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncWorker", "Milestone check failed for $server: ${e.message}")
            }
        }
    }

    private fun handleServerRemoval(
        context: Context,
        mcUsername: String,
        server: String,
        removedServers: MutableSet<String>
    ) {
        if (removedServers.contains(server)) return
        removedServers.add(server)

        val updated = getSelectedServers(context).filterNot { it == server }
        setSelectedServers(context, updated)
        removeServerKey(context, mcUsername, server)
        removeInviteCodeForServer(context, server)
        showServerRemovedNotification(context, server)
    }

    private fun showServerRemovedNotification(context: Context, serverName: String) {
        createSystemChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = "Removed from app because it no longer exists on the backend."
        val notification = NotificationCompat.Builder(context, SYSTEM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_stepcraft)
            .setContentTitle("Server removed · $serverName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val id = ("removed" + serverName).hashCode()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun createPushChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PUSH_CHANNEL_ID,
                "StepCraft Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createSystemChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SYSTEM_CHANNEL_ID,
                "StepCraft Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showPushNotification(context: Context, serverName: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PUSH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_stepcraft)
            .setContentTitle("StepCraft · $serverName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val id = (serverName + message).hashCode()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
