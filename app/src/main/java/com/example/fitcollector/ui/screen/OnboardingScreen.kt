package com.example.fitcollector.ui.screen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.example.fitcollector.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.URLEncoder
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    requestPermissions: (Set<String>) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseUrl = "https://api.stepcraft.org/"
    val globalApi = remember { buildApi(baseUrl, "") }
    val deviceId = remember { getOrCreateDeviceId(context) }

    var step by remember { mutableIntStateOf(1) }
    var wantsBackground by remember { mutableStateOf(false) }
    var autoAdvancedBackground by remember { mutableStateOf(false) }
    var mcUsername by remember { mutableStateOf("") }
    var selectedServers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var servers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var inviteCodeInput by remember { mutableStateOf("") }
    var inviteCodesByServer by remember { mutableStateOf(getInviteCodesByServer(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmLoading by remember { mutableStateOf(false) }
    var showPublicServers by remember { mutableStateOf(false) }
    var showPrivateServer by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showNotificationsPrompt by remember { mutableStateOf(false) }
    var pendingComplete by remember { mutableStateOf(false) }
    
    // Step Source State
    var selectedSource by remember { mutableStateOf("") }
    var recentSources by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isRefreshingSources by remember { mutableStateOf(false) }
    var showHealthConnectErrorDialog by remember { mutableStateOf(false) }

    val readStepsPermission = remember {
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(StepsRecord::class)
    }
    val backgroundPermission = remember { "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" }
    val permissions = remember { setOf(readStepsPermission, backgroundPermission) }
    var hasReadPerms by remember { mutableStateOf(false) }
    var hasBackgroundPerm by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            error = "Camera permission required to scan QR codes."
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (pendingComplete) {
            pendingComplete = false
            onComplete()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (step == 1) {
                val hc = HealthConnectClient.getOrCreate(context)
                val granted = hc.permissionController.getGrantedPermissions()
                val readGranted = granted.contains(readStepsPermission)
                val backgroundGranted = granted.contains(backgroundPermission)
                if (readGranted != hasReadPerms || backgroundGranted != hasBackgroundPerm) {
                    hasReadPerms = readGranted
                    hasBackgroundPerm = backgroundGranted
                    wantsBackground = backgroundGranted
                    if (readGranted) {
                        error = null
                    }
                }
                if (readGranted && backgroundGranted && !autoAdvancedBackground) {
                    autoAdvancedBackground = true
                    wantsBackground = true
                    step = 2
                }
            }
            if (step == 2) {
                val powerManager = context.getSystemService(PowerManager::class.java)
                val ignoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                if (ignoring) {
                    step = 3
                }
            }
        }
    }

    val refreshSources = { isManual: Boolean ->
        scope.launch {
            isRefreshingSources = true
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
                if (selectedSource.isEmpty() && recentSources.size == 1) {
                    selectedSource = recentSources.first()
                }
            } catch (e: Exception) {}
            isRefreshingSources = false
        }
    }

    LaunchedEffect(step) {
        if (step == 1) {
            val hc = HealthConnectClient.getOrCreate(context)
            val granted = hc.permissionController.getGrantedPermissions()
            hasReadPerms = granted.contains(readStepsPermission)
            hasBackgroundPerm = granted.contains(backgroundPermission)
            wantsBackground = hasBackgroundPerm
            if (!hasBackgroundPerm) {
                autoAdvancedBackground = false
            }
        }
        if (step == 3) {
            refreshSources(false)
        }
    }

    LaunchedEffect(Unit) {
        try {
            val resp = globalApi.getAvailableServers()
            var merged = resp.servers.toMutableList()
            val storedInvites = getInviteCodesByServer(context)
            inviteCodesByServer = storedInvites
            storedInvites.values.distinct().forEach { code ->
                try {
                    val privateResp = globalApi.getAvailableServers(code)
                    val existing = merged.map { it.server_name }.toSet()
                    val newOnes = privateResp.servers.filter { it.server_name !in existing }
                    merged.addAll(newOnes)
                } catch (_: Exception) {}
            }
            servers = merged.sortedBy { it.server_name.lowercase() }
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val totalSteps = if (wantsBackground) 6 else 5
        val visibleStep = when (step) {
            1 -> 1
            2 -> 2
            3 -> if (wantsBackground) 3 else 2
            4 -> if (wantsBackground) 4 else 3
            5 -> if (wantsBackground) 5 else 4
            6 -> if (wantsBackground) 6 else 5
            else -> 1
        }
        
        Text("Welcome to StepCraft", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
        Spacer(Modifier.height(8.dp))
        Text("Complete these steps to start earning rewards.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalSteps) { i ->
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(if (visibleStep > i) Color(0xFF2E7D32) else Color.LightGray)
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
                    
                    if (hasReadPerms) {
                        Button(
                            onClick = {
                                error = null
                                wantsBackground = hasBackgroundPerm
                                step = if (hasBackgroundPerm) 2 else 3
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continue")
                        }
                    } else {
                        Button(onClick = { requestPermissions(permissions) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Authorize Health Connect")
                        }
                    }
                }
            }
            2 -> {
                OnboardingStep(
                    number = 2,
                    title = "Background Sync",
                    description = "Disable battery optimization for more reliable background sync.",
                    icon = Icons.Default.PhonelinkSetup
                ) {
                    Text(
                        "Disabling battery optimization helps StepCraft sync steps even when the app is closed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable Battery Optimization")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { step = 3 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Skip for now")
                    }
                }
            }
            3 -> {
                OnboardingStep(
                    number = if (wantsBackground) 3 else 2,
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
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        try {
                                            val settingsIntent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                                            context.startActivity(settingsIntent)
                                        } catch (e: Exception) {
                                            showHealthConnectErrorDialog = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Open Health Connect Settings", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        error = null
                                        setAllowedStepSources(context, emptySet())
                                        step = 4
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Skip for now")
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
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { refreshSources(true) },
                                modifier = Modifier.weight(1f),
                                enabled = !isRefreshingSources
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Refresh")
                            }
                            Button(
                                onClick = { 
                                    if (selectedSource.isNotEmpty()) {
                                        setAllowedStepSources(context, setOf(selectedSource))
                                        step = 4
                                    } else {
                                        error = "Please select a step source."
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = selectedSource.isNotEmpty()
                            ) {
                                Text("Next")
                            }
                        }
                    }
                }
            }
            4 -> {
                OnboardingStep(
                    number = if (wantsBackground) 4 else 3,
                    title = "Minecraft Username",
                    description = "Enter your exact Minecraft username.",
                    icon = Icons.Default.Person
                ) {
                    if (error != null) {
                        Text(error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = mcUsername,
                        onValueChange = { mcUsername = it },
                        label = { Text("Minecraft Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (mcUsername.isNotBlank()) {
                                error = null
                                step = 5
                            }
                        },
                        enabled = mcUsername.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Next")
                    }
                }
            }
            5 -> {
                OnboardingStep(
                    number = if (wantsBackground) 5 else 4,
                    title = "Confirm Username",
                    description = "Is this the correct Minecraft username?",
                    icon = Icons.Default.Person
                ) {
                    if (error != null) {
                        Text(error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val cleaned = mcUsername.trim()
                        val avatarUrl = remember(cleaned) {
                            if (cleaned.isNotBlank()) {
                                val enc = URLEncoder.encode(cleaned, "UTF-8")
                                "https://minotar.net/armor/bust/$enc/160"
                            } else null
                        }
                        avatarUrl?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = "Minecraft skin preview",
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                        Text(cleaned, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { step = 4 },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Back")
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        confirmLoading = true
                                        error = null
                                        try {
                                            val profile = fetchMinecraftProfile(cleaned)
                                            if (profile == null || profile.id == null) {
                                                val details = profile?.errorMessage ?: "no response"
                                                error = "'$cleaned' is not a valid Minecraft username. API: $details"
                                            } else {
                                                step = 6
                                            }
                                        } catch (e: Exception) {
                                            error = e.message ?: "Validation failed"
                                        } finally {
                                            confirmLoading = false
                                        }
                                    }
                                },
                                enabled = !confirmLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (confirmLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                else Text("Confirm")
                            }
                        }
                    }
                }
            }
            6 -> {
                OnboardingStep(
                    number = if (wantsBackground) 6 else 5,
                    title = "Select Servers",
                    description = "Choose which servers to sync with.",
                    icon = Icons.Default.PhonelinkSetup
                ) {
                    if (error != null) {
                        Text(error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showPublicServers = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Browse public")
                        }
                        OutlinedButton(
                            onClick = { showPrivateServer = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add private")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (selectedServers.isEmpty()) {
                        Text("No servers selected.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        Text("Selected servers:", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        selectedServers.sorted().forEach { name ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = { selectedServers = selectedServers - name }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(name)
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                error = null
                                try {
                                    val cleaned = mcUsername.trim()
                                    selectedServers.forEach { serverName ->
                                        try {
                                            val invite = inviteCodesByServer[serverName]
                                            val resp = globalApi.register(RegisterPayload(cleaned, deviceId, serverName, invite))
                                            saveServerKey(context, cleaned, serverName, resp.player_api_key)
                                        } catch (e: HttpException) {
                                            if (e.code() == 409) {
                                                try {
                                                    val recoveryResp = globalApi.recoverKey(RegisterPayload(cleaned, deviceId, serverName))
                                                    saveServerKey(context, cleaned, serverName, recoveryResp.player_api_key)
                                                } catch (_: Exception) {}
                                            } else throw e
                                        }
                                    }
                                    setMinecraftUsername(context, cleaned)
                                    setSelectedServers(context, selectedServers.toList())
                                    setOnboardingComplete(context, true)
                                    pendingComplete = true
                                    showNotificationsPrompt = true
                                } catch (e: Exception) {
                                    error = e.message ?: "Registration failed"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && selectedServers.isNotEmpty()
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        else Text("Complete setup")
                    }
                }
            }
        }
    }
    
    if (showHealthConnectErrorDialog) {
        AlertDialog(
            onDismissRequest = { showHealthConnectErrorDialog = false },
            title = { Text("Unable to Open Health Connect") },
            text = {
                Text("We couldn't open Health Connect settings automatically. Please open it manually in your phone settings.")
            },
            confirmButton = {
                TextButton(onClick = { showHealthConnectErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showPublicServers) {
        PublicServersDialog(
            servers = servers,
            selectedServers = selectedServers,
            onSelectionChanged = { selectedServers = it },
            onDismiss = { showPublicServers = false }
        )
    }

    if (showPrivateServer) {
        PrivateServerDialog(
            inviteCode = inviteCodeInput,
            onInviteCodeChanged = { inviteCodeInput = it },
            onAdd = { code ->
                scope.launch {
                    error = null
                    val trimmed = code.trim()
                    if (trimmed.isBlank()) {
                        error = "Enter an invite code."
                        return@launch
                    }
                    try {
                        val previousNames = servers.map { it.server_name }.toSet()
                        val resp = globalApi.getAvailableServers(trimmed)
                        val added = resp.servers.filter { it.server_name !in previousNames }
                        if (added.isNotEmpty()) {
                            inviteCodesByServer = inviteCodesByServer + added.associate { it.server_name to trimmed }
                            setInviteCodesByServer(context, inviteCodesByServer)
                            servers = (servers + added).distinctBy { it.server_name }
                                .sortedBy { it.server_name.lowercase() }
                            added.forEach { selectedServers = selectedServers + it.server_name }
                            inviteCodeInput = ""
                            showPrivateServer = false
                        } else {
                            error = "Invite code not found."
                        }
                    } catch (e: Exception) {
                        error = "Could not add invite code: ${e.message}"
                    }
                }
            },
            onScanQr = { cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA) },
            onDismiss = { showPrivateServer = false }
        )
    }

    if (showQrScanner) {
        OnboardingQrScannerDialog(
            onCodeScanned = { raw: String ->
                inviteCodeInput = extractInviteCodeOnboarding(raw)
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false }
        )
    }

    if (showNotificationsPrompt) {
        AlertDialog(
            onDismissRequest = { showNotificationsPrompt = false },
            title = { Text("Enable Notifications?") },
            text = {
                Text(
                    "Would you like to receive notifications for your connected servers? " +
                        "You can customize notifications anytime in Settings."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationsPrompt = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            if (pendingComplete) {
                                pendingComplete = false
                                onComplete()
                            }
                        }
                    }
                ) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotificationsPrompt = false
                        if (pendingComplete) {
                            pendingComplete = false
                            onComplete()
                        }
                    }
                ) {
                    Text("Skip for now")
                }
            }
        )
    }
}

@Composable
fun PublicServersDialog(
    servers: List<ServerInfo>,
    selectedServers: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredServers = remember(searchQuery, servers) {
        servers.filter { it.server_name.contains(searchQuery, ignoreCase = true) }
            .sortedBy { it.server_name.lowercase() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Public servers", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun PrivateServerDialog(
    inviteCode: String,
    onInviteCodeChanged: (String) -> Unit,
    onAdd: (String) -> Unit,
    onScanQr: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Add private server", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = onInviteCodeChanged,
                    label = { Text("Invite Code") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onScanQr, modifier = Modifier.weight(1f)) {
                        Text("Scan QR")
                    }
                    Button(onClick = { onAdd(inviteCode) }, modifier = Modifier.weight(1f)) {
                        Text("Add")
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun OnboardingQrScannerDialog(
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val hasScanned = remember { mutableStateOf(false) }
    val executor = remember { ContextCompat.getMainExecutor(context) }

    DisposableEffect(previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(executor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null && !hasScanned.value) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val raw = barcodes.firstOrNull()?.rawValue
                        if (!raw.isNullOrBlank() && !hasScanned.value) {
                            hasScanned.value = true
                            onCodeScanned(raw)
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }

        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (_: Exception) {}
        }

        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            analysis.clearAnalyzer()
            scanner.close()
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {}
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 520.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Scan Invite QR", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Center the QR code inside the frame.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

fun extractInviteCodeOnboarding(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return trimmed
    return try {
        val uri = android.net.Uri.parse(trimmed)
        uri.getQueryParameter("code")
            ?: uri.getQueryParameter("invite")
            ?: uri.getQueryParameter("invite_code")
            ?: trimmed
    } catch (_: Exception) {
        trimmed
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