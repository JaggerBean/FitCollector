package com.example.fitcollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import androidx.compose.foundation.layout.statusBarsPadding


class MainActivity : ComponentActivity() {

    private lateinit var requestPermissions: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions =
            registerForActivityResult(
                PermissionController.createRequestPermissionResultContract()
            ) { /* result handled by re-check */ }

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    StepsTodayScreen(
                        requestPermissions = { perms -> requestPermissions.launch(perms) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepsTodayScreen(
    requestPermissions: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val permissions = remember {
        setOf(HealthPermission.getReadPermission(StepsRecord::class))
    }

    var status by remember { mutableStateOf("Not checked") }
    var hasPerms by remember { mutableStateOf(false) }
    var stepsToday by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var syncResult by remember { mutableStateOf<String?>(null) }

    var client by remember { mutableStateOf<HealthConnectClient?>(null) }

    // --- Backend wiring ---
    val deviceId = remember { getOrCreateDeviceId(context) }

    // Choose ONE:
    //val baseUrl = "http://10.0.2.2:8000/"            // emulator
    val baseUrl = "http://192.168.1.174:8000/"     // real phone on same Wi-Fi

    // If you added API key auth, set it here:
    val apiKey = "fc_live_7f3c9b2a7b2c4a2f9c8d1d0d9b3a"
    val api = remember { buildApi(baseUrl, apiKey) }   // if no apiKey in your buildApi, use buildApi(baseUrl)

    var mcUsername by remember { mutableStateOf(getMinecraftUsername(context)) }
    var mcDraft by remember { mutableStateOf(mcUsername) }
    var mcSaved by remember { mutableStateOf(mcUsername.isNotBlank()) }


    fun checkAvailability() {
        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        status = when (sdkStatus) {
            HealthConnectClient.SDK_AVAILABLE -> "SDK_AVAILABLE"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "UPDATE_REQUIRED"
            HealthConnectClient.SDK_UNAVAILABLE -> "SDK_UNAVAILABLE"
            else -> "SDK_STATUS=$sdkStatus"
        }

        client = if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    suspend fun refreshGrantedPermissions(): Boolean {
        val hc = client ?: return false
        return try {
            val granted = hc.permissionController.getGrantedPermissions()
            val ok = granted.containsAll(permissions)
            hasPerms = ok
            ok
        } catch (e: Exception) {
            error = e.message
            false
        }
    }

    suspend fun readStepsToday(hc: HealthConnectClient): Long {
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.now(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val now = Instant.now()

        val resp = hc.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, now)
            )
        )
        return resp.records.sumOf { it.count }
    }

    suspend fun syncSteps(steps: Long) {
        try {
            val resp = api.ingest(
                IngestPayload(
                    minecraft_username = mcUsername,
                    device_id = deviceId,
                    steps_today = steps
                )
            )

            syncResult = "Synced OK: ${resp.steps_today} steps on ${resp.day}"
        } catch (e: Exception) {
            syncResult = "Sync failed: ${e.message}"
        }
    }

    // ✅ Auto flow: availability -> perms -> read -> sync
    LaunchedEffect(Unit) {
        error = null
        syncResult = null

        checkAvailability()
        val hc = client
        if (hc == null) return@LaunchedEffect

        val ok = refreshGrantedPermissions()
        if (!ok) {
            // No perms yet; user must tap Request permission once.
            return@LaunchedEffect
        }

        try {
            val steps = readStepsToday(hc)
            stepsToday = steps
            syncSteps(steps)
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        }
    }

    // ✅ Optional: also auto-sync if stepsToday changes (prevents double-run issues)
    LaunchedEffect(stepsToday) {
        val steps = stepsToday ?: return@LaunchedEffect
        // Only sync if we haven't already synced successfully for this value
        if (syncResult?.startsWith("Synced OK") == true) return@LaunchedEffect
        if (!mcSaved) return@LaunchedEffect
        // Comment this out if you don’t want re-sync on every read
        // syncSteps(steps)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("FitCollector", style = MaterialTheme.typography.headlineSmall)
        Text("Device: $deviceId")
        Text("Health Connect status: $status")
        Text("Perms granted: $hasPerms")

        Text("Minecraft username (required)")

        OutlinedTextField(
            value = mcDraft,
            onValueChange = { mcDraft = it },
            singleLine = true,
            placeholder = { Text("e.g. MinerSteve123") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = mcDraft.trim().isNotEmpty(),
                onClick = {
                    val cleaned = mcDraft.trim()
                    setMinecraftUsername(context, cleaned)
                    mcUsername = cleaned
                    mcSaved = true
                }
            ) { Text("Save") }

            Text(if (mcSaved) "Saved: $mcUsername" else "Not saved", modifier = Modifier.padding(top = 12.dp))
        }


        if (stepsToday != null) {
            Text("Steps today: $stepsToday")
        }

        // --- Manual controls (still useful) ---
        Button(onClick = {
            error = null
            syncResult = null
            checkAvailability()
            scope.launch {
                refreshGrantedPermissions()
            }
        }) { Text("Re-check Health Connect") }

        Button(
            enabled = client != null && !hasPerms,
            onClick = {
                error = null
                requestPermissions(permissions)
            }
        ) { Text("Request Steps Permission") }

        Button(
            enabled = client != null && hasPerms,
            onClick = {
                val hc = client ?: return@Button
                error = null
                syncResult = null
                scope.launch {
                    try {
                        val steps = readStepsToday(hc)
                        stepsToday = steps
                        syncSteps(steps)
                    } catch (e: Exception) {
                        error = e.message ?: "Unknown error"
                    }
                }
            }
        ) { Text("Read + Sync Now") }

        if (syncResult != null) {
            Text(syncResult!!)
        }

        if (error != null) {
            Text("Error: $error", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Auto behavior: if Health Connect is available and permission is granted, " +
                    "the app reads today's steps and syncs automatically on launch."
        )
    }
}

