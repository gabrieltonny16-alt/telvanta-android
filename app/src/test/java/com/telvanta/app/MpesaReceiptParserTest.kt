package com.telvanta.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpesaReceiptParserTest {
    @Test
    fun extractsFullSenderDetailsFromSafaricomTillConfirmation() {
        val message = "UK7JD1A27L Confirmed.on 7/11/26 at 2:28 PMKSH83.00 received from 254140843700 Gabriel  Ngui Tonny. New Account balance is KSH83.64. Transaction cost, KSH0.00. Receiving payments of Ksh 500 & below in your Lipa na M-PESA till is now FREE!"

        val parsed = MpesaReceiptParser.parse("M-PESA", message)

        assertEquals("UK7JD1A27L", parsed.receiptCode)
        assertEquals(83L, parsed.amountKes)
        assertEquals("254140843700", parsed.senderPhone)
        assertEquals("Gabriel Ngui Tonny", parsed.senderName)
        assertTrue(parsed.mentionsConfirmation)
    }
    @Test
    fun extractsExactTillConfirmationFormatSharedByUser() {
        val message = "UFAL477KQJ Confirmed. on 12/11/26 at 8:25 AM. Ksh500.00 received from 254712345678 JANE DOE. New Account balance is Ksh15,450.00."

        val parsed = MpesaReceiptParser.parse("M-PESA", message)

        assertEquals("UFAL477KQJ", parsed.receiptCode)
        assertEquals(500L, parsed.amountKes)
        assertEquals("254712345678", parsed.senderPhone)
        assertEquals("JANE DOE", parsed.senderName)
        assertTrue(parsed.mentionsConfirmation)
    }

}
