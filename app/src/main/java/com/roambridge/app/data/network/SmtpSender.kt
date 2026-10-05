package com.roambridge.app.data.network

import android.util.Log
import com.roambridge.app.data.classifier.ClassificationResult
import com.roambridge.app.data.classifier.SmsTopic
import com.roambridge.app.data.model.AppSettings
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

class SmtpSender {

    suspend fun forwardSms(
        settings: AppSettings,
        sender: String,
        body: String,
        classification: ClassificationResult,
        timestamp: Long
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (settings.destinationEmail.isBlank() || settings.smtpHost.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Destination email or SMTP host is missing"))
            }

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.host", settings.smtpHost)
                put("mail.smtp.port", settings.smtpPort.toString())
                if (settings.smtpUseSsl) {
                    put("mail.smtp.socketFactory.port", settings.smtpPort.toString())
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.ssl.enable", "true")
                } else {
                    put("mail.smtp.starttls.enable", "true")
                }
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "10000")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(settings.smtpUsername, settings.smtpPassword)
                }
            })

            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
            val subject = formatSubject(sender, classification)
            val htmlContent = formatHtmlBody(sender, body, classification, timeStr)

            val mimeMessage = MimeMessage(session).apply {
                setFrom(InternetAddress(settings.smtpUsername, "RoamBridge Home SIM"))
                setRecipient(Message.RecipientType.TO, InternetAddress(settings.destinationEmail))
                setSubject(subject, "UTF-8")
                setContent(htmlContent, "text/html; charset=utf-8")
                sentDate = Date(timestamp)
            }

            Transport.send(mimeMessage)
            Log.d(TAG, "Email forwarded successfully to ${settings.destinationEmail}")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMTP email", e)
            Result.failure(e)
        }
    }

    suspend fun testConnection(settings: AppSettings): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.host", settings.smtpHost)
                put("mail.smtp.port", settings.smtpPort.toString())
                if (settings.smtpUseSsl) {
                    put("mail.smtp.socketFactory.port", settings.smtpPort.toString())
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.ssl.enable", "true")
                } else {
                    put("mail.smtp.starttls.enable", "true")
                }
                put("mail.smtp.connectiontimeout", "8000")
                put("mail.smtp.timeout", "8000")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(settings.smtpUsername, settings.smtpPassword)
                }
            })

            val mimeMessage = MimeMessage(session).apply {
                setFrom(InternetAddress(settings.smtpUsername, "RoamBridge"))
                setRecipient(Message.RecipientType.TO, InternetAddress(settings.destinationEmail))
                setSubject("RoamBridge SMTP Test Successful", "UTF-8")
                setText("Your SMTP configuration is working correctly! SMS alerts will now be forwarded here.", "UTF-8")
                sentDate = Date()
            }

            Transport.send(mimeMessage)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatSubject(sender: String, classification: ClassificationResult): String {
        return when (classification.topic) {
            SmsTopic.CRITICAL_OTP -> {
                val otpPart = if (classification.extractedOtp != null) "[OTP: ${classification.extractedOtp}]" else "[OTP Alert]"
                "$otpPart From $sender"
            }
            SmsTopic.BANKING_FINANCE -> {
                val amtPart = if (classification.extractedAmount != null) " (${classification.extractedAmount})" else ""
                "[Banking Alert$amtPart] From $sender"
            }
            SmsTopic.GOV_UTILITY -> "[Utility/Gov Alert] From $sender"
            SmsTopic.SPAM_PROMO -> "[Promo] From $sender"
            SmsTopic.PERSONAL -> "[SMS] From $sender"
        }
    }

    private fun formatHtmlBody(
        sender: String,
        body: String,
        classification: ClassificationResult,
        timeStr: String
    ): String {
        val topicBadgeColor = when (classification.topic) {
            SmsTopic.CRITICAL_OTP -> "#D32F2F"
            SmsTopic.BANKING_FINANCE -> "#1976D2"
            SmsTopic.GOV_UTILITY -> "#388E3C"
            SmsTopic.SPAM_PROMO -> "#757575"
            SmsTopic.PERSONAL -> "#7B1FA2"
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 20px; background-color: #f4f6f8; }
                    .card { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 24px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
                    .badge { display: inline-block; padding: 6px 12px; border-radius: 20px; color: #fff; font-size: 13px; font-weight: bold; background-color: $topicBadgeColor; }
                    .otp-box { margin: 16px 0; padding: 16px; background-color: #ffebee; border-left: 4px solid #d32f2f; border-radius: 4px; }
                    .otp-code { font-size: 28px; font-weight: bold; letter-spacing: 4px; color: #d32f2f; font-family: monospace; }
                    .meta-table { width: 100%; border-collapse: collapse; margin: 16px 0; font-size: 14px; }
                    .meta-table td { padding: 6px 0; border-bottom: 1px solid #eee; }
                    .meta-table td.label { color: #666; width: 30%; }
                    .message-box { background: #f8f9fa; border-radius: 8px; padding: 16px; font-family: monospace; white-space: pre-wrap; font-size: 14px; word-break: break-word; }
                    .footer { text-align: center; margin-top: 20px; font-size: 12px; color: #999; }
                </style>
            </head>
            <body>
                <div class="card">
                    <span class="badge">${classification.topic.badgeEmoji} ${classification.topic.displayName}</span>
                    
                    ${
            if (classification.extractedOtp != null) {
                """
                        <div class="otp-box">
                            <div style="font-size: 12px; color: #c62828; font-weight: bold; text-transform: uppercase;">One-Time Password</div>
                            <div class="otp-code">${classification.extractedOtp}</div>
                        </div>
                        """
            } else ""
        }
                    
                    <table class="meta-table">
                        <tr>
                            <td class="label">From Sender:</td>
                            <td><b>$sender</b></td>
                        </tr>
                        ${if (classification.extractedAmount != null) "<tr><td class=\"label\">Amount:</td><td><b>${classification.extractedAmount}</b></td></tr>" else ""}
                        <tr>
                            <td class="label">Received At:</td>
                            <td>$timeStr</td>
                        </tr>
                    </table>

                    <div style="font-size: 13px; font-weight: bold; color: #555; margin-bottom: 6px;">Original Message:</div>
                    <div class="message-box">$body</div>

                    <div class="footer">
                        Relayed securely by <b>RoamBridge</b> from your Home SIM device.
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private const val TAG = "SmtpSender"
    }
}
