package com.roambridge.app.data.model

data class AppSettings(
    // Telegram Configuration
    val telegramEnabled: Boolean = false,
    val telegramBotToken: String = "",
    val telegramChatId: String = "",

    // Direct SMTP Configuration
    val smtpEnabled: Boolean = false,
    val smtpHost: String = "smtp.gmail.com",
    val smtpPort: Int = 465,
    val smtpUseSsl: Boolean = true,
    val smtpUsername: String = "",
    val smtpPassword: String = "", // App Password
    val destinationEmail: String = "",

    // Reverse SMS & IMAP Configuration
    val reverseSmsEnabled: Boolean = false,
    val reverseSmsPin: String = "1234",
    val imapHost: String = "imap.gmail.com",
    val imapPort: Int = 993,
    val imapUseSsl: Boolean = true,
    val imapUsername: String = "",
    val imapPassword: String = "",

    // Forwarding Rules by Topic
    val otpForwardTelegram: Boolean = true,
    val otpForwardEmail: Boolean = true,
    val bankingForwardTelegram: Boolean = true,
    val bankingForwardEmail: Boolean = true,
    val utilityForwardTelegram: Boolean = false,
    val utilityForwardEmail: Boolean = true,
    val personalForwardTelegram: Boolean = true,
    val personalForwardEmail: Boolean = false,
    val spamFilterDrop: Boolean = true,

    // SIM / Number Filter (empty = track all SIMs, non-empty = only listed numbers)
    val trackedPhoneNumbers: Set<String> = emptySet(),

    // Keep-Alive & Daemon
    val bridgeServiceEnabled: Boolean = true,
    val heartbeatDailyEnabled: Boolean = true
)
