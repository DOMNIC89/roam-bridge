package com.roambridge.app.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roambridge.app.data.db.MessageDirection
import com.roambridge.app.data.repository.SettingsRepository
import com.roambridge.app.data.repository.SmsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboxScreen(
    smsRepo: SmsRepository,
    settingsRepo: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs by smsRepo.getAllLogsFlow().collectAsState(initial = emptyList())
    val settings by settingsRepo.settingsFlow.collectAsState()

    val outboundLogs = logs.filter { it.direction == MessageDirection.OUTBOUND }

    var recipientNumber by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reverse SMS Outbox", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00796B))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remote Trigger Cheatsheet", fontWeight = FontWeight.Bold, color = Color(0xFF004D40))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "1. Telegram: Send `/sms <number> <message>` to your bot.\n" +
                                "2. Email: Send to your mailbox with subject `[SEND-SMS: ${settings.reverseSmsPin}]`\n" +
                                "   Body: `TO: +1234567890` & `MSG: Your text`",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF004D40)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Direct Test Dispatch Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Manual Outbound Test", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = recipientNumber,
                        onValueChange = { recipientNumber = it },
                        label = { Text("Recipient Phone Number") },
                        placeholder = { Text("+1234567890") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = { Text("SMS Message Content") },
                        placeholder = { Text("Type SMS content to send from this SIM...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isSending = true
                                val result = smsRepo.executeReverseSms(recipientNumber, messageText, "Manual App UI")
                                isSending = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, "SMS dispatched successfully!", Toast.LENGTH_SHORT).show()
                                    messageText = ""
                                } else {
                                    Toast.makeText(context, "Send failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isSending && recipientNumber.isNotBlank() && messageText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.padding(2.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send SMS via Home SIM")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("Outbound SMS Logs (${outboundLogs.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(6.dp))

            if (outboundLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No outbound SMS messages recorded yet.", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(outboundLogs, key = { it.id }) { log ->
                        SmsLogItemCard(log)
                    }
                }
            }
        }
    }
}
