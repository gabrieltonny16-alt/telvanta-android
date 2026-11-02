package com.telvanta.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Opt-in local notification observer.
 * Android requires the device owner to enable Notification Access in system settings.
 * This service only records notifications from recognizable Safaricom/M-PESA apps.
 * It does not verify payment or trigger orders, USSD, or delivery.
 */
class MpesaNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName.orEmpty()
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        val source = (packageName + " " + title).lowercase()
        val message = text.lowercase()
        val looksLikeMpesa = source.contains("safaricom") ||
            source.contains("mpesa") || source.contains("m-pesa") ||
            message.contains("m-pesa") || message.contains("mpesa") ||
            message.contains("confirmed") && message.contains("ksh")

        if (!looksLikeMpesa || text.isBlank()) return

        // Store only the latest candidate locally for explicit review; never auto-mark paid.
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_PACKAGE, packageName)
            .putString(KEY_TITLE, title.take(120))
            .putString(KEY_TEXT, text.take(1200))
            .putLong(KEY_TIME, sbn.postTime)
            .apply()
    }

    companion object {
        const val PREFS = "bingwapulse_mpesa_notifications"
        const val KEY_PACKAGE = "source_package"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_TIME = "posted_at"
    }
}
