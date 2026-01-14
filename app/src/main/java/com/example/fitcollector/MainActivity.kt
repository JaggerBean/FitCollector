package com.example.fitcollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Professional Color Palette
private val HealthGreen = Color(0xFF2E7D32)
private val HealthLightGreen = Color(0xFFE8F5E9)
private val HealthBlue = Color(0xFF1565C0)
private val HealthLightBlue = Color(0xFFE3F2FD)

class MainActivity : ComponentActivity() {

    private lateinit var requestPermissions: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions =
            registerForActivityResult(
                PermissionController.createRequestPermissionResultContract()
            ) { /* result handled by re-check */ }

        setContent {
            FitCollectorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showLog by remember { mutableStateOf(false) }
                    
                    if (showLog) {
                        ActivityLogScreen(onBack = { showLog = false })
                    } else {
                        MainDashboard(
                            requestPermissions = { perms -> requestPermissions.launch(perms) },
                            onShowLog = { showLog = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FitCollectorTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = HealthGreen,
        onPrimary = Color.White,
        primaryContainer = HealthLightGreen,
        onPrimaryContainer = HealthGreen,
        secondary = HealthBlue,
        onSecondary = Color.White,
        secondaryContainer = HealthLightBlue,
        onSecondaryContainer = HealthBlue,
        background = Color(0xFFF8F9FA),
        surface = Color.White
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainDashboard(
    requestPermissions: (Set<String>) -> Unit,
    onShowLog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val permissions = remember {
        setOf(HealthPermission.getReadPermission(StepsRecord::class))
    }

    var hcStatus by remember { mutableStateOf("Checking...") }
    var hasPerms by remember { mutableStateOf(false) }
    var stepsToday by remember { mutableStateOf<Long?>(getLastKnownSteps(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var syncResult by remember { mutableStateOf<String?>(null) }
    var lastSyncInstant by remember { mutableStateOf<Instant?>(null) }
    var client by remember { mutableStateOf<HealthConnectClient?>(null) }

    val deviceId = remember { getOrCreateDeviceId(context) }
    val baseUrl = "http://74.208.73.134/"
    val apiKey = "fc_live_7f3c9b2a7b2c4a2f9c8d1d0d9b3a"
    val api = remember { buildApi(baseUrl, apiKey) }

    var mcUsername by remember { mutableStateOf(getMinecraftUsername(context)) }
    var mcDraft by remember { mutableStateOf(mcUsername) }
    var mcSaved by remember { mutableStateOf(mcUsername.isNotBlank()) }
    var canChangeMc by remember { mutableStateOf(canChangeMinecraftUsername(context)) }
    var queuedName by remember { mutableStateOf(getQueuedUsername(context)) }
    var autoSyncEnabled by remember { mutableStateOf(isAutoSyncEnabled(context)) }

    val logTimeFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }

    fun checkAvailability() {
        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        hcStatus = when (sdkStatus) {
            HealthConnectClient.SDK_AVAILABLE -> "Available"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Update Required"
            HealthConnectClient.SDK_UNAVAILABLE -> "Unavailable"
            else -> "Unknown"
        }
        client = if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context) else null
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
        val resp = hc.readRecords(ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(start, now)))
        val total = resp.records.sumOf { it.count }
        saveLastKnownSteps(context, total)
        return total
    }

    suspend fun syncSteps(steps: Long, source: String) {
        val nowStr = ZonedDateTime.now().format(logTimeFormatter)
        try {
            val resp = api.ingest(IngestPayload(mcUsername, deviceId, steps))
            syncResult = "Successfully synced ${resp.steps_today} steps"
            lastSyncInstant = Instant.now()
            addSyncLogEntry(context, SyncLogEntry(nowStr, steps, source, true, "Success: ${resp.steps_today} steps"))
        } catch (e: Exception) {
            syncResult = "Sync failed"
            lastSyncInstant = null
            addSyncLogEntry(context, SyncLogEntry(nowStr, steps, source, false, e.message ?: "Network error"))
        }
    }

    LaunchedEffect(Unit) {
        val applied = applyQueuedUsernameIfPossible(context)
        if (applied != null) {
            mcUsername = applied
            mcDraft = applied
            mcSaved = true
            queuedName = null
            canChangeMc = canChangeMinecraftUsername(context)
        }
        checkAvailability()
        val hc = client
        if (hc != null && refreshGrantedPermissions()) {
            try {
                stepsToday = readStepsToday(hc)
                if (autoSyncEnabled) {
                    syncSteps(stepsToday!!, "Auto (Boot)")
                }
            } catch (e: Exception) { 
                error = e.message 
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FitCollector", fontWeight = FontWeight.Bold) },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Auto-Sync", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = { enabled ->
                                autoSyncEnabled = enabled
                                setAutoSyncEnabled(context, enabled)
                                if (enabled) {
                                    scope.launch {
                                        checkAvailability()
                                        if (refreshGrantedPermissions()) {
                                            client?.let { hc ->
                                                try {
                                                    stepsToday = readStepsToday(hc)
                                                    syncSteps(stepsToday!!, "Auto (Toggle)")
                                                } catch (e: Exception) { error = e.message }
                                            }
                                        }
                                    }
                                }
                            },
                            thumbContent = {
                                if (autoSyncEnabled) Icon(Icons.Default.Check, null, Modifier.size(12.dp))
                            }
                        )
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Activity Overview Section
            item {
                ActivityCard(
                    stepsToday = stepsToday, 
                    isSyncEnabled = client != null && hasPerms,
                    onSyncClick = {
                        scope.launch {
                            client?.let { hc ->
                                if (refreshGrantedPermissions()) {
                                    try {
                                        stepsToday = readStepsToday(hc)
                                        syncSteps(stepsToday!!, "Manual")
                                    } catch (e: Exception) { error = e.message }
                                }
                            }
                        }
                    }
                )
            }

            // Sync Status Result
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

            // Health Connect Status & Controls
            item {
                HealthConnectStatusBanner(
                    hasPerms = hasPerms,
                    hcStatus = hcStatus,
                    onRequestPermissions = { requestPermissions(permissions) },
                    onRefresh = {
                        checkAvailability()
                        scope.launch { refreshGrantedPermissions() }
                    }
                )
            }

            // Account Section
            item {
                AccountCard(
                    mcDraft = mcDraft,
                    onDraftChange = { mcDraft = it },
                    mcUsername = mcUsername,
                    mcSaved = mcSaved,
                    canChangeMc = canChangeMc,
                    queuedName = queuedName,
                    onSaveClick = {
                        val cleaned = mcDraft.trim()
                        if (canChangeMc || mcDraft.trim() == mcUsername) {
                            setMinecraftUsername(context, cleaned)
                            mcUsername = cleaned
                            mcSaved = true
                            queuedName = null
                        } else {
                            queueMinecraftUsername(context, cleaned)
                            queuedName = cleaned
                        }
                        canChangeMc = canChangeMinecraftUsername(context)
                    }
                )
            }

            // Navigation to Log
            item {
                Button(
                    onClick = onShowLog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HealthBlue)
                ) {
                    Icon(Icons.Default.List, null)
                    Spacer(Modifier.width(12.dp))
                    Text("RECENT ACTIVITY LOG", fontWeight = FontWeight.Bold)
                }
            }

            item {
                Text(
                    if (autoSyncEnabled) 
                        "Auto behavior: app reads today's steps and syncs automatically on launch."
                    else 
                        "Auto-sync is disabled. Use 'Sync Now' to manually upload steps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val syncLog = remember { getSyncLog(context) }
    
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recent Activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (syncLog.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No activity logged yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(syncLog) { entry ->
                    LogEntryRow(entry)
                }
            }
        }
    }
}

@Composable
fun HealthConnectStatusBanner(
    hasPerms: Boolean,
    hcStatus: String,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = when {
                    !hasPerms -> Color(0xFFFBC02D)
                    hcStatus == "Available" -> HealthGreen
                    else -> Color.Red
                }
                Icon(
                    if (hasPerms) Icons.Default.CheckCircle else Icons.Default.Info,
                    null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Health Connect",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (hasPerms) "Connected" else hcStatus,
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!hasPerms) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Authorize", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityCard(stepsToday: Long?, isSyncEnabled: Boolean, onSyncClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(HealthGreen, Color(0xFF1B5E20))
                    )
                )
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Steps Today",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stepsToday?.toString() ?: "--",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )

                Text(
                    "Keep moving to earn rewards!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(Modifier.height(24.dp))
                
                Button(
                    onClick = onSyncClick,
                    enabled = isSyncEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = HealthGreen,
                        disabledContainerColor = Color.White.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "SYNC NOW", 
                        fontWeight = FontWeight.ExtraBold, 
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AccountCard(
    mcDraft: String,
    onDraftChange: (String) -> Unit,
    mcUsername: String,
    mcSaved: Boolean,
    canChangeMc: Boolean,
    queuedName: String?,
    onSaveClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = HealthBlue)
                Spacer(Modifier.width(8.dp))
                Text("Minecraft Integration", fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(12.dp))
            
            OutlinedTextField(
                value = mcDraft,
                onValueChange = onDraftChange,
                label = { Text("Minecraft Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            val isChanging = mcDraft.trim() != mcUsername
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isChanging) {
                    Button(
                        onClick = onSaveClick,
                        enabled = mcDraft.trim().isNotEmpty(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (!canChangeMc) "Queue for Tomorrow" else "Save Settings")
                    }
                }
                
                if (mcSaved && !isChanging) {
                    Icon(Icons.Default.CheckCircle, null, tint = HealthGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Saved", color = HealthGreen, style = MaterialTheme.typography.labelLarge)
                }
            }

            if (queuedName != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Next up: $queuedName (Applying tomorrow)",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            } else if (isChanging && !canChangeMc) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Changed once today. Wait: ${getTimeUntilNextChange()}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun SyncStatusBanner(msg: String, isSuccess: Boolean, timestamp: Instant? = null) {
    val bgColor = if (isSuccess) HealthLightGreen else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isSuccess) HealthGreen else MaterialTheme.colorScheme.error

    var timeAgo by remember { mutableStateOf("just now") }

    if (isSuccess && timestamp != null) {
        LaunchedEffect(timestamp) {
            while (true) {
                val now = Instant.now()
                val diff = Duration.between(timestamp, now)
                val seconds = diff.seconds
                timeAgo = when {
                    seconds < 2 -> "just now"
                    seconds < 60 -> if (seconds == 1L) "1 second ago" else "$seconds seconds ago"
                    seconds < 3600 -> {
                        val mins = seconds / 60
                        if (mins == 1L) "1 minute ago" else "$mins minutes ago"
                    }
                    else -> {
                        val hours = seconds / 3600
                        if (hours == 1L) "1 hour ago" else "$hours hours ago"
                    }
                }
                delay(1000)
            }
        }
    }

    Surface(
        color = bgColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning, null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(msg, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                if (isSuccess) {
                    Text(timeAgo, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun LogEntryRow(entry: SyncLogEntry) {
    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (entry.success) HealthGreen else Color.Red)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.message, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(entry.timestamp, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Text(
                entry.source,
                style = MaterialTheme.typography.labelSmall,
                color = HealthBlue,
                modifier = Modifier
                    .background(HealthLightBlue, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
