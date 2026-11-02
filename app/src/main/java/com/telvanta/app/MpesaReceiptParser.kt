package com.telvanta.app

import java.util.Locale

/**
 * Conservative parser for locally observed M-PESA notification text.
 *
 * Parsed values are candidates only. A notification is not proof of payment;
 * callers must not mark an order paid from this result alone.
 */
data class MpesaReceiptCandidate(
    val receiptCode: String?,
    val amountKes: Long?,
    val senderPhone: String?,
    val mentionsConfirmation: Boolean
)

object MpesaReceiptParser {
    private val receiptPattern = Regex("""\b([A-Z0-9]{10})\b""")
    private val amountPatterns = listOf(
        Regex("""(?i)\bKSH\.?\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""(?i)\bKES\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""(?i)\b([\d,]+(?:\.\d{1,2})?)\s*(?:KSH|KES)\b""")
    )
    private val phonePattern = Regex("""(?<!\d)(?:\+?254|0)7\d{8}(?!\d)""")

    fun parse(title: String, body: String): MpesaReceiptCandidate {
        val text = (title + " " + body).trim()
        val normalized = text.uppercase(Locale.ROOT)
        val receipt = receiptPattern.findAll(normalized)
            .map { it.groupValues[1] }
            .firstOrNull { it.any(Char::isLetter) && it.any(Char::isDigit) }
        val amount = amountPatterns.asSequence()
            .mapNotNull { it.find(text)?.groupValues?.getOrNull(1) }
            .mapNotNull { raw -> raw.replace(",", "").toBigDecimalOrNull() }
            .mapNotNull { value -> runCatching { value.longValueExact() }.getOrNull() }
            .firstOrNull { it >= 0L }
        val phone = phonePattern.find(text)?.value
        val confirmation = normalized.contains("CONFIRMED") ||
            normalized.contains("RECEIVED") ||
            normalized.contains("YOU HAVE SENT")
        return MpesaReceiptCandidate(receipt, amount, phone, confirmation)
    }
}
