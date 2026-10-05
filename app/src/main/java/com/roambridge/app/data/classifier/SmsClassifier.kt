package com.roambridge.app.data.classifier

import java.util.regex.Pattern

object SmsClassifier {

    // Common OTP Regex patterns
    private val OTP_EXPLICIT_PATTERNS = listOf(
        Pattern.compile("""(?i)(?:otp|code|passcode|token|pin|secret|verification code|one time password)(?:\s+is|\s*[:=-]\s*|\s+is:\s*|\s+is\s+)?\s*([0-9]{4,8})"""),
        Pattern.compile("""(?i)\b([0-9]{4,8})\b\s*(?:is\s+your\s+(?:otp|verification\s+code|login\s+code|passcode|security\s+code))"""),
        Pattern.compile("""(?i)(?:use|enter)\s+([0-9]{4,8})\s+(?:to\s+verify|as\s+your|for\s+login|to\s+complete)"""),
        Pattern.compile("""(?i)(?:código|clave|código de verificación)(?:\s+es|\s*[:=-]\s*)?\s*([0-9]{4,8})"""),
        Pattern.compile("""(?i)\b([0-9]{4,8})\b(?=.*(?:do not share|valid for|expires in))""")
    )

    // Fallback 4-8 digit standalone number if strong OTP keywords are present
    private val OTP_KEYWORDS = listOf(
        "otp", "verification code", "one time password", "passcode", "security code",
        "valid for", "do not share", "expires in", "secret code", "2fa", "two-factor",
        "login code", "auth code", "authenticate"
    )

    private val GENERIC_4_8_DIGIT_PATTERN = Pattern.compile("""\b([0-9]{4,8})\b""")

    // Financial & Transaction Regex
    private val FINANCIAL_KEYWORDS = listOf(
        "debited", "credited", "spent", "withdrawn", "deposited", "transferred",
        "account ending", "acct ending", "card ending", "a/c no", "avl bal",
        "available balance", "statement", "txn of", "transaction alert", "pos transaction",
        "atm withdrawal", "bank", "credit card", "debit card", "upi", "neft", "rtgs", "imps"
    )

    private val AMOUNT_PATTERN = Pattern.compile(
        """(?i)(?:(?:rs\.?|inr|usd|eur|gbp|aud|cad|\$|€|₹|£)\s*([0-9]+(?:,[0-9]+)*(?:\.[0-9]{1,2})?)|([0-9]+(?:,[0-9]+)*(?:\.[0-9]{1,2})?)\s*(?:rs\.?|inr|usd|eur|gbp|aud|cad|\$|€|₹|£))"""
    )

    // Government & Utility Keywords
    private val UTILITY_KEYWORDS = listOf(
        "electricity bill", "water bill", "gas bill", "utility", "broadband", "recharge",
        "due date", "bill due", "invoice", "tax payment", "irs", "incometax", "gov",
        "passport", "visa application", "consulate", "embassy", "national id", "aadhaar"
    )

    // Promotional & Spam Keywords
    private val SPAM_KEYWORDS = listOf(
        "offer", "discount", "flat 50%", "sale is live", "cashback", "win cash",
        "exclusive deal", "free gift", "claim now", "click here", "t&c apply",
        "limited time deal", "subscribe", "optout", "unsubscribe", "apply for loan",
        "pre-approved loan", "rummy", "casino", "lottery"
    )

