package com.example.fitcollector.ui.screen.components

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitcollector.SyncLogEntry
import com.example.fitcollector.ui.theme.*
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun SyncStatusBanner(msg: String, isSuccess: Boolean, timestamp: Instant? = null) {
    val bgColor = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

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
                        Icons.Default.Refresh,
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (entry.success) HealthGreen else MaterialTheme.colorScheme.error)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    entry.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ResetTimer() {
    val centralZone = ZoneId.of("America/Chicago")
    var timeRemaining by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = ZonedDateTime.now(centralZone)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(centralZone)
            val diff = Duration.between(now, nextMidnight)
            val hours = diff.toHours()
            val minutes = diff.toMinutes() % 60
            val seconds = diff.seconds % 60
            timeRemaining = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            delay(1000)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Time Until Reset: ",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                timeRemaining,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
