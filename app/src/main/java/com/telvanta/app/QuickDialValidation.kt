package com.telvanta.app

/**
 * Normalizes supported Kenyan mobile-number formats to E.164.
 * Returns null for incomplete, unsupported, or malformed numbers.
 */
internal fun normalizeKenyanMobileNumber(input: String): String? {
    val digits = input.filter { it.isDigit() }
    val normalized = when {
        digits.length == 12 && digits.startsWith("254") -> "+$digits"
        digits.length == 10 && digits.startsWith("0") -> "+254" + digits.drop(1)
        digits.length == 9 && (digits.startsWith("7") || digits.startsWith("1")) -> "+254$digits"
        else -> return null
    }
    return normalized.takeIf { Regex("^\\+254[17][0-9]{8}$").matches(it) }
}