    /**
     * Classifies an SMS message based on its sender and body.
     */
    fun classify(sender: String, body: String): ClassificationResult {
        val cleanBody = body.trim()
        val lowerBody = cleanBody.lowercase()
        val lowerSender = sender.lowercase()

        // 1. Check for OTP / 2FA (Highest Priority)
        val extractedOtp = extractOtp(cleanBody)
        val containsOtpKeyword = OTP_KEYWORDS.any { lowerBody.contains(it) }

        if (extractedOtp != null || containsOtpKeyword) {
            val merchant = extractMerchant(sender, cleanBody)
            val amount = extractAmount(cleanBody)
            return ClassificationResult(
                topic = SmsTopic.CRITICAL_OTP,
                extractedOtp = extractedOtp,
                extractedAmount = amount,
                merchantOrSenderName = merchant,
                summary = buildString {
                    append("OTP Verification Code")
                    if (extractedOtp != null) append(": $extractedOtp")
                    if (merchant != null) append(" ($merchant)")
                },
                confidence = if (extractedOtp != null) 0.95f else 0.80f
            )
        }

        // 2. Check for Banking & Financial Transactions
        val containsFinancialKeyword = FINANCIAL_KEYWORDS.any { lowerBody.contains(it) } ||
                lowerSender.contains("bank") || lowerSender.contains("pay") || lowerSender.contains("card")

        if (containsFinancialKeyword) {
            val amount = extractAmount(cleanBody)
            val merchant = extractMerchant(sender, cleanBody)
            val isDebit = lowerBody.contains("debit") || lowerBody.contains("spent") || lowerBody.contains("withdrawn")
            val isCredit = lowerBody.contains("credit") || lowerBody.contains("deposited") || lowerBody.contains("received")

            val txnType = when {
                isDebit -> "Debit Alert"
                isCredit -> "Credit Alert"
                else -> "Banking Alert"
            }

            return ClassificationResult(
                topic = SmsTopic.BANKING_FINANCE,
                extractedAmount = amount,
                merchantOrSenderName = merchant,
                summary = buildString {
                    append(txnType)
                    if (amount != null) append(" of $amount")
                    if (merchant != null) append(" at $merchant")
                },
                confidence = 0.90f
            )
        }

        // 3. Check for Government / Utility Bills
        val containsUtilityKeyword = UTILITY_KEYWORDS.any { lowerBody.contains(it) }
        if (containsUtilityKeyword) {
            val amount = extractAmount(cleanBody)
            return ClassificationResult(
                topic = SmsTopic.GOV_UTILITY,
                extractedAmount = amount,
                merchantOrSenderName = sender,
                summary = "Utility/Gov Notice" + (if (amount != null) " (Amount: $amount)" else ""),
                confidence = 0.85f
            )
        }

        // 4. Check for Spam / Promotional SMS
        val containsSpamKeyword = SPAM_KEYWORDS.any { lowerBody.contains(it) }
        if (containsSpamKeyword) {
            return ClassificationResult(
                topic = SmsTopic.SPAM_PROMO,
                summary = "Promotional Offer / Marketing",
                confidence = 0.80f
            )
        }

        // 5. Default to Personal / Direct SMS
        return ClassificationResult(
            topic = SmsTopic.PERSONAL,
            merchantOrSenderName = sender,
            summary = "Direct message from $sender",
            confidence = 0.70f
        )
    }

    /**
     * Extracts numerical OTP code from text using targeted regex.
     */
    fun extractOtp(body: String): String? {
        for (pattern in OTP_EXPLICIT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val code = matcher.group(1)
                if (code != null && code.length in 4..8) {
                    return code
                }
            }
        }

        // Fallback: If OTP keywords are present in text, find any isolated 4-8 digit number
        val lower = body.lowercase()
        if (OTP_KEYWORDS.any { lower.contains(it) }) {
            val matcher = GENERIC_4_8_DIGIT_PATTERN.matcher(body)
            while (matcher.find()) {
                val candidate = matcher.group(1)
                // Filter out years (e.g. 2024, 2025, 2026) if other digits exist
                if (candidate != null && candidate.length in 4..8) {
                    if (candidate.length == 4 && (candidate.startsWith("19") || candidate.startsWith("20"))) {
                        continue
                    }
                    return candidate
                }
            }
        }

        return null
    }

    /**
     * Extracts currency amounts (e.g., $120.00, Rs. 500, ₹4500).
     */
    fun extractAmount(body: String): String? {
        val matcher = AMOUNT_PATTERN.matcher(body)
        if (matcher.find()) {
            return matcher.group(0)?.trim()
        }
        return null
    }

    /**
     * Attempts to extract the originating entity/bank from sender mask or body.
     */
    private fun extractMerchant(sender: String, body: String): String? {
        // Parse sender headers like "AD-HDFCBK", "VK-CHASE", "MY-AMEX"
        if (sender.contains("-")) {
            val parts = sender.split("-")
            if (parts.size > 1 && parts[1].length >= 3) {
                return parts[1].uppercase()
            }
        }

        val lower = body.lowercase()
        val knownEntities = listOf(
            "chase", "bank of america", "wells fargo", "citi", "capital one", "hdfc",
            "icici", "sbi", "axis", "barclays", "hsbc", "revolut", "monzo", "paypal",
            "stripe", "apple", "google", "amazon", "uber", "netflix", "whatsapp"
        )
        for (entity in knownEntities) {
            if (lower.contains(entity)) {
                return entity.replaceFirstChar { it.uppercase() }
            }
        }

        return if (sender.isNotBlank() && !sender.all { it.isDigit() }) sender else null
    }
}
