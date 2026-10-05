package com.roambridge.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roambridge.app.data.model.AppSettings
import com.roambridge.app.data.network.SmtpSender
import com.roambridge.app.data.network.TelegramService
import com.roambridge.app.data.repository.SettingsRepository
import com.roambridge.app.util.PermissionHelper
import com.roambridge.app.util.SimHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepo.settingsFlow.collectAsState()

    var currentSettings by remember(settings) { mutableStateOf(settings) }
    var isTestingTelegram by remember { mutableStateOf(false) }
    var isTestingSmtp by remember { mutableStateOf(false) }

    fun save(updated: AppSettings) {
        currentSettings = updated
        settingsRepo.updateSettings(updated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Credentials", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Telegram Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF29B6F6))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Telegram Bot Relay", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Switch(
                            checked = currentSettings.telegramEnabled,
                            onCheckedChange = { save(currentSettings.copy(telegramEnabled = it)) }
                        )
                    }

                    if (currentSettings.telegramEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = currentSettings.telegramBotToken,
                            onValueChange = { save(currentSettings.copy(telegramBotToken = it)) },
                            label = { Text("Bot Token") },
                            placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentSettings.telegramChatId,
                            onValueChange = { save(currentSettings.copy(telegramChatId = it)) },
                            label = { Text("Authorized Chat ID") },
                            placeholder = { Text("e.g. 987654321") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isTestingTelegram = true
                                    val result = TelegramService().testConnection(
                                        currentSettings.telegramBotToken,
                                        currentSettings.telegramChatId
                                    )
                                    isTestingTelegram = false
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Telegram test succeeded!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isTestingTelegram && currentSettings.telegramBotToken.isNotBlank() && currentSettings.telegramChatId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTestingTelegram) {
                                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                            } else {
                                Text("Send Test Message to Telegram")
                            }
                        }
                    }
                }
            }

            // Direct SMTP Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFFEA4335))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Direct SMTP Email", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Switch(
                            checked = currentSettings.smtpEnabled,
                            onCheckedChange = { save(currentSettings.copy(smtpEnabled = it)) }
                        )
                    }

                    if (currentSettings.smtpEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = currentSettings.smtpHost,
                                onValueChange = { save(currentSettings.copy(smtpHost = it)) },
                                label = { Text("SMTP Host") },
                                modifier = Modifier.weight(2f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = currentSettings.smtpPort.toString(),
                                onValueChange = {
                                    val port = it.toIntOrNull() ?: 465
                                    save(currentSettings.copy(smtpPort = port))
                                },
                                label = { Text("Port") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentSettings.smtpUsername,
                            onValueChange = { save(currentSettings.copy(smtpUsername = it)) },
                            label = { Text("Sender Email (Username)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentSettings.smtpPassword,
                            onValueChange = { save(currentSettings.copy(smtpPassword = it)) },
                            label = { Text("App Password / Token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentSettings.destinationEmail,
                            onValueChange = { save(currentSettings.copy(destinationEmail = it)) },
                            label = { Text("Forwarding Destination Email") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isTestingSmtp = true
                                    val result = SmtpSender().testConnection(currentSettings)
                                    isTestingSmtp = false
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "SMTP test email sent!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isTestingSmtp && currentSettings.destinationEmail.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTestingSmtp) {
                                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                            } else {
                                Text("Send Test Email via SMTP")
                            }
                        }
                    }
                }
            }

            // Reverse SMS & Security
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00897B))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reverse SMS (Email-to-SIM)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Switch(
                            checked = currentSettings.reverseSmsEnabled,
                            onCheckedChange = { save(currentSettings.copy(reverseSmsEnabled = it)) }
                        )
                    }

                    if (currentSettings.reverseSmsEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = currentSettings.reverseSmsPin,
                            onValueChange = { save(currentSettings.copy(reverseSmsPin = it)) },
                            label = { Text("Secret Authorization PIN") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentSettings.imapHost,
                            onValueChange = { save(currentSettings.copy(imapHost = it)) },
                            label = { Text("IMAP Server Host") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentSettings.imapUsername,
                            onValueChange = { save(currentSettings.copy(imapUsername = it)) },
                            label = { Text("IMAP Username / Email") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentSettings.imapPassword,
                            onValueChange = { save(currentSettings.copy(imapPassword = it)) },
                            label = { Text("IMAP App Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                }
            }

            // ── SIM / Number Filter ─────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SimCard,
                            contentDescription = null,
                            tint = Color(0xFF7C4DFF)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "SIM / Number Filter",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Forward only from selected SIMs. Leave all unchecked to forward from every SIM.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Detected SIM slots
                    val detectedSims = remember { SimHelper.getActiveSims(context) }
                    if (detectedSims.isNotEmpty()) {
                        Text(
                            "Detected SIMs",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        detectedSims.forEach { sim ->
                            val chipLabel = buildString {
                                append("SIM ${sim.slotIndex + 1}")
                                if (sim.displayName.isNotBlank()) append(" · ${sim.displayName}")
                                sim.phoneNumber?.let { append(" · $it") }
                            }
                            val number = sim.phoneNumber ?: "sim_slot_${sim.slotIndex}"
                            val selected = currentSettings.trackedPhoneNumbers.contains(number)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = if (selected)
                                        currentSettings.trackedPhoneNumbers - number
                                    else
                                        currentSettings.trackedPhoneNumbers + number
                                    save(currentSettings.copy(trackedPhoneNumbers = updated))
                                },
                                label = { Text(chipLabel, fontSize = 13.sp) },
                                leadingIcon = if (selected) {{
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.then(Modifier)
                                    )
                                }} else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF7C4DFF).copy(alpha = 0.15f),
                                    selectedLabelColor = Color(0xFF7C4DFF)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Manual number entry
                    var manualNumber by remember { mutableStateOf("") }
                    Text(
                        "Add number manually",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualNumber,
                            onValueChange = { manualNumber = it },
                            label = { Text("Phone number") },
                            placeholder = { Text("+1 555 000 1234") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val trimmed = manualNumber.trim()
                                if (trimmed.isNotBlank()) {
                                    save(currentSettings.copy(
                                        trackedPhoneNumbers = currentSettings.trackedPhoneNumbers + trimmed
                                    ))
                                    manualNumber = ""
                                }
                            },
                            enabled = manualNumber.isNotBlank()
                        ) { Text("Add") }
                    }

                    // Tracked number chips (manually added or auto-detected)
                    val manuallyAdded = currentSettings.trackedPhoneNumbers.filter { n ->
                        detectedSims.none { it.phoneNumber == n }
                    }
                    if (manuallyAdded.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Manually added",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        manuallyAdded.forEach { number ->
                            AssistChip(
                                onClick = {
                                    save(currentSettings.copy(
                                        trackedPhoneNumbers = currentSettings.trackedPhoneNumbers - number
                                    ))
                                },
                                label = { Text(number, fontSize = 13.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFF7C4DFF).copy(alpha = 0.10f),
                                    labelColor = Color(0xFF7C4DFF)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    if (currentSettings.trackedPhoneNumbers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "✓ Forwarding only from ${currentSettings.trackedPhoneNumbers.size} number(s)",
                            fontSize = 12.sp,
                            color = Color(0xFF7C4DFF),
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Forwarding from all SIMs (no filter active)",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            // ────────────────────────────────────────────────────────────────────

            // Device Reliability & Battery
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = Color(0xFFF57F17))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Daemon & Battery Optimization", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "To prevent Android from killing the background relay when idle for days, exclude RoamBridge from battery optimizations.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { PermissionHelper.requestIgnoreBatteryOptimizations(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable Battery Optimization")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Daily Heartbeat Report", fontSize = 14.sp)
                        Switch(
                            checked = currentSettings.heartbeatDailyEnabled,
                            onCheckedChange = { save(currentSettings.copy(heartbeatDailyEnabled = it)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
