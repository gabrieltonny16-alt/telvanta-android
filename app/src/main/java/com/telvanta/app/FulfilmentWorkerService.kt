package com.telvanta.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * User-started foreground queue worker. It only claims orders after the server-side
 * business kill-switch, verified payment, active provider, and verified menu-rule gates pass.
 * An uncertain session is never automatically retried because that could double-purchase.
 */
class FulfilmentWorkerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("BingwaPulse fulfilment worker")
            .setContentText("Waiting for eligible paid orders")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val businessId = intent?.getStringExtra(EXTRA_BUSINESS_ID).orEmpty()
        val deviceId = intent?.getStringExtra(EXTRA_DEVICE_ID).orEmpty()
        if (businessId.isBlank() || deviceId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            scope.launch { runQueue(businessId, deviceId) }
        }
        return START_STICKY
    }

    private suspend fun runQueue(businessId: String, deviceId: String) {
        val store = SecureSessionStore(this)
        val api = BingwaSupabaseApi()
        val prefs = getSharedPreferences(UssdAccessibilityService.PREFS, MODE_PRIVATE)
        val jobVault = SecureFulfilmentJobStore(this)
        while (scope.isActive) {
            try {
                val saved = store.load() ?: throw IllegalStateException("Sign in again to run the fulfilment worker.")
                if (store.isLocalWorkspace()) throw IllegalStateException("Local preview cannot run fulfilment.")
                val session = if (saved.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 90L) {
                    api.refreshSession(saved).also(store::save)
                } else saved
                api.heartbeatWorkerDevice(session, businessId, deviceId)

                // Express has no verified manual completion flow yet. Pause before claiming
                // orders so switching modes cannot strand a paid job.
                val processingMode = prefs.getString(KEY_PROCESSING_MODE, MODE_EXPRESS) ?: MODE_EXPRESS
                if (processingMode != MODE_ADVANCED) {
                    updateNotification("Express mode selected: automatic fulfilment paused")
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // Advanced mode requires both capabilities before an order is claimed.
                if (!isUssdAccessibilityEnabled() ||
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    updateNotification("Advanced mode needs Accessibility and Phone permissions")
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                if (jobVault.load() != null) {
                    delay(POLL_INTERVAL_MS)
                    continue
                }
                val job = api.claimNextFulfilmentOrder(session, businessId, deviceId)
                if (job == null) {
                    delay(POLL_INTERVAL_MS)
                    continue
                }
                jobVault.save(job.toString())
                prefs.edit()
                    .putInt(UssdAccessibilityService.KEY_ACTIVE_STEP, 0)
                    .remove(UssdAccessibilityService.KEY_OFFER_IDENTITY_CONFIRMED)
                    .putLong(UssdAccessibilityService.KEY_JOB_STARTED_AT, System.currentTimeMillis())
                    .apply()

                api.recordFulfilmentAttemptResult(
                    session, businessId, job.getString("order_id"), deviceId,
                    job.getInt("attempt_no"), "USSD_SENT", 0, "USSD launch requested"
                )

                val ussdCode = job.optString("ussd_code")
                if (!ussdCode.matches(Regex("^\\*[0-9*]+#$"))) {
                    finishUnknown(api, session, businessId, deviceId, job, "INVALID_USSD_CODE", "Configured USSD code is invalid.")
                    continue
                }
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    finishUnknown(api, session, businessId, deviceId, job, "CALL_PERMISSION_MISSING", "Allow Phone permission before running USSD.")
                    continue
                }

                val callIntent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", ussdCode, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(callIntent)
                } catch (error: Exception) {
                    finishUnknown(api, session, businessId, deviceId, job, "USSD_LAUNCH_BLOCKED", error.message ?: "Android did not allow the USSD screen to open.")
                    continue
                }

                // Wait for Accessibility to record a final result. Timeout becomes UNKNOWN, never a retry.
                var waited = 0L
                while (waited < SESSION_TIMEOUT_MS && jobVault.load() != null) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    waited += HEARTBEAT_INTERVAL_MS
                    api.heartbeatWorkerDevice(session, businessId, deviceId)
                }
                if (jobVault.load() != null) {
                    finishUnknown(api, session, businessId, deviceId, job, "USSD_TIMEOUT", "No definitive carrier result arrived before timeout.", dismissUssd = true)
                }
            } catch (error: Exception) {
                updateNotification("Worker paused: ${error.message ?: "connection error"}")
                delay(ERROR_BACKOFF_MS)
            }
        }
    }

    private fun isUssdAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${UssdAccessibilityService::class.java.name}"
        return android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private suspend fun finishUnknown(
        api: BingwaSupabaseApi,
        session: BingwaSession,
        businessId: String,
        deviceId: String,
        job: JSONObject,
        code: String,
        detail: String,
        dismissUssd: Boolean = false
    ) {
        runCatching {
            api.recordFulfilmentAttemptResult(
                session, businessId, job.getString("order_id"), deviceId,
                job.getInt("attempt_no"), "UNKNOWN", 0, detail,
                errorCode = code, errorDetail = detail
            )
        }
        SecureFulfilmentJobStore(this).clear()
        getSharedPreferences(UssdAccessibilityService.PREFS, MODE_PRIVATE).edit()
            .remove(UssdAccessibilityService.KEY_ACTIVE_STEP)
            .remove(UssdAccessibilityService.KEY_JOB_STARTED_AT)
            .remove(UssdAccessibilityService.KEY_OFFER_IDENTITY_CONFIRMED)
            .apply()
        if (dismissUssd) {
            sendBroadcast(Intent(UssdAccessibilityService.ACTION_DISMISS_USSD).setPackage(packageName))
        }
        updateNotification("Order needs reconciliation; automatic retry is disabled")
    }

    private suspend fun updateNotification(message: String) = withContext(Dispatchers.Main) {
        val notification = Notification.Builder(this@FulfilmentWorkerService, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("BingwaPulse fulfilment worker")
            .setContentText(message.take(120))
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Fulfilment worker", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_BUSINESS_ID = "business_id"
        const val EXTRA_DEVICE_ID = "device_id"
        private const val KEY_PROCESSING_MODE = "processing_mode"
        private const val MODE_EXPRESS = "express"
        private const val MODE_ADVANCED = "advanced"
        private const val CHANNEL_ID = "bingwapulse_fulfilment"
        private const val NOTIFICATION_ID = 7301
        private const val POLL_INTERVAL_MS = 8000L
        private const val SESSION_TIMEOUT_MS = 120000L
        private const val HEARTBEAT_INTERVAL_MS = 20000L
        private const val ERROR_BACKOFF_MS = 15000L
    }
}
