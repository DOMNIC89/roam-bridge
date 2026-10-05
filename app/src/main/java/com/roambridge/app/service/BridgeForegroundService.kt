package com.roambridge.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.roambridge.app.MainActivity
import com.roambridge.app.R
import com.roambridge.app.data.network.ImapReceiver
import com.roambridge.app.data.network.TelegramService
import com.roambridge.app.data.repository.SettingsRepository
import com.roambridge.app.data.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BridgeForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var smsRepo: SmsRepository
    private val telegramService = TelegramService()
    private val imapReceiver = ImapReceiver()

    private var telegramPollingJob: Job? = null
    private var imapPollingJob: Job? = null
    private var telegramOffset: Long = 0

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(applicationContext)
        smsRepo = SmsRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        startTelegramPolling()
        startImapPolling()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "BridgeForegroundService destroyed.")
    }

    private fun startTelegramPolling() {
        telegramPollingJob?.cancel()
        telegramPollingJob = serviceScope.launch {
            while (isActive) {
                val settings = settingsRepo.getSettings()
                if (settings.telegramEnabled && settings.telegramBotToken.isNotBlank()) {
                    try {
                        val updates = telegramService.getUpdates(settings.telegramBotToken, telegramOffset)
                        for (update in updates) {
                            telegramOffset = update.updateId + 1

                            // Security check: Only process messages from the authorized Chat ID
                            if (update.chatId == settings.telegramChatId.trim()) {
                                handleTelegramCommand(update.text, settings.telegramBotToken, update.chatId)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Telegram polling error", e)
                        delay(5000)
                    }
                } else {
                    delay(10000)
                }
            }
        }
    }

    private suspend fun handleTelegramCommand(text: String, botToken: String, chatId: String) {
        val trimmed = text.trim()
        when {
            trimmed.startsWith("/sms", ignoreCase = true) || trimmed.startsWith("/send", ignoreCase = true) -> {
                val parts = trimmed.split(Regex("""\s+"""), limit = 3)
                if (parts.size >= 3) {
                    val recipient = parts[1].trim()
                    val smsText = parts[2].trim()
                    telegramService.sendMessage(botToken, chatId, "⏳ <i>Dispatching SMS to $recipient...</i>")
                    smsRepo.executeReverseSms(recipient, smsText, "Telegram /sms command")
                } else {
                    telegramService.sendMessage(
                        botToken,
                        chatId,
                        "⚠️ <b>Invalid Command Format</b>\nUsage: <code>/sms &lt;phone_number&gt; &lt;message&gt;</code>\nExample: <code>/sms +1234567890 Hello World</code>"
                    )
                }
            }
            trimmed.equals("/status", ignoreCase = true) -> {
                val batteryStatus = getBatteryStatus()
                val statusMsg = """
                    📱 <b>RoamBridge Home Device Status</b>
                    ━━━━━━━━━━━━━━━━━━━
                    🔋 <b>Battery:</b> $batteryStatus
                    🟢 <b>Service Daemon:</b> Active & Running
                    ✉️ <b>Reverse SMS:</b> Ready
                """.trimIndent()
                telegramService.sendMessage(botToken, chatId, statusMsg)
            }
            trimmed.equals("/help", ignoreCase = true) || trimmed.equals("/start", ignoreCase = true) -> {
                val helpMsg = """
                    🤖 <b>RoamBridge Bot Help</b>
                    ━━━━━━━━━━━━━━━━━━━
                    • <b>/sms &lt;number&gt; &lt;msg&gt;</b> - Send SMS from home SIM
                    • <b>/status</b> - Check phone battery & connection
                    • <b>/help</b> - Show this help menu
                """.trimIndent()
                telegramService.sendMessage(botToken, chatId, helpMsg)
            }
        }
    }

    private fun startImapPolling() {
        imapPollingJob?.cancel()
        imapPollingJob = serviceScope.launch {
            while (isActive) {
                val settings = settingsRepo.getSettings()
                if (settings.reverseSmsEnabled && settings.imapHost.isNotBlank()) {
                    try {
                        val commands = imapReceiver.checkInboundSmsCommands(settings)
                        for (cmd in commands) {
                            Log.d(TAG, "Executing Reverse SMS via IMAP command to ${cmd.recipient}")
                            smsRepo.executeReverseSms(cmd.recipient, cmd.messageText, cmd.senderSource)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "IMAP polling error", e)
                    }
                    delay(30000) // Poll every 30 seconds
                } else {
                    delay(60000)
                }
            }
        }
    }

    private fun getBatteryStatus(): String {
        return try {
            val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            "$batteryPct% " + (if (isCharging) "(🔌 Charging)" else "(🔋 On Battery)")
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_daemon_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_daemon_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_running_title))
            .setContentText(getString(R.string.service_running_desc))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "BridgeFGS"
        private const val CHANNEL_ID = "roambridge_daemon_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, BridgeForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BridgeForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
