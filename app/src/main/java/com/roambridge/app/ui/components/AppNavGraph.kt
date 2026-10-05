package com.roambridge.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    OUTBOX("Reverse SMS", Icons.Default.Outbox),
    RULES("Rules", Icons.Default.Rule),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun BottomNavBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    NavigationBar {
        Screen.entries.forEach { screen ->
            NavigationBarItem(
                selected = currentScreen == screen,
                onClick = { onScreenSelected(screen) },
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) }
            )
        }
    }
}
