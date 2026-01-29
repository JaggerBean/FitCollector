package com.example.fitcollector.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitcollector.*
import com.example.fitcollector.GLOBAL_API_KEY
import com.example.fitcollector.BASE_URL
import com.example.fitcollector.ui.screen.components.ActivityCard
import com.example.fitcollector.ui.screen.components.ResetTimer
import com.example.fitcollector.ui.screen.components.SyncStatusBanner
import com.example.fitcollector.ui.theme.MinecraftDirt
import com.example.fitcollector.ui.theme.MinecraftGrass
import kotlinx.coroutines.launch
import java.time.Instant
import coil.compose.AsyncImage
import java.net.URLEncoder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.metadata.Metadata
import retrofit2.HttpException
import com.google.gson.JsonParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (com.example.fitcollector.AppScreen) -> Unit
) {
    val context = LocalContext.current
    val needsOnboarding = remember {
        val username = getMinecraftUsername(context)
        val servers = getSelectedServers(context)
        val hasKeys = username.isNotBlank() && servers.isNotEmpty() &&
            servers.all { getServerKey(context, username, it) != null }
        username.isBlank() || servers.isEmpty() || !hasKeys
    }
    LaunchedEffect(needsOnboarding) {
        if (needsOnboarding) {
            onNavigate(com.example.fitcollector.AppScreen.Onboarding)
        }
    }
    if (needsOnboarding) {
        return
    }
    val scope = rememberCoroutineScope()
    val deviceZone = remember { ZoneId.systemDefault() }

    val permissions = remember {
        setOf(androidx.health.connect.client.permission.HealthPermission.getReadPermission(StepsRecord::class))
    }

    var hcStatus by remember { mutableStateOf("Checking...") }
    var hasPerms by remember { mutableStateOf(false) }
    var stepsToday by remember { mutableStateOf<Long?>(getLastKnownSteps(context)) }
    var syncResult by remember { mutableStateOf<Pair<String?, String?>?>(null) }
    var lastSyncInstant by remember { mutableStateOf<Instant?>(null) }
    var client by remember { mutableStateOf<androidx.health.connect.client.HealthConnectClient?>(null) }
    var autoTimeDisabled by remember { mutableStateOf(false) }
    var claimStatuses by remember { mutableStateOf<Map<String, ClaimStatusResponse>>(emptyMap()) }
    var yesterdaySteps by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var blockedSourcesWarning by remember { mutableStateOf<List<String>?>(null) }
    var rewardTiersByServer by remember { mutableStateOf<Map<String, List<RewardTier>>>(emptyMap()) }
    var trackedTiers by remember { mutableStateOf(getTrackedTiersByServer(context)) }

    val deviceId = remember { getOrCreateDeviceId(context) }
    var mcUsername by remember { mutableStateOf(getMinecraftUsername(context)) }
    
    var autoSyncEnabled by remember { mutableStateOf(isAutoSyncEnabled(context)) }
    var backgroundSyncEnabled by remember { mutableStateOf(isBackgroundSyncEnabled(context)) }

    val logTimeFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(deviceZone) }

    fun checkAvailability() {
        autoTimeDisabled = !isAutomaticTimeEnabled(context)
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

    suspend fun readStepsForRange(hc: androidx.health.connect.client.HealthConnectClient, start: Instant, end: Instant): Long {
        return try {
            val allowedSources = getAllowedStepSources(context)
            var totalSteps = 0L
            var pageToken: String? = null
            
            val foundSources = mutableSetOf<String>()
            val blocked = mutableListOf<String>()

            do {
                val response = hc.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageToken = pageToken
                    )
                )
                
                for (record in response.records) {
                    val pkg = record.metadata.dataOrigin.packageName
                    foundSources.add(pkg)
                    
                    if (isRecordValid(record, allowedSources)) {
                        totalSteps += record.count
                    } else if (record.metadata.recordingMethod != Metadata.RECORDING_METHOD_MANUAL_ENTRY) {
                        if (!blocked.contains(pkg)) blocked.add(pkg)
                    }
                }
                pageToken = response.pageToken
            } while (pageToken != null)
            
            if (allowedSources.isEmpty() && foundSources.size == 1) {
                val singleSource = foundSources.first()
                setAllowedStepSources(context, setOf(singleSource))
                return readStepsForRange(hc, start, end)
            }

            if (totalSteps == 0L && blocked.isNotEmpty()) {
                blockedSourcesWarning = blocked
            } else if (totalSteps > 0) {
                blockedSourcesWarning = null
            }
            
            totalSteps
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun refreshClaimStatuses() {
        if (mcUsername.isBlank()) return
        val selectedServers = getSelectedServers(context)
        val globalApi = buildApi(BASE_URL, GLOBAL_API_KEY)
        val newStatuses = mutableMapOf<String, ClaimStatusResponse>()
        val newSteps = mutableMapOf<String, Long>()
        selectedServers.forEach { server ->
            try {
                val status = globalApi.getClaimStatus(mcUsername, server)
                newStatuses[server] = status
                
                newSteps[server] = 0L
                
                val key = getServerKey(context, mcUsername, server)
                if (key != null) {
                    val stepsResp = globalApi.getStepsYesterday(mcUsername, key)
                    if (stepsResp.server_name == server) {
                        newSteps[server] = stepsResp.steps_yesterday
                    }
                }
            } catch (e: Exception) { }
        }
        claimStatuses = newStatuses
        yesterdaySteps = newSteps
    }

    suspend fun refreshRewardTiers() {
        if (mcUsername.isBlank()) return
        val selectedServers = getSelectedServers(context)
        val globalApi = buildApi(BASE_URL, GLOBAL_API_KEY)
        val newTiers = mutableMapOf<String, List<RewardTier>>()
        selectedServers.forEach { server ->
            try {
                val key = getServerKey(context, mcUsername, server) ?: return@forEach
                val resp = globalApi.getPlayerRewards(deviceId, server, key)
                newTiers[server] = resp.tiers
            } catch (_: Exception) { }
        }
        rewardTiersByServer = newTiers
        trackedTiers = getTrackedTiersByServer(context)
    }

    fun parseErrorMessage(e: Throwable): String {
        if (e is HttpException) {
            try {
                val errorBody = e.response()?.errorBody()?.string()
                if (!errorBody.isNullOrBlank()) {
                    val json = JsonParser.parseString(errorBody).asJsonObject
                    return when {
                        json.has("message") -> json.get("message").asString
                        json.has("error") -> json.get("error").asString
                        else -> errorBody
                    }
                }
            } catch (_: Exception) {}
            return "HTTP ${e.code()}: ${e.message()}"
        }
        return e.message ?: "Unknown error"
    }

    suspend fun syncSteps(manual: Boolean = false) {
        val hc = client ?: return
        if (!isAutomaticTimeEnabled(context)) {
            syncResult = "Automatic time is disabled" to null
            autoTimeDisabled = true
            return
        }

        val nowDevice = ZonedDateTime.now(deviceZone)
        val todayStr = nowDevice.format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        val todayStart = nowDevice.toLocalDate().atStartOfDay(deviceZone).toInstant()
        val nowInstant = Instant.now()

        val stepsTodayVal = readStepsForRange(hc, todayStart, nowInstant)
        
        stepsToday = stepsTodayVal
        saveLastKnownSteps(context, stepsTodayVal)

        val selectedServers = getSelectedServers(context)
        if (selectedServers.isEmpty()) {
            syncResult = "No servers selected" to null
            return
        }

        val successServers = mutableListOf<String>()
        val errorGroups = mutableMapOf<String, MutableList<String>>()
        val globalApi = buildApi(BASE_URL, GLOBAL_API_KEY)

        selectedServers.forEach { server ->
            suspend fun getOrRecoverKey(): String? {
                var key = getServerKey(context, mcUsername, server)
                if (key != null) return key
                try {
                    val resp = globalApi.recoverKey(RegisterPayload(mcUsername, deviceId, server))
                    saveServerKey(context, mcUsername, server, resp.player_api_key)
                    return resp.player_api_key
                } catch (e: Exception) {}
                try {
                    val resp = globalApi.register(RegisterPayload(mcUsername, deviceId, server))
                    saveServerKey(context, mcUsername, server, resp.player_api_key)
                    return resp.player_api_key
                } catch (e: Exception) {}
                return null
            }

            suspend fun performIngest(key: String, steps: Long, day: String): Boolean {
                globalApi.ingest(IngestPayload(mcUsername, deviceId, steps, key, day, "health_connect", nowInstant.toString()))
                return true
            }

            try {
                val key = getOrRecoverKey()
                if (key == null) {
                    errorGroups.getOrPut("Could not get API key") { mutableListOf() }.add(server)
                    return@forEach
                }
                
                try {
                    // Sync ONLY today's steps to prevent inheriting yesterday's data on new servers
                    if (performIngest(key, stepsTodayVal, todayStr)) {
                        successServers.add(server)
                    }
                } catch (e: HttpException) {
                    if (e.code() == 401) {
                        try {
                            val resp = globalApi.recoverKey(RegisterPayload(mcUsername, deviceId, server))
                            val newKey = resp.player_api_key
                            if (newKey != null && newKey != key) {
                                saveServerKey(context, mcUsername, server, newKey)
                                if (performIngest(newKey, stepsTodayVal, todayStr)) {
                                    successServers.add(server)
                                    return@forEach
                                }
                            }
                        } catch (e2: Exception) {}
                    }
                    throw e
                }
            } catch (e: Exception) {
                errorGroups.getOrPut(parseErrorMessage(e)) { mutableListOf() }.add(server)
            }
        }

        val successMsg = if (successServers.isNotEmpty()) "Synced to ${successServers.joinToString(", ")}" else null
        val errorMsg = if (errorGroups.isNotEmpty()) {
            errorGroups.entries.joinToString("\n") { (err, srvs) -> "Failed for ${srvs.joinToString(", ")}: $err" }
        } else null
        
        syncResult = successMsg to errorMsg
        val logMessage = when {
            successMsg != null && errorMsg != null -> "Partial: $successMsg | $errorMsg"
            successMsg != null -> successMsg
            else -> errorMsg ?: "Unknown failure"
        }
        
        val nowStr = nowDevice.format(logTimeFormatter)
        addSyncLogEntry(context, SyncLogEntry(nowStr, stepsTodayVal, if (manual) "Manual" else "Auto", successServers.isNotEmpty(), logMessage))
        
        if (successServers.isNotEmpty()) {
            lastSyncInstant = Instant.now()
            refreshClaimStatuses()
            refreshRewardTiers()
        }
    }

    LaunchedEffect(Unit) {
        val applied = applyQueuedUsernameIfPossible(context)
        if (applied != null) mcUsername = applied
        checkAvailability()
        if (client != null && refreshGrantedPermissions()) {
            if (autoSyncEnabled) syncSteps(false)
            else {
                stepsToday = readStepsForRange(client!!, ZonedDateTime.now(deviceZone).toLocalDate().atStartOfDay(deviceZone).toInstant(), Instant.now())
            }
        }
        refreshClaimStatuses()
        refreshRewardTiers()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        val logoRes = remember { context.resources.getIdentifier("ic_custom_foreground", "drawable", context.packageName) }
                        if (logoRes != 0) {
                            Image(painter = painterResource(id = logoRes), contentDescription = "App logo", modifier = Modifier.size(48.dp))
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp).offset(y = 1.dp))
                                Box(modifier = Modifier.size(18.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(MinecraftDirt).padding(1.dp)) {
                                    Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(MinecraftGrass))
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("StepCraft", fontWeight = FontWeight.Black, fontSize = 28.sp, fontFamily = FontFamily.Monospace, letterSpacing = (-1.5).sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    val avatarUrl = remember(mcUsername) {
                        if (!mcUsername.isNullOrBlank()) {
                            val enc = URLEncoder.encode(mcUsername, "UTF-8")
                            "https://minotar.net/avatar/$enc/48"
                        } else null
                    }
                    avatarUrl?.let { url ->
                        Box(modifier = Modifier.padding(start = 12.dp)) {
                            AsyncImage(model = url, contentDescription = "Minecraft avatar", modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(com.example.fitcollector.AppScreen.Settings) }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ResetTimer() }
            
            // Shared animation specs
            val animationSpec = fadeIn() + expandVertically()
            val exitSpec = fadeOut() + shrinkVertically()

            // 1. Unclaimed Rewards
            val unclaimedServersWithSteps = claimStatuses.filter { !it.value.claimed && (yesterdaySteps[it.key] ?: 0L) > 0 }
            item {
                AnimatedVisibility(
                    visible = unclaimedServersWithSteps.isNotEmpty(),
                    enter = animationSpec,
                    exit = exitSpec
                ) {
                    Surface(color = Color(0xFFFFF9C4), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🎁", fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Unclaimed rewards for yesterday's steps:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF574300), fontWeight = FontWeight.Bold)
                                unclaimedServersWithSteps.forEach { (server, _) ->
                                    val steps = yesterdaySteps[server] ?: 0L
                                    Text("• $server: $steps steps", style = MaterialTheme.typography.bodySmall, color = Color(0xFF574300))
                                }
                                Text("Join the server to claim rewards!", style = MaterialTheme.typography.labelSmall, color = Color(0xFF574300), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. Claimed Rewards
            val claimedServersWithSteps = claimStatuses.filter { it.value.claimed && (yesterdaySteps[it.key] ?: 0L) > 0 }
            item {
                AnimatedVisibility(
                    visible = claimedServersWithSteps.isNotEmpty(),
                    enter = animationSpec,
                    exit = exitSpec
                ) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🎉", fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Rewards claimed for yesterday's steps:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                claimedServersWithSteps.forEach { (server, _) ->
                                    val steps = yesterdaySteps[server] ?: 0L
                                    Text("• $server: $steps steps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                }
            }

            item {
                ActivityCard(stepsToday = stepsToday, isSyncEnabled = client != null && hasPerms && mcUsername.isNotBlank() && getSelectedServers(context).isNotEmpty() && !autoTimeDisabled, onSyncClick = {
                    scope.launch { syncSteps(true) }
                })
            }
            
            blockedSourcesWarning?.let { sources ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onNavigate(com.example.fitcollector.AppScreen.Settings) }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Step data found from ${sources.joinToString(", ")}, but it's not enabled in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(visible = syncResult != null) {
                    syncResult?.let { (successMsg, errorMsg) ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (successMsg != null) SyncStatusBanner(msg = successMsg, isSuccess = true, timestamp = lastSyncInstant)
                            if (errorMsg != null) SyncStatusBanner(msg = errorMsg, isSuccess = false, timestamp = null)
                        }
                    }
                }
            }
            item {
                val trackedList = trackedTiers.entries.toList()
                val currentSteps = stepsToday ?: 0L
                AnimatedVisibility(
                    visible = trackedList.isNotEmpty(),
                    enter = animationSpec,
                    exit = exitSpec
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1B3A20), Color(0xFF0F2414))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👣", fontSize = 22.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Tracked milestones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE8F5E9))
                                    Text("Keep stepping — you’re getting close!", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB7D5BB))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            trackedList.forEach { (server, minSteps) ->
                                val label = rewardTiersByServer[server]?.firstOrNull { it.min_steps == minSteps }?.label
                                    ?: "Milestone"
                                val progress = if (minSteps > 0) (currentSteps.toFloat() / minSteps.toFloat()).coerceIn(0f, 1f) else 0f

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF1D1F1A), Color(0xFF0F120D))
                                            )
                                        )
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("🏁", fontSize = 18.sp)
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "$server · $label",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFEAE7D6)
                                            )
                                            Spacer(Modifier.weight(1f))
                                            if (progress >= 1f) {
                                                Text("✅", fontSize = 18.sp)
                                            } else if (progress >= 0.8f) {
                                                Text("🔥", fontSize = 18.sp)
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(16.dp)
                                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                                .background(Color(0xFF2A3326))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(progress)
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(Color(0xFF7BE07B), Color(0xFF47C1FF))
                                                        )
                                                    )
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.White.copy(alpha = 0.06f))
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text("$currentSteps / $minSteps steps", style = MaterialTheme.typography.bodySmall, color = Color(0xFFBDB7A6))
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
            if (!hasPerms || hcStatus != "Available" || mcUsername.isBlank() || getSelectedServers(context).isEmpty() || autoTimeDisabled) {
                item {
                    val message = when {
                        autoTimeDisabled -> "Automatic Date & Time must be enabled in system settings."
                        hcStatus != "Available" -> "Health Connect is not available on this device."
                        !hasPerms -> "Health Connect permissions are required to read steps."
                        mcUsername.isBlank() -> "Please set your Minecraft username in Settings."
                        getSelectedServers(context).isEmpty() -> "Please select at least one server in Settings."
                        else -> ""
                    }
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable { if (!autoTimeDisabled) onNavigate(com.example.fitcollector.AppScreen.Settings) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            item {
                val selectedCount = getSelectedServers(context).size
                val syncStatus = when {
                    autoSyncEnabled && backgroundSyncEnabled -> "Auto-sync & Background-sync enabled ($selectedCount servers)."
                    autoSyncEnabled -> "Auto-sync enabled ($selectedCount servers)."
                    backgroundSyncEnabled -> "Background-sync enabled."
                    else -> "Sync is disabled."
                }
                Text(syncStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}
