package com.roambridge.app.data.network

import android.util.Log
import com.roambridge.app.data.model.AppSettings
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.search.FlagTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import java.util.regex.Pattern

data class OutboundSmsCommand(
    val recipient: String,
    val messageText: String,
    val senderSource: String
)

class ImapReceiver {

    suspend fun checkInboundSmsCommands(settings: AppSettings): List<OutboundSmsCommand> = withContext(Dispatchers.IO) {
        val commands = mutableListOf<OutboundSmsCommand>()
        var store: Store? = null
        var inbox: Folder? = null

        try {
            if (!settings.reverseSmsEnabled || settings.imapHost.isBlank() || settings.imapUsername.isBlank()) {
                return@withContext emptyList()
            }

            val props = Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", settings.imapHost)
                put("mail.imaps.port", settings.imapPort.toString())
                if (settings.imapUseSsl) {
                    put("mail.imaps.ssl.enable", "true")
                    put("mail.imaps.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                }
                put("mail.imaps.connectiontimeout", "10000")
                put("mail.imaps.timeout", "10000")
            }

            val session = Session.getInstance(props)
            store = session.getStore("imaps")
            store.connect(settings.imapHost, settings.imapUsername, settings.imapPassword)

            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)

            // Search for unread emails
            val unreadFlag = FlagTerm(Flags(Flags.Flag.SEEN), false)
            val messages = inbox.search(unreadFlag)

            for (msg in messages) {
                val subject = msg.subject ?: ""
                val from = (msg.from?.firstOrNull()?.toString()) ?: ""

                // Verify PIN authorization in Subject: e.g., [SEND-SMS: 1234] or [ROAMBRIDGE: 1234]
                if (isAuthorizedSubject(subject, settings.reverseSmsPin)) {
                    val body = extractBodyText(msg)
                    val parsed = parseSmsContent(body)
                    if (parsed != null) {
                        commands.add(parsed.copy(senderSource = "Email: $from"))
                        // Mark as read so it isn't processed again
                        msg.setFlag(Flags.Flag.SEEN, true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "IMAP check failed", e)
        } finally {
            try {
                inbox?.close(true)
                store?.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }

        commands
    }

    private fun isAuthorizedSubject(subject: String, secretPin: String): Boolean {
        if (secretPin.isBlank()) return false
        val cleanSubject = subject.trim().uppercase()
        return cleanSubject.contains("[SEND-SMS: $secretPin]".uppercase()) ||
                cleanSubject.contains("[SEND-SMS:$secretPin]".uppercase()) ||
                cleanSubject.contains("[ROAMBRIDGE: $secretPin]".uppercase()) ||
                cleanSubject.contains("[ROAMBRIDGE:$secretPin]".uppercase())
    }

    /**
     * Parses recipient phone number and SMS content from body.
     * Supports formats:
     * TO: +1234567890
     * MSG: Hello World
     *
     * OR:
     * +1234567890: Hello World
     */
    fun parseSmsContent(body: String): OutboundSmsCommand? {
        val lines = body.lines().map { it.trim() }.filter { it.isNotBlank() }
        var recipient: String? = null
        val messageLines = mutableListOf<String>()

        var isReadingMessage = false

        for (line in lines) {
            if (line.startsWith("TO:", ignoreCase = true)) {
                recipient = line.substring(3).trim()
            } else if (line.startsWith("MSG:", ignoreCase = true) || line.startsWith("MESSAGE:", ignoreCase = true)) {
                isReadingMessage = true
                val prefix = if (line.startsWith("MSG:", ignoreCase = true)) 4 else 8
                val firstLineText = line.substring(prefix).trim()
                if (firstLineText.isNotBlank()) messageLines.add(firstLineText)
            } else if (isReadingMessage) {
                messageLines.add(line)
            }
        }

        if (recipient != null && messageLines.isNotEmpty()) {
            return OutboundSmsCommand(
                recipient = cleanPhoneNumber(recipient),
                messageText = messageLines.joinToString("\n"),
                senderSource = "Email"
            )
        }

        // Fallback pattern: +1234567890: message
        val colonIndex = body.indexOf(':')
        if (colonIndex in 5..20) {
            val potentialNumber = body.substring(0, colonIndex).trim()
            val potentialMessage = body.substring(colonIndex + 1).trim()
            if (isValidPhoneNumber(potentialNumber) && potentialMessage.isNotBlank()) {
                return OutboundSmsCommand(
                    recipient = cleanPhoneNumber(potentialNumber),
                    messageText = potentialMessage,
                    senderSource = "Email"
                )
            }
        }

        return null
    }

    private fun extractBodyText(message: Message): String {
        return try {
            val content = message.content
            when (content) {
                is String -> content
                is jakarta.mail.Multipart -> {
                    val sb = StringBuilder()
                    for (i in 0 until content.count) {
                        val bodyPart = content.getBodyPart(i)
                        if (bodyPart.isMimeType("text/plain")) {
                            sb.append(bodyPart.content.toString())
                        }
                    }
                    if (sb.isEmpty() && content.count > 0) {
                        content.getBodyPart(0).content.toString()
                    } else {
                        sb.toString()
                    }
                }
                else -> content.toString()
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun cleanPhoneNumber(number: String): String {
        return number.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
    }

    private fun isValidPhoneNumber(number: String): Boolean {
        val cleaned = cleanPhoneNumber(number)
        return cleaned.matches(Regex("""\+?[0-9]{7,15}"""))
    }

    companion object {
        private const val TAG = "ImapReceiver"
    }
}
