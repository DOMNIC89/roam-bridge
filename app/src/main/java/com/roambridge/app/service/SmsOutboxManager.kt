package com.roambridge.app.service

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsOutboxManager(private val context: Context) {

    /**
     * Sends an outbound SMS using the device's default cellular subscription.
     */
    suspend fun sendSms(destinationAddress: String, text: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val dividedMessage = smsManager.divideMessage(text)
            if (dividedMessage.size > 1) {
                smsManager.sendMultipartTextMessage(
                    destinationAddress,
                    null,
                    dividedMessage,
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    destinationAddress,
                    null,
                    text,
                    null,
                    null
                )
            }

            Log.d(TAG, "Successfully dispatched SMS to $destinationAddress")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $destinationAddress", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "SmsOutboxManager"
    }
}
