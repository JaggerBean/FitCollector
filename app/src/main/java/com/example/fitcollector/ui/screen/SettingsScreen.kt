package com.example.fitcollector.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fitcollector.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.HealthConnectClient
import retrofit2.HttpException
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    requestPermissions: (Set<String>) -> Unit,
    onBack: () -> Unit,
    onNavigateToRawHealth: () -> Unit,
    onNavigateToLog: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceId = remember { getOrCreateDeviceId(context) }
    val globalApi = remember { buildApi(BASE_URL, "") }

    var mcUsername by remember { mutableStateOf(getMinecraftUsername(context)) }
    var autoSyncEnabled by remember { mutableStateOf(isAutoSyncEnabled(context)) }
    var backgroundSyncEnabled by remember { mutableStateOf(isBackgroundSyncEnabled(context)) }
    var backgroundSyncInterval by remember { mutableStateOf(getBackgroundSyncIntervalMinutes(context)) }
    var currentTheme by remember { mutableStateOf(getThemeMode(context)) }
    
    var mcDraft by remember { mutableStateOf(mcUsername) }
    var availableServers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var selectedServers by remember { mutableStateOf(getSelectedServers(context).toSet()) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var queuedName by remember { mutableStateOf(getQueuedUsername(context)) }
    var inviteCodeInput by remember { mutableStateOf("") }
    var inviteCodesByServer by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    var showServerSelector by remember { mutableStateOf(false) }
    var showKeysDialog by remember { mutableStateOf(false) }
    var showHealthConnectErrorDialog by remember { mutableStateOf(false) }
    var showBatteryOffDialog by remember { mutableStateOf(false) }

    val canChangeMc = remember { canChangeMinecraftUsername(context) }
    
    val permissions = remember { 
        setOf(
            androidx.health.connect.client.permission.HealthPermission.getReadPermission(StepsRecord::class),
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
        ) 
    }

    var allPermissionsGranted by remember { mutableStateOf(false) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(false) }
    var recentSources by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Logic to re-check all critical status whenever the app is foregrounded
    fun refreshSystemStatus() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)
        
        try {
            val hc = HealthConnectClient.getOrCreate(context)
            scope.launch {
                val granted = hc.permissionController.getGrantedPermissions()
                allPermissionsGranted = granted.containsAll(permissions)
                
                // Refresh recent sources
                val now = Instant.now()
                val start = now.minus(java.time.Duration.ofDays(7))
                val response = hc.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, now)
                    )
                )
                recentSources = response.records.map { it.metadata.dataOrigin.packageName }.toSet()
            }
        } catch (e: Exception) {}
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshSystemStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initial load logic
    LaunchedEffect(Unit) {
        refreshSystemStatus()
        try { 
            val resp = globalApi.getAvailableServers()
            availableServers = resp.servers.sortedBy { it.server_name.lowercase() }
        } catch (e: Exception) {}
    }

    // Step Source State
    val currentSources = getAllowedStepSources(context)
    var selectedSource by remember { mutableStateOf(currentSources.firstOrNull() ?: "") }

    var timeUntilReset by remember { mutableStateOf(getTimeUntilNextChange()) }
    LaunchedEffect(key1 = Unit) {
        while(true) {
            timeUntilReset = getTimeUntilNextChange()
            delay(1000L * 60L)
        }
    }

    BackHandler(onBack = onBack)

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Button(
                    onClick = { onNavigateToLog() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, null)
                    Spacer(Modifier.width(12.dp))
                    Text("RECENT ACTIVITY LOG", fontWeight = FontWeight.Bold)
                }
            }

            item {
                Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("System", "Light", "Dark").forEach { mode ->
                                FilterChip(
                                    selected = currentTheme == mode,
                                    onClick = { 
                                        currentTheme = mode
                                        setThemeMode(context, mode)
                                    },
                                    label = { Text(mode) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

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
                            singleLine = true
                        )

                        Spacer(Modifier.height(8.dp))

                        val isChanging = mcDraft.trim() != mcUsername
                        
                        if (queuedName != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Next up: $queuedName",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Applying in $timeUntilReset",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    TextButton(onClick = {
                                        cancelQueuedUsername(context)
                                        queuedName = null
                                        mcDraft = mcUsername
                                    }) {
                                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        } else if (isChanging && !canChangeMc) {
                            Text(
                                "Changed once today. Wait $timeUntilReset or queue for tomorrow",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Text("Private Server Invite Code", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("Add a private server using its invite code.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inviteCodeInput,
                            onValueChange = { inviteCodeInput = it },
                            label = { Text("Invite Code") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val code = inviteCodeInput.trim()
                                    if (code.isBlank()) {
                                        message = "Enter an invite code." to false
                                        return@launch
                                    }
                                    val currentName = mcUsername.trim()
                                    if (currentName.isBlank()) {
                                        message = "Set your Minecraft username before adding private servers." to false
                                        return@launch
                                    }
                                    try {
                                        val previousNames = availableServers.map { it.server_name }.toSet()
                                        val resp = globalApi.getAvailableServers(code)
                                        availableServers = resp.servers.sortedBy { it.server_name.lowercase() }
                                        val added = resp.servers.filter { it.server_name !in previousNames }
                                        if (added.isNotEmpty()) {
                                            inviteCodesByServer = inviteCodesByServer + added.associate { it.server_name to code }
                                            val addedNames = added.map { it.server_name }
                                            selectedServers = selectedServers + addedNames
                                            setSelectedServers(context, selectedServers.toList())
                                            addedNames.forEach { serverName ->
                                                if (getServerKey(context, currentName, serverName) == null) {
                                                    try {
                                                        val respReg = globalApi.register(RegisterPayload(currentName, deviceId, serverName, code))
                                                        saveServerKey(context, currentName, serverName, respReg.player_api_key)
                                                    } catch (e: HttpException) {
                                                        if (e.code() == 409) {
                                                            try {
                                                                val recoveryResp = globalApi.recoverKey(RegisterPayload(currentName, deviceId, serverName))
                                                                saveServerKey(context, currentName, serverName, recoveryResp.player_api_key)
                                                            } catch (_: Exception) {}
                                                        } else {
                                                            throw e
                                                        }
                                                    }
                                                }
                                            }
                                            inviteCodeInput = ""
                                            message = "Private server added and registered." to true
                                        } else {
                                            message = "Invite code not found." to false
                                        }
                                    } catch (e: Exception) {
                                        message = "Could not add invite code: ${e.message}" to false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Private Server")
                        }

                        Spacer(Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { showServerSelector = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (selectedServers.isEmpty()) "Select Servers" else "Servers: ${selectedServers.size} selected")
                        }

                        Spacer(Modifier.height(16.dp))

                        val hasChanges = mcDraft != mcUsername || selectedServers != getSelectedServers(context).toSet()
                        Button(
                            onClick = {
                                val cleaned = mcDraft.trim()
                                if (canChangeMc || cleaned == mcUsername) {
                                    scope.launch {
                                        isLoading = true
                                        try {
                                                if (cleaned != mcUsername) {
                                                    val profile = fetchMinecraftProfile(cleaned)
                                                    if (profile == null || profile.id == null) {
                                                        val details = profile?.errorMessage ?: "no response"
                                                        message = "'$cleaned' is not a valid Minecraft username. API: $details" to false
                                                        isLoading = false
                                                        return@launch
                                                    }
                                                }
                                            selectedServers.forEach { serverName ->
                                                if (getServerKey(context, cleaned, serverName) == null) {
                                                    try {
                                                        val invite = inviteCodesByServer[serverName]
                                                        val resp = globalApi.register(RegisterPayload(cleaned, deviceId, serverName, invite))
                                                        saveServerKey(context, cleaned, serverName, resp.player_api_key)
                                                    } catch (e: HttpException) {
                                                        if (e.code() == 409) {
                                                            try {
                                                                val recoveryResp = globalApi.recoverKey(RegisterPayload(cleaned, deviceId, serverName))
                                                                saveServerKey(context, cleaned, serverName, recoveryResp.player_api_key)
                                                            } catch (ex: Exception) {}
                                                        } else throw e
                                                    }
                                                }
                                            }

                                            setMinecraftUsername(context, cleaned)
                                            setSelectedServers(context, selectedServers.toList())
                                            mcUsername = cleaned
                                            queuedName = null
                                            message = "Settings saved & registered!" to true
                                        } catch (e: Exception) {
                                            message = (e.message ?: "Registration failed") to false
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            val profile = fetchMinecraftProfile(cleaned)
                                            if (profile == null || profile.id == null) {
                                                val details = profile?.errorMessage ?: "no response"
                                                message = "'$cleaned' is not a valid Minecraft username. API: $details" to false
                                                isLoading = false
                                                return@launch
                                            }
                                            queueMinecraftUsername(context, cleaned)
                                            queuedName = cleaned
                                            setSelectedServers(context, selectedServers.toList())
                                            message = "Username queued for tomorrow!" to true
                                        } catch (e: Exception) {
                                            message = (e.message ?: "Failed to queue username") to false
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            enabled = hasChanges && !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            else {
                                val text = if (!canChangeMc && mcDraft.trim() != mcUsername) "Queue for Tomorrow" else "Save & Register All"
                                Text(text)
                            }
                        }

                        message?.let { (msg, success) ->
                            Text(msg, color = if (success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error, 
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }

            item {
                Text("Sync & Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Sync on Open", style = MaterialTheme.typography.labelLarge)
                                Text("Sync steps immediately when you open the app.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Switch(
                                checked = autoSyncEnabled,
                                onCheckedChange = {
                                    autoSyncEnabled = it
                                    setAutoSyncEnabled(context, it)
                                }
                            )
                        }

                        HorizontalDivider(Modifier.padding(vertical = 16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Background Sync", style = MaterialTheme.typography.labelLarge)
                                val description = if (backgroundSyncEnabled) {
                                    "Periodic sync every ${backgroundSyncInterval} mins while app is closed."
                                } else {
                                    "Disabled. No periodic sync while app is closed."
                                }
                                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Switch(
                                checked = backgroundSyncEnabled,
                                onCheckedChange = {
                                    backgroundSyncEnabled = it
                                    setBackgroundSyncEnabled(context, it)
                                    if (it) {
                                        SyncWorker.schedule(context)
                                    } else {
                                        SyncWorker.cancel(context)
                                    }
                                }
                            )
                        }

                        if (backgroundSyncEnabled) {
                            Spacer(Modifier.height(12.dp))
                            Text("Sync Frequency", style = MaterialTheme.typography.labelLarge)
                            Text("Every ${backgroundSyncInterval} minutes", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Slider(
                                value = backgroundSyncInterval.toFloat(),
                                onValueChange = { value ->
                                    val snapped = (value / 15f).roundToInt() * 15
                                    backgroundSyncInterval = snapped.coerceIn(15, 120)
                                },
                                valueRange = 15f..120f,
                                steps = 6,
                                onValueChangeFinished = {
                                    setBackgroundSyncIntervalMinutes(context, backgroundSyncInterval)
                                    SyncWorker.schedule(context)
                                }
                            )
                        }

                        HorizontalDivider(Modifier.padding(vertical = 16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("High Reliability Mode", style = MaterialTheme.typography.labelLarge)
                                    if (backgroundSyncEnabled && !isIgnoringBatteryOptimizations) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = "Recommended for background sync",
                                            tint = Color(0xFFFFC107)
                                        )
                                    }
                                }
                                Text("Ignore battery optimizations to keep sync running smoothly.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                if (backgroundSyncEnabled && !isIgnoringBatteryOptimizations) {
                                    Text("Recommended for background sync success.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFC107))
                                }
                            }
                            Switch(
                                checked = isIgnoringBatteryOptimizations,
                                onCheckedChange = {
                                    if (!isIgnoringBatteryOptimizations) {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        showBatteryOffDialog = true
                                    }
                                }
                            )
                        }

                        if (!allPermissionsGranted) {
                            HorizontalDivider(Modifier.padding(vertical = 16.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Background Health Access", style = MaterialTheme.typography.labelLarge)
                                    Text("Required to read steps while the app is not in the foreground.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Button(
                                    onClick = { requestPermissions(permissions) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.Lock, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Enable")
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    showHealthConnectErrorDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Settings, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Health Connect Settings")
                        }
                    }
                }
            }

            item {
                Text("Health Connect Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Choose your step source", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))

                        val allAvailableSources = (recentSources + (if (selectedSource.isNotEmpty()) setOf(selectedSource) else emptySet())).sorted()
                        if (allAvailableSources.isEmpty()) {
                            Text("No step sources found in the last 7 days.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        } else {
                            allAvailableSources.forEach { pkg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedSource = pkg
                                            setAllowedStepSources(context, setOf(pkg))
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedSource == pkg,
                                        onClick = {
                                            selectedSource = pkg
                                            setAllowedStepSources(context, setOf(pkg))
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    val displayName = when(pkg) {
                                        "com.google.android.apps.fitness" -> "Google Fit"
                                        "com.sec.android.app.shealth" -> "Samsung Health"
                                        context.packageName -> "StepCraft (This App)"
                                        else -> pkg
                                    }
                                    Text(displayName, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 16.dp))

                        OutlinedButton(
                            onClick = onNavigateToRawHealth,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Info, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Debug: View Raw Health Records")
                        }
                    }
                }
            }

            item {
                Text("Developer Debug", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))) {
                    Column(Modifier.padding(16.dp)) {
                        Button(
                            onClick = { showKeysDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text("View Saved API Keys")
                        }

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                clearAllServerKeys(context)
                                message = "All server keys cleared!" to true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Clear All API Keys")
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Device ID: $deviceId",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }

        }
    }

    if (showServerSelector) {
        ServerSelectorDialog(
            availableServers = availableServers,
            privateServerNames = inviteCodesByServer.keys,
            selectedServers = selectedServers,
            onSelectionChanged = { selectedServers = it },
            onDismiss = { showServerSelector = false }
        )
    }

    if (showKeysDialog) {
        val keys = getAllServerKeys(context)
        Dialog(onDismissRequest = { showKeysDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Saved API Keys", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))

                    if (keys.isEmpty()) {
                        Text("No keys saved.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(keys.toList()) { (ident, key) ->
                                Column(Modifier.padding(vertical = 8.dp)) {
                                    Text(ident, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    Text(key, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                HorizontalDivider()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showKeysDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Battery optimization OFF dialog
    if (showBatteryOffDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryOffDialog = false },
            title = { Text("Enable Optimization?") },
            text = { Text("Android does not allow apps to turn optimization back ON automatically. We will now take you to the system list; please find 'StepCraft' and set it back to 'Optimized'.") },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryOffDialog = false
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryOffDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Health Connect error dialog
    if (showHealthConnectErrorDialog) {
        AlertDialog(
            onDismissRequest = { showHealthConnectErrorDialog = false },
            title = { Text("Unable to Open Health Connect") },
            text = {
                Text(
                    "We couldn't open Health Connect settings automatically. " +
                    "Please open it manually:\n\n" +
                    "1. Open your phone's Settings app\n" +
                    "2. Search for 'Health Connect'\n" +
                    "3. Tap 'App permissions'\n" +
                    "4. Find your fitness tracker app\n" +
                    "5. Enable 'Steps' permission"
                )
            },
            confirmButton = {
                TextButton(onClick = { showHealthConnectErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectorDialog(
    availableServers: List<ServerInfo>,
    privateServerNames: Set<String>,
    selectedServers: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val (privateServers, publicServers) = remember(searchQuery, availableServers, privateServerNames) {
        val filtered = availableServers.filter { it.server_name.contains(searchQuery, ignoreCase = true) }
        val privates = filtered.filter { privateServerNames.contains(it.server_name) }
            .sortedBy { it.server_name.lowercase() }
        val publics = filtered.filterNot { privateServerNames.contains(it.server_name) }
            .sortedBy { it.server_name.lowercase() }
        privates to publics
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Servers", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Servers") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(Modifier.height(8.dp))
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (privateServers.isNotEmpty()) {
                        item {
                            Text(
                                "Private servers",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(privateServers) { server ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedServers.contains(server.server_name)) {
                                            onSelectionChanged(selectedServers - server.server_name)
                                        } else {
                                            onSelectionChanged(selectedServers + server.server_name)
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedServers.contains(server.server_name),
                                    onCheckedChange = { checked ->
                                        if (checked) onSelectionChanged(selectedServers + server.server_name)
                                        else onSelectionChanged(selectedServers - server.server_name)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(server.server_name)
                            }
                        }
                    }

                    if (publicServers.isNotEmpty()) {
                        item {
                            Text(
                                "Public servers",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(publicServers) { server ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedServers.contains(server.server_name)) {
                                            onSelectionChanged(selectedServers - server.server_name)
                                        } else {
                                            onSelectionChanged(selectedServers + server.server_name)
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedServers.contains(server.server_name),
                                    onCheckedChange = { checked ->
                                        if (checked) onSelectionChanged(selectedServers + server.server_name)
                                        else onSelectionChanged(selectedServers - server.server_name)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(server.server_name)
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Done")
                }
            }
        }
    }
}
