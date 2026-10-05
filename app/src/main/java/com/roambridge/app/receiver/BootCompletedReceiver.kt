package com.roambridge.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.roambridge.app.data.repository.SettingsRepository
import com.roambridge.app.service.BridgeForegroundService

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Boot event detected: $action")
            val settingsRepo = SettingsRepository(context.applicationContext)
            val settings = settingsRepo.getSettings()

            if (settings.bridgeServiceEnabled) {
                val serviceIntent = Intent(context, BridgeForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "RoamBridge Foreground Service auto-started after reboot.")
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
