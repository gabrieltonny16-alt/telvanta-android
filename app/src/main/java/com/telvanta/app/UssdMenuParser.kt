package com.telvanta.app

import java.util.Locale

/** One visible option parsed from a live USSD menu. Its path is valid only for that captured menu. */
data class UssdMenuOption(
    val optionNumber: Int,
    val label: String,
    val amountKes: Long?,
    val dataDescription: String?
)

/**
 * Parses visible USSD menu lines conservatively. Parsed options are candidates for review,
 * not permanent signatures: menus can vary by SIM, offer, time, and network.
 */
object UssdMenuParser {
    private val optionLine = Regex("""^\s*(\d{1,2})\s*[.)\-:]?\s+(.+?)\s*$""")
    private val amountPatterns = listOf(
        Regex("""(?i)\bKSH\.?\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""(?i)\bKES\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""(?i)\b([\d,]+(?:\.\d{1,2})?)\s*(?:KSH|KES)\b""")
    )

    fun parse(menuText: String): List<UssdMenuOption> {
        if (menuText.isBlank()) return emptyList()
        return menuText.lineSequence().mapNotNull { line ->
            val match = optionLine.find(line) ?: return@mapNotNull null
            val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val label = match.groupValues[2].trim().take(240)
            if (label.isBlank()) return@mapNotNull null

            val amount = amountPatterns.asSequence()
                .mapNotNull { it.find(label)?.groupValues?.getOrNull(1) }
                .mapNotNull { raw -> raw.replace(",", "").toBigDecimalOrNull() }
                .mapNotNull { value -> runCatching { value.longValueExact() }.getOrNull() }
                .firstOrNull { it >= 0L }

            val lower = label.lowercase(Locale.ROOT)
            val looksLikeData = listOf("mb", "gb", "data", "bundle", "daily", "weekly", "monthly", "valid").any(lower::contains)
            UssdMenuOption(
                optionNumber = number,
                label = label,
                amountKes = amount,
                dataDescription = if (looksLikeData) label else null
            )
        }.distinctBy { it.optionNumber }
            .take(30)
            .toList()
    }
}
