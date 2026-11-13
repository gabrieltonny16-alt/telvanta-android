package com.telvanta.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Opt-in M-PESA notification reader and authenticated relay.
 *
 * Notifications are captured under the signed-in user's Supabase JWT. The server
 * validates the receipt and automatically matches it to one pending Quick Dial order.
 * Incomplete, duplicate or ambiguous notifications remain held and never dispatch USSD.
 */
class MpesaNotificationListenerService : NotificationListenerService() {
    private val relayExecutor = Executors.newSingleThreadExecutor()
    private var retryReceiverRegistered = false

    private val retryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_RETRY_RELAY) retryLatestCandidate()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        ensureActivityChannel()
        if (!retryReceiverRegistered) {
            val filter = IntentFilter(ACTION_RETRY_RELAY)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(retryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(retryReceiver, filter)
            }
            retryReceiverRegistered = true
        }
        retryLatestCandidate()
    }

    override fun onListenerDisconnected() {
        if (retryReceiverRegistered) {
            runCatching { unregisterReceiver(retryReceiver) }
            retryReceiverRegistered = false
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName.orEmpty()
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val normalText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }.orEmpty()
        val text = listOf(bigText, normalText, subText, summaryText, textLines, title)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
            .take(1600)

        val source = (packageName + " " + title).lowercase()
        val message = text.lowercase()
        val looksLikeMpesa = source.contains("safaricom") ||
            source.contains("mpesa") || source.contains("m-pesa") ||
            message.contains("m-pesa") || message.contains("mpesa") ||
            (message.contains("confirmed") && (message.contains("ksh") || message.contains("kes")))
        if (!looksLikeMpesa || text.isBlank()) return

        val parsed = MpesaReceiptParser.parse(title, text)
        val settings = getSharedPreferences("bingwapulse_settings", MODE_PRIVATE)
        val isTillConfirmation = parsed.isTillConfirmation
        val processingEnabled = if (isTillConfirmation) settings.getBoolean("process_till", true)
            else settings.getBoolean("process_mpesa", true)
        if (!processingEnabled) return
        val receipt = parsed.receiptCode ?: extractReceipt(text)
        val amount = extractAmount(text)
        val sender = parsed.senderPhone ?: extractSenderPhone(text)
        val senderName = parsed.senderName
        val completeness = when {
            parsed.isTillConfirmation && receipt != null && amount != null && sender != null -> "REVIEW_READY"
            receipt != null || amount != null || sender != null || senderName != null -> "PARTIAL_CANDIDATE"
            else -> "RAW_NOTIFICATION_ONLY"
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_PACKAGE, packageName)
            .putString(KEY_TITLE, title.take(120))
            .putString(KEY_TEXT, text)
            .putString(KEY_RECEIPT, receipt.orEmpty())
            .putString(KEY_AMOUNT, amount.orEmpty())
            .putString(KEY_SENDER_PHONE, sender.orEmpty())
            .putString(KEY_SENDER_NAME, senderName.orEmpty())
            .putString(KEY_CANDIDATE_STATUS, completeness)
            .putLong(KEY_TIME, sbn.postTime)
            .putString(KEY_SYNC_STATUS, "QUEUED")
            .apply()

        enqueueRelay(packageName, title, text, receipt, amount, sender, senderName, completeness, sbn.postTime)
        postCapturedPaymentAlert(receipt, amount, completeness)
    }


    private fun ensureActivityChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel("bingwapulse_live_activity", "BingwaPulse live activity", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Payment alerts and order activity" }
            )
        }
    }

    private fun postCapturedPaymentAlert(receipt: String?, amount: String?, status: String) {
        ensureActivityChannel()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val detail = buildString {
            append("Payment notification captured · ")
            append(status.replace('_', ' ').lowercase())
            if (!amount.isNullOrBlank()) append(" · KES ").append(amount)
            if (!receipt.isNullOrBlank()) append(" · ").append(receipt)
        }
        val notification = Notification.Builder(this, "bingwapulse_live_activity")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("BingwaPulse · Payment alert received")
            .setContentText(detail.take(180))
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun retryLatestCandidate() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val rawText = prefs.getString(KEY_TEXT, "").orEmpty()
        if (rawText.isBlank()) return
        val status = prefs.getString(KEY_SYNC_STATUS, "").orEmpty()
        if (status == "SYNCED") return
        enqueueRelay(
            prefs.getString(KEY_PACKAGE, "").orEmpty(),
            prefs.getString(KEY_TITLE, "").orEmpty(),
            rawText,
            prefs.getString(KEY_RECEIPT, "").orEmpty().ifBlank { null },
            prefs.getString(KEY_AMOUNT, "").orEmpty().ifBlank { null },
            prefs.getString(KEY_SENDER_PHONE, "").orEmpty().ifBlank { null },
            prefs.getString(KEY_SENDER_NAME, "").orEmpty().ifBlank { null },
            prefs.getString(KEY_CANDIDATE_STATUS, "RAW_NOTIFICATION_ONLY").orEmpty(),
            prefs.getLong(KEY_TIME, 0L)
        )
    }

    private fun enqueueRelay(
        sourcePackage: String,
        title: String,
        rawText: String,
        receipt: String?,
        amount: String?,
        sender: String?,
        senderName: String?,
        candidateStatus: String,
        postedAt: Long
    ) {
        relayExecutor.execute {
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            try {
                val store = SecureSessionStore(applicationContext)
                var session = store.load()
                if (session == null) {
                    prefs.edit().putString(KEY_SYNC_STATUS, "SIGN_IN_REQUIRED").apply()
                    return@execute
                }

                val api = BingwaSupabaseApi()
                if (session.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                    session = api.refreshSession(session)
                    store.save(session)
                }
                val businessId = session.businessId
                    ?: api.firstBusinessId(session)
                    ?: throw IllegalStateException("No active business workspace is linked to this account.")
                if (session.businessId == null) store.save(session.copy(businessId = businessId))

                val amountMinor = amount?.let {
                    BigDecimal(it).movePointRight(2).longValueExact()
                }?.takeIf { it > 0L }
                val fingerprintInput = listOf(
                    businessId, sourcePackage, title, receipt.orEmpty(),
                    amount.orEmpty(), sender.orEmpty(), senderName.orEmpty(), rawText, postedAt.toString()
                ).joinToString("\n")
                val fingerprint = sha256(fingerprintInput)

                api.submitMpesaNotificationCandidate(
                    session = session.copy(businessId = businessId),
                    businessId = businessId,
                    sourcePackage = sourcePackage,
                    title = title,
                    rawText = rawText,
                    receiptCandidate = receipt,
                    amountMinor = amountMinor,
                    senderPhoneCandidate = sender,
                    senderNameCandidate = senderName,
                    notificationHash = fingerprint,
                    candidateStatus = candidateStatus,
                    postedAtEpochMillis = postedAt
                )
                prefs.edit()
                    .putString(KEY_SYNC_STATUS, "SYNCED")
                    .putLong(KEY_SYNCED_AT, System.currentTimeMillis())
                    .remove(KEY_SYNC_ERROR)
                    .apply()
            } catch (error: Exception) {
                prefs.edit()
                    .putString(KEY_SYNC_STATUS, "RETRY_PENDING")
                    .putString(KEY_SYNC_ERROR, error.message?.take(180).orEmpty())
                    .apply()
            }
        }
    }

    private fun extractReceipt(text: String): String? {
        val confirmed = Regex(
            """\b([A-Z0-9]{8,16})\s+confirmed\b""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.getOrNull(1)
        if (!confirmed.isNullOrBlank()) return confirmed.uppercase()

        return Regex(
            """\b(?:receipt|transaction(?:\s+id)?|reference)\s*(?:no\.?|:)?\s*([A-Z0-9]{8,16})\b""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.getOrNull(1)?.uppercase()
    }

    private fun extractAmount(text: String): String? {
        val raw = Regex(
            """(?:\bKES|\bKSH|(?<=PM)KSH)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.getOrNull(1) ?: return null
        return raw.replace(",", "")
    }

    private fun extractSenderPhone(text: String): String? {
        val fromIndex = text.indexOf("from", ignoreCase = true)
        if (fromIndex < 0) return null
        val senderSection = text.substring(fromIndex, minOf(text.length, fromIndex + 180))
        val candidate = Regex(
            """(?<![0-9])(?:\+?254[17][0-9]{8}|0[17][0-9]{8})(?![0-9])"""
        ).find(senderSection)?.value ?: return null
        return when {
            candidate.startsWith("+") -> candidate.substring(1)
            candidate.startsWith("0") -> "254" + candidate.substring(1)
            else -> candidate
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    override fun onDestroy() {
        relayExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RETRY_RELAY = "com.telvanta.app.action.RETRY_MPESA_RELAY"
        const val PREFS = "bingwapulse_mpesa_notifications"
        const val KEY_PACKAGE = "source_package"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_RECEIPT = "receipt_candidate"
        const val KEY_AMOUNT = "amount_candidate"
        const val KEY_SENDER_PHONE = "sender_phone_candidate"
        const val KEY_SENDER_NAME = "sender_name_candidate"
        const val KEY_CANDIDATE_STATUS = "candidate_status"
        const val KEY_TIME = "posted_at"
        const val KEY_SYNC_STATUS = "sync_status"
        const val KEY_SYNCED_AT = "synced_at"
        const val KEY_SYNC_ERROR = "sync_error"
    }
}
