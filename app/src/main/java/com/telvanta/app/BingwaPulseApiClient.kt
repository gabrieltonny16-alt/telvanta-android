package com.telvanta.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class BingwaPulseApiClient(context: Context, private val baseUrl: String) {
    private val sessions = SessionStore(context.applicationContext)
    private val base = baseUrl.trim().trimEnd('/')

    init {
        require(base.startsWith("https://", ignoreCase = true)) { "Use an HTTPS API address. Plain HTTP is not allowed for account sessions." }
    }

    fun login(email: String, password: String) {
        val response = request("POST", "/v1/auth/login", JSONObject().put("email", email.trim()).put("password", password), authenticated = false)
        val token = response.optString("token")
        require(token.isNotBlank()) { "API did not return a session token" }
        sessions.saveToken(token)
    }

    fun isSignedIn(): Boolean = !sessions.token().isNullOrBlank()
    fun signOut() = sessions.clear()

    fun sync(db: LocalDatabase): String {
        val token = sessions.token() ?: throw IllegalStateException("Sign in before syncing")
        var applied = 0
        var conflicts = 0
        val pending = db.pendingSyncOperations()
        var offset = 0
        while (offset < pending.length()) {
            val batch = JSONArray()
            for (i in offset until minOf(offset + 100, pending.length())) batch.put(pending.getJSONObject(i))
            val response = request("POST", "/v1/sync/push", JSONObject().put("operations", batch), token = token)
            val results = response.optJSONArray("results") ?: JSONArray()
            for (i in 0 until results.length()) {
                val result = results.getJSONObject(i)
                val outcome = result.optString("outcome")
                val revision = if (result.isNull("serverRevision")) null else result.optLong("serverRevision")
                db.markSyncResult(result.optString("operationId"), outcome, revision)
                if (outcome == "APPLIED") applied++ else if (outcome == "CONFLICT") conflicts++
            }
            offset += batch.length()
        }

        var cursor = db.syncCursor()
        var keepGoing = true
        var pages = 0
        while (keepGoing && pages++ < 20) {
            val response = request("GET", "/v1/sync/pull?after=$cursor&limit=100", token = token)
            val events = response.optJSONArray("events") ?: JSONArray()
            if (events.length() == 0) break
            var stopped = false
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i)
                if (!db.applyRemoteEvent(event)) { stopped = true; break }
                cursor = event.optLong("cursor", cursor)
                db.setSyncCursor(cursor)
            }
            if (stopped) break
            keepGoing = response.optBoolean("hasMore", false)
        }
        return "Sync complete · $applied changes uploaded · $conflicts conflicts · ${db.pendingCount()} pending"
    }

    private fun request(method: String, path: String, body: JSONObject? = null, authenticated: Boolean = true, token: String? = sessions.token()): JSONObject {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            if (authenticated && !token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) throw IllegalStateException(json.optString("error").ifBlank { "API request failed ($status)" })
            return json
        } finally { connection.disconnect() }
    }
}
