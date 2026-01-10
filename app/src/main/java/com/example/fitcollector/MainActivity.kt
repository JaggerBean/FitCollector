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

    var client by remember { mutableStateOf<HealthConnectClient?>(null) }

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

    fun refreshGrantedPermissions() {
        val hc = client ?: return
        scope.launch {
            try {
                val granted = hc.permissionController.getGrantedPermissions()
                hasPerms = granted.containsAll(permissions)
            } catch (e: Exception) {
                error = e.message
            }
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

    // --- Backend wiring (simple) ---
    val deviceId = remember { getOrCreateDeviceId(context) }

    // IMPORTANT: choose the right baseUrl:
    // Emulator:
    val baseUrl = "http://10.0.2.2:8000/"
    // Real phone on same Wi-Fi as PC (replace with your PC IPv4):
    //    val baseUrl = "http://192.168.1.174:8000/"

    val api = remember { buildApi(baseUrl) }
    var syncResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        checkAvailability()
        refreshGrantedPermissions()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("FitCollector", style = MaterialTheme.typography.headlineSmall)
            Text("Device: $deviceId")
            Text("Health Connect status: $status")

            Button(onClick = {
                error = null
                checkAvailability()
                refreshGrantedPermissions()
            }) { Text("Check Health Connect") }

            Button(
                enabled = client != null && !hasPerms,
                onClick = {
                    error = null
                    requestPermissions(permissions)
                }
            ) { Text("Request Steps Permission") }

            TextButton(
                enabled = client != null,
                onClick = { refreshGrantedPermissions() }
            ) { Text("Refresh permission status") }

            Button(
                enabled = client != null && hasPerms,
                onClick = {
                    val hc = client ?: return@Button
                    error = null
                    scope.launch {
                        try {
                            stepsToday = readStepsToday(hc)
                        } catch (e: Exception) {
                            error = e.message ?: "Unknown error"
                        }
                    }
                }
            ) { Text("Read Steps Today") }

            if (stepsToday != null) {
                Text("Steps today: $stepsToday")
            }

            // ✅ NORMAL sync button
            Button(
                enabled = (stepsToday != null),
                onClick = {
                    val steps = stepsToday ?: return@Button
                    error = null
                    syncResult = null
                    scope.launch {
                        try {
                            val resp = api.ingest(
                                IngestPayload(device_id = deviceId, steps_today = steps)
                            )
                            syncResult = "Synced OK: ${resp.steps_today} steps on ${resp.day}"
                        } catch (e: Exception) {
                            syncResult = "Sync failed: ${e.message}"
                        }
                    }
                }
            ) { Text("Sync to backend") }

            if (syncResult != null) {
                Text(syncResult!!)
            }

            if (error != null) {
                Text("Error: $error", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
