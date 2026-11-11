package com.telvanta.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.util.concurrent.Executors

/** Config-driven USSD executor. Every menu action requires an Admin-configured screen signature. */
class UssdAccessibilityService : AccessibilityService() {
    private val automationExecutor = Executors.newSingleThreadExecutor()
    private var lastManualPinPromptAt = 0L
    private var dismissReceiverRegistered = false
    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_DISMISS_USSD) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                recordState("USSD_SESSION_DISMISSED", "USSD session closed after the worker stopped the attempt.")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100L
        }
        recordState("SERVICE_CONNECTED", "Accessibility service enabled")
        if (!dismissReceiverRegistered) {
            val filter = IntentFilter(ACTION_DISMISS_USSD)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(dismissReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else {
                @Suppress("DEPRECATION")
                registerReceiver(dismissReceiver, filter)
            }
            dismissReceiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val root = rootInActiveWindow
        val screenText = buildString {
            event.text.forEach { append(it).append(' ') }
            if (isBlank()) append(readVisibleText(root))
            else if (root != null) {
                val screen = readVisibleText(root)
                if (screen.isNotBlank()) append(' ').append(screen)
            }
        }.trim().take(2500)
        if (screenText.isBlank()) return
        val packageName = event.packageName?.toString().orEmpty()
        val likelyUssd = screenText.contains("USSD", true) || screenText.contains("reply", true) ||
            screenText.contains("cancel", true) || screenText.contains("balance", true) ||
            screenText.contains("bundle", true) || screenText.contains("enter", true) ||
            packageName.contains("phone", true) || packageName.contains("dialer", true)
        if (likelyUssd) {
            recordState("USSD_SCREEN_DETECTED", "App: $packageName\n$screenText")
            executeConfiguredStep(screenText, root)
        }
    }

    private fun executeConfiguredStep(screenText: String, root: AccessibilityNodeInfo?) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val job = runCatching { SecureFulfilmentJobStore(this).load()?.let(::JSONObject) }.getOrNull() ?: return
        val steps = job.optJSONArray("menu_steps") ?: return
        val stepIndex = prefs.getInt(KEY_ACTIVE_STEP, 0)
        val classification = UssdScreenClassifier.classify(screenText)
        if (classification.type == UssdScreenType.SUCCESS_OR_RECEIPT) {
            finishJob(job, "DELIVERED", screenText, null, null); return
        }
        if (classification.type == UssdScreenType.ERROR_OR_UNAVAILABLE) {
            finishJob(job, "FAILED", screenText, "CARRIER_REJECTED", "Safaricom displayed an error or unavailable response."); return
        }
        if (stepIndex >= steps.length()) {
            recordState("WAITING_FOR_RESULT", "All configured menu steps were sent. Waiting for a carrier success or error confirmation.\n$screenText"); return
        }
        val step = steps.optJSONObject(stepIndex) ?: return
        val matchText = step.optString("match_text").trim()
        val action = step.optString("action").lowercase()
        val offerName = job.optString("offer_name").trim()
        val validityLabel = job.optString("validity_label").trim()
        val containsOfferIdentity = containsExactPhrase(matchText, offerName) ||
            containsExactPhrase(matchText, validityLabel)
        var confirmsOfferIdentity = false

        // The exact offer name AND exact validity must be visible together before
        // any later Buy with Airtime action is permitted.
        if (containsOfferIdentity) {
            if (offerName.isBlank() || validityLabel.isBlank() ||
                !containsExactPhrase(matchText, offerName) ||
                !containsExactPhrase(matchText, validityLabel) ||
                !containsExactPhrase(screenText, offerName) ||
                !containsExactPhrase(screenText, validityLabel)) {
                recordState("OFFER_IDENTITY_MISMATCH", "Provider screen did not show the exact offer name and validity. Automation stopped without selecting an offer.")
                return
            }
            confirmsOfferIdentity = true
        }

        val buyWithAirtimeStep = containsExactPhrase(matchText, "Buy with Airtime")
        if (buyWithAirtimeStep) {
            if (!prefs.getBoolean(KEY_OFFER_IDENTITY_CONFIRMED, false)) {
                recordState("OFFER_IDENTITY_NOT_CONFIRMED", "Buy with Airtime was blocked because the exact offer name and validity were not confirmed first.")
                return
            }
            if (!containsExactPhrase(screenText, "Buy with Airtime")) {
                recordState("PAYMENT_METHOD_MISMATCH", "The provider screen did not show the exact Buy with Airtime option. Automation stopped.")
                return
            }
        }

        // A manual PIN step is a pause, not a completed automation action.
        // Advance only after the PIN prompt disappears and the next configured
        // screen signature is visible; never inspect or capture the entered PIN.
        if (action == "manual_pin") {
            if (matchText.isNotBlank() &&
                containsExactPhrase(screenText, matchText) &&
                classification.type == UssdScreenType.PURCHASE_CONFIRMATION) {
                promptManualPin()
                recordState("WAITING_FOR_MANUAL_PIN", "Enter the SIM PIN directly in the Android USSD prompt. BingwaPulse does not read or store it.")
                return
            }
            val nextStep = steps.optJSONObject(stepIndex + 1) ?: return
            val nextMatch = nextStep.optString("match_text").trim()
            if (nextMatch.isNotBlank() &&
                containsExactPhrase(screenText, nextMatch) &&
                classification.type in setOf(UssdScreenType.MENU, UssdScreenType.PAYMENT_METHOD, UssdScreenType.PURCHASE_CONFIRMATION)) {
                prefs.edit().putInt(KEY_ACTIVE_STEP, stepIndex + 1).apply()
                executeConfiguredStep(screenText, root)
            }
            return
        }

        if (matchText.isBlank() || !containsExactPhrase(screenText, matchText)) return
        val actionScreenAllowed = when (action) {
            "reply" -> classification.type in setOf(UssdScreenType.MENU, UssdScreenType.PAYMENT_METHOD, UssdScreenType.PURCHASE_CONFIRMATION)
            "tap_text" -> classification.type in setOf(UssdScreenType.MENU, UssdScreenType.PAYMENT_METHOD)
            "manual_pin" -> classification.type == UssdScreenType.PURCHASE_CONFIRMATION
            else -> false
        }
        if (!actionScreenAllowed) {
            recordState("UNEXPECTED_USSD_SCREEN", "Configured action '$action' was not allowed on screen type '${classification.type}'. No response was sent.")
            return
        }
        val value = step.optString("value")
            .replace("{recipient_phone}", job.optString("recipient_phone"))
            .replace("{payer_phone}", job.optString("payer_phone"))
            .replace("{offer_name}", job.optString("offer_name"))
            .replace("{customer_price_kes}", job.optString("customer_price_kes"))
        val completed = when (action) {
            "reply" -> enterReply(root, value)
            "tap_text" -> tapVisibleText(root, value)
            "manual_pin" -> false // handled above as a human-only pause
            else -> { recordState("CONFIGURATION_ERROR", "Unsupported configured action at step ${stepIndex + 1}: $action"); false }
        }
        if (completed) {
            prefs.edit().putInt(KEY_ACTIVE_STEP, stepIndex + 1)
                .also { if (confirmsOfferIdentity) it.putBoolean(KEY_OFFER_IDENTITY_CONFIRMED, true) }
                .apply()
            recordState(if (action == "manual_pin") "WAITING_FOR_MANUAL_PIN" else "USSD_STEP_SENT",
                "Configured step ${stepIndex + 1}/${steps.length()} matched '$matchText' and action '$action'.")
        } else recordState("USSD_ACTION_NOT_COMPLETED", "Could not safely perform '$action' for '$matchText'. No fallback tap was attempted.")
    }

    private fun containsExactPhrase(text: String, phrase: String): Boolean {
        if (text.isBlank() || phrase.isBlank()) return false
        val normalizedText = text.replace(Regex("\\s+"), " ").trim()
        val normalizedPhrase = phrase.replace(Regex("\\s+"), " ").trim()
        val pattern = Regex(
            "(?<![\\p{L}\\p{N}])" + Regex.escape(normalizedPhrase) + "(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        )
        return pattern.containsMatchIn(normalizedText)
    }

    private fun enterReply(root: AccessibilityNodeInfo?, value: String): Boolean {
        if (root == null || value.isBlank()) return false
        val inputs = findEditables(root)
        val buttons = findButtons(root, listOf("send", "ok", "reply", "submit", "continue"))
        if (inputs.size != 1 || buttons.size != 1) return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        if (!inputs.single().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        return buttons.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun tapVisibleText(root: AccessibilityNodeInfo?, value: String): Boolean {
        if (root == null || value.isBlank()) return false
        val targets = findTextTargets(root, value)
        if (targets.size != 1) return false
        return targets.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findEditables(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo> = mutableListOf()): List<AccessibilityNodeInfo> {
        if (node == null) return result
        if (node.isEditable && node.isEnabled) result.add(node)
        for (i in 0 until node.childCount) findEditables(node.getChild(i), result)
        return result
    }

    private fun findButtons(node: AccessibilityNodeInfo?, labels: List<String>, result: MutableList<AccessibilityNodeInfo> = mutableListOf()): List<AccessibilityNodeInfo> {
        if (node == null) return result
        val label = (node.text?.toString() ?: node.contentDescription?.toString()).orEmpty().trim().lowercase()
        if (node.isEnabled && node.isClickable && labels.any { label == it || label.contains(it) }) result.add(node)
        for (i in 0 until node.childCount) findButtons(node.getChild(i), labels, result)
        return result.distinctBy { it.boundsInScreen.toShortString() }
    }

    private fun findTextTargets(node: AccessibilityNodeInfo?, value: String, result: MutableList<AccessibilityNodeInfo> = mutableListOf()): List<AccessibilityNodeInfo> {
        if (node == null) return result
        val label = (node.text?.toString() ?: node.contentDescription?.toString()).orEmpty().trim()
        if (label.equals(value, true)) {
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            if (target != null) result.add(target)
        }
        for (i in 0 until node.childCount) findTextTargets(node.getChild(i), value, result)
        return result.distinctBy { it.boundsInScreen.toShortString() }
    }
    private fun promptManualPin() {
        val now = System.currentTimeMillis()
        if (now - lastManualPinPromptAt < 5000L) return
        lastManualPinPromptAt = now
        runCatching {
            val vibrator = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
            else vibrateLegacy(vibrator)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateLegacy(vibrator: Vibrator?) { vibrator?.vibrate(350L) }

    private fun finishJob(job: JSONObject, state: String, screenText: String, errorCode: String?, errorDetail: String?) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val saved = SecureSessionStore(this).load()
        if (saved == null) { recordState("RESULT_NOT_SYNCED", "Carrier result reached $state but session is unavailable. Manager reconciliation is required."); return }
        automationExecutor.execute {
            try {
                val api = BingwaSupabaseApi()
                val session = if (saved.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L)
                    api.refreshSession(saved).also { SecureSessionStore(this).save(it) } else saved
                api.recordFulfilmentAttemptResult(session, job.getString("business_id"), job.getString("order_id"),
                    job.getString("device_id"), job.getInt("attempt_no"), state, prefs.getInt(KEY_ACTIVE_STEP, 0),
                    redactSensitiveText(screenText), errorCode = errorCode, errorDetail = errorDetail)
                SecureFulfilmentJobStore(this).clear()
                prefs.edit().remove(KEY_ACTIVE_STEP).remove(KEY_JOB_STARTED_AT).remove(KEY_OFFER_IDENTITY_CONFIRMED).apply()
                // Dismiss the system USSD dialog only after the server accepts a
                // definitive carrier result. A failed sync leaves the session visible.
                Handler(Looper.getMainLooper()).post {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    recordState(state, screenText)
                }
            } catch (error: Exception) {
                recordState("RESULT_NOT_SYNCED", error.message ?: "Could not save carrier result. Manager reconciliation is required.")
            }
        }
    }

    override fun onInterrupt() { recordState("SERVICE_INTERRUPTED", "Android interrupted the accessibility service") }
    private fun readVisibleText(node: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (node == null || depth > 12) return ""
        val result = StringBuilder()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { result.append(it).append(' ') }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { result.append(it).append(' ') }
        for (index in 0 until node.childCount) { result.append(readVisibleText(node.getChild(index), depth + 1)); if (result.length >= 2500) break }
        return result.toString().take(2500)
    }
    private fun redactSensitiveText(value: String): String {
        val labelled = value.replace(
            Regex("(?i)(pin|passcode|password)(\\s*[:=\\-]?\\s*)[0-9]{4,8}"),
            "$1$2[REDACTED]"
        )
        return labelled.replace(Regex("\\b[0-9]{4,8}\\b"), "[REDACTED]")
    }

    private fun recordState(state: String, detail: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STATE, state)
            .putString(KEY_DETAIL, redactSensitiveText(detail).take(2500)).putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply()
    }
    override fun onDestroy() {
        if (dismissReceiverRegistered) runCatching { unregisterReceiver(dismissReceiver) }
        automationExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val PREFS = "bingwapulse_accessibility"
        const val KEY_STATE = "state"
        const val KEY_DETAIL = "detail"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_ACTIVE_STEP = "active_fulfilment_step"
        const val KEY_JOB_STARTED_AT = "active_fulfilment_started_at"
        const val KEY_OFFER_IDENTITY_CONFIRMED = "active_offer_identity_confirmed"
        const val ACTION_DISMISS_USSD = "com.telvanta.app.ACTION_DISMISS_USSD"
    }
}
