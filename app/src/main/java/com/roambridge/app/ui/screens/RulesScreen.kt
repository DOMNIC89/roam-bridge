package com.roambridge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roambridge.app.data.classifier.SmsTopic
import com.roambridge.app.data.model.AppSettings
import com.roambridge.app.data.repository.SettingsRepository
import com.roambridge.app.ui.components.TopicBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    settingsRepo: SettingsRepository
) {
    val settings by settingsRepo.settingsFlow.collectAsState()

    fun update(updated: AppSettings) {
        settingsRepo.updateSettings(updated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Topic Routing Rules", fontWeight = FontWeight.Bold) }
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
            Text(
                "Configure how incoming SMS are classified and dispatched based on topic.",
                fontSize = 13.sp,
                color = Color.Gray
            )

            // Critical OTP Rule Card
            TopicRuleCard(
                topic = SmsTopic.CRITICAL_OTP,
                description = "One-Time Passwords, 2FA tokens, and authentication codes.",
                tgChecked = settings.otpForwardTelegram,
                onTgChange = { update(settings.copy(otpForwardTelegram = it)) },
                emailChecked = settings.otpForwardEmail,
                onEmailChange = { update(settings.copy(otpForwardEmail = it)) }
            )

            // Banking Rule Card
            TopicRuleCard(
                topic = SmsTopic.BANKING_FINANCE,
                description = "Account debits, credits, card swipes, balance alerts, and transactions.",
                tgChecked = settings.bankingForwardTelegram,
                onTgChange = { update(settings.copy(bankingForwardTelegram = it)) },
                emailChecked = settings.bankingForwardEmail,
                onEmailChange = { update(settings.copy(bankingForwardEmail = it)) }
            )

            // Gov & Utility Rule Card
            TopicRuleCard(
                topic = SmsTopic.GOV_UTILITY,
                description = "Electricity, broadband bills, tax alerts, and official government notices.",
                tgChecked = settings.utilityForwardTelegram,
                onTgChange = { update(settings.copy(utilityForwardTelegram = it)) },
                emailChecked = settings.utilityForwardEmail,
                onEmailChange = { update(settings.copy(utilityForwardEmail = it)) }
            )

            // Personal Rule Card
            TopicRuleCard(
                topic = SmsTopic.PERSONAL,
                description = "Direct text messages from friends, family, and standard phone numbers.",
                tgChecked = settings.personalForwardTelegram,
                onTgChange = { update(settings.copy(personalForwardTelegram = it)) },
                emailChecked = settings.personalForwardEmail,
                onEmailChange = { update(settings.copy(personalForwardEmail = it)) }
            )

            // Spam Filter
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
                        Column(modifier = Modifier.weight(1f)) {
                            TopicBadge(topic = SmsTopic.SPAM_PROMO)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Suppress & Drop Marketing SMS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Silently drops promotional offers, loan ads, and casino marketing.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = settings.spamFilterDrop,
                            onCheckedChange = { update(settings.copy(spamFilterDrop = it)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun TopicRuleCard(
    topic: SmsTopic,
    description: String,
    tgChecked: Boolean,
    onTgChange: (Boolean) -> Unit,
    emailChecked: Boolean,
    onEmailChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TopicBadge(topic = topic)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = description, fontSize = 12.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(checked = tgChecked, onCheckedChange = onTgChange)
                    Text("Telegram", fontSize = 14.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(checked = emailChecked, onCheckedChange = onEmailChange)
                    Text("Email (SMTP)", fontSize = 14.sp)
                }
            }
        }
    }
}
