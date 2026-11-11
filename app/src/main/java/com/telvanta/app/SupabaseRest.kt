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
    val isActive: Boolean,
    val validityLabel: String?,
    val entryCode: String?
)

data class FulfilmentOrderSummary(
    val id: String,
    val recipientPhone: String,
    val amountKes: Long,
    val status: String,
    val offerId: String,
    val createdAt: String?
)

data class FulfilmentRuleSummary(
    val id: String,
    val amountMinor: Long,
    val offerId: String,
    val menuEntryCode: String,
    val isEnabled: Boolean,
    val verifiedAt: String?,
    val verifiedBy: String?,
    val verificationNote: String?,
    val version: Int
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

    fun getPaymentIngressTill(session: BingwaSession, businessId: String): String? {
        val rows = JSONArray(request(
            "GET",
            "/rest/v1/payment_ingress_channels?select=shortcode&business_id=eq.$businessId&channel_type=eq.till&is_active=eq.true&limit=1",
            token = session.accessToken
        ))
        return if (rows.length() == 0) null else rows.getJSONObject(0).optString("shortcode").takeIf { it.isNotBlank() }
    }

    fun savePaymentIngressTill(session: BingwaSession, businessId: String, shortcode: String) {
        val till = shortcode.trim()
        require(businessId.isNotBlank() && till.matches(Regex("^[0-9]{5,8}$"))) {
            "Enter the exact 5–8 digit M-PESA Till number."
        }
        val existing = JSONArray(request(
            "GET",
            "/rest/v1/payment_ingress_channels?select=id,business_id&shortcode=eq.$till&limit=1",
            token = session.accessToken
        ))
        if (existing.length() > 0 && existing.getJSONObject(0).optString("business_id") != businessId) {
            throw IOException("This Till number is already assigned to another business workspace.")
        }
        val body = JSONObject()
            .put("business_id", businessId)
            .put("shortcode", till)
            .put("channel_type", "till")
            .put("is_active", true)
        request(
            "POST",
            "/rest/v1/payment_ingress_channels?on_conflict=shortcode",
            body,
            session.accessToken,
            "resolution=merge-duplicates,return=minimal"
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



    fun syncLocalOfferToCloud(session: BingwaSession, businessId: String, offer: OfferRow) {
        require(businessId.isNotBlank())
        val roundedSellingPrice = offer.price.toInt()
        require(offer.price > 0.0 && kotlin.math.abs(offer.price - roundedSellingPrice.toDouble()) < 0.0001) {
            "Cloud offers require a whole-KES selling price. Update this offer before syncing."
        }

        val providerName = if (offer.source.contains("signature", ignoreCase = true)) "Safaricom Signature" else "Safaricom"
        val providerBody = JSONObject()
            .put("business_id", businessId)
            .put("name", providerName)
            .put("adapter_key", "ussd_assisted")
            .put("status", "active")
        val providerRows = JSONArray(request(
            "POST",
            "/rest/v1/providers?on_conflict=business_id,name",
            providerBody,
            session.accessToken,
            "resolution=merge-duplicates,return=representation"
        ))
        if (providerRows.length() == 0) throw IOException("Safaricom provider could not be synchronized.")
        val providerId = providerRows.getJSONObject(0).getString("id")

        val category = when (offer.category.trim().lowercase()) {
            "data" -> "data"
            "voice", "minutes" -> "minutes"
            "sms" -> "sms"
            "combo" -> "combo"
            else -> throw IllegalArgumentException("Unsupported offer category: ${offer.category}")
        }
        val externalRef = offer.sourceId.takeIf { it.isNotBlank() } ?: "local-${offer.id}"
        val offerBody = JSONObject()
            .put("business_id", businessId)
            .put("provider_id", providerId)
            .put("external_offer_ref", externalRef)
            .put("code", offer.ussdCode.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("name", offer.name)
            .put("category", category)
            .put("description", listOf(offer.source, offer.simProfile, offer.tags).filter { it.isNotBlank() }.joinToString(" · "))
            .put("provider_cost_kes", (offer.originalPrice ?: 0.0).toInt().coerceAtLeast(0))
            .put("validity_label", offer.duration.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("is_active", offer.active)
            .put("source_observed_at", java.time.Instant.now().toString())
        val offerRows = JSONArray(request(
            "POST",
            "/rest/v1/offers?on_conflict=business_id,provider_id,external_offer_ref",
            offerBody,
            session.accessToken,
            "resolution=merge-duplicates,return=representation"
        ))
        if (offerRows.length() == 0) throw IOException("Offer could not be synchronized to the business catalogue.")
        val cloudOfferId = offerRows.getJSONObject(0).getString("id")

        val agentOfferBody = JSONObject()
            .put("business_id", businessId)
            .put("offer_id", cloudOfferId)
            .put("agent_user_id", session.userId)
            .put("selling_price_kes", roundedSellingPrice)
            .put("is_published", offer.active)
        request(
            "POST",
            "/rest/v1/agent_offers?on_conflict=offer_id,agent_user_id",
            agentOfferBody,
            session.accessToken,
            "resolution=merge-duplicates,return=minimal"
        )
    }

    fun createQuickDialOrder(
        session: BingwaSession,
        businessId: String,
        offerId: String,
        recipientPhone: String,
        amountKes: Int
    ): JSONObject {
        require(businessId.isNotBlank() && offerId.isNotBlank() && amountKes > 0)
        val body = JSONObject()
            .put("p_business_id", businessId)
            .put("p_offer_id", offerId)
            .put("p_recipient_phone", recipientPhone)
            .put("p_amount_kes", amountKes)
        val response = request(
            "POST",
            "/rest/v1/rpc/create_quick_dial_order",
            body,
            session.accessToken
        )
        return JSONObject(response)
    }

    fun listFulfilmentOffers(session: BingwaSession, businessId: String): List<FulfilmentOffer> {
        val query = "/rest/v1/offers?select=id,name,category,provider_id,is_active,validity_label,code&business_id=eq.$businessId&is_active=eq.true&order=name.asc"
        val rows = JSONArray(request("GET", query, token = session.accessToken))
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                add(FulfilmentOffer(
                    id = row.getString("id"),
                    name = row.optString("name"),
                    category = row.optString("category"),
                    providerId = row.optString("provider_id"),
                    isActive = row.optBoolean("is_active", false),
                    validityLabel = row.optString("validity_label").takeIf { it.isNotBlank() && it != "null" },
                    entryCode = row.optString("code").takeIf { it.isNotBlank() && it != "null" }
                ))
            }
        }
    }

    fun listFulfilmentRules(session: BingwaSession, businessId: String): List<FulfilmentRuleSummary> {
        val query = "/rest/v1/payment_offer_rules?select=id,amount_minor,offer_id,menu_entry_code,is_enabled,menu_flow_verified_at,menu_flow_verified_by,menu_flow_verified_note,menu_flow_version&business_id=eq.$businessId&order=amount_minor.asc"
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
                    verifiedAt = row.optString("menu_flow_verified_at").takeIf { it.isNotBlank() && it != "null" },
                    verifiedBy = row.optString("menu_flow_verified_by").takeIf { it.isNotBlank() && it != "null" },
                    verificationNote = row.optString("menu_flow_verified_note").takeIf { it.isNotBlank() && it != "null" },
                    version = row.optInt("menu_flow_version", 1)
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
        enableAfterManagerVerification: Boolean,
        verificationNote: String = ""
    ) {
        require(amountKes > 0L && offerId.isNotBlank())
        require(ussdCode.matches(Regex("^\\\\*[0-9*]+#$")))
        require(menuSteps.length() > 0)
        if (enableAfterManagerVerification) {
            require(verificationNote.trim().length >= 12) { "Add a short note describing the successful recipe test." }
        }
        val now = java.time.Instant.now().toString()
        val body = JSONObject()
            .put("business_id", businessId)
            .put("amount_minor", amountKes * 100L)
            .put("offer_id", offerId)
            .put("menu_entry_code", ussdCode)
            .put("menu_steps", menuSteps)
            .put("is_enabled", enableAfterManagerVerification)
            .put("menu_flow_verified_at", if (enableAfterManagerVerification) now else JSONObject.NULL)
            .put("menu_flow_verified_by", if (enableAfterManagerVerification) session.userId else JSONObject.NULL)
            .put("menu_flow_verified_note", if (enableAfterManagerVerification) verificationNote.trim().take(500) else JSONObject.NULL)
        request(
            "POST",
            "/rest/v1/payment_offer_rules?on_conflict=business_id,amount_minor",
            body,
            session.accessToken,
            "resolution=merge-duplicates,return=minimal"
        )
    }


    fun listFulfilmentOrders(session: BingwaSession, businessId: String): List<FulfilmentOrderSummary> {
        val query = "/rest/v1/orders?select=id,recipient_phone,customer_price_kes,status,offer_id,created_at&business_id=eq.$businessId&order=created_at.desc&limit=25"
        val rows = JSONArray(request("GET", query, token = session.accessToken))
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                add(FulfilmentOrderSummary(
                    id = row.getString("id"),
                    recipientPhone = row.optString("recipient_phone"),
                    amountKes = row.optLong("customer_price_kes"),
                    status = row.optString("status"),
                    offerId = row.optString("offer_id"),
                    createdAt = row.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                ))
            }
        }
    }

    fun retryFulfilmentOrderAfterReconciliation(
        session: BingwaSession,
        businessId: String,
        orderId: String,
        managerReason: String
    ) {
        require(managerReason.trim().length >= 10) { "Enter a reconciliation note of at least 10 characters." }
        val body = JSONObject()
            .put("p_business_id", businessId)
            .put("p_order_id", orderId)
            .put("p_manager_reason", managerReason.trim().take(500))
        request("POST", "/rest/v1/rpc/retry_fulfilment_order_after_reconciliation", body, session.accessToken)
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


    fun createSambazaSubscriptionIntent(
        session: BingwaSession,
        businessId: String,
        planCode: String
    ): String {
        require(businessId.isNotBlank() && planCode.isNotBlank())
        val response = JSONObject(request(
            "POST",
            "/functions/v1/sambaza-subscriptions",
            JSONObject()
                .put("action", "create-intent")
                .put("business_id", businessId)
                .put("plan_code", planCode),
            token = session.accessToken
        ))
        return response.getString("attempt_id")
    }

    fun recordSambazaSenderResponse(
        session: BingwaSession,
        businessId: String,
        attemptId: String,
        responseText: String
    ): String {
        require(businessId.isNotBlank() && attemptId.isNotBlank() && responseText.isNotBlank())
        val response = JSONObject(request(
            "POST",
            "/functions/v1/sambaza-subscriptions",
            JSONObject()
                .put("action", "sender-result")
                .put("business_id", businessId)
                .put("attempt_id", attemptId)
                .put("response", responseText.take(1200)),
            token = session.accessToken
        ))
        return response.optString("status", "unknown")
    }

    fun getSambazaSubscriptionStatus(
        session: BingwaSession,
        businessId: String,
        attemptId: String
    ): String {
        require(businessId.isNotBlank() && attemptId.isNotBlank())
        val response = JSONObject(request(
            "POST",
            "/functions/v1/sambaza-subscriptions",
            JSONObject()
                .put("action", "status")
                .put("business_id", businessId)
                .put("attempt_id", attemptId),
            token = session.accessToken
        ))
        return response.optString("status", "unknown")
    }

    fun cancelSambazaSubscriptionIntent(
        session: BingwaSession,
        businessId: String,
        attemptId: String
    ) {
        require(businessId.isNotBlank() && attemptId.isNotBlank())
        request(
            "POST",
            "/functions/v1/sambaza-subscriptions",
            JSONObject()
                .put("action", "cancel-intent")
                .put("business_id", businessId)
                .put("attempt_id", attemptId),
            token = session.accessToken
        )
    }

    fun getPlatformWalletSnapshot(
        session: BingwaSession,
        businessId: String
    ): PlatformWalletSnapshot {
        require(businessId.isNotBlank())

        val planRows = JSONArray(request(
            "GET",
            "/rest/v1/platform_subscription_plans?select=code,name,duration_seconds,price_kes,description&is_active=eq.true&order=sort_order.asc",
            token = session.accessToken
        ))
        val plans = buildList {
            for (i in 0 until planRows.length()) {
                val row = planRows.getJSONObject(i)
                add(PlatformSubscriptionPlan(
                    code = row.getString("code"),
                    name = row.getString("name"),
                    durationSeconds = row.optLong("duration_seconds"),
                    priceKes = row.optInt("price_kes"),
                    description = row.optString("description")
                ))
            }
        }

        val packageRows = JSONArray(request(
            "GET",
            "/rest/v1/platform_token_packages?select=code,tokens,price_kes&is_active=eq.true&order=sort_order.asc",
            token = session.accessToken
        ))
        val packages = buildList {
            for (i in 0 until packageRows.length()) {
                val row = packageRows.getJSONObject(i)
                add(PlatformTokenPackage(
                    code = row.getString("code"),
                    tokens = row.optInt("tokens"),
                    priceKes = row.optInt("price_kes")
                ))
            }
        }

        val subscriptionRows = JSONArray(request(
            "GET",
            "/rest/v1/business_platform_subscriptions?select=id,plan_code,status,starts_at,ends_at&business_id=eq.$businessId&order=created_at.desc&limit=30",
            token = session.accessToken
        ))
        val subscriptions = buildList {
            for (i in 0 until subscriptionRows.length()) {
                val row = subscriptionRows.getJSONObject(i)
                add(PlatformSubscriptionRecord(
                    id = row.getString("id"),
                    planCode = row.getString("plan_code"),
                    status = row.optString("status"),
                    startsAt = row.optString("starts_at"),
                    endsAt = row.optString("ends_at")
                ))
            }
        }

        val balanceRows = JSONArray(request(
            "GET",
            "/rest/v1/platform_wallet_balances?select=token_balance,reserved_tokens&business_id=eq.$businessId&limit=1",
            token = session.accessToken
        ))
        val balance = if (balanceRows.length() == 0) 0L else balanceRows.getJSONObject(0).optLong("token_balance")
        val reserved = if (balanceRows.length() == 0) 0L else balanceRows.getJSONObject(0).optLong("reserved_tokens")

        val ledgerRows = JSONArray(request(
            "GET",
            "/rest/v1/business_platform_token_ledger?select=id,entry_type,tokens_delta,source_type,note,created_at&business_id=eq.$businessId&order=created_at.desc&limit=30",
            token = session.accessToken
        ))
        val ledger = buildList {
            for (i in 0 until ledgerRows.length()) {
                val row = ledgerRows.getJSONObject(i)
                add(PlatformTokenLedgerEntry(
                    id = row.getString("id"),
                    entryType = row.optString("entry_type"),
                    tokensDelta = row.optInt("tokens_delta"),
                    sourceType = row.optString("source_type"),
                    note = row.optString("note").takeIf { it.isNotBlank() && it != "null" },
                    createdAt = row.optString("created_at")
                ))
            }
        }

        return PlatformWalletSnapshot(
            plans = plans,
            packages = packages,
            subscriptions = subscriptions,
            tokenEntries = ledger,
            tokenBalance = balance,
            reservedTokens = reserved
        )
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
