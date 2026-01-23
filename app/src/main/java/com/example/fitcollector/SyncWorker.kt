package com.example.fitcollector

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val dayFormatter = DateTimeFormatter.ISO_LOCAL_DATE

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
            // Use the device's local timezone to determine the start of "today"
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
            var failCount = 0
            val successServers = mutableListOf<String>()
            val failedServers = mutableListOf<String>()
            val errors = mutableListOf<String>()

            val globalApi = buildApi(BASE_URL, "")

            selectedServers.forEach { server ->
                suspend fun getOrRecoverKey(): String? {
                    var key = getServerKey(context, mcUsername, server)
                    if (key != null) return key

                    try {
                        val resp = globalApi.recoverKey(RegisterPayload(mcUsername, deviceId, server))
                        saveServerKey(context, mcUsername, server, resp.player_api_key)
                        return resp.player_api_key
                    } catch (e: Exception) {}

                    try {
                        val resp = globalApi.register(RegisterPayload(mcUsername, deviceId, server))
                        saveServerKey(context, mcUsername, server, resp.player_api_key)
                        return resp.player_api_key
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
                        failCount++
                        failedServers.add(server)
                        errors.add("Could not get key for $server")
                        return@forEach
                    }

                    try {
                        if (performIngest(key)) {
                            successCount++
                            successServers.add(server)
                        }
                    } catch (e: retrofit2.HttpException) {
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
                            } catch (e2: Exception) {}
                        }
                        throw e
                    }
                } catch (e: Exception) {
                    failCount++
                    failedServers.add(server)
                    errors.add("$server: ${e.message}")
                }
            }

            if (successCount > 0) {
                Log.d("SyncWorker", "Background sync success: $totalSteps steps to ${successServers.joinToString(", ")}")
                addSyncLogEntry(context, SyncLogEntry(
                    timestamp = nowStr,
                    steps = totalSteps,
                    source = "Background",
                    success = true,
                    message = "Auto-synced to ${successServers.joinToString(", ")}"
                ))
            }
            
            if (failCount > 0) {
                Log.e("SyncWorker", "Background sync failed for ${failedServers.joinToString(", ")}")
                addSyncLogEntry(context, SyncLogEntry(
                    timestamp = nowStr,
                    steps = totalSteps,
                    source = "Background",
                    success = successCount > 0,
                    message = if (successCount > 0) "Partial: ✓ ${successServers.joinToString(", ")} | ✗ ${failedServers.joinToString(", ")}" else "Failed: ${failedServers.joinToString(", ")}"
                ))
            }

            if (successCount == 0 && failCount > 0) {
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
                message = "BG Sync Critical Failure: ${e.message}"
            ))
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "StepCraftSyncWork"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d("SyncWorker", "Worker scheduled for 15 minute intervals.")
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
}
