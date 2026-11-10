package com.telvanta.app

import org.junit.Assert.assertEquals
import org.junit.Test

class UssdScreenClassifierTest {
    @Test
    fun transactionReferenceAloneIsNotDeliveryProof() {
        val result = UssdScreenClassifier.classify("M-PESA message. Transaction ID QWERTY123456")
        assertEquals(UssdScreenType.UNKNOWN, result.type)
    }

    @Test
    fun genericMpesaConfirmationIsNotProviderPurchaseSuccess() {
        val result = UssdScreenClassifier.classify("M-PESA confirmed. You have received KES 100")
        assertEquals(UssdScreenType.UNKNOWN, result.type)
    }

    @Test
    fun explicitBundleActivationIsRecognized() {
        val result = UssdScreenClassifier.classify("Your data bundle has been activated successfully")
        assertEquals(UssdScreenType.SUCCESS_OR_RECEIPT, result.type)
    }

    @Test
    fun insufficientAirtimeTakesPriorityOverPaymentMethodWords() {
        val result = UssdScreenClassifier.classify("Payment method: Airtime. Insufficient airtime.")
        assertEquals(UssdScreenType.ERROR_OR_UNAVAILABLE, result.type)
    }

    @Test
    fun unknownMenuIsNeverGuessed() {
        val result = UssdScreenClassifier.classify("Welcome. Choose an option not listed in the configured recipe.")
        assertEquals(UssdScreenType.UNKNOWN, result.type)
    }
}
