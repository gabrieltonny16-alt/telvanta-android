package com.telvanta.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class BingwaSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAtEpochSeconds: Long,
    val businessId: String? = null
)

class SecureSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("bingwapulse_secure_session", Context.MODE_PRIVATE)
    private val alias = "bingwapulse.session.aes"

    fun isLocalWorkspace(): Boolean = prefs.getBoolean("local_workspace", false)

    fun enableLocalWorkspace() {
        prefs.edit().putBoolean("local_workspace", true).remove("session").apply()
    }

    fun hasSession(): Boolean = isLocalWorkspace() ||
        load()?.expiresAtEpochSeconds?.let { it > System.currentTimeMillis() / 1000L + 60L } == true

    fun save(session: BingwaSession) {
        prefs.edit().putBoolean("local_workspace", false).apply()
        val json = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("user_id", session.userId)
            .put("email", session.email)
            .put("expires_at", session.expiresAtEpochSeconds)
            .put("business_id", session.businessId ?: "")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        prefs.edit().putString("session", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun load(): BingwaSession? {
        val packedText = prefs.getString("session", null) ?: return null
        return try {
            val packed = Base64.decode(packedText, Base64.NO_WRAP)
            if (packed.size <= 12) return null
            val iv = packed.copyOfRange(0, 12)
            val ciphertext = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
            BingwaSession(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                userId = json.getString("user_id"),
                email = json.optString("email"),
                expiresAtEpochSeconds = json.optLong("expires_at"),
                businessId = json.optString("business_id").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().remove("session").remove("local_workspace").apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

data class BingwaDevice(
    val id: String,
    val label: String,
    val status: String,
    val lastSeenAt: String?,
    val pairedAt: String?,
    val assignedUserId: String? = null
)

data class FulfilmentOffer(
    val id: String,
    val name: String,
    val category: String,
    val providerId: String,
    val isActive: Boolean
)

data class FulfilmentRuleSummary(
    val id: String,
    val amountMinor: Long,
    val offerId: String,
    val menuEntryCode: String,
    val isEnabled: Boolean,
    val verifiedAt: String?
)

data class MpesaNotificationReviewCandidate(
    val id: String,
    val receiptCode: String?,
    val amountMinor: Long?,
    val senderPhone: String?,
    val senderName: String?,
    val candidateStatus: String,
    val matchStatus: String,
    val stagingStatus: String,
    val stagingReason: String?,
    val postedAt: String?,
    val stagedOrderId: String?
)

class BingwaSupabaseApi {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    fun signUp(email: String, password: String): BingwaSession? {
        val response = request(
            "POST", "/auth/v1/signup",
            JSONObject().put("email", email).put("password", password)
        )
        val json = JSONObject(response)
        return json.optString("access_token").takeIf { it.isNotBlank() }?.let { parseSession(json) }
    }

    fun signIn(email: String, password: String): BingwaSession {
        val response = request(
            "POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password)
        )
        return parseSession(JSONObject(response))
    }

    fun signUpPhone(phone: String, password: String) {
        val normalized = normalizePhone(phone)
        request("POST", "/auth/v1/signup", JSONObject().put("phone", normalized).put("password", password))
    }

    fun verifyPhoneOtp(phone: String, token: String): BingwaSession {
        val normalized = normalizePhone(phone)
        val response = request("POST", "/auth/v1/verify",
            JSONObject().put("type", "sms").put("phone", normalized).put("token", token))
        return parseSession(JSONObject(response))
    }

    fun signInPhone(phone: String, password: String): BingwaSession {
        val normalized = normalizePhone(phone)
        val response = request("POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("phone", normalized).put("password", password))
        return parseSession(JSONObject(response))
    }

    fun resendEmailConfirmation(email: String) {
        request("POST", "/auth/v1/resend", JSONObject().put("type", "signup").put("email", email.trim()))
    }

    private fun normalizePhone(input: String): String {
        val compact = input.trim().replace(Regex("[\\s()\\-]"), "")
        val normalized = when {
            compact.startsWith("+") -> compact
            compact.startsWith("0") && compact.length == 10 -> "+254" + compact.drop(1)
            compact.startsWith("254") -> "+$compact"
            else -> compact
        }
        require(normalized.matches(Regex("^\\+[1-9][0-9]{7,14}$"))) {
            "Enter a valid phone number with country code, for example +2547XXXXXXXX."
        }
        return normalized
    }

    fun refreshSession(session: BingwaSession): BingwaSession {
        val response = request(
            "POST", "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", session.refreshToken)
        )
        return parseSession(JSONObject(response), session.businessId)
    }

    fun firstBusinessId(session: BingwaSession): String? {
        val query = "/rest/v1/business_memberships?select=business_id&user_id=eq.${session.userId}&is_active=eq.true&limit=1"
        val array = JSONArray(request("GET", query, token = session.accessToken))
        return if (array.length() == 0) null else array.getJSONObject(0).optString("business_id").takeIf { it.isNotBlank() }
    }

    fun createBusiness(session: BingwaSession, name: String, slug: String): String {
        val body = JSONObject()
            .put("name", name.trim())
            .put("slug", slug)
            .put("created_by", session.userId)
        val response = request(
            "POST", "/rest/v1/businesses",
            body = body,
            token = session.accessToken,
            prefer = "return=representation"
        )
        val array = JSONArray(response)
        if (array.length() == 0) throw IOException("The business was not returned by the database.")
        return array.getJSONObject(0).getString("id")
    }

    fun createDevicePairingChallenge(
        session: BingwaSession,
        businessId: String,
        deviceLabel: String,
        pairingCode: String
    ) {
        require(businessId.isNotBlank() && deviceLabel.trim().length in 2..100)
        require(pairingCode.matches(Regex("^[A-Z0-9]{10}$")))
        val codeHash = MessageDigest.getInstance("SHA-256")
            .digest(pairingCode.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val body = JSONObject()
            .put("business_id", businessId)
            .put("created_by", session.userId)
            .put("device_label", deviceLabel.trim())
            .put("code_hash", codeHash)
            .put("expires_at", java.time.Instant.now().plusSeconds(600).toString())
        request(
            "POST", "/rest/v1/device_pairing_challenges",
            body = body,
            token = session.accessToken,
            prefer = "return=minimal"
        )
    }


    fun registerCurrentWorkerDevice(
        session: BingwaSession,
        businessId: String,
        deviceLabel: String
    ): BingwaDevice {
        require(businessId.isNotBlank() && deviceLabel.trim().length in 2..100)
        val now = java.time.Instant.now().toString()
        val body = JSONObject()
            .put("business_id", businessId)
            .put("assigned_user_id", session.userId)
            .put("device_label", deviceLabel.trim())
            .put("platform", "android")
            .put("status", "online")
            .put("paired_at", now)
            .put("last_seen_at", now)
        val response = JSONArray(request(
            "POST", "/rest/v1/devices", body,
            session.accessToken, "return=representation"
        ))
        if (response.length() == 0) throw IOException("The worker device was not registered.")
        val row = response.getJSONObject(0)
        return BingwaDevice(
            id = row.getString("id"),
            label = row.optString("device_label", deviceLabel),
            status = row.optString("status", "online"),
            lastSeenAt = row.optString("last_seen_at").takeIf { it.isNotBlank() && it != "null" },
            pairedAt = row.optString("paired_at").takeIf { it.isNotBlank() && it != "null" },
            assignedUserId = row.optString("assigned_user_id").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    fun heartbeatWorkerDevice(session: BingwaSession, businessId: String, deviceId: String) {
        val body = JSONObject().put("last_seen_at", java.time.Instant.now().toString())
        request(
            "PATCH",
            "/rest/v1/devices?id=eq.$deviceId&business_id=eq.$businessId&assigned_user_id=eq.${session.userId}&status=eq.online",
            body,
            session.accessToken,
            "return=minimal"
        )
    }

    fun listDevices(session: BingwaSession, businessId: String): List<BingwaDevice> {
        val query = "/rest/v1/devices?select=id,device_label,status,last_seen_at,paired_at,assigned_user_id,created_at&business_id=eq.$businessId&order=created_at.desc"
        val rows = JSONArray(request("GET", query, token = session.accessToken))
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                add(BingwaDevice(
                    id = row.getString("id"),
                    label = row.optString("device_label", "Android device"),
                    status = row.optString("status", "unknown"),
                    lastSeenAt = row.optString("last_seen_at").takeIf { it.isNotBlank() && it != "null" },
                    pairedAt = row.optString("paired_at").takeIf { it.isNotBlank() && it != "null" },
                    assignedUserId = row.optString("assigned_user_id").takeIf { it.isNotBlank() && it != "null" }
                ))
            }
        }
    }

    fun approveDevice(session: BingwaSession, businessId: String, deviceId: String) {
        require(businessId.isNotBlank() && deviceId.isNotBlank())
        val body = JSONObject()
            .put("status", "online")
            .put("paired_at", java.time.Instant.now().toString())
        request(
            "PATCH", "/rest/v1/devices?id=eq.$deviceId&business_id=eq.$businessId",
            body = body,
            token = session.accessToken,
            prefer = "return=minimal"
        )
    }

    fun listMpesaNotificationCandidates(
        session: BingwaSession,
        businessId: String
    ): List<MpesaNotificationReviewCandidate> {
        require(businessId.isNotBlank())
        val query = "/rest/v1/payment_notification_candidates?select=id,receipt_candidate,amount_minor,sender_phone_candidate,sender_name_candidate,candidate_status,match_status,staging_status,staging_reason,notification_posted_at,staged_order_id&business_id=eq.$businessId&order=notification_posted_at.desc&limit=100"
        val rows = JSONArray(request("GET", query, token = session.accessToken))
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                fun optional(key: String): String? =
                    row.optString(key).takeIf { it.isNotBlank() && it != "null" }
                add(MpesaNotificationReviewCandidate(
                    id = row.getString("id"),
                    receiptCode = optional("receipt_candidate"),
                    amountMinor = if (row.isNull("amount_minor")) null else row.optLong("amount_minor"),
                    senderPhone = optional("sender_phone_candidate"),
                    senderName = optional("sender_name_candidate"),
                    candidateStatus = row.optString("candidate_status", "UNKNOWN"),
                    matchStatus = row.optString("match_status", "pending"),
                    stagingStatus = row.optString("staging_status", "not_staged"),
                    stagingReason = optional("staging_reason"),
                    postedAt = optional("notification_posted_at"),
                    stagedOrderId = optional("staged_order_id")
                ))
            }
        }
    }

    fun retryMpesaNotificationCandidate(
        session: BingwaSession,
        businessId: String,
        candidateId: String
    ) {
        require(businessId.isNotBlank() && candidateId.isNotBlank())
        val body = JSONObject()
            .put("p_business_id", businessId)
            .put("p_candidate_id", candidateId)
        request(
            "POST", "/rest/v1/rpc/retry_mpesa_notification_candidate",
            body = body,
            token = session.accessToken,
            prefer = "return=minimal"
        )
    }

    fun reviewMpesaNotificationCandidate(
        session: BingwaSession,
        businessId: String,
        candidateId: String,
        decision: String
    ) {
        require(businessId.isNotBlank() && candidateId.isNotBlank())
        require(decision in setOf("verify", "reject"))
        val body = JSONObject()
            .put("p_business_id", businessId)
            .put("p_candidate_id", candidateId)
            .put("p_decision", decision)
        request(
            "POST", "/rest/v1/rpc/review_mpesa_notification_candidate",
            body = body,
            token = session.accessToken,
            prefer = "return=representation"
        )
    }

    /**
     * Relay a locally observed M-PESA notification as unverified evidence.
     * The access token is user-scoped; database RLS verifies membership and ownership.
     * This method never marks a payment verified and never creates or dispatches an order.
     */
    fun submitMpesaNotificationCandidate(
        session: BingwaSession,
        businessId: String,
        sourcePackage: String,
        title: String,
        rawText: String,
        receiptCandidate: String?,
        amountMinor: Long?,
        senderPhoneCandidate: String?,
        senderNameCandidate: String?,
        notificationHash: String,
        candidateStatus: String,
        postedAtEpochMillis: Long
    ) {
        require(businessId.isNotBlank() && notificationHash.matches(Regex("^[0-9a-f]{64}$")))
        val body = JSONObject()
            .put("business_id", businessId)
            .put("source_package", sourcePackage.take(160))
            .put("notification_title", title.take(120))
            .put("raw_notification_text", rawText.take(1600))
            .put("notification_hash", notificationHash)
            .put("candidate_status", candidateStatus)
            .put("notification_posted_at", java.time.Instant.ofEpochMilli(postedAtEpochMillis).toString())
        if (!receiptCandidate.isNullOrBlank()) body.put("receipt_candidate", receiptCandidate)
        if (amountMinor != null && amountMinor > 0L) body.put("amount_minor", amountMinor)
        if (!senderPhoneCandidate.isNullOrBlank()) body.put("sender_phone_candidate", senderPhoneCandidate)
        if (!senderNameCandidate.isNullOrBlank()) {
            body.put("sender_name_candidate", senderNameCandidate.trim().replace(Regex("\\s+"), " ").take(120))
        }

        request(
            "POST", "/rest/v1/payment_notification_candidates",
            body = body,
            token = session.accessToken,
            prefer = "resolution=ignore-duplicates,return=minimal"
        )
    }



    fun listFulfilmentOffers(session: BingwaSession, businessId: String): List<FulfilmentOffer> {
        val query = "/rest/v1/offers?select=id,name,category,provider_id,is_active&business_id=eq.$businessId&is_active=eq.true&order=name.asc"
        val rows = JSONArray(request("GET", query, token = session.accessToken))
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                add(FulfilmentOffer(
                    id = row.getString("id"),
                    name = row.optString("name"),
                    category = row.optString("category"),
                    providerId = row.optString("provider_id"),
                    isActive = row.optBoolean("is_active", false)
                ))
            }
        }
    }

    fun listFulfilmentRules(session: BingwaSession, businessId: String): List<FulfilmentRuleSummary> {
        val query = "/rest/v1/payment_offer_rules?select=id,amount_minor,offer_id,menu_entry_code,is_enabled,menu_flow_verified_at&business_id=eq.$businessId&order=amount_minor.asc"
        val rows = JSONArray(request("GET", query, token = session.accessToken))
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                add(FulfilmentRuleSummary(
                    id = row.getString("id"),
                    amountMinor = row.optLong("amount_minor"),
                    offerId = row.optString("offer_id"),
                    menuEntryCode = row.optString("menu_entry_code"),
                    isEnabled = row.optBoolean("is_enabled", false),
                    verifiedAt = row.optString("menu_flow_verified_at").takeIf { it.isNotBlank() && it != "null" }
                ))
            }
        }
    }

    fun saveFulfilmentRule(
        session: BingwaSession,
        businessId: String,
        amountKes: Long,
        offerId: String,
        ussdCode: String,
        menuSteps: org.json.JSONArray,
        enableAfterManagerVerification: Boolean
    ) {
        require(amountKes > 0L && offerId.isNotBlank())
        require(ussdCode.matches(Regex("^\\\\*[0-9*]+#$")))
        require(menuSteps.length() > 0)
        val now = java.time.Instant.now().toString()
        val body = JSONObject()
            .put("business_id", businessId)
            .put("amount_minor", amountKes * 100L)
            .put("offer_id", offerId)
            .put("menu_entry_code", ussdCode)
            .put("menu_steps", menuSteps)
            .put("is_enabled", enableAfterManagerVerification)
            .put("menu_flow_verified_at", if (enableAfterManagerVerification) now else JSONObject.NULL)
        request(
            "POST",
            "/rest/v1/payment_offer_rules?on_conflict=business_id,amount_minor",
            body,
            session.accessToken,
            "resolution=merge-duplicates,return=minimal"
        )
    }

    fun getFulfilmentControls(session: BingwaSession, businessId: String): JSONObject {
        val rows = JSONArray(request(
            "GET",
            "/rest/v1/businesses?select=fulfilment_enabled,fulfilment_paused,fulfilment_require_manual_pin&id=eq.$businessId&limit=1",
            token = session.accessToken
        ))
        if (rows.length() == 0) throw IOException("Business fulfilment settings were not found.")
        return rows.getJSONObject(0)
    }

    fun setFulfilmentControls(
        session: BingwaSession,
        businessId: String,
        enabled: Boolean,
        paused: Boolean,
        requireManualPin: Boolean
    ) {
        val body = JSONObject()
            .put("p_business_id", businessId)
            .put("p_enabled", enabled)
            .put("p_paused", paused)
            .put("p_require_manual_pin", requireManualPin)
        request("POST", "/rest/v1/rpc/set_business_fulfilment_controls", body, session.accessToken)
    }

    fun claimNextFulfilmentOrder(
        session: BingwaSession,
        businessId: String,
        deviceId: String
    ): JSONObject? {
        val body = JSONObject().put("p_business_id", businessId).put("p_device_id", deviceId)
        val response = request("POST", "/rest/v1/rpc/claim_next_fulfilment_order", body, session.accessToken)
        if (response.isBlank() || response == "null") return null
        return JSONObject(response)
    }

    fun recordFulfilmentAttemptResult(
        session: BingwaSession,
        businessId: String,
        orderId: String,
        deviceId: String,
        attemptNo: Int,
        state: String,
        menuStep: Int = 0,
        screenText: String? = null,
        providerReference: String? = null,
        errorCode: String? = null,
        errorDetail: String? = null
    ) {
        require(state in setOf("USSD_SENT", "DELIVERED", "FAILED", "UNKNOWN"))
        val body = JSONObject()
            .put("p_business_id", businessId)
            .put("p_order_id", orderId)
            .put("p_device_id", deviceId)
            .put("p_attempt_no", attemptNo)
            .put("p_state", state)
            .put("p_menu_step", menuStep)
        if (screenText != null) body.put("p_screen_text", screenText.take(2500))
        if (providerReference != null) body.put("p_provider_reference", providerReference)
        if (errorCode != null) body.put("p_error_code", errorCode)
        if (errorDetail != null) body.put("p_error_detail", errorDetail)
        request("POST", "/rest/v1/rpc/record_fulfilment_attempt_result", body, session.accessToken)
    }

    private fun parseSession(json: JSONObject, existingBusinessId: String? = null): BingwaSession {
        val user = json.optJSONObject("user") ?: throw IOException("The authentication response did not include a user.")
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) throw IOException("The authentication response did not include a usable session.")
        val expiresAt = json.optLong(
            "expires_at",
            (System.currentTimeMillis() / 1000L) + json.optLong("expires_in", 3600L)
        )
        return BingwaSession(
            accessToken = access,
            refreshToken = refresh,
            userId = user.getString("id"),
            email = user.optString("email"),
            expiresAtEpochSeconds = expiresAt,
            businessId = existingBusinessId
        )
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        token: String? = null,
        prefer: String? = null
    ): String {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${token ?: publishableKey}")
            if (prefer != null) setRequestProperty("Prefer", prefer)
            if (body != null) doOutput = true
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = try {
                    val error = JSONObject(response)
                    error.optString("msg").ifBlank {
                        error.optString("message").ifBlank {
                            error.optString("error_description").ifBlank { error.optString("error") }
                        }
                    }
                } catch (_: Exception) { "" }
                throw IOException(message.ifBlank { "Server request failed (HTTP $status)." })
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
