package com.roambridge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.roambridge.app.data.repository.SettingsRepository
import com.roambridge.app.data.repository.SmsRepository
import com.roambridge.app.service.BridgeForegroundService
import com.roambridge.app.ui.components.BottomNavBar
import com.roambridge.app.ui.components.Screen
import com.roambridge.app.ui.screens.DashboardScreen
import com.roambridge.app.ui.screens.OutboxScreen
import com.roambridge.app.ui.screens.RulesScreen
import com.roambridge.app.ui.screens.SettingsScreen
import com.roambridge.app.ui.theme.RoamBridgeTheme
import com.roambridge.app.util.PermissionHelper

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepo = SettingsRepository(applicationContext)
        val smsRepo = SmsRepository(applicationContext)

        setContent {
            RoamBridgeTheme {
                MainAppContainer(settingsRepo, smsRepo)
            }
        }
    }
}

@Composable
fun MainAppContainer(
    settingsRepo: SettingsRepository,
    smsRepo: SmsRepository
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            val settings = settingsRepo.getSettings()
            if (settings.bridgeServiceEnabled) {
                BridgeForegroundService.start(context)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!PermissionHelper.hasAllPermissions(context)) {
            permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavBar(
                currentScreen = currentScreen,
                onScreenSelected = { currentScreen = it }
            )
        }
    ) { innerPadding ->
        Modifier.padding(innerPadding)
        when (currentScreen) {
            Screen.DASHBOARD -> DashboardScreen(smsRepo = smsRepo, settingsRepo = settingsRepo)
            Screen.OUTBOX -> OutboxScreen(smsRepo = smsRepo, settingsRepo = settingsRepo)
            Screen.RULES -> RulesScreen(settingsRepo = settingsRepo)
            Screen.SETTINGS -> SettingsScreen(settingsRepo = settingsRepo)
        }
    }
}
