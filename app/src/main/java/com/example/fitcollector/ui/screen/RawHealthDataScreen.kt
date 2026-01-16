package com.example.fitcollector.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawHealthDataScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var records by remember { mutableStateOf<List<StepsRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var lookbackDays by remember { mutableIntStateOf(0) }
    
    val centralZone = remember { ZoneId.of("America/Chicago") }
    val deviceZone = remember { ZoneId.systemDefault() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss").withZone(deviceZone) }
    
    var debugQueryRange by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)

    LaunchedEffect(lookbackDays) {
        isLoading = true
        try {
            val hc = HealthConnectClient.getOrCreate(context)
            
            // We use the device's local timezone to determine the start of "today" for the query,
            // but the backend will still interpret the day parameter relative to Central Time.
            val nowDevice = ZonedDateTime.now(deviceZone)
            
            val start = when(lookbackDays) {
                0 -> nowDevice.toLocalDate().atStartOfDay(deviceZone).toInstant()
                1 -> Instant.now().minus(Duration.ofHours(24))
                else -> Instant.now().minus(Duration.ofDays(lookbackDays.toLong()))
            }
            val end = Instant.now()
            
            debugQueryRange = "Range (Local): ${timeFormatter.format(start)} to ${timeFormatter.format(end)}"

            val allRecords = mutableListOf<StepsRecord>()
            var pageToken: String? = null
            
            do {
                val response = hc.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageToken = pageToken
                    )
                )
                allRecords.addAll(response.records)
                pageToken = response.pageToken
            } while (pageToken != null)

            records = allRecords.sortedByDescending { it.startTime }
        } catch (e: Exception) {
            debugQueryRange = "Error: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Data Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Filter Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = lookbackDays == 0,
                    onClick = { lookbackDays = 0 },
                    label = { Text("Today") }
                )
                FilterChip(
                    selected = lookbackDays == 1,
                    onClick = { lookbackDays = 1 },
                    label = { Text("24 Hours") }
                )
                FilterChip(
                    selected = lookbackDays == 7,
                    onClick = { lookbackDays = 7 },
                    label = { Text("7 Days") }
                )
            }

            Text(
                text = debugQueryRange,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            if (isLoading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (records.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("No records found for this period.", color = Color.Gray)
                        Text("Check permissions in Samsung Health.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            } else {
                val appData = records.groupBy { it.metadata.dataOrigin.packageName }
                    .mapValues { entry -> 
                        val totalSteps = entry.value.sumOf { it.count }
                        val recordCount = entry.value.size
                        totalSteps to recordCount
                    }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("App Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                appData.forEach { (pkg, data) ->
                                    val (steps, count) = data
                                    Column(Modifier.padding(vertical = 4.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(pkg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            Text("$steps steps", fontWeight = FontWeight.Bold)
                                        }
                                        Text("$count individual records", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }

                    items(records) { record ->
                        RawRecordCard(record, timeFormatter)
                    }
                }
            }
        }
    }
}

@Composable
fun RawRecordCard(record: StepsRecord, formatter: DateTimeFormatter) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${record.count} Steps", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatter.format(record.startTime),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Source: ${record.metadata.dataOrigin.packageName}", style = MaterialTheme.typography.bodySmall)
            Text("Method: ${getRecordingMethodName(record.metadata.recordingMethod)}", style = MaterialTheme.typography.bodySmall)
            
            val deviceName = record.metadata.device?.model ?: "Unknown Device"
            Text("Device: $deviceName", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

fun getRecordingMethodName(method: Int): String {
    return when (method) {
        0 -> "Unknown"
        1 -> "Automatically Recorded"
        2 -> "Manual Entry"
        3 -> "Active Session"
        else -> "Code: $method"
    }
}
