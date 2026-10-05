package com.roambridge.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class SimInfo(
    val slotIndex: Int,
    val subscriptionId: Int,
    val displayName: String,
    val phoneNumber: String?      // null if carrier withholds it
)

object SimHelper {

    /**
     * Returns info for every active SIM/eSIM slot on the device.
     * Requires READ_PHONE_STATE; returns empty list if permission is absent.
     */
    fun getActiveSims(context: Context): List<SimInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        return try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as SubscriptionManager
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            @Suppress("MissingPermission")
            val subscriptions = subManager.activeSubscriptionInfoList ?: return emptyList()

            subscriptions.map { info ->
                val simTm = tm.createForSubscriptionId(info.subscriptionId)
                @Suppress("MissingPermission")
                val number = simTm.line1Number?.takeIf { it.isNotBlank() }

                SimInfo(
                    slotIndex = info.simSlotIndex,
                    subscriptionId = info.subscriptionId,
                    displayName = info.displayName?.toString()
                        ?: "SIM ${info.simSlotIndex + 1}",
                    phoneNumber = number
                )
            }.sortedBy { it.slotIndex }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
