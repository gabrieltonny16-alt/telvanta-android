package com.bingwapulse.connectpulse

import android.os.BatteryManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ConnectPulseCredentials(val deviceId: String, val credential: String)

class ConnectPulseSecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("connectpulse_device_secret", Context.MODE_PRIVATE)
    private val alias = "connectpulse.device.aes"

    fun save(value: ConnectPulseCredentials) {
        val json = JSONObject().put("device_id", value.deviceId).put("credential", value.credential)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val packed = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        prefs.edit().putString("device", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun load(): ConnectPulseCredentials? {
        val encoded = prefs.getString("device", null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size <= 12) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            val json = JSONObject(String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8))
            ConnectPulseCredentials(json.getString("device_id"), json.getString("credential"))
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() { prefs.edit().remove("device").apply() }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

object ConnectPulseApi {
    private const val ENDPOINT = "https://tuuhucisljlaaovtnbeu.supabase.co/functions/v1/connectpulse-device"
    private const val PUBLISHABLE_KEY = "sb_publishable_6ymGXYgVhVjk5GXY_KfYfA_3hul4qS"

    fun pair(code: String, deviceLabel: String): ConnectPulseCredentials {
        val response = post(JSONObject().put("action", "pair").put("code", code.uppercase())
            .put("device_label", deviceLabel.trim()))
        return ConnectPulseCredentials(response.getString("device_id"), response.getString("credential"))
    }

    fun status(credentials: ConnectPulseCredentials): String {
        val response = post(JSONObject().put("action", "status")
            .put("device_id", credentials.deviceId).put("credential", credentials.credential))
        return response.optString("status", "unknown")
    }

    fun heartbeat(context: Context, credentials: ConnectPulseCredentials): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork
        val caps = network?.let { connectivity.getNetworkCapabilities(it) }
        val networkState = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "connected"
        }
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
        val body = JSONObject().put("action", "heartbeat")
            .put("device_id", credentials.deviceId)
            .put("credential", credentials.credential)
            .put("battery_percent", battery ?: JSONObject.NULL)
            .put("network_state", networkState)
            .put("app_version", version)
        return post(body).optString("status", "unknown")
    }

    private fun post(body: JSONObject): JSONObject {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("apikey", PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(raw).optString("error") }.getOrNull().orEmpty()
                throw IOException(message.ifBlank { "ConnectPulse request failed (HTTP $status)." })
            }
            return JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }
}

