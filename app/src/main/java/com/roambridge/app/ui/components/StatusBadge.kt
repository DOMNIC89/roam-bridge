package com.roambridge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roambridge.app.data.classifier.SmsTopic
import com.roambridge.app.data.db.RelayStatus
import com.roambridge.app.ui.theme.BankBlue
import com.roambridge.app.ui.theme.OtpRed
import com.roambridge.app.ui.theme.PersonalPurple
import com.roambridge.app.ui.theme.SpamGray
import com.roambridge.app.ui.theme.UtilityGreen

@Composable
fun TopicBadge(topic: SmsTopic, modifier: Modifier = Modifier) {
    val (color, emoji) = when (topic) {
        SmsTopic.CRITICAL_OTP -> Pair(OtpRed, "🚨")
        SmsTopic.BANKING_FINANCE -> Pair(BankBlue, "💳")
        SmsTopic.GOV_UTILITY -> Pair(UtilityGreen, "🏛️")
        SmsTopic.PERSONAL -> Pair(PersonalPurple, "💬")
        SmsTopic.SPAM_PROMO -> Pair(SpamGray, "📢")
    }

    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$emoji ${topic.displayName}",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StatusBadge(label: String, status: RelayStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status) {
        RelayStatus.SENT -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        RelayStatus.FAILED -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
        RelayStatus.PENDING -> Pair(Color(0xFFFFF8E1), Color(0xFFF57F17))
        RelayStatus.SKIPPED -> Pair(Color(0xFFF5F5F5), Color(0xFF757575))
    }

    Box(
        modifier = modifier
            .background(bgColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "$label: ${status.name}",
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
