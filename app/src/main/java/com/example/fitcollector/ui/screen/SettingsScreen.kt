package com.example.fitcollector.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fitcollector.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    requestPermissions: (Set<String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceId = remember { getOrCreateDeviceId(context) }
    val globalApi = remember { buildApi(BASE_URL, GLOBAL_API_KEY) }

    var mcUsername by remember { mutableStateOf(getMinecraftUsername(context)) }
    var autoSyncEnabled by remember { mutableStateOf(isAutoSyncEnabled(context)) }
    var currentTheme by remember { mutableStateOf(getThemeMode(context)) }
    
    var mcDraft by remember { mutableStateOf(mcUsername) }
    var availableServers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var selectedServers by remember { mutableStateOf(getSelectedServers(context).toSet()) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var queuedName by remember { mutableStateOf(getQueuedUsername(context)) }

    val canChangeMc = remember { canChangeMinecraftUsername(context) }
    val permissions = remember { setOf(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.StepsRecord::class)) }

    var timeUntilReset by remember { mutableStateOf(getTimeUntilNextChange()) }
    LaunchedEffect(Unit) {
        while(true) {
            timeUntilReset = getTimeUntilNextChange()
            delay(1000 * 60) // Update every minute
        }
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        try { 
            val resp = globalApi.getAvailableServers()
            availableServers = resp.servers
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
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
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
                        
                        Text("Available Servers:", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        availableServers.forEach { server ->
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
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedServers.contains(server.server_name),
                                    onCheckedChange = { checked ->
                                        selectedServers = if (checked) selectedServers + server.server_name else selectedServers - server.server_name
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(server.server_name)
                            }
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
                                            selectedServers.forEach { server ->
                                                val resp = globalApi.register(RegisterPayload(cleaned, deviceId, server))
                                                saveServerKey(context, cleaned, server, resp.player_api_key)
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
                                    // Also save server selections even if username is queued
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
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))
                        
                        Button(
                            onClick = { requestPermissions(permissions) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
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
