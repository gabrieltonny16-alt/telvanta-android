package com.telvanta.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickDialValidationTest {
    @Test
    fun normalizes07Numbers() {
        assertEquals("+254712345678", normalizeKenyanMobileNumber("0712345678"))
    }

    @Test
    fun normalizes01Numbers() {
        assertEquals("+254112345678", normalizeKenyanMobileNumber("0112345678"))
    }

    @Test
    fun accepts254AndInternationalFormats() {
        assertEquals("+254712345678", normalizeKenyanMobileNumber("254712345678"))
        assertEquals("+254112345678", normalizeKenyanMobileNumber("+254112345678"))
    }

    @Test
    fun acceptsNineDigitLocalMobileInput() {
        assertEquals("+254712345678", normalizeKenyanMobileNumber("712345678"))
        assertEquals("+254112345678", normalizeKenyanMobileNumber("112345678"))
    }

    @Test
    fun rejectsUnsupportedOrIncompleteNumbers() {
        assertNull(normalizeKenyanMobileNumber("071234567"))
        assertNull(normalizeKenyanMobileNumber("254912345678"))
        assertNull(normalizeKenyanMobileNumber("0201234567"))
        assertNull(normalizeKenyanMobileNumber(""))
    }
}
