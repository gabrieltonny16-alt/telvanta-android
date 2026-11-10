package com.bingwapulse.connectpulse

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Opt-in receiver-side notification relay for Safaricom Sambaza confirmations.
 * It reads only SMS app notifications; ConnectPulse is not the default SMS app
 * and does not read, delete, or modify the SMS inbox.
 */
class SambazaReceiverNotificationListener : NotificationListenerService() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName.orEmpty()
        if (packageName !in SUPPORTED_SMS_PACKAGES) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val normalText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }.orEmpty()
        val message = listOf(bigText, normalText, textLines)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
            .take(1200)

        val content = (title + "\n" + message).lowercase()
        val relevant = content.contains("safaricom") &&
            (content.contains("airtime") || content.contains("sambaza")) &&
            (content.contains("received") || content.contains("credited"))
        if (!relevant || message.isBlank()) return

        val credentials = ConnectPulseSecureStore(applicationContext).load() ?: return
        val fingerprint = sha256("$packageName\n$title\n$message\n${sbn.postTime}")
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_EVENT, null) == fingerprint) return
        prefs.edit().putString(KEY_LAST_EVENT, fingerprint).apply()

        executor.execute {
            runCatching {
                ConnectPulseApi.reportSambazaReceiverNotification(
                    credentials = credentials,
                    sourcePackage = packageName,
                    title = title,
                    text = message,
                    postedAtEpochMillis = sbn.postTime
                )
            }
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val PREFS = "sambaza_receiver_notifications"
        private const val KEY_LAST_EVENT = "last_event_hash"
        private val SUPPORTED_SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.messaging"
        )
    }
}
