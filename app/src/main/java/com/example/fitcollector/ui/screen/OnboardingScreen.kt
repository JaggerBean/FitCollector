package com.example.fitcollector.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.fitcollector.*
import kotlinx.coroutines.launch
import java.time.Instant
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    requestPermissions: (Set<String>) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseUrl = "http://74.208.73.134/"
    val globalApiKey = "fc_live_7f3c9b2a7b2c4a2f9c8d1d0d9b3a"
    val globalApi = remember { buildApi(baseUrl, globalApiKey) }
    val deviceId = remember { getOrCreateDeviceId(context) }

    var step by remember { mutableIntStateOf(1) }
    var mcUsername by remember { mutableStateOf("") }
    var selectedServers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var servers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Step Source State
    var selectedSource by remember { mutableStateOf("") }
    var recentSources by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isRefreshingSources by remember { mutableStateOf(false) }
    var hasAttemptedRefresh by remember { mutableStateOf(false) }
    var lastRefreshFoundSomethingNew by remember { mutableStateOf(true) }

    val permissions = remember { 
        setOf(
            androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.StepsRecord::class),
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
        ) 
    }
    var hasPerms by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (step == 1) {
                val hc = androidx.health.connect.client.HealthConnectClient.getOrCreate(context)
                val granted = hc.permissionController.getGrantedPermissions().containsAll(permissions)
                if (granted != hasPerms) {
                    hasPerms = granted
                    if (granted) {
                        error = null
                        step = 2
                    }
                }
            }
        }
    }

    val refreshSources = { isManual: Boolean ->
        scope.launch {
            val prevSources = recentSources
            isRefreshingSources = true
            if (isManual) hasAttemptedRefresh = true
            try {
                val hc = androidx.health.connect.client.HealthConnectClient.getOrCreate(context)
                val now = Instant.now()
                val start = now.minus(java.time.Duration.ofDays(7))
                val response = hc.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, now)
                    )
                )
                val newSources = response.records.map { it.metadata.dataOrigin.packageName }.toSet()
                if (isManual) {
                    lastRefreshFoundSomethingNew = (newSources - prevSources).isNotEmpty()
                }
                recentSources = newSources
                if (selectedSource.isEmpty() && recentSources.size == 1) {
                    selectedSource = recentSources.first()
                }
            } catch (e: Exception) {}
            isRefreshingSources = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            val resp = globalApi.getAvailableServers()
            servers = resp.servers
        } catch (e: Exception) {
            error = "Could not fetch servers: ${e.message}"
        }
    }

    LaunchedEffect(step) {
        if (step == 1) {
            val hc = androidx.health.connect.client.HealthConnectClient.getOrCreate(context)
            hasPerms = hc.permissionController.getGrantedPermissions().containsAll(permissions)
        }
        if (step == 2) {
            refreshSources(false)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to StepCraft", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
        Spacer(Modifier.height(8.dp))
        Text("Complete these steps to start earning rewards.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(if (step > i) Color(0xFF2E7D32) else Color.LightGray)
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
                    if (error != null) {
                        Text(error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    if (hasPerms) {
                        Button(onClick = { 
                            error = null
                            step = 2 
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Next")
                        }
                    } else {
                        Button(onClick = { requestPermissions(permissions) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Authorize Health Connect")
                        }
                        TextButton(onClick = {
                            scope.launch {
                                val hc = androidx.health.connect.client.HealthConnectClient.getOrCreate(context)
                                hasPerms = hc.permissionController.getGrantedPermissions().containsAll(permissions)
                                if (hasPerms) {
                                    error = null
                                    step = 2
                                } else {
                                    error = "Permissions not yet granted. Please enable them in the Health Connect app."
                                }
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
                    title = "Steps Source",
                    description = "Choose which app StepCraft should trust for your step count.",
                    icon = Icons.Default.Source
                ) {
                    val allAvailableSources = (recentSources + (if (selectedSource.isNotEmpty()) setOf(selectedSource) else emptySet())).sorted()
                    
                    if (allAvailableSources.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("No step sources found", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "To earn rewards, another app (like Google Fit or Samsung Health) must be sending data to Health Connect.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Text(
                                    "How to fix this:\n" +
                                    "1. Open Health Connect Settings\n" +
                                    "2. Tap 'App permissions'\n" +
                                    "3. Find your preferred tracker app\n" +
                                    "4. Ensure it has 'Steps' permission turned ON.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Open Health Connect Settings", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        allAvailableSources.forEach { pkg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSource = pkg }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedSource == pkg,
                                    onClick = { selectedSource = pkg }
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
                        
                        Spacer(Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = {
                                val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Health Connect Settings", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { refreshSources(true) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRefreshingSources
                    ) {
                        if (isRefreshingSources) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Refresh Available Health Connect Apps")
                        }
                    }

                    if (hasAttemptedRefresh && !lastRefreshFoundSomethingNew) {
                        Text(
                            "Still nothing? You may need to open your steps app (e.g. Google Fit) to trigger a sync, or you can skip and select it in Settings later.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    
                    Button(
                        onClick = { 
                            if (selectedSource.isNotEmpty()) {
                                setAllowedStepSources(context, setOf(selectedSource))
                            }
                            step = 3 
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (allAvailableSources.isEmpty()) "Skip for now" else "Next")
                    }
                }
            }
            3 -> {
                OnboardingStep(
                    number = 3,
                    title = "Minecraft Identity",
                    description = "Enter your exact Minecraft username and select your servers.",
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
                    Text("Select Servers:", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(servers) { server ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedServers = if (selectedServers.contains(server.server_name)) {
                                            selectedServers - server.server_name
                                        } else {
                                            selectedServers + server.server_name
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedServers.contains(server.server_name),
                                    onCheckedChange = { checked ->
                                        selectedServers = if (checked) {
                                            selectedServers + server.server_name
                                        } else {
                                            selectedServers - server.server_name
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(server.server_name)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { step = 4 },
                        enabled = mcUsername.isNotBlank() && selectedServers.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Next")
                    }
                }
            }
            4 -> {
                OnboardingStep(
                    number = 4,
                    title = "Register Device",
                    description = "Link this device to your account to start syncing steps.",
                    icon = Icons.Default.PhonelinkSetup
                ) {
                    if (error != null) {
                        Text(error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                error = null
                                try {
                                    // Try to recover keys if they already exist for this device/username
                                    val keysResp = globalApi.getKeys(deviceId, mcUsername)
                                    keysResp.servers.forEach { (server, key) ->
                                        saveServerKey(context, mcUsername, server, key)
                                    }
                                    
                                    // Register for any missing servers
                                    selectedServers.forEach { server ->
                                        if (getServerKey(context, mcUsername, server) == null) {
                                            val resp = globalApi.register(RegisterPayload(mcUsername, deviceId, server))
                                            saveServerKey(context, mcUsername, server, resp.player_api_key)
                                        }
                                    }
                                    
                                    setMinecraftUsername(context, mcUsername)
                                    setSelectedServers(context, selectedServers.toList())
                                    setOnboardingComplete(context, true)
                                    onComplete()
                                } catch (e: Exception) {
                                    // If keys recovery fails, just try registering normally
                                    try {
                                        selectedServers.forEach { server ->
                                            if (getServerKey(context, mcUsername, server) == null) {
                                                val resp = globalApi.register(RegisterPayload(mcUsername, deviceId, server))
                                                saveServerKey(context, mcUsername, server, resp.player_api_key)
                                            }
                                        }
                                        setMinecraftUsername(context, mcUsername)
                                        setSelectedServers(context, selectedServers.toList())
                                        setOnboardingComplete(context, true)
                                        onComplete()
                                    } catch (e2: Exception) {
                                        error = e2.message ?: "Network error"
                                    }
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
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = Color(0xFF2E7D32))
        Spacer(Modifier.height(16.dp))
        Text("Step $number: $title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        content()
    }
}
