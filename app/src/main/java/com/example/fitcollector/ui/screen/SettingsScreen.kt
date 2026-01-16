package com.example.fitcollector.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fitcollector.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    requestPermissions: (Set<String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseUrl = "http://74.208.73.134/"
    val globalApiKey = "fc_live_7f3c9b2a7b2c4a2f9c8d1d0d9b3a"
    val globalApi = remember { buildApi(baseUrl, globalApiKey) }
    val deviceId = remember { getOrCreateDeviceId(context) }

    var mcUsername by remember { mutableStateOf(getMinecraftUsername(context)) }
    var selectedServers by remember { mutableStateOf(getSelectedServers(context).toSet()) }
    var autoSyncEnabled by remember { mutableStateOf(isAutoSyncEnabled(context)) }
    var mcDraft by remember { mutableStateOf(mcUsername) }
    var serverDraft by remember { mutableStateOf(selectedServers) }
    var servers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var registeredKeys by remember { mutableStateOf(getServerKeysForUser(context, mcUsername)) }
    val canChangeMc = remember { canChangeMinecraftUsername(context) }
    var queuedUsername by remember { mutableStateOf(getQueuedUsername(context)) }
    val permissions = remember { setOf(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.StepsRecord::class)) }

    LaunchedEffect(Unit) {
        val applied = applyQueuedUsernameIfPossible(context)
        if (applied != null) {
            mcUsername = applied
            mcDraft = applied
            queuedUsername = null
        }
        try {
            val resp = globalApi.getAvailableServers()
            servers = resp.servers
        } catch (e: Exception) {}
        registeredKeys = getServerKeysForUser(context, mcUsername)
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
                            singleLine = true
                        )
                        if (queuedUsername != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Change queued: $queuedUsername", style = MaterialTheme.typography.labelMedium)
                                    Text("Will apply tomorrow.", style = MaterialTheme.typography.labelSmall)
                                    TextButton(onClick = { cancelQueuedUsername(context); queuedUsername = null; mcDraft = mcUsername }) {
                                        Text("Cancel", color = Color.Red)
                                    }
                                }
                            }
                        } else if (!canChangeMc && mcDraft != mcUsername) {
                            Text("You've already changed your name today. This new name will be queued to apply in ${getTimeUntilNextChange()}.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Selected Servers:", style = MaterialTheme.typography.labelLarge)
                        servers.forEach { server ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        serverDraft = if (serverDraft.contains(server.server_name)) {
                                            serverDraft - server.server_name
                                        } else {
                                            serverDraft + server.server_name
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = serverDraft.contains(server.server_name),
                                    onCheckedChange = { checked ->
                                        serverDraft = if (checked) {
                                            serverDraft + server.server_name
                                        } else {
                                            serverDraft - server.server_name
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(server.server_name)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        val hasChanges = mcDraft != mcUsername || serverDraft != selectedServers
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    try {
                                        if (mcDraft != mcUsername) {
                                            if (canChangeMc) {
                                                serverDraft.forEach { server ->
                                                    if (getServerKey(context, mcDraft, server) == null) {
                                                        val resp = globalApi.register(RegisterPayload(mcDraft, deviceId, server))
                                                        saveServerKey(context, mcDraft, server, resp.player_api_key)
                                                    }
                                                }
                                                setMinecraftUsername(context, mcDraft)
                                                mcUsername = mcDraft
                                            } else {
                                                serverDraft.forEach { server ->
                                                    if (getServerKey(context, mcDraft, server) == null) {
                                                        val resp = globalApi.register(RegisterPayload(mcDraft, deviceId, server))
                                                        saveServerKey(context, mcDraft, server, resp.player_api_key)
                                                    }
                                                }
                                                queueMinecraftUsername(context, mcDraft)
                                                queuedUsername = mcDraft
                                            }
                                        }
                                        serverDraft.forEach { server ->
                                            if (getServerKey(context, mcUsername, server) == null) {
                                                val resp = globalApi.register(RegisterPayload(mcUsername, deviceId, server))
                                                saveServerKey(context, mcUsername, server, resp.player_api_key)
                                            }
                                        }
                                        setSelectedServers(context, serverDraft.toList())
                                        selectedServers = serverDraft
                                        registeredKeys = getServerKeysForUser(context, mcUsername)
                                        message = (if (mcDraft != mcUsername && !canChangeMc) "Username change queued. Servers updated!" else "Settings saved!") to true
                                    } catch (e: Exception) {
                                        message = (e.message ?: "Network error") to false
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = hasChanges && !isLoading && serverDraft.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            else Text("Save & Sync Servers")
                        }
                        message?.let { (msg, success) ->
                            Text(msg, color = if (success) Color(0xFF2E7D32) else Color.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            item {
                Text("Registered API Keys (Debug)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (registeredKeys.isEmpty()) {
                            Text("No servers registered for '$mcUsername'.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        } else {
                            registeredKeys.forEach { (server, key) ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(server, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    Text(key, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Color.DarkGray)
                                    if (registeredKeys.keys.last() != server) {
                                        Divider(modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
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
                        Text("If enabled, StepCraft will automatically sync your steps when the app opens.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Divider(Modifier.padding(vertical = 16.dp))
                        Button(
                            onClick = { requestPermissions(permissions) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
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
