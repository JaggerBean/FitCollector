package com.example.fitcollector.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitcollector.*
import com.example.fitcollector.ui.screen.components.ActivityCard
import com.example.fitcollector.ui.screen.components.ResetTimer
import com.example.fitcollector.ui.screen.components.SyncStatusBanner
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (com.example.fitcollector.AppScreen) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val centralZone = remember { ZoneId.of("America/Chicago") }

    val permissions = remember {
        setOf(androidx.health.connect.client.permission.HealthPermission.getReadPermission(StepsRecord::class))
    }

    var hcStatus by remember { mutableStateOf("Checking...") }
    var hasPerms by remember { mutableStateOf(false) }
    var stepsToday by remember { mutableStateOf<Long?>(getLastKnownSteps(context)) }
    var syncResult by remember { mutableStateOf<String?>(null) }
    var lastSyncInstant by remember { mutableStateOf<Instant?>(null) }
    var client by remember { mutableStateOf<androidx.health.connect.client.HealthConnectClient?>(null) }

    val deviceId = remember { getOrCreateDeviceId(context) }
    var mcUsername by remember { mutableStateOf(getMinecraftUsername(context)) }
    var autoSyncEnabled by remember { mutableStateOf(isAutoSyncEnabled(context)) }

    val logTimeFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(centralZone) }

    fun checkAvailability() {
        val sdkStatus = androidx.health.connect.client.HealthConnectClient.getSdkStatus(context)
        hcStatus = when (sdkStatus) {
            androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE -> "Available"
            androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Update Required"
            androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE -> "Unavailable"
            else -> "Unknown"
        }
        client = if (sdkStatus == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) androidx.health.connect.client.HealthConnectClient.getOrCreate(context) else null
    }

    suspend fun refreshGrantedPermissions(): Boolean {
        val hc = client ?: return false
        return try {
            val granted = hc.permissionController.getGrantedPermissions()
            val ok = granted.containsAll(permissions)
            hasPerms = ok
            ok
        } catch (e: Exception) {
            false
        }
    }

    suspend fun readStepsToday(hc: androidx.health.connect.client.HealthConnectClient): Long {
        return try {
            val start = ZonedDateTime.now(centralZone).toLocalDate().atStartOfDay(centralZone).toInstant()
            val now = Instant.now()
            
            val response = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, now)
                )
            )
            val total = response[StepsRecord.COUNT_TOTAL] ?: 0L
            saveLastKnownSteps(context, total)
            total
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun syncSteps(steps: Long, source: String) {
        val nowZoned = ZonedDateTime.now(centralZone)
        val nowStr = nowZoned.format(logTimeFormatter)
        val dayStr = nowZoned.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val timestampStr = Instant.now().toString()
        val selectedServers = getSelectedServers(context)
        if (selectedServers.isEmpty()) {
            syncResult = "No servers selected"
            return
        }
        var successCount = 0
        var failCount = 0
        val errors = mutableListOf<String>()
        val globalApi = buildApi(BASE_URL, GLOBAL_API_KEY)

        selectedServers.forEach { server ->
            suspend fun getOrRecoverKey(): String? {
                var key = getServerKey(context, mcUsername, server)
                if (key != null) return key
                try {
                    val resp = globalApi.getKeys(deviceId, mcUsername)
                    resp.servers.forEach { (s, k) -> saveServerKey(context, mcUsername, s, k) }
                    key = resp.servers[server]
                    if (key != null) return key
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
                    steps_today = steps,
                    player_api_key = key,
                    day = dayStr,
                    source = "health_connect",
                    timestamp = timestampStr
                ))
                return true
            }

            try {
                var key = getOrRecoverKey()
                if (key == null) {
                    failCount++
                    return@forEach
                }
                try {
                    if (performIngest(key)) {
                        successCount++
                    }
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 401) {
                        try {
                            val resp = globalApi.getKeys(deviceId, mcUsername)
                            resp.servers.forEach { (s, k) -> saveServerKey(context, mcUsername, s, k) }
                            val newKey = resp.servers[server]
                            if (newKey != null && newKey != key) {
                                if (performIngest(newKey)) {
                                    successCount++
                                    return@forEach
                                }
                            }
                        } catch (e2: Exception) {}
                    }
                    throw e
                }
            } catch (e: Exception) {
                failCount++
                errors.add(e.message ?: "Unknown error")
            }
        }
        val logMessage = when {
            successCount > 0 && failCount == 0 -> "Success: $steps steps to all $successCount servers"
            successCount > 0 && failCount > 0 -> "Partial Success: Synced to $successCount, Failed for $failCount"
            else -> "Failed: Could not sync to any servers"
        }
        addSyncLogEntry(context, SyncLogEntry(nowStr, steps, source, successCount > 0, logMessage))
        syncResult = if (successCount > 0) "Synced to $successCount server(s)" else "Sync failed"
        if (successCount > 0) lastSyncInstant = Instant.now()
    }

    LaunchedEffect(Unit) {
        val applied = applyQueuedUsernameIfPossible(context)
        if (applied != null) {
            mcUsername = applied
        }
        checkAvailability()
        val hc = client
        if (hc != null && refreshGrantedPermissions()) {
            try {
                stepsToday = readStepsToday(hc)
                if (autoSyncEnabled) {
                    syncSteps(stepsToday!!, "Auto (Boot)")
                }
            } catch (e: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(24.dp).offset(y = 1.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                                    .background(Color(0xFF795548))
                                    .padding(1.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF4CAF50)))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "StepCraft",
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-1.5).sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(com.example.fitcollector.AppScreen.Settings) }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ResetTimer()
            }
            item {
                ActivityCard(
                    stepsToday = stepsToday,
                    isSyncEnabled = client != null && hasPerms && mcUsername.isNotBlank() && getSelectedServers(context).isNotEmpty(),
                    onSyncClick = {
                        scope.launch {
                            client?.let { hc ->
                                if (refreshGrantedPermissions()) {
                                    try {
                                        stepsToday = readStepsToday(hc)
                                        syncSteps(stepsToday!!, "Manual")
                                    } catch (e: Exception) { }
                                }
                            }
                        }
                    }
                )
            }
            item {
                AnimatedVisibility(visible = syncResult != null) {
                    syncResult?.let { msg ->
                        SyncStatusBanner(
                            msg = msg,
                            isSuccess = !msg.contains("failed"),
                            timestamp = lastSyncInstant
                        )
                    }
                }
            }
            if (!hasPerms || hcStatus != "Available" || mcUsername.isBlank() || getSelectedServers(context).isEmpty()) {
                item {
                    val message = when {
                        hcStatus != "Available" -> "Health Connect is not available on this device."
                        !hasPerms -> "Health Connect permissions are required to read steps."
                        mcUsername.isBlank() -> "Please set your Minecraft username in Settings."
                        getSelectedServers(context).isEmpty() -> "Please select at least one server in Settings."
                        else -> ""
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onNavigate(com.example.fitcollector.AppScreen.Settings) }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { onNavigate(com.example.fitcollector.AppScreen.Log) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, null)
                    Spacer(Modifier.width(12.dp))
                    Text("RECENT ACTIVITY LOG", fontWeight = FontWeight.Bold)
                }
            }
            item {
                val selectedCount = getSelectedServers(context).size
                Text(
                    if (autoSyncEnabled) "Auto-sync is enabled ($selectedCount servers)." else "Auto-sync is disabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
