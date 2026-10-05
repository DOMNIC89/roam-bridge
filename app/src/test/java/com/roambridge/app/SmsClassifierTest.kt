package com.roambridge.app

import com.roambridge.app.data.classifier.SmsClassifier
import com.roambridge.app.data.classifier.SmsTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsClassifierTest {

    // =========================================================================
    // OTP / 2FA classification
    // =========================================================================

    @Test
    fun testOtpExtractionStandardBank() {
        val message = "Your Chase Bank OTP is 482910. Valid for 10 minutes. Do not share this code with anyone."
        val result = SmsClassifier.classify("CHASE", message)

        assertEquals(SmsTopic.CRITICAL_OTP, result.topic)
        assertEquals("482910", result.extractedOtp)
        assertEquals("Chase", result.merchantOrSenderName)
    }

    @Test
    fun testOtpExtractionVerificationCodeFormat() {
        val message = "392817 is your Google verification code."
        val result = SmsClassifier.classify("Google", message)

        assertEquals(SmsTopic.CRITICAL_OTP, result.topic)
        assertEquals("392817", result.extractedOtp)
        assertEquals("Google", result.merchantOrSenderName)
    }

    @Test
    fun testOtpExtractionIndianBankFormat() {
        val message = "Dear Customer, 948123 is the OTP for your online txn of Rs. 4,500.00 on HDFC Bank Card ending 1234. OTP valid for 5 mins."
        val result = SmsClassifier.classify("AD-HDFCBK", message)

        assertEquals(SmsTopic.CRITICAL_OTP, result.topic)
        assertEquals("948123", result.extractedOtp)
        assertNotNull(result.extractedAmount)
    }

    @Test
    fun testOtpExtractionUseEnterPattern() {
        // Pattern: "use XXXXXX to verify"
        val message = "Use 837291 to verify your login. This code expires in 5 minutes."
        val result = SmsClassifier.classify("SecureApp", message)

        assertEquals(SmsTopic.CRITICAL_OTP, result.topic)
        assertEquals("837291", result.extractedOtp)
    }

    @Test
    fun testOtpKeywordPresentButNoDigitCode() {
        // "valid for" is an OTP keyword, but no extractable digit code — still classifies as OTP
        val message = "Your session is valid for 30 minutes. Please log in to continue."
        val result = SmsClassifier.classify("AppService", message)

        assertEquals(SmsTopic.CRITICAL_OTP, result.topic)
        assertNull("No isolated 4-8 digit code — OTP should be null", result.extractedOtp)
    }

    @Test
    fun testYearIsNotExtractedAsOtp() {
        // "2026" should be skipped as a year, and "583920" should be picked up
        val message = "583920 is your verification code. Expires in 2026-10-05."
        val result = SmsClassifier.classify("Bank", message)

        assertEquals(SmsTopic.CRITICAL_OTP, result.topic)
        assertEquals("583920", result.extractedOtp)
    }

    @Test
    fun testOtpHighConfidenceWhenCodeExtracted() {
        val message = "Your OTP is 123456."
        val result = SmsClassifier.classify("Bank", message)

        assertEquals(SmsTopic.CRITICAL_OTP, result.topic)
        assertTrue("Confidence should be >= 0.95 when OTP is extracted", result.confidence >= 0.95f)
    }

    @Test
    fun testOtpLowerConfidenceWhenKeywordOnlyNoCode() {
        val message = "Do not share your authentication credentials with anyone."
        val result = SmsClassifier.classify("Bank", message)

        assertEquals(SmsTopic.CRITICAL_OTP, result.topic)
        assertTrue("Confidence should be 0.80 when only keyword matches", result.confidence < 0.95f)
    }

    // =========================================================================
    // Banking & Finance classification
    // =========================================================================

    @Test
    fun testBankingDebitTransaction() {
        val message = "Your a/c no. XX9812 has been debited for USD 85.50 on 2026-08-29 at Amazon. Avl Bal is USD 1,420.00."
        val result = SmsClassifier.classify("WELLSFARGO", message)

        assertEquals(SmsTopic.BANKING_FINANCE, result.topic)
        assertNotNull(result.extractedAmount)
        assertEquals("Amazon", result.merchantOrSenderName)
    }

    @Test
    fun testBankingCreditTransaction() {
        val message = "Your a/c XX3344 has been credited with USD 500.00 from PayPal on 2026-09-01. Avl Bal: USD 2,300.00."
        val result = SmsClassifier.classify("BARCLAYS", message)

        assertEquals(SmsTopic.BANKING_FINANCE, result.topic)
        assertNotNull(result.extractedAmount)
        assertTrue("Summary should mention Credit", result.summary.contains("Credit", ignoreCase = true))
    }

    @Test
    fun testBankingKeywordInSenderTriggersClassification() {
        // Sender contains "bank" — should be enough to trigger BANKING_FINANCE
        val message = "Your account statement is ready. Log in to view."
        val result = SmsClassifier.classify("mybank-alerts", message)

        assertEquals(SmsTopic.BANKING_FINANCE, result.topic)
    }

    @Test
    fun testBankingAmountExtractionRupees() {
        val message = "INR 12,500.00 debited from your Axis Bank account. UPI Ref: 2938471023."
        val result = SmsClassifier.classify("AD-AXISBK", message)

        assertEquals(SmsTopic.BANKING_FINANCE, result.topic)
        assertNotNull(result.extractedAmount)
        assertTrue(result.extractedAmount!!.contains("12,500"))
    }

    // =========================================================================
    // Government / Utility classification
    // =========================================================================

    @Test
    fun testGovUtilityBillAlert() {
        val message = "Dear consumer, your electricity bill of $142.50 for account 991823 is due on 05-Sep-2026. Pay online to avoid disconnection."
        val result = SmsClassifier.classify("POWERCORP", message)

        assertEquals(SmsTopic.GOV_UTILITY, result.topic)
        assertEquals("$142.50", result.extractedAmount)
    }

    @Test
    fun testGovUtilityIncomeTaxKeyword() {
        val message = "Your incometax return filing deadline is approaching. File before 31-Oct-2026 to avoid penalties."
        val result = SmsClassifier.classify("GOVTAX", message)

        assertEquals(SmsTopic.GOV_UTILITY, result.topic)
    }

    @Test
    fun testGovUtilityAadhaarKeyword() {
        val message = "Your Aadhaar verification has been successfully completed. Thank you."
        val result = SmsClassifier.classify("UIDAI", message)

        assertEquals(SmsTopic.GOV_UTILITY, result.topic)
    }

    // =========================================================================
    // Spam / Promotional classification
    // =========================================================================

    @Test
    fun testSpamPromotionalMessage() {
        val message = "Exclusive Deal! Flat 50% discount on summer shoes. Click here to claim now! T&C apply. Optout reply STOP."
        val result = SmsClassifier.classify("DEALSHUB", message)

        assertEquals(SmsTopic.SPAM_PROMO, result.topic)
        assertNull(result.extractedOtp)
    }

    @Test
    fun testSpamCasinoKeyword() {
        val message = "You've been invited to our premium casino rewards program. Play and win big!"
        val result = SmsClassifier.classify("CASINOS", message)

        assertEquals(SmsTopic.SPAM_PROMO, result.topic)
    }

    @Test
    fun testSpamLoanKeyword() {
        val message = "Congratulations! You have a pre-approved loan of $10,000. Apply now within 24 hours."
        val result = SmsClassifier.classify("LOANFAST", message)

        assertEquals(SmsTopic.SPAM_PROMO, result.topic)
    }

    // =========================================================================
    // Personal classification
    // =========================================================================

    @Test
    fun testPersonalSms() {
        val message = "Hey, are you free this evening for dinner?"
        val result = SmsClassifier.classify("+14155552671", message)

        assertEquals(SmsTopic.PERSONAL, result.topic)
        assertNull(result.extractedOtp)
    }

    @Test
    fun testPersonalSmsFromNumericSender() {
        val message = "Running 10 mins late, sorry!"
        val result = SmsClassifier.classify("19175550001", message)

        assertEquals(SmsTopic.PERSONAL, result.topic)
    }

    // =========================================================================
    // extractOtp — direct API tests
    // =========================================================================

    @Test
    fun testExtractOtpDirectlyExplicitPattern() {
        assertEquals("847293", SmsClassifier.extractOtp("Your OTP is 847293."))
        assertEquals("1234", SmsClassifier.extractOtp("Passcode: 1234"))
        assertEquals("77821", SmsClassifier.extractOtp("77821 is your verification code"))
    }

    @Test
    fun testExtractOtpReturnsNullWhenAbsent() {
        assertNull(SmsClassifier.extractOtp("Hello, how are you?"))
        assertNull(SmsClassifier.extractOtp(""))
    }

    @Test
    fun testExtractOtpSkipsYears() {
        // "2026" should be filtered out; "938274" should be returned
        val body = "938274 is your auth code. Valid until 2026-10-10."
        assertEquals("938274", SmsClassifier.extractOtp(body))
    }

    // =========================================================================
    // extractAmount — direct API tests
    // =========================================================================

    @Test
    fun testExtractAmountDollarSign() {
        val result = SmsClassifier.extractAmount("Your charge of \$49.99 has been processed.")
        assertNotNull(result)
        assertTrue(result!!.contains("49.99"))
    }

    @Test
    fun testExtractAmountRupeeSymbol() {
        val result = SmsClassifier.extractAmount("Amount ₹1,250.00 debited from your account.")
        assertNotNull(result)
        assertTrue(result!!.contains("1,250"))
    }

    @Test
    fun testExtractAmountRsPrefix() {
        val result = SmsClassifier.extractAmount("Rs. 4,500.00 has been transferred.")
        assertNotNull(result)
        assertTrue(result!!.contains("4,500"))
    }

    @Test
    fun testExtractAmountReturnsNullWhenAbsent() {
        assertNull(SmsClassifier.extractAmount("No monetary values in this text."))
    }

    // =========================================================================
    // Merchant extraction — via classify()
    // =========================================================================

    @Test
    fun testMerchantExtractedFromDashedSenderPrefix() {
        // Sender "VK-CHASE" → merchant should be the part after the dash, uppercased
        val message = "You spent USD 20.00 at a store."
        val result = SmsClassifier.classify("VK-CHASE", message)

        // Banking path triggers via "spent"
        assertEquals(SmsTopic.BANKING_FINANCE, result.topic)
        assertEquals("CHASE", result.merchantOrSenderName)
    }

    @Test
    fun testMerchantExtractedFromKnownEntityInBody() {
        // "debited" is a financial keyword; "Netflix" is in the known-entity list
        val message = "USD 15.99 has been debited for your Netflix subscription."
        val result = SmsClassifier.classify("BILLING", message)

        assertEquals(SmsTopic.BANKING_FINANCE, result.topic)
        assertEquals("Netflix", result.merchantOrSenderName)
    }

    // =========================================================================
    // Priority ordering — OTP beats Banking even when both keywords present
    // =========================================================================

    @Test
    fun testOtpTakesPriorityOverBanking() {
        // Message has both OTP and banking keywords — OTP must win (highest priority)
        val message = "Your OTP is 837261 for a transaction of USD 250.00 on your HDFC card ending 5678."
        val result = SmsClassifier.classify("AD-HDFCBK", message)

        assertEquals(SmsTopic.CRITICAL_OTP, result.topic)
        assertEquals("837261", result.extractedOtp)
        assertNotNull(result.extractedAmount)  // Amount is still extracted even in OTP path
    }

    // =========================================================================
    // SmsTopic enum helper
    // =========================================================================

    @Test
    fun testSmsTopicFromStringCaseInsensitive() {
        assertEquals(SmsTopic.CRITICAL_OTP, SmsTopic.fromString("critical_otp"))
        assertEquals(SmsTopic.BANKING_FINANCE, SmsTopic.fromString("BANKING_FINANCE"))
        assertEquals(SmsTopic.PERSONAL, SmsTopic.fromString("unknown_value"))  // fallback
    }
}
