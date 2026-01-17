package com.example.fitcollector.ui.screen

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.fitcollector.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.HealthConnectClient
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    requestPermissions: (Set<String>) -> Unit,
    onBack: () -> Unit,
    onNavigateToRawHealth: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceId = remember { getOrCreateDeviceId(context) }
    val globalApi = remember { buildApi(BASE_URL, GLOBAL_API_KEY) }

    var mcUsername by remember { mutableStateOf(getMinecraftUsername(context)) }
    var autoSyncEnabled by remember { mutableStateOf(isAutoSyncEnabled(context)) }
    var backgroundSyncEnabled by remember { mutableStateOf(isBackgroundSyncEnabled(context)) }
    var currentTheme by remember { mutableStateOf(getThemeMode(context)) }
    
    var mcDraft by remember { mutableStateOf(mcUsername) }
    var availableServers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var selectedServers by remember { mutableStateOf(getSelectedServers(context).toSet()) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var queuedName by remember { mutableStateOf(getQueuedUsername(context)) }
    
    var showServerSelector by remember { mutableStateOf(false) }
    var showKeysDialog by remember { mutableStateOf(false) }

    val canChangeMc = remember { canChangeMinecraftUsername(context) }
    
    // Updated permissions set to include background read
    val permissions = remember { 
        setOf(
            androidx.health.connect.client.permission.HealthPermission.getReadPermission(StepsRecord::class),
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
        ) 
    }

    var allPermissionsGranted by remember { mutableStateOf(false) }

    // Step Source State
    val currentSources = getAllowedStepSources(context)
    var selectedSource by remember { mutableStateOf(currentSources.firstOrNull() ?: "") }
    var recentSources by remember { mutableStateOf<Set<String>>(emptySet()) }

    var timeUntilReset by remember { mutableStateOf(getTimeUntilNextChange()) }
    LaunchedEffect(Unit) {
        while(true) {
            timeUntilReset = getTimeUntilNextChange()
            delay(1000 * 60)
        }
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        try { 
            val resp = globalApi.getAvailableServers()
            availableServers = resp.servers.sortedBy { it.server_name.lowercase() }
        } catch (e: Exception) {}

        try {
            val hc = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val start = now.minus(java.time.Duration.ofDays(7))
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, now)
                )
            )
            recentSources = response.records.map { it.metadata.dataOrigin.packageName }.toSet()
            
            // Check permissions
            val granted = hc.permissionController.getGrantedPermissions()
            allPermissionsGranted = granted.containsAll(permissions)
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
                                            // 1. Recover existing keys if any (handles re-installs/device switches)
                                            try {
                                                val keysResp = globalApi.getKeys(deviceId, cleaned)
                                                keysResp.servers.forEach { (server, key) ->
                                                    saveServerKey(context, cleaned, server, key)
                                                }
                                            } catch (e: Exception) {
                                                // Ignore if keys recovery fails
                                            }

                                            // 2. Register for servers that don't have keys yet
                                            selectedServers.forEach { serverName ->
                                                if (getServerKey(context, cleaned, serverName) == null) {
                                                    try {
                                                        val resp = globalApi.register(RegisterPayload(cleaned, deviceId, serverName))
                                                        saveServerKey(context, cleaned, serverName, resp.player_api_key)
                                                    } catch (e: HttpException) {
                                                        if (e.code() == 409) {
                                                            // Conflict: Already registered. Fetch keys to get the API key.
                                                            try {
                                                                val keysResp = globalApi.getKeys(deviceId, cleaned)
                                                                keysResp.servers[serverName]?.let { key ->
                                                                    saveServerKey(context, cleaned, serverName, key)
                                                                }
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
                                    queueMinecraftUsername(context, cleaned)
                                    queuedName = cleaned
                                    setSelectedServers(context, selectedServers.toList())
                                    message = "Username queued for tomorrow!" to true
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
                Text("Health Connect Integrity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Choose your steps source", style = MaterialTheme.typography.labelLarge)
                        Text("Only allow steps from one specific app.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        
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
                                Text("Periodic sync every 15 mins while app is closed.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Switch(
                                checked = backgroundSyncEnabled,
                                onCheckedChange = {
                                    backgroundSyncEnabled = it
                                    setBackgroundSyncEnabled(context, it)
                                }
                            )
                        }
                        
                        if (backgroundSyncEnabled) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("IMPORTANT: Background Sync Requirement", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        "1. Tap 'Health Connect Settings' below\n" +
                                        "2. Find 'Background read' in the permissions list\n" +
                                        "3. Turn it ON to allow syncing while closed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        if (!allPermissionsGranted) {
                            Button(
                                onClick = { requestPermissions(permissions) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Lock, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Update Permissions")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Settings, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Health Connect Settings")
                            }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectorDialog(
    availableServers: List<ServerInfo>,
    selectedServers: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredServers = remember(searchQuery, availableServers) {
        availableServers.filter { it.server_name.contains(searchQuery, ignoreCase = true) }
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
                    items(filteredServers) { server ->
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
