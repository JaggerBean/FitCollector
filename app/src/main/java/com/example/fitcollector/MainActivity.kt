package com.example.fitcollector
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith


import androidx.compose.foundation.layout.fillMaxSize

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.compose.ui.graphics.Color
import com.example.fitcollector.ui.screen.OnboardingScreen
import com.example.fitcollector.ui.screen.DashboardScreen
import com.example.fitcollector.ui.screen.SettingsScreen
import com.example.fitcollector.ui.screen.ActivityLogScreen
import com.example.fitcollector.ui.screen.components.ActivityCard
import com.example.fitcollector.ui.screen.components.SyncStatusBanner
import com.example.fitcollector.ui.screen.components.LogEntryRow
import com.example.fitcollector.ui.theme.FitCollectorTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier

// Professional Color Palette
private val HealthGreen = Color(0xFF2E7D32)
private val HealthLightGreen = Color(0xFFE8F5E9)
private val HealthBlue = Color(0xFF1565C0)
private val HealthLightBlue = Color(0xFFE3F2FD)
private val MinecraftDirt = Color(0xFF795548)
private val MinecraftGrass = Color(0xFF4CAF50)

enum class AppScreen {
    Onboarding, Dashboard, Settings, Log
}

class MainActivity : ComponentActivity() {

    private lateinit var requestPermissions: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions =
            registerForActivityResult(
                PermissionController.createRequestPermissionResultContract()
            ) { /* result handled by re-check */ }

        // Schedule background sync
        SyncWorker.schedule(this)

        setContent {
            FitCollectorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    var currentScreen by remember { 
                        mutableStateOf(if (isOnboardingComplete(context)) AppScreen.Dashboard else AppScreen.Onboarding) 
                    }
                    
                    val onNavigate = { screen: AppScreen -> currentScreen = screen }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "ScreenTransition"
                    ) { screen ->
                        when (screen) {
                            AppScreen.Onboarding -> OnboardingScreen(
                                requestPermissions = { perms -> requestPermissions.launch(perms) },
                                onComplete = { onNavigate(AppScreen.Dashboard) }
                            )
                            AppScreen.Dashboard -> DashboardScreen(
                                onNavigate = onNavigate
                            )
                            AppScreen.Settings -> SettingsScreen(
                                requestPermissions = { perms -> requestPermissions.launch(perms) },
                                onBack = { onNavigate(AppScreen.Dashboard) }
                            )
                            AppScreen.Log -> ActivityLogScreen(
                                onBack = { onNavigate(AppScreen.Dashboard) }
                            )
                        }
                    }
                }
            }
        }
    }
}
