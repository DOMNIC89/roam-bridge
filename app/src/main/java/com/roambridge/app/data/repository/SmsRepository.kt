package com.roambridge.app.data.repository

import android.content.Context
import android.util.Log
import com.roambridge.app.data.classifier.SmsClassifier
import com.roambridge.app.data.classifier.SmsTopic
import com.roambridge.app.data.db.AppDatabase
import com.roambridge.app.data.db.MessageDirection
import com.roambridge.app.data.db.RelayStatus
import com.roambridge.app.data.db.SmsLogEntity
import com.roambridge.app.data.model.AppSettings
import com.roambridge.app.data.network.SmtpSender
import com.roambridge.app.data.network.TelegramService
import com.roambridge.app.service.SmsOutboxManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SmsRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.smsLogDao()
    private val telegramService = TelegramService()
    private val smtpSender = SmtpSender()
    private val outboxManager = SmsOutboxManager(context)
    private val settingsRepo = SettingsRepository(context)

    fun getAllLogsFlow(): Flow<List<SmsLogEntity>> = dao.getAllLogsFlow()

    fun getRelayedCountFlow(since: Long): Flow<Int> = dao.getMessageCountSinceFlow(since)

    suspend fun clearLogs() = dao.clearAll()

    /**
     * Processes an incoming SMS intercepted by the BroadcastReceiver.
     */
    suspend fun processInboundSms(sender: String, body: String, timestamp: Long) {
        val settings = settingsRepo.getSettings()
        val classification = SmsClassifier.classify(sender, body)

        Log.d(TAG, "Inbound SMS classified as ${classification.topic.name}: $body")

        // Check Spam Filter
        if (classification.topic == SmsTopic.SPAM_PROMO && settings.spamFilterDrop) {
            val entity = SmsLogEntity(
                sender = sender,
                body = body,
                direction = MessageDirection.INBOUND,
                topic = classification.topic,
                extractedOtp = classification.extractedOtp,
                timestamp = timestamp,
                telegramStatus = RelayStatus.SKIPPED,
                emailStatus = RelayStatus.SKIPPED,
                errorMessage = "Filtered: Spam/Promotional rule"
            )
            dao.insert(entity)
            return
        }

        val forwardToTelegram = settings.telegramEnabled && shouldForwardTelegram(classification.topic, settings)
        val forwardToEmail = settings.smtpEnabled && shouldForwardEmail(classification.topic, settings)

        var tgStatus = if (forwardToTelegram) RelayStatus.PENDING else RelayStatus.SKIPPED
        var emailStatus = if (forwardToEmail) RelayStatus.PENDING else RelayStatus.SKIPPED
        var errorMsg: String? = null

        // Forward to Telegram
        if (forwardToTelegram) {
            val tgResult = telegramService.forwardSms(
                botToken = settings.telegramBotToken,
                chatId = settings.telegramChatId,
                sender = sender,
                body = body,
                classification = classification,
                timestamp = timestamp
            )
            if (tgResult.isSuccess) {
                tgStatus = RelayStatus.SENT
            } else {
                tgStatus = RelayStatus.FAILED
                errorMsg = "Telegram: " + tgResult.exceptionOrNull()?.message
            }
        }

        // Forward to Email
        if (forwardToEmail) {
            val emailResult = smtpSender.forwardSms(
                settings = settings,
                sender = sender,
                body = body,
                classification = classification,
                timestamp = timestamp
            )
            if (emailResult.isSuccess) {
                emailStatus = RelayStatus.SENT
            } else {
                emailStatus = RelayStatus.FAILED
                errorMsg = (errorMsg?.plus(" | ") ?: "") + ("Email: " + emailResult.exceptionOrNull()?.message)
            }
        }

        // Save log in database
        val entity = SmsLogEntity(
            sender = sender,
            body = body,
            direction = MessageDirection.INBOUND,
            topic = classification.topic,
            extractedOtp = classification.extractedOtp,
            timestamp = timestamp,
            telegramStatus = tgStatus,
            emailStatus = emailStatus,
            errorMessage = errorMsg
        )
        dao.insert(entity)
    }

    /**
     * Executes a Reverse SMS command from the local SIM.
     */
    suspend fun executeReverseSms(recipient: String, messageText: String, requestedBy: String): Result<Boolean> {
        val sendResult = outboxManager.sendSms(recipient, messageText)
        val isSuccess = sendResult.isSuccess
        val settings = settingsRepo.getSettings()

        val entity = SmsLogEntity(
            sender = "Home Device",
            recipient = recipient,
            body = messageText,
            direction = MessageDirection.OUTBOUND,
            topic = SmsTopic.PERSONAL,
            timestamp = System.currentTimeMillis(),
            telegramStatus = if (isSuccess) RelayStatus.SENT else RelayStatus.FAILED,
            emailStatus = RelayStatus.SKIPPED,
            errorMessage = if (isSuccess) "Sent via $requestedBy" else sendResult.exceptionOrNull()?.message
        )
        dao.insert(entity)

        // Send delivery receipt back to Telegram if enabled
        if (settings.telegramEnabled && settings.telegramBotToken.isNotBlank() && settings.telegramChatId.isNotBlank()) {
            val receipt = if (isSuccess) {
                "📤 <b>SMS Sent Successfully from Home SIM</b>\n━━━━━━━━━━━━━━━━━━━\n" +
                        "📱 <b>To:</b> <code>$recipient</code>\n" +
                        "💬 <b>Message:</b>\n<pre>$messageText</pre>\n" +
                        "👤 <i>Triggered by: $requestedBy</i>"
            } else {
                "❌ <b>Failed to send SMS to $recipient</b>\nError: ${sendResult.exceptionOrNull()?.message}"
            }
            telegramService.sendMessage(settings.telegramBotToken, settings.telegramChatId, receipt)
        }

        return sendResult
    }

    private fun shouldForwardTelegram(topic: SmsTopic, settings: AppSettings): Boolean {
        return when (topic) {
            SmsTopic.CRITICAL_OTP -> settings.otpForwardTelegram
            SmsTopic.BANKING_FINANCE -> settings.bankingForwardTelegram
            SmsTopic.GOV_UTILITY -> settings.utilityForwardTelegram
            SmsTopic.PERSONAL -> settings.personalForwardTelegram
            SmsTopic.SPAM_PROMO -> false
        }
    }

    private fun shouldForwardEmail(topic: SmsTopic, settings: AppSettings): Boolean {
        return when (topic) {
            SmsTopic.CRITICAL_OTP -> settings.otpForwardEmail
            SmsTopic.BANKING_FINANCE -> settings.bankingForwardEmail
            SmsTopic.GOV_UTILITY -> settings.utilityForwardEmail
            SmsTopic.PERSONAL -> settings.personalForwardEmail
            SmsTopic.SPAM_PROMO -> false
        }
    }

    companion object {
        private const val TAG = "SmsRepository"
    }
}
