package com.roambridge.app.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.roambridge.app.data.repository.SettingsRepository
import com.roambridge.app.data.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val pendingResult = goAsync()
        val settingsRepo = SettingsRepository(context.applicationContext)
        val repository = SmsRepository(context.applicationContext)

        // Concatenate multi-part SMS
        val sender = messages[0].displayOriginatingAddress ?: "Unknown"
        val timestamp = messages[0].timestampMillis
        val body = buildString {
            for (sms in messages) {
                append(sms.displayMessageBody ?: "")
            }
        }

        Log.d(TAG, "SMS received from: $sender | Length: ${body.length}")

        // ── SIM filter ──────────────────────────────────────────────────────────
        val settings = settingsRepo.getSettings()
        if (settings.trackedPhoneNumbers.isNotEmpty()) {
            val receivingSimNumber = resolveSimPhoneNumber(context, intent)
            if (receivingSimNumber == null || !settings.trackedPhoneNumbers.any { tracked ->
                    normalizeNumber(tracked) == normalizeNumber(receivingSimNumber)
                }) {
                Log.d(TAG, "SMS dropped — receiving SIM ($receivingSimNumber) is not in tracked set")
                pendingResult.finish()
                return
            }
            Log.d(TAG, "SMS accepted — receiving SIM $receivingSimNumber is tracked")
        }
        // ────────────────────────────────────────────────────────────────────────

        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.processInboundSms(sender, body, timestamp)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing inbound SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Resolves the phone number of the SIM that received this SMS by reading
     * the subscription ID embedded in the broadcast intent.
     *
     * Returns null if READ_PHONE_STATE is not granted or the number is unknown.
     */
    private fun resolveSimPhoneNumber(context: Context, intent: Intent): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        return try {
            val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX",
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            } else {
                @Suppress("DEPRECATION")
                intent.getIntExtra("subscription",
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            }

            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                // Fall back: just use the default TelephonyManager (single-SIM)
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                @Suppress("MissingPermission")
                tm.line1Number?.takeIf { it.isNotBlank() }
            } else {
                val tm = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                    .createForSubscriptionId(subId)
                @Suppress("MissingPermission")
                tm.line1Number?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve SIM phone number: ${e.message}")
            null
        }
    }

    /** Strips spaces, dashes, parentheses and normalises to digits + optional leading '+'. */
    private fun normalizeNumber(number: String): String =
        number.replace(Regex("[\\s\\-().]+"), "")

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }
}
