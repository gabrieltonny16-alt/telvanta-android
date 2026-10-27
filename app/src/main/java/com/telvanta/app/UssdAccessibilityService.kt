package com.telvanta.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * First-stage USSD observer. It records visible session text for diagnostics but
 * deliberately does not click/submit USSD choices until a verified carrier menu
 * flow has been configured. This prevents accidental purchases on an unknown menu.
 */
class UssdAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100L
        }
        recordState("SERVICE_CONNECTED", "Accessibility service enabled")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val root = rootInActiveWindow
        val text = buildString {
            event.text.forEach { append(it).append(' ') }
            if (isBlank()) append(readVisibleText(root))
            else if (root != null) {
                val screen = readVisibleText(root)
                if (screen.isNotBlank()) append(' ').append(screen)
            }
        }.trim().take(2500)
        if (text.isBlank()) return

        val packageName = event.packageName?.toString().orEmpty()
        val isLikelyUssd = text.contains("USSD", true) ||
            text.contains("reply", true) ||
            text.contains("cancel", true) ||
            text.contains("balance", true) ||
            text.contains("bundle", true) ||
            text.contains("enter", true) ||
            packageName.contains("phone", true) ||
            packageName.contains("dialer", true)

        if (isLikelyUssd) {
            recordState("USSD_SCREEN_DETECTED", "App: $packageName\n$text")
        }
    }

    override fun onInterrupt() {
        recordState("SERVICE_INTERRUPTED", "Android interrupted the accessibility service")
    }

    private fun readVisibleText(node: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (node == null || depth > 12) return ""
        val result = StringBuilder()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            result.append(it).append(' ')
        }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
            result.append(it).append(' ')
        }
        for (index in 0 until node.childCount) {
            result.append(readVisibleText(node.getChild(index), depth + 1))
            if (result.length >= 2500) break
        }
        return result.toString().take(2500)
    }

    private fun recordState(state: String, detail: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_STATE, state)
            .putString(KEY_DETAIL, detail.take(2500))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    companion object {
        const val PREFS = "bingwapulse_accessibility"
        const val KEY_STATE = "state"
        const val KEY_DETAIL = "detail"
        const val KEY_UPDATED_AT = "updated_at"
    }
}
