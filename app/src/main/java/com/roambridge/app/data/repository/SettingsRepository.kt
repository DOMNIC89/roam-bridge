package com.roambridge.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.roambridge.app.data.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "roambridge_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for emulator or devices with KeyStore issues
        context.getSharedPreferences("roambridge_fallback_prefs", Context.MODE_PRIVATE)
    }

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    fun getSettings(): AppSettings {
        return loadSettings()
    }

    private fun loadSettings(): AppSettings {
        return AppSettings(
            telegramEnabled = prefs.getBoolean(KEY_TG_ENABLED, false),
            telegramBotToken = prefs.getString(KEY_TG_BOT_TOKEN, "") ?: "",
            telegramChatId = prefs.getString(KEY_TG_CHAT_ID, "") ?: "",

            smtpEnabled = prefs.getBoolean(KEY_SMTP_ENABLED, false),
            smtpHost = prefs.getString(KEY_SMTP_HOST, "smtp.gmail.com") ?: "smtp.gmail.com",
            smtpPort = prefs.getInt(KEY_SMTP_PORT, 465),
            smtpUseSsl = prefs.getBoolean(KEY_SMTP_SSL, true),
            smtpUsername = prefs.getString(KEY_SMTP_USER, "") ?: "",
            smtpPassword = prefs.getString(KEY_SMTP_PASS, "") ?: "",
            destinationEmail = prefs.getString(KEY_DEST_EMAIL, "") ?: "",

            reverseSmsEnabled = prefs.getBoolean(KEY_REVERSE_SMS_ENABLED, false),
            reverseSmsPin = prefs.getString(KEY_REVERSE_SMS_PIN, "1234") ?: "1234",
            imapHost = prefs.getString(KEY_IMAP_HOST, "imap.gmail.com") ?: "imap.gmail.com",
            imapPort = prefs.getInt(KEY_IMAP_PORT, 993),
            imapUseSsl = prefs.getBoolean(KEY_IMAP_SSL, true),
            imapUsername = prefs.getString(KEY_IMAP_USER, "") ?: "",
            imapPassword = prefs.getString(KEY_IMAP_PASS, "") ?: "",

            otpForwardTelegram = prefs.getBoolean(KEY_RULE_OTP_TG, true),
            otpForwardEmail = prefs.getBoolean(KEY_RULE_OTP_EMAIL, true),
            bankingForwardTelegram = prefs.getBoolean(KEY_RULE_BANK_TG, true),
            bankingForwardEmail = prefs.getBoolean(KEY_RULE_BANK_EMAIL, true),
            utilityForwardTelegram = prefs.getBoolean(KEY_RULE_UTIL_TG, false),
            utilityForwardEmail = prefs.getBoolean(KEY_RULE_UTIL_EMAIL, true),
            personalForwardTelegram = prefs.getBoolean(KEY_RULE_PERS_TG, true),
            personalForwardEmail = prefs.getBoolean(KEY_RULE_PERS_EMAIL, false),
            spamFilterDrop = prefs.getBoolean(KEY_RULE_SPAM_DROP, true),

            trackedPhoneNumbers = prefs.getStringSet(KEY_TRACKED_NUMBERS, emptySet())?.toSet() ?: emptySet(),

            bridgeServiceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, true),
            heartbeatDailyEnabled = prefs.getBoolean(KEY_HEARTBEAT_ENABLED, true)
        )
    }

    fun  updateSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putBoolean(KEY_TG_ENABLED, newSettings.telegramEnabled)
            putString(KEY_TG_BOT_TOKEN, newSettings.telegramBotToken)
            putString(KEY_TG_CHAT_ID, newSettings.telegramChatId)

            putBoolean(KEY_SMTP_ENABLED, newSettings.smtpEnabled)
            putString(KEY_SMTP_HOST, newSettings.smtpHost)
            putInt(KEY_SMTP_PORT, newSettings.smtpPort)
            putBoolean(KEY_SMTP_SSL, newSettings.smtpUseSsl)
            putString(KEY_SMTP_USER, newSettings.smtpUsername)
            putString(KEY_SMTP_PASS, newSettings.smtpPassword)
            putString(KEY_DEST_EMAIL, newSettings.destinationEmail)

            putBoolean(KEY_REVERSE_SMS_ENABLED, newSettings.reverseSmsEnabled)
            putString(KEY_REVERSE_SMS_PIN, newSettings.reverseSmsPin)
            putString(KEY_IMAP_HOST, newSettings.imapHost)
            putInt(KEY_IMAP_PORT, newSettings.imapPort)
            putBoolean(KEY_IMAP_SSL, newSettings.imapUseSsl)
            putString(KEY_IMAP_USER, newSettings.imapUsername)
            putString(KEY_IMAP_PASS, newSettings.imapPassword)

            putBoolean(KEY_RULE_OTP_TG, newSettings.otpForwardTelegram)
            putBoolean(KEY_RULE_OTP_EMAIL, newSettings.otpForwardEmail)
            putBoolean(KEY_RULE_BANK_TG, newSettings.bankingForwardTelegram)
            putBoolean(KEY_RULE_BANK_EMAIL, newSettings.bankingForwardEmail)
            putBoolean(KEY_RULE_UTIL_TG, newSettings.utilityForwardTelegram)
            putBoolean(KEY_RULE_UTIL_EMAIL, newSettings.utilityForwardEmail)
            putBoolean(KEY_RULE_PERS_TG, newSettings.personalForwardTelegram)
            putBoolean(KEY_RULE_PERS_EMAIL, newSettings.personalForwardEmail)
            putBoolean(KEY_RULE_SPAM_DROP, newSettings.spamFilterDrop)

            putStringSet(KEY_TRACKED_NUMBERS, newSettings.trackedPhoneNumbers)

            putBoolean(KEY_SERVICE_ENABLED, newSettings.bridgeServiceEnabled)
            putBoolean(KEY_HEARTBEAT_ENABLED, newSettings.heartbeatDailyEnabled)
            apply()
        }
        _settingsFlow.value = newSettings
    }

    companion object {
        private const val KEY_TG_ENABLED = "tg_enabled"
        private const val KEY_TG_BOT_TOKEN = "tg_bot_token"
        private const val KEY_TG_CHAT_ID = "tg_chat_id"

        private const val KEY_SMTP_ENABLED = "smtp_enabled"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_SMTP_SSL = "smtp_ssl"
        private const val KEY_SMTP_USER = "smtp_user"
        private const val KEY_SMTP_PASS = "smtp_pass"
        private const val KEY_DEST_EMAIL = "dest_email"

        private const val KEY_REVERSE_SMS_ENABLED = "reverse_sms_enabled"
        private const val KEY_REVERSE_SMS_PIN = "reverse_sms_pin"
        private const val KEY_IMAP_HOST = "imap_host"
        private const val KEY_IMAP_PORT = "imap_port"
        private const val KEY_IMAP_SSL = "imap_ssl"
        private const val KEY_IMAP_USER = "imap_user"
        private const val KEY_IMAP_PASS = "imap_pass"

        private const val KEY_RULE_OTP_TG = "rule_otp_tg"
        private const val KEY_RULE_OTP_EMAIL = "rule_otp_email"
        private const val KEY_RULE_BANK_TG = "rule_bank_tg"
        private const val KEY_RULE_BANK_EMAIL = "rule_bank_email"
        private const val KEY_RULE_UTIL_TG = "rule_util_tg"
        private const val KEY_RULE_UTIL_EMAIL = "rule_util_email"
        private const val KEY_RULE_PERS_TG = "rule_pers_tg"
        private const val KEY_RULE_PERS_EMAIL = "rule_pers_email"
        private const val KEY_RULE_SPAM_DROP = "rule_spam_drop"

        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_HEARTBEAT_ENABLED = "heartbeat_enabled"
        private const val KEY_TRACKED_NUMBERS = "tracked_phone_numbers"
    }
}
