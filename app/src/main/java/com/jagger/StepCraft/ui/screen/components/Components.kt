package com.jagger.StepCraft.ui.screen.components

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagger.StepCraft.SyncLogEntry
import com.jagger.StepCraft.ui.theme.*
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
    fun getEncouragingMessage(steps: Long?): String {
        return when {
            steps == null || steps < 1500 -> listOf(
                "Every step counts! Start with just one.",
                "Let's get moving! You've got this.",
                "Time to earn those rewards!",
                "Your journey begins with a single step.",
                "Small steps lead to big accomplishments.",
                "Come on, you can do it!",
                "Ready to make a difference?",
                "One foot in front of the other!",
                "Get up and go!",
                "You've got the power to move!",
                "Start strong today!",
                "Let's make today count!",
                "Time to shine and step up!",
                "Every movement matters!",
                "You're stronger than you think!"
            ).random()
            steps < 3000 -> listOf(
                "Great start! Keep the momentum going.",
                "You're on your way! Keep going.",
                "Nice work! Keep it up.",
                "Building up those steps steadily!",
                "You're doing well, keep moving!",
                "Getting there! Keep the pace!",
                "Excellent progress so far!",
                "You're on fire today!",
                "Keep crushing those steps!",
                "This is looking great!",
                "You're in the zone!",
                "Stay focused, you're doing amazing!",
                "Momentum is building!",
                "Keep riding this wave!",
                "You've got this energy flowing!"
            ).random()
            steps < 4500 -> listOf(
                "Solid progress! You're crushing it.",
                "Very impressive! Keep the pace.",
                "Making great progress, keep it up!",
                "You're in a great rhythm now!",
                "Outstanding effort so far!",
                "You're a step machine today!",
                "This pace is incredible!",
                "You're really flying now!",
                "Keep that fire burning!",
                "Look at you go!",
                "You're absolutely dominating!",
                "This is impressive work!",
                "Keep up this fantastic energy!",
                "You're unstoppable today!",
                "What a performance so far!"
            ).random()
            steps < 6000 -> listOf(
                "Incredible pace! You're on fire!",
                "Fantastic work—you're a star!",
                "Amazing work! Keep it rolling.",
                "You're an absolute powerhouse!",
                "This is fantastic progress!",
                "You're in overdrive!",
                "Nothing can stop you now!",
                "You're breaking records!",
                "This is legendary work!",
                "You're absolutely crushing it!",
                "Keep this intensity going!",
                "You're a walking achievement!",
                "This pace is unreal!",
                "You're writing your own story!",
                "Keep that excellence flowing!"
            ).random()
            steps < 7500 -> listOf(
                "Nearly there! You're unstoppable!",
                "Phenomenal effort! You're crushing goals!",
                "You're in elite territory now!",
                "This is phenomenal work!",
                "Keep that incredible pace going!",
                "You're on the verge of greatness!",
                "This is next-level amazing!",
                "You're a superstar today!",
                "Keep reaching for that peak!",
                "You're absolutely legendary!",
                "This performance is insane!",
                "You're redefining excellence!",
                "Keep that winning streak alive!",
                "You're absolutely nailing it!",
                "This is record-breaking work!"
            ).random()
            steps < 10000 -> listOf(
                "So close to LEGEND status!",
                "Absolutely legendary performance!",
                "You're rewriting the record books!",
                "This is absolutely incredible!",
                "You're breaking your own limits!",
                "You're almost at LEGEND tier!",
                "This is the pinnacle of excellence!",
                "You're unstoppable, keep going!",
                "One final push to legends!",
                "You're an unstoppable force!",
                "This performance is EPIC!",
                "You're writing history today!",
                "This is pure domination!",
                "Keep that unstoppable energy!",
                "You're seconds away from glory!"
            ).random()
            else -> listOf(
                "10,000+ steps! You're a LEGEND!",
                "Congratulations, you're unstoppable!",
                "You're in a class of your own!",
                "Absolutely LEGENDARY performance!",
                "You've conquered the day!",
                "LEGENDARY! You're unmatched!",
                "You ARE the legend!",
                "Peak performance achieved!",
                "You're the ultimate champion!",
                "This is LEGENDARY status!",
                "You're immortal today!",
                "Hall of fame material right here!",
                "You're a WALKING LEGEND!",
                "This is peak human achievement!",
                "You're in the LEGEND zone!"
            ).random()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.DirectionsRun,
                        null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Steps Today",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = stepsToday?.let { String.format("%,d", it) } ?: "--",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 56.sp,
                        letterSpacing = (-2).sp
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                
                Text(
                    getEncouragingMessage(stepsToday),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(Modifier.height(32.dp))
                
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
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "SYNC NOW",
                        fontWeight = FontWeight.Black,
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
    var secondsRemaining by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = ZonedDateTime.now(centralZone)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(centralZone)
            val diff = Duration.between(now, nextMidnight)
            val hours = diff.toHours()
            val minutes = diff.toMinutes() % 60
            val seconds = diff.seconds % 60
            secondsRemaining = diff.seconds
            timeRemaining = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            delay(1000)
        }
    }

    // Color gradient function: blue -> orange -> red based on 6 hours
    fun getTimerColor(): Color {
        val maxSeconds = (6 * 60 * 60).toLong() // 6 hours in seconds
        val progress = (maxSeconds - secondsRemaining).coerceIn(0L, maxSeconds).toFloat() / maxSeconds
        
        return when {
            progress < 0.5f -> {
                // Blue to Orange (0 to 0.5)
                val t = progress * 2 // 0 to 1
                val blueR = 33f / 255f
                val blueG = 150f / 255f
                val blueB = 243f / 255f
                val orangeR = 255f / 255f
                val orangeG = 152f / 255f
                val orangeB = 0f / 255f
                
                Color(
                    red = (blueR + (orangeR - blueR) * t),
                    green = (blueG + (orangeG - blueG) * t),
                    blue = (blueB + (orangeB - blueB) * t)
                )
            }
            else -> {
                // Orange to Red (0.5 to 1)
                val t = (progress - 0.5f) * 2 // 0 to 1
                val orangeR = 255f / 255f
                val orangeG = 152f / 255f
                val orangeB = 0f / 255f
                val redR = 244f / 255f
                val redG = 67f / 255f
                val redB = 54f / 255f
                
                Color(
                    red = (orangeR + (redR - orangeR) * t),
                    green = (orangeG + (redG - orangeG) * t),
                    blue = (orangeB + (redB - orangeB) * t)
                )
            }
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
                "Time Until Daily Step Reset: ",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                timeRemaining,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = getTimerColor(),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
