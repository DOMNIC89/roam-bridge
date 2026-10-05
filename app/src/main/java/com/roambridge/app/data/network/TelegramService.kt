package com.roambridge.app.data.network

import android.util.Log
import com.roambridge.app.data.classifier.ClassificationResult
import com.roambridge.app.data.classifier.SmsTopic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TelegramService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Sends an incoming SMS alert formatted to Telegram.
     */
    suspend fun forwardSms(
        botToken: String,
        chatId: String,
        sender: String,
        body: String,
        classification: ClassificationResult,
        timestamp: Long
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (botToken.isBlank() || chatId.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Bot Token or Chat ID is empty"))
            }

            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
            val formattedMessage = formatRelayMessage(sender, body, classification, timeStr)

            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", formattedMessage)
                put("parse_mode", "HTML")
                put("disable_web_page_preview", true)
            }

            val request = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/sendMessage")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val err = response.body?.string() ?: "HTTP ${response.code}"
                    Log.e(TAG, "Telegram send error: $err")
                    Result.failure(Exception("Telegram API error: $err"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Telegram exception", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a raw message (e.g. Heartbeat, Delivery receipt, or Command response) to Telegram.
     */
    suspend fun sendMessage(
        botToken: String,
        chatId: String,
        messageHtml: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", messageHtml)
                put("parse_mode", "HTML")
            }

            val request = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/sendMessage")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(true)
                else Result.failure(Exception("Failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Long-polls for updates to process Telegram bot commands (/sms, /send, /status, /help).
     */
    suspend fun getUpdates(botToken: String, offset: Long): List<TelegramUpdate> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=$offset&timeout=25"
            val request = Request.Builder().url(url).get().build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = JSONObject(response.body?.string() ?: "{}")
                if (!json.optBoolean("ok", false)) return@withContext emptyList()

                val resultsArray = json.optJSONArray("result") ?: JSONArray()
                val updates = mutableListOf<TelegramUpdate>()

                for (i in 0 until resultsArray.length()) {
                    val updateObj = resultsArray.getJSONObject(i)
                    val updateId = updateObj.getLong("update_id")
                    val messageObj = updateObj.optJSONObject("message")
                    if (messageObj != null) {
                        val text = messageObj.optString("text", "")
                        val fromChat = messageObj.optJSONObject("chat")
                        val chatId = fromChat?.optLong("id")?.toString() ?: ""
                        updates.add(TelegramUpdate(updateId, chatId, text))
                    }
                }
                updates
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun testConnection(botToken: String, chatId: String): Result<Boolean> {
        return sendMessage(
            botToken = botToken,
            chatId = chatId,
            messageHtml = "✅ <b>RoamBridge Connected!</b>\nYour Telegram Bot is configured and ready to relay SMS."
        )
    }

    private fun formatRelayMessage(
        sender: String,
        body: String,
        classification: ClassificationResult,
        timeStr: String
    ): String {
        return buildString {
            when (classification.topic) {
                SmsTopic.CRITICAL_OTP -> {
                    append("🚨 <b>CRITICAL OTP ALERT</b>\n")
                    if (classification.merchantOrSenderName != null) {
                        append("🏦 <b>Entity:</b> <code>${escapeHtml(classification.merchantOrSenderName)}</code>\n")
                    }
                    if (classification.extractedOtp != null) {
                        append("🔑 <b>OTP CODE:</b> <code>${classification.extractedOtp}</code>\n")
                    }
                    append("━━━━━━━━━━━━━━━━━━━\n")
                }
                SmsTopic.BANKING_FINANCE -> {
                    append("💳 <b>BANKING & TRANSACTION</b>\n")
                    if (classification.extractedAmount != null) {
                        append("💰 <b>Amount:</b> <b>${escapeHtml(classification.extractedAmount)}</b>\n")
                    }
                    append("━━━━━━━━━━━━━━━━━━━\n")
                }
                SmsTopic.GOV_UTILITY -> {
                    append("🏛️ <b>GOV & UTILITY NOTICE</b>\n")
                    append("━━━━━━━━━━━━━━━━━━━\n")
                }
                SmsTopic.SPAM_PROMO -> {
                    append("📢 <b>PROMOTIONAL MESSAGE</b>\n")
                    append("━━━━━━━━━━━━━━━━━━━\n")
                }
                SmsTopic.PERSONAL -> {
                    append("💬 <b>NEW SMS RECEIVED</b>\n")
                    append("━━━━━━━━━━━━━━━━━━━\n")
                }
            }

            append("👤 <b>From:</b> <code>${escapeHtml(sender)}</code>\n")
            append("⏱ <b>Time:</b> ${escapeHtml(timeStr)}\n\n")
            append("📝 <b>Message:</b>\n<pre>${escapeHtml(body)}</pre>")
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    companion object {
        private const val TAG = "TelegramService"
    }
}

data class TelegramUpdate(
    val updateId: Long,
    val chatId: String,
    val text: String
)
