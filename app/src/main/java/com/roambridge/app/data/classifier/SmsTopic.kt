package com.roambridge.app.data.classifier

enum class SmsTopic(val displayName: String, val badgeEmoji: String) {
    CRITICAL_OTP("Critical OTP / 2FA", "🚨"),
    BANKING_FINANCE("Banking & Transactions", "💳"),
    GOV_UTILITY("Govt & Utilities", "🏛️"),
    PERSONAL("Personal & Direct", "💬"),
    SPAM_PROMO("Promotional / Spam", "📢");

    companion object {
        fun fromString(value: String): SmsTopic {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: PERSONAL
        }
    }
}

data class ClassificationResult(
    val topic: SmsTopic,
    val extractedOtp: String? = null,
    val extractedAmount: String? = null,
    val merchantOrSenderName: String? = null,
    val summary: String,
    val confidence: Float = 1.0f
)
