package com.roambridge.app.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.roambridge.app.data.network.TelegramService
import com.roambridge.app.data.repository.SettingsRepository
import com.roambridge.app.data.repository.SmsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class HeartbeatWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settingsRepo = SettingsRepository(context)
        val settings = settingsRepo.getSettings()

        // WorkManager workers have a foreground service start exemption on Android 12+,
        // making this the safe place to (re)start BridgeForegroundService.
        if (settings.bridgeServiceEnabled) {
            BridgeForegroundService.start(context)
        }

        if (!settings.heartbeatDailyEnabled) {
            return Result.success()
        }

        try {
            val smsRepo = SmsRepository(context)
            val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
            val relayedToday = smsRepo.getRelayedCountFlow(twentyFourHoursAgo).first()

            val batteryStatus = getBatteryStatus()
            val heartbeatText = """
                💓 <b>RoamBridge Daily Heartbeat</b>
                ━━━━━━━━━━━━━━━━━━━
                📱 <b>Status:</b> Online & Relaying
                🔋 <b>Battery:</b> $batteryStatus
                📊 <b>SMS Relayed (24h):</b> $relayedToday
                🕒 <b>Check:</b> Normal
            """.trimIndent()

            if (settings.telegramEnabled && settings.telegramBotToken.isNotBlank()) {
                val telegramService = TelegramService()
                telegramService.sendMessage(settings.telegramBotToken, settings.telegramChatId, heartbeatText)
            }

            Log.d(TAG, "Daily heartbeat delivered successfully.")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat failed", e)
            return Result.retry()
        }
    }

    private fun getBatteryStatus(): String {
        return try {
            val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            "$batteryPct% " + (if (isCharging) "(🔌 Charging)" else "(🔋 Discharging)")
        } catch (e: Exception) {
            "Unknown"
        }
    }

    companion object {
        private const val TAG = "HeartbeatWorker"
        private const val WORK_NAME = "roambridge_heartbeat_work"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(12, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
