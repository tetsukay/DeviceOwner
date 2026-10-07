package app.tetsukay.deviceowner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun verifyAcceptsCorrectPin() {
        val h = PinHasher.hash("1234")
        assertTrue(PinHasher.verify("1234", h.hashHex, h.saltHex))
    }

    @Test
    fun verifyRejectsWrongPin() {
        val h = PinHasher.hash("1234")
        assertFalse(PinHasher.verify("1235", h.hashHex, h.saltHex))
        assertFalse(PinHasher.verify("", h.hashHex, h.saltHex))
    }

    @Test
    fun hashDoesNotContainPlainPin() {
        val h = PinHasher.hash("987654")
        assertFalse(h.hashHex.contains("987654"))
        assertEquals(64, h.hashHex.length) // SHA-256
    }

    @Test
    fun sameSaltGivesSameHash() {
        val salt = ByteArray(16) { it.toByte() }
        assertEquals(PinHasher.hash("1234", salt), PinHasher.hash("1234", salt))
    }

    @Test
    fun differentSaltGivesDifferentHash() {
        val a = PinHasher.hash("1234")
        val b = PinHasher.hash("1234")
        assertNotEquals(a.saltHex, b.saltHex)
        assertNotEquals(a.hashHex, b.hashHex)
    }

    @Test
    fun verifyRejectsMalformedStoredValues() {
        val h = PinHasher.hash("1234")
        assertFalse(PinHasher.verify("1234", "zz", h.saltHex))
        assertFalse(PinHasher.verify("1234", h.hashHex, "abc"))
        assertFalse(PinHasher.verify("1234", h.hashHex.dropLast(2), h.saltHex))
    }

    @Test
    fun pinFormatValidation() {
        assertTrue(PinHasher.isValidPin("0000"))
        assertTrue(PinHasher.isValidPin("123456789012"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("1234567890123"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }
}
