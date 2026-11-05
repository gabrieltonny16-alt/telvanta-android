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

    fun hasSession(): Boolean = load()?.expiresAtEpochSeconds?.let { it > System.currentTimeMillis() / 1000L + 60L } == true

    fun save(session: BingwaSession) {
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
        prefs.edit().remove("session").apply()
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

        request(
            "POST", "/rest/v1/payment_notification_candidates",
            body = body,
            token = session.accessToken,
            prefer = "resolution=ignore-duplicates,return=minimal"
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
