package com.roambridge.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.roambridge.app.data.classifier.SmsTopic

enum class MessageDirection {
    INBOUND,
    OUTBOUND
}

enum class RelayStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED
}

@Entity(tableName = "sms_logs")
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val recipient: String? = null,
    val body: String,
    val direction: MessageDirection = MessageDirection.INBOUND,
    val topic: SmsTopic = SmsTopic.PERSONAL,
    val extractedOtp: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val telegramStatus: RelayStatus = RelayStatus.SKIPPED,
    val emailStatus: RelayStatus = RelayStatus.SKIPPED,
    val errorMessage: String? = null
)
