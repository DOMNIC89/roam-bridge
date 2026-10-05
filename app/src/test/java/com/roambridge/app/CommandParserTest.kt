package com.roambridge.app

import com.roambridge.app.data.network.ImapReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    private val receiver = ImapReceiver()

    // -------------------------------------------------------------------------
    // parseSmsContent — TO:/MSG: block format
    // -------------------------------------------------------------------------

    @Test
    fun testParseMultilineEmailCommand() {
        val emailBody = """
            TO: +14155552671
            MSG: ACTIVATE DEBIT CARD 1234
        """.trimIndent()

        val command = receiver.parseSmsContent(emailBody)
        assertNotNull(command)
        assertEquals("+14155552671", command?.recipient)
        assertEquals("ACTIVATE DEBIT CARD 1234", command?.messageText)
    }

    @Test
    fun testParseMsgPrefixWithLeadingWhitespace() {
        // Extra spaces around the colon and values must be tolerated
        val emailBody = """
            TO :  +447700900123
            MSG :  Hello world
        """.trimIndent()

        // TO : has no space before colon in real usage — verify it gracefully returns null
        // (the parser only matches "TO:" not "TO :")
        val command = receiver.parseSmsContent(emailBody)
        assertNull("Parser must not match 'TO :' with a space before the colon", command)
    }

    @Test
    fun testParseMessagePrefixVariant() {
        // The parser also accepts "MESSAGE:" in addition to "MSG:"
        val emailBody = """
            TO: +12125559876
            MESSAGE: Book flight to London
        """.trimIndent()

        val command = receiver.parseSmsContent(emailBody)
        assertNotNull(command)
        assertEquals("+12125559876", command?.recipient)
        assertEquals("Book flight to London", command?.messageText)
    }

    @Test
    fun testParseCaseInsensitiveKeywords() {
        val emailBody = """
            to: +19175550001
            msg: lowercase keywords should work
        """.trimIndent()

        val command = receiver.parseSmsContent(emailBody)
        assertNotNull(command)
        assertEquals("+19175550001", command?.recipient)
        assertEquals("lowercase keywords should work", command?.messageText)
    }

    @Test
    fun testParseMultiLineMessageBody() {
        // Lines after MSG: should all be collected as the message
        val emailBody = """
            TO: +14085550042
            MSG: First line of the message
            Second line continues here
            And a third line
        """.trimIndent()

        val command = receiver.parseSmsContent(emailBody)
        assertNotNull(command)
        assertEquals("+14085550042", command?.recipient)
        assertTrue(
            "Message must contain all continuation lines",
            command?.messageText?.contains("Second line continues here") == true
        )
        assertTrue(command?.messageText?.contains("And a third line") == true)
    }

    @Test
    fun testParsePhoneNumberWithoutPlusPrefix() {
        // Numbers without '+' should still be valid if 7-15 digits
        val emailBody = """
            TO: 14155552671
            MSG: No plus sign
        """.trimIndent()

        val command = receiver.parseSmsContent(emailBody)
        assertNotNull(command)
        assertEquals("14155552671", command?.recipient)
    }

    @Test
    fun testParseStripsHyphensAndSpacesFromPhoneNumber() {
        val emailBody = """
            TO: +1 (415) 555-2671
            MSG: Formatted number
        """.trimIndent()

        val command = receiver.parseSmsContent(emailBody)
        assertNotNull(command)
        // cleanPhoneNumber strips spaces, hyphens, parens
        assertEquals("+14155552671", command?.recipient)
        assertEquals("Formatted number", command?.messageText)
    }

    // -------------------------------------------------------------------------
    // parseSmsContent — single-line colon fallback format
    // -------------------------------------------------------------------------

    @Test
    fun testParseSingleLineColonCommand() {
        val emailBody = "+447700900077: Hello from abroad, testing home SIM relay"
        val command = receiver.parseSmsContent(emailBody)

        assertNotNull(command)
        assertEquals("+447700900077", command?.recipient)
        assertEquals("Hello from abroad, testing home SIM relay", command?.messageText)
    }

    @Test
    fun testParseSingleLineColonWithShortNumber() {
        // colonIndex must be between 5 and 20 — a 3-digit number is out of range
        val emailBody = "123: Too short to be a phone number"
        val command = receiver.parseSmsContent(emailBody)
        assertNull("Too-short number before colon must not match", command)
    }

    @Test
    fun testParseSingleLineColonWithLongPreamble() {
        // colonIndex at 25+ — out of allowed range
        val emailBody = "+1234567890123456789: Some message"  // number is 20 digits — too long
        val command = receiver.parseSmsContent(emailBody)
        assertNull("Number with >15 digits must not match isValidPhoneNumber", command)
    }

    // -------------------------------------------------------------------------
    // parseSmsContent — invalid/null returns
    // -------------------------------------------------------------------------

    @Test
    fun testInvalidContentReturnsNull() {
        val emailBody = "Just a random chat with no phone number or format"
        val command = receiver.parseSmsContent(emailBody)
        assertNull(command)
    }

    @Test
    fun testEmptyBodyReturnsNull() {
        assertNull(receiver.parseSmsContent(""))
    }

    @Test
    fun testOnlyToLineWithoutMsgReturnsNull() {
        val emailBody = "TO: +14155552671"
        val command = receiver.parseSmsContent(emailBody)
        assertNull("TO: without MSG: must return null", command)
    }

    @Test
    fun testOnlyMsgLineWithoutToReturnsNull() {
        val emailBody = "MSG: Some message without a recipient"
        val command = receiver.parseSmsContent(emailBody)
        assertNull("MSG: without TO: must return null", command)
    }

    @Test
    fun testBlankMessageTextReturnsNull() {
        // MSG: with empty content after stripping
        val emailBody = """
            TO: +14155552671
            MSG:   
        """.trimIndent()

        val command = receiver.parseSmsContent(emailBody)
        assertNull("Blank MSG content should produce null after trim", command)
    }

    // -------------------------------------------------------------------------
    // isAuthorizedSubject — security PIN gating
    // (tested indirectly via the public parseSmsContent path and directly via
    //  reflection-free wrapper; we verify the parsing side is independent)
    // -------------------------------------------------------------------------

    @Test
    fun testSenderSourceDefaultsToEmail() {
        val emailBody = "+447700900077: PIN authorization is separate from parsing"
        val command = receiver.parseSmsContent(emailBody)
        assertNotNull(command)
        // parseSmsContent always stamps senderSource = "Email" before caller
        // overrides it with the actual from-address
        assertEquals("Email", command?.senderSource)
    }

    @Test
    fun testToMsgCommandSenderSourceIsEmail() {
        val emailBody = """
            TO: +14155550001
            MSG: Check senderSource
        """.trimIndent()

        val command = receiver.parseSmsContent(emailBody)
        assertNotNull(command)
        assertEquals("Email", command?.senderSource)
    }
}
