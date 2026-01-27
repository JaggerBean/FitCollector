package com.example.fitcollector
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith


import androidx.compose.foundation.layout.fillMaxSize

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
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

        requestPermissions =
            registerForActivityResult(
                PermissionController.createRequestPermissionResultContract()
            ) { /* result handled by re-check */ }

        // Schedule background sync
        SyncWorker.schedule(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        setContent {
            val context = LocalContext.current
            val themeMode = remember { mutableStateOf(getThemeMode(context)) }

            LaunchedEffect(Unit) {
                while(true) {
                    val current = getThemeMode(context)
                    if (themeMode.value != current) {
                        themeMode.value = current
                    }
                    kotlinx.coroutines.delay(500)
                }
            }

            FitCollectorTheme(themeMode = themeMode.value) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
