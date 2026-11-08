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
    val senderName: String?,
    val mentionsConfirmation: Boolean
)

object MpesaReceiptParser {
    private val receiptPattern = Regex("""\b([A-Z0-9]{8,16})\s+confirmed\b""", RegexOption.IGNORE_CASE)
    private val amountPatterns = listOf(
        Regex("""(?i)\bKSH\.?\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""(?i)\bKES\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""(?i)\b([\d,]+(?:\.\d{1,2})?)\s*(?:KSH|KES)\b""")
    )
    private val phonePattern = Regex("""(?<!\d)(?:\+?254|0)[17]\d{8}(?!\d)""")
    private val senderPattern = Regex(
        """(?is)\bfrom\s+(?:\+?254[17]\d{8}|0[17]\d{8})\s+(.+?)(?=\s+(?:on\s+\d{1,2}/\d{1,2}/\d{2,4}|new\s+account\s+balance|transaction\s+cost|receiving\s+payments)|[.]?\s*$)"""
    )

    fun parse(title: String, body: String): MpesaReceiptCandidate {
        val text = (title + " " + body).trim()
        val normalized = text.uppercase(Locale.ROOT)
        val receipt = receiptPattern.find(text)?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT)
            ?: Regex("""\b([A-Z0-9]{10})\b""").find(normalized)?.groupValues?.getOrNull(1)
                ?.takeIf { it.any(Char::isLetter) && it.any(Char::isDigit) }
        val amount = amountPatterns.asSequence()
            .mapNotNull { it.find(text)?.groupValues?.getOrNull(1) }
            .mapNotNull { raw -> raw.replace(",", "").toBigDecimalOrNull() }
            .mapNotNull { value -> runCatching { value.longValueExact() }.getOrNull() }
            .firstOrNull { it >= 0L }
        val phone = phonePattern.find(text)?.value?.let {
            when {
                it.startsWith("+") -> it.drop(1)
                it.startsWith("0") -> "254" + it.drop(1)
                else -> it
            }
        }
        val senderName = senderPattern.find(text)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.trimEnd('.', ',', ';', ':')
            ?.replace(Regex("""\s+"""), " ")
            ?.takeIf { name -> name.isNotBlank() && name.any(Char::isLetter) }
        val confirmation = normalized.contains("CONFIRMED") || normalized.contains("RECEIVED") ||
            normalized.contains("YOU HAVE SENT")
        return MpesaReceiptCandidate(receipt, amount, phone, senderName, confirmation)
    }
}
