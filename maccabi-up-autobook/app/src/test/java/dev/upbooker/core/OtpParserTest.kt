package dev.upbooker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpParserTest {
    @Test
    fun `extracts code when the sender matches`() {
        assertEquals("482913", OtpParser.extract("Maccabi", "Your code: 482913", "maccabi"))
    }

    @Test
    fun `extracts code when the body mentions the keyword`() {
        assertEquals("1234", OtpParser.extract("+972501234567", "קוד הכניסה שלך למכבי הוא 1234", "מכבי"))
    }

    @Test
    fun `ignores unrelated messages`() {
        assertNull(OtpParser.extract("Bank", "Your code is 999999", "מכבי"))
    }

    @Test
    fun `ignores numbers that are too long to be a code`() {
        assertNull(OtpParser.extract("Maccabi", "Call 0747553333", "Maccabi"))
    }

    @Test
    fun `blank keyword never matches`() {
        assertNull(OtpParser.extract("Maccabi", "123456", ""))
    }
}
