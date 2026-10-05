package com.roambridge.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roambridge.app.data.classifier.SmsTopic
import com.roambridge.app.data.db.MessageDirection
import com.roambridge.app.data.db.SmsLogEntity
import com.roambridge.app.data.repository.SettingsRepository
import com.roambridge.app.data.repository.SmsRepository
import com.roambridge.app.service.BridgeForegroundService
import com.roambridge.app.ui.components.StatusBadge
import com.roambridge.app.ui.components.TopicBadge
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    smsRepo: SmsRepository,
    settingsRepo: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs by smsRepo.getAllLogsFlow().collectAsState(initial = emptyList())
    val settings by settingsRepo.settingsFlow.collectAsState()

    val totalLogs = logs.size
    val otpCount = logs.count { it.topic == SmsTopic.CRITICAL_OTP }
    val outboundCount = logs.count { it.direction == MessageDirection.OUTBOUND }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "RoamBridge Dashboard",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { scope.launch { smsRepo.clearLogs() } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear History")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.bridgeServiceEnabled)
                        Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (settings.bridgeServiceEnabled) Color(0xFF2E7D32) else Color(0xFFC62828))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (settings.bridgeServiceEnabled) "Daemon Running" else "Daemon Paused",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (settings.bridgeServiceEnabled) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                            )
                            Text(
                                text = if (settings.bridgeServiceEnabled) "Relaying SMS & Commands in Real-Time" else "Tap toggle to activate service",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Switch(
                        checked = settings.bridgeServiceEnabled,
                        onCheckedChange = { enabled ->
                            val updated = settings.copy(bridgeServiceEnabled = enabled)
                            settingsRepo.updateSettings(updated)
                            if (enabled) {
                                BridgeForegroundService.start(context)
                            } else {
                                BridgeForegroundService.stop(context)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Total SMS",
                    value = "$totalLogs",
                    icon = Icons.Default.Message,
                    color = Color(0xFF3F51B5),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "OTPs Relayed",
                    value = "$otpCount",
                    icon = Icons.Default.Key,
                    color = Color(0xFFE53935),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Outbound Sent",
                    value = "$outboundCount",
                    icon = Icons.Default.Send,
                    color = Color(0xFF00897B),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Live Relay Activity",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No SMS relayed yet",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Text(
                            "Incoming messages will appear here instantly",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        SmsLogItemCard(log)
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(text = title, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SmsLogItemCard(log: SmsLogEntity) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    val timeStr = dateFormat.format(Date(log.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopicBadge(topic = log.topic)
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (log.direction == MessageDirection.INBOUND) "From: " else "To: ",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Text(
                    text = if (log.direction == MessageDirection.INBOUND) log.sender else (log.recipient ?: "Unknown"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            if (log.extractedOtp != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFEBEE), shape = RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🔑 OTP: ${log.extractedOtp}",
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = log.body,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadge(label = "Telegram", status = log.telegramStatus)
                StatusBadge(label = "Email", status = log.emailStatus)
            }

            if (log.errorMessage != null && (log.telegramStatus == com.roambridge.app.data.db.RelayStatus.FAILED || log.emailStatus == com.roambridge.app.data.db.RelayStatus.FAILED)) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠️ ${log.errorMessage}",
                    fontSize = 11.sp,
                    color = Color(0xFFC62828)
                )
            }
        }
    }
}
