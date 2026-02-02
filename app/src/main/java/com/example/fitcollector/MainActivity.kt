package com.example.fitcollector
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith


import androidx.compose.foundation.layout.fillMaxSize

import android.os.Bundle
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import com.example.fitcollector.ui.screen.OnboardingScreen
import com.example.fitcollector.ui.screen.DashboardScreen
import com.example.fitcollector.ui.screen.SettingsScreen
import com.example.fitcollector.ui.screen.ActivityLogScreen
import com.example.fitcollector.ui.screen.RawHealthDataScreen
import com.example.fitcollector.ui.theme.FitCollectorTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier

enum class AppScreen {
    Onboarding, Dashboard, Settings, Log, RawHealth
}

class MainActivity : ComponentActivity() {

    private lateinit var requestPermissions: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureInstallState(this)
        applyQueuedUsernameIfPossible(this)

        requestPermissions =
            registerForActivityResult(
                PermissionController.createRequestPermissionResultContract()
            ) { /* result handled by re-check */ }

        // Schedule background sync
        SyncWorker.schedule(this)

        setContent {
            val context = LocalContext.current
            FitCollectorTheme(themeMode = "System") {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val shouldForceOnboarding = remember {
                        !isOnboardingComplete(context)
                    }

                    var currentScreen by remember {
                        mutableStateOf(if (shouldForceOnboarding) AppScreen.Onboarding else AppScreen.Dashboard)
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
                                onBack = { onNavigate(AppScreen.Dashboard) },
                                onNavigateToRawHealth = { onNavigate(AppScreen.RawHealth) },
                                onNavigateToLog = { onNavigate(AppScreen.Log) }
                            )
                            AppScreen.Log -> ActivityLogScreen(
                                onBack = { onNavigate(AppScreen.Dashboard) }
                            )
                            AppScreen.RawHealth -> RawHealthDataScreen(
                                onBack = { onNavigate(AppScreen.Settings) }
                            )
                        }
                    }
                }
            }
        }
    }
}
