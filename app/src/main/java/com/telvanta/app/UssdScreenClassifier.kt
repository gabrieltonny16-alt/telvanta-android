package com.telvanta.app

import java.util.Locale

/**
 * Classifies captured USSD text for review and safe navigation planning.
 *
 * This is deliberately read-only: it never selects a menu option, confirms a purchase,
 * enters a PIN, or marks an order paid. A classification is a hint, not proof of a
 * successful transaction or of an offer's eligibility.
 */
enum class UssdScreenType {
    PAYMENT_METHOD,
    PURCHASE_CONFIRMATION,
    SUCCESS_OR_RECEIPT,
    MENU,
    ERROR_OR_UNAVAILABLE,
    UNKNOWN
}

data class UssdScreenClassification(
    val type: UssdScreenType,
    val matchedSignals: List<String>
)

object UssdScreenClassifier {
    private val paymentMethodPatterns = listOf(
        Regex("""(?i)\bairtime\b"""),
        Regex("""(?i)\bm-?pesa\b"""),
        Regex("""(?i)\bbonga(?:\s+points?)?\b"""),
        Regex("""(?i)\bpayment\s+method\b"""),
        Regex("""(?i)\bpay\s+using\b"""),
        Regex("""(?i)\bchoose\s+(?:a\s+)?payment\b""")
    )

    private val confirmationPatterns = listOf(
        Regex("""(?i)\bconfirm(?:ation)?\b"""),
        Regex("""(?i)\bdo\s+you\s+want\s+to\s+(?:buy|purchase|subscribe)\b"""),
        Regex("""(?i)\bproceed\s+with\s+(?:purchase|payment)\b"""),
        Regex("""(?i)\bwill\s+be\s+charged\b"""),
        Regex("""(?i)\bto\s+continue\b"""),
        Regex("""(?i)\benter\s+(?:your\s+)?(?:pin|password)\b""")
    )

    private val successPatterns = listOf(
        Regex("""(?i)\b(transaction|purchase|payment)\s+(?:was\s+)?successful\b"""),
        Regex("""(?i)\byou\s+have\s+(?:successfully\s+)?purchased\b"""),
        Regex("""(?i)\bbundle\s+(?:has\s+been\s+)?activated\b"""),
        Regex("""(?i)\btransaction\s+id\b"""),
        Regex("""(?i)\bmpesa\s+message\b""")
    )

    private val errorPatterns = listOf(
        Regex("""(?i)\bnot\s+available\b"""),
        Regex("""(?i)\binvalid\s+(?:option|choice|input|response)\b"""),
        Regex("""(?i)\binsufficient\s+(?:airtime|balance|funds)\b"""),
        Regex("""(?i)\btry\s+again\s+later\b"""),
        Regex("""(?i)\brequest\s+failed\b"""),
        Regex("""(?i)\bservice\s+unavailable\b"""),
        Regex("""(?i)\bsession\s+expired\b""")
    )

    private val menuOptionPattern = Regex("""(?m)^\s*\d{1,2}\s*[.)\-:]?\s+\S.+$""")

    fun classify(screenText: String): UssdScreenClassification {
        if (screenText.isBlank()) {
            return UssdScreenClassification(UssdScreenType.UNKNOWN, emptyList())
        }

        val normalized = screenText.trim().lowercase(Locale.ROOT)
        val paymentSignals = matchingSignals(paymentMethodPatterns, normalized)
        val confirmationSignals = matchingSignals(confirmationPatterns, normalized)
        val successSignals = matchingSignals(successPatterns, normalized)
        val errorSignals = matchingSignals(errorPatterns, normalized)

        // Confirmation/payment screens take precedence over generic menu detection.
        if (paymentSignals.isNotEmpty()) {
            return UssdScreenClassification(UssdScreenType.PAYMENT_METHOD, paymentSignals)
        }
        if (confirmationSignals.isNotEmpty()) {
            return UssdScreenClassification(UssdScreenType.PURCHASE_CONFIRMATION, confirmationSignals)
        }
        if (successSignals.isNotEmpty()) {
            return UssdScreenClassification(UssdScreenType.SUCCESS_OR_RECEIPT, successSignals)
        }
        if (errorSignals.isNotEmpty()) {
            return UssdScreenClassification(UssdScreenType.ERROR_OR_UNAVAILABLE, errorSignals)
        }
        if (menuOptionPattern.containsMatchIn(screenText)) {
            return UssdScreenClassification(UssdScreenType.MENU, listOf("numbered_menu_option"))
        }
        return UssdScreenClassification(UssdScreenType.UNKNOWN, emptyList())
    }

    private fun matchingSignals(patterns: List<Regex>, text: String): List<String> =
        patterns.mapIndexedNotNull { index, pattern ->
            if (pattern.containsMatchIn(text)) "signal_${index + 1}" else null
        }
}
