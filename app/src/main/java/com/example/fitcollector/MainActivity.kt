package com.example.fitcollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.ui.text.font.FontFamily
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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Professional Color Palette
private val HealthGreen = Color(0xFF2E7D32)
private val HealthLightGreen = Color(0xFFE8F5E9)
private val HealthBlue = Color(0xFF1565C0)
private val HealthLightBlue = Color(0xFFE3F2FD)
private val MinecraftDirt = Color(0xFF795548)
private val MinecraftGrass = Color(0xFF4CAF50)

enum class AppScreen {
    Onboarding, Dashboard, Settings, Log
}

class MainActivity : ComponentActivity() {

    private lateinit var requestPermissions: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions =
            registerForActivityResult(
                PermissionController.createRequestPermissionResultContract()
            ) { /* result handled by re-check */ }

        setContent {
            StepCraftTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    var currentScreen by remember { 
                        mutableStateOf(if (isOnboardingComplete(context)) AppScreen.Dashboard else AppScreen.Onboarding) 
                    }
                    
                    val onNavigate = { screen: AppScreen -> currentScreen = screen }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "ScreenTransition"
                    ) { screen ->
                        when (screen) {
                            AppScreen.Onboarding -> OnboardingScreen(
                                requestPermissions = { perms -> requestPermissions.launch(perms) },
                                onComplete = { onNavigate(AppScreen.Dashboard) }
                            )
                            AppScreen.Dashboard -> MainDashboard(
                                onNavigate = onNavigate
                            )
                            AppScreen.Settings -> SettingsScreen(
                                requestPermissions = { perms -> requestPermissions.launch(perms) },
                                onBack = { onNavigate(AppScreen.Dashboard) }
                            )
                            AppScreen.Log -> ActivityLogScreen(
                                onBack = { onNavigate(AppScreen.Dashboard) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StepCraftTheme(content: @Composable () -> Unit) {
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
    onNavigate: (AppScreen) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val permissions = remember {
        setOf(HealthPermission.getReadPermission(StepsRecord::class))
    }

    var hcStatus by remember { mutableStateOf("Checking...") }
    var hasPerms by remember { mutableStateOf(false) }
    var stepsToday by remember { mutableStateOf<Long?>(getLastKnownSteps(context)) }
    var syncResult by remember { mutableStateOf<String?>(null) }
    var lastSyncInstant by remember { mutableStateOf<Instant?>(null) }
    var client by remember { mutableStateOf<HealthConnectClient?>(null) }

    val deviceId = remember { getOrCreateDeviceId(context) }
    val baseUrl = "http://74.208.73.134/"
    val apiKey = "fc_live_7f3c9b2a7b2c4a2f9c8d1d0d9b3a"
    val api = remember { buildApi(baseUrl, apiKey) }

    var mcUsername by remember { mutableStateOf(getMinecraftUsername(context)) }
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
                                tint = HealthGreen,
                                modifier = Modifier.size(24.dp).offset(y = 1.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MinecraftDirt)
                                    .padding(1.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(MinecraftGrass))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "StepCraft", 
                            fontWeight = FontWeight.Black, 
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-1.5).sp,
                            color = HealthGreen
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(AppScreen.Settings) }) {
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
                ActivityCard(
                    stepsToday = stepsToday, 
                    isSyncEnabled = client != null && hasPerms && mcUsername.isNotBlank(),
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

            if (!hasPerms || hcStatus != "Available" || mcUsername.isBlank()) {
                item {
                    val message = when {
                        hcStatus != "Available" -> "Health Connect is not available on this device."
                        !hasPerms -> "Health Connect permissions are required to read steps."
                        mcUsername.isBlank() -> "Please set your Minecraft username in Settings."
                        else -> ""
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onNavigate(AppScreen.Settings) }
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
                    onClick = { onNavigate(AppScreen.Log) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HealthBlue)
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, null)
                    Spacer(Modifier.width(12.dp))
                    Text("RECENT ACTIVITY LOG", fontWeight = FontWeight.Bold)
                }
            }

            item {
                Text(
                    if (autoSyncEnabled) "Auto-sync is enabled." else "Auto-sync is disabled.",
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
fun OnboardingScreen(
    requestPermissions: (Set<String>) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { buildApi("http://74.208.73.134/", "fc_live_7f3c9b2a7b2c4a2f9c8d1d0d9b3a") }
    val deviceId = remember { getOrCreateDeviceId(context) }

    var step by remember { mutableIntStateOf(1) }
    var mcUsername by remember { mutableStateOf("") }
    var selectedServer by remember { mutableStateOf("") }
    var servers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissions = remember { setOf(HealthPermission.getReadPermission(StepsRecord::class)) }
    var hasPerms by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val resp = api.getAvailableServers()
            servers = resp.servers
        } catch (e: Exception) {
            error = "Could not fetch servers: ${e.message}"
        }
    }

    LaunchedEffect(step) {
        if (step == 1) {
            val hc = HealthConnectClient.getOrCreate(context)
            hasPerms = hc.permissionController.getGrantedPermissions().containsAll(permissions)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to StepCraft", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = HealthGreen)
        Spacer(Modifier.height(8.dp))
        Text("Complete these steps to start earning rewards.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        
        Spacer(Modifier.height(32.dp))

        // Progress Indicators
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(if (step > i) HealthGreen else Color.LightGray)
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        when (step) {
            1 -> {
                OnboardingStep(
                    number = 1,
                    title = "Health Permissions",
                    description = "Allow StepCraft to read your daily step count from Health Connect.",
                    icon = Icons.Default.Favorite
                ) {
                    if (hasPerms) {
                        Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                            Text("Next")
                        }
                    } else {
                        Button(onClick = { requestPermissions(permissions) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Authorize Health Connect")
                        }
                        TextButton(onClick = { 
                            scope.launch {
                                val hc = HealthConnectClient.getOrCreate(context)
                                hasPerms = hc.permissionController.getGrantedPermissions().containsAll(permissions)
                                if (hasPerms) step = 2
                            }
                        }) {
                            Text("I've authorized it, check again")
                        }
                    }
                }
            }
            2 -> {
                OnboardingStep(
                    number = 2,
                    title = "Minecraft Identity",
                    description = "Enter your exact Minecraft username and select your server.",
                    icon = Icons.Default.Person
                ) {
                    OutlinedTextField(
                        value = mcUsername,
                        onValueChange = { mcUsername = it },
                        label = { Text("Minecraft Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Select Server:", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(servers) { server ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedServer = server.server_name }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedServer == server.server_name, onClick = { selectedServer = server.server_name })
                                Spacer(Modifier.width(8.dp))
                                Text(server.server_name)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { step = 3 },
                        enabled = mcUsername.isNotBlank() && selectedServer.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Next")
                    }
                }
            }
            3 -> {
                OnboardingStep(
                    number = 3,
                    title = "Register Device",
                    description = "Link this device to your account to start syncing steps.",
                    icon = Icons.Default.PhonelinkSetup
                ) {
                    if (error != null) {
                        Text(error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                error = null
                                try {
                                    api.register(RegisterPayload(mcUsername, deviceId, selectedServer))
                                    setMinecraftUsername(context, mcUsername)
                                    setSelectedServer(context, selectedServer)
                                    setOnboardingComplete(context, true)
                                    onComplete()
                                } catch (e: Exception) {
                                    error = e.message ?: "Network error"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        else Text("Complete Registration")
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingStep(number: Int, title: String, description: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = HealthGreen)
        Spacer(Modifier.height(16.dp))
        Text("Step $number: $title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    requestPermissions: (Set<String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { buildApi("http://74.208.73.134/", "fc_live_7f3c9b2a7b2c4a2f9c8d1d0d9b3a") }
    val deviceId = remember { getOrCreateDeviceId(context) }

    var mcUsername by remember { mutableStateOf(getMinecraftUsername(context)) }
    var selectedServer by remember { mutableStateOf(getSelectedServer(context)) }
    var autoSyncEnabled by remember { mutableStateOf(isAutoSyncEnabled(context)) }
    
    var mcDraft by remember { mutableStateOf(mcUsername) }
    var serverDraft by remember { mutableStateOf(selectedServer) }
    var servers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // Text to Success/Error

    val canChangeMc = remember { canChangeMinecraftUsername(context) }
    val permissions = remember { setOf(HealthPermission.getReadPermission(StepsRecord::class)) }

    LaunchedEffect(Unit) {
        try { 
            val resp = api.getAvailableServers()
            servers = resp.servers
        } catch (e: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Account Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = mcDraft,
                            onValueChange = { mcDraft = it },
                            label = { Text("Minecraft Username") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = canChangeMc || mcDraft == mcUsername
                        )
                        if (!canChangeMc && mcDraft != mcUsername) {
                            Text("You can only change your username once per day. Next change in ${getTimeUntilNextChange()}", 
                                style = MaterialTheme.typography.labelSmall, color = Color.Red)
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Text("Selected Server:", style = MaterialTheme.typography.labelLarge)
                        servers.forEach { server ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { serverDraft = server.server_name }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = serverDraft == server.server_name, onClick = { serverDraft = server.server_name })
                                Spacer(Modifier.width(8.dp))
                                Text(server.server_name)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        val hasChanges = mcDraft != mcUsername || serverDraft != selectedServer
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    try {
                                        api.register(RegisterPayload(mcDraft, deviceId, serverDraft))
                                        setMinecraftUsername(context, mcDraft)
                                        setSelectedServer(context, serverDraft)
                                        mcUsername = mcDraft
                                        selectedServer = serverDraft
                                        message = "Settings saved & device registered!" to true
                                    } catch (e: Exception) {
                                        message = (e.message ?: "Network error") to false
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = hasChanges && !isLoading && (canChangeMc || mcDraft == mcUsername),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            else Text("Save & Register Device")
                        }

                        message?.let { (msg, success) ->
                            Text(msg, color = if (success) HealthGreen else Color.Red, 
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }

            item {
                Text("App Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-Sync Steps", modifier = Modifier.weight(1f))
                            Switch(
                                checked = autoSyncEnabled,
                                onCheckedChange = {
                                    autoSyncEnabled = it
                                    setAutoSyncEnabled(context, it)
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("If enabled, StepCraft will automatically sync your steps when the app opens.", 
                            style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))
                        
                        Button(
                            onClick = { requestPermissions(permissions) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = HealthBlue)
                        ) {
                            Icon(Icons.Default.Lock, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Update Health Permissions")
                        }
                    }
                }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onSyncClick) {
                        Icon(Icons.Default.Refresh, "Sync Now", tint = Color.White)
                    }
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
