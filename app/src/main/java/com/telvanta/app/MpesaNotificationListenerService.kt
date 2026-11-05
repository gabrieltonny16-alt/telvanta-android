package com.telvanta.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Opt-in local M-PESA notification reader.
 *
 * It extracts best-effort candidates from the notification text for review.
 * These values are NOT authenticated payment proof and never trigger fulfilment.
 * A complete sender number is only retained when it appears after "from".
 */
class MpesaNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName.orEmpty()
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val normalText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val text = listOf(bigText, normalText)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
            .take(1600)

        val combined = "$title\n$text"
        val source = (packageName + " " + title).lowercase()
        val message = combined.lowercase()
        val looksLikeMpesa = source.contains("safaricom") ||
            source.contains("mpesa") || source.contains("m-pesa") ||
            message.contains("m-pesa") || message.contains("mpesa") ||
            (message.contains("confirmed") && (message.contains("ksh") || message.contains("kes")))

        if (!looksLikeMpesa || text.isBlank()) return

        val receipt = extractReceipt(combined)
        val amount = extractAmount(combined)
        val sender = extractSenderPhone(combined)
        val completeness = when {
            receipt != null && amount != null && sender != null -> "REVIEW_READY"
            receipt != null || amount != null || sender != null -> "PARTIAL_CANDIDATE"
            else -> "RAW_NOTIFICATION_ONLY"
        }

        // Keep the original text and extracted values locally for owner review only.
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_PACKAGE, packageName)
            .putString(KEY_TITLE, title.take(120))
            .putString(KEY_TEXT, text)
            .putString(KEY_RECEIPT, receipt.orEmpty())
            .putString(KEY_AMOUNT, amount.orEmpty())
            .putString(KEY_SENDER_PHONE, sender.orEmpty())
            .putString(KEY_CANDIDATE_STATUS, completeness)
            .putLong(KEY_TIME, sbn.postTime)
            .apply()
    }

    private fun extractReceipt(text: String): String? {
        val confirmed = Regex(
            """\b([A-Z0-9]{8,12})\s+confirmed\b""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.getOrNull(1)
        if (!confirmed.isNullOrBlank()) return confirmed.uppercase()

        return Regex(
            """\b(?:receipt|transaction(?:\s+id)?|reference)\s*(?:no\.?|:)?\s*([A-Z0-9]{8,12})\b""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.getOrNull(1)?.uppercase()
    }

    private fun extractAmount(text: String): String? {
        val raw = Regex(
            """\b(?:KES|KSH)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.getOrNull(1) ?: return null
        return raw.replace(",", "")
    }

    private fun extractSenderPhone(text: String): String? {
        val fromIndex = text.indexOf("from", ignoreCase = true)
        if (fromIndex < 0) return null
        val senderSection = text.substring(fromIndex, minOf(text.length, fromIndex + 140))
        val candidate = Regex(
            """(?<![0-9])(?:\+?254[17][0-9]{8}|0[17][0-9]{8})(?![0-9])"""
        ).find(senderSection)?.value ?: return null
        return when {
            candidate.startsWith("+") -> candidate.substring(1)
            candidate.startsWith("0") -> "254" + candidate.substring(1)
            else -> candidate
        }
    }

    companion object {
        const val PREFS = "bingwapulse_mpesa_notifications"
        const val KEY_PACKAGE = "source_package"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_RECEIPT = "receipt_candidate"
        const val KEY_AMOUNT = "amount_candidate"
        const val KEY_SENDER_PHONE = "sender_phone_candidate"
        const val KEY_CANDIDATE_STATUS = "candidate_status"
        const val KEY_TIME = "posted_at"
    }
}
