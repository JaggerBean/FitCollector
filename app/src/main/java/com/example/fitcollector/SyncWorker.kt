package com.example.fitcollector

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.*
import java.time.Instant
import java.time.LocalDate
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

        // 2. Check if credentials are set
        val mcUsername = getMinecraftUsername(context)
        val playerApiKey = getPlayerApiKey(context)
        val deviceId = getOrCreateDeviceId(context)
        
        if (mcUsername.isBlank() || playerApiKey.isBlank()) {
            Log.e("SyncWorker", "Missing credentials, failing.")
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
            // 5. Read steps
            val zone = ZoneId.systemDefault()
            val nowZoned = ZonedDateTime.now(zone)
            val start = nowZoned.toLocalDate().atStartOfDay(zone).toInstant()
            val now = Instant.now()
            val resp = client.readRecords(ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(start, now)))
            val totalSteps = resp.records.sumOf { it.count }
            
            saveLastKnownSteps(context, totalSteps)

            // 6. Sync to backend
            val baseUrl = "http://74.208.73.134/"
            val api = buildApi(baseUrl, playerApiKey)

            val dayStr = nowZoned.format(dayFormatter)
            val timestampStr = now.toString()

            api.ingest(IngestPayload(
                minecraft_username = mcUsername,
                device_id = deviceId,
                steps_today = totalSteps,
                player_api_key = playerApiKey,
                day = dayStr,
                source = "health_connect",
                timestamp = timestampStr
            ))

            // 7. Log success
            val nowStr = nowZoned.format(logTimeFormatter)
            Log.d("SyncWorker", "Background sync success: $totalSteps steps")
            addSyncLogEntry(context, SyncLogEntry(
                timestamp = nowStr,
                steps = totalSteps,
                source = "Background",
                success = true,
                message = "Auto-synced $totalSteps steps"
            ))

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed: ${e.message}")
            val nowStr = ZonedDateTime.now().format(logTimeFormatter)
            addSyncLogEntry(context, SyncLogEntry(
                timestamp = nowStr,
                steps = 0,
                source = "Background",
                success = false,
                message = "BG Sync Failed: ${e.message}"
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
