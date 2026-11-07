package com.bingwapulse.connectpulse

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val Background = Color(0xFF050817)
private val Panel = Color(0xFF101A35)
private val PanelBright = Color(0xFF17264A)
private val ElectricBlue = Color(0xFF397BFF)
private val Cyan = Color(0xFF42E8FF)
private val White = Color(0xFFF3F7FF)
private val Muted = Color(0xFF9BAACB)

class ConnectPulseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 8, 23)
        window.navigationBarColor = android.graphics.Color.rgb(5, 8, 23)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ElectricBlue,
                    secondary = Cyan,
                    background = Background,
                    surface = Panel,
                    onPrimary = Color.White,
                    onBackground = White,
                    onSurface = White
                )
            ) {
                ConnectPulseHome()
            }
        }
    }
}

@Composable
private fun ConnectPulseHome() {
    val context = LocalContext.current
    val store = remember { ConnectPulseSecureStore(context) }
    val scope = rememberCoroutineScope()
    var deviceName by remember { mutableStateOf("Shop phone") }
    var pairingCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var credentials by remember { mutableStateOf(store.load()) }
    var connectionStatus by remember { mutableStateOf("NOT PAIRED") }
    var busy by remember { mutableStateOf(false) }

    fun scheduleHeartbeat() {
        val request = PeriodicWorkRequestBuilder<ConnectPulseHeartbeatWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "connectpulse-device-heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    LaunchedEffect(credentials?.deviceId) {
        val saved = credentials
        if (saved != null) {
            scheduleHeartbeat()
            try {
                val status = withContext(Dispatchers.IO) { ConnectPulseApi.status(saved) }
                connectionStatus = status.replace("_", " ").uppercase()
                message = if (status == "pending_pairing") {
                    "Pairing request sent. Approve this device in BingwaPulse."
                } else if (status == "online") {
                    "Device is approved. Secure heartbeat monitoring is scheduled."
                } else {
                    "Device status: " + status
                }
            } catch (error: Exception) {
                connectionStatus = "CHECK CONNECTION"
                message = error.message ?: "Could not reach BingwaPulse."
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CONNECTPULSE", color = White, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = 2.sp)
                Text("MOVE. CONNECT. CONTROL.", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.8.sp)
                Text("BingwaPulse companion", color = Muted, fontSize = 13.sp)
            }

            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DEVICE LINK", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                    Text("Connection status", color = Muted, fontSize = 13.sp)
                    Text(connectionStatus, color = if (connectionStatus == "ONLINE") Cyan else White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(
                        if (credentials == null) "No companion credential is stored on this device."
                        else "Device ID: " + credentials!!.deviceId,
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (credentials == null) "Pair this companion" else "Device is registered", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                Text(
                    if (credentials == null) "Generate a one-time code in BingwaPulse, then enter it here."
                    else "The owner must approve the pairing request in BingwaPulse before heartbeats are accepted.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }

            if (credentials == null) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it.take(100) },
                    label = { Text("Device name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = PanelBright, focusedLabelColor = Cyan, cursorColor = Cyan)
                )
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it.filter(Char::isLetterOrDigit).take(10).uppercase() },
                    label = { Text("10-character pairing code") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = PanelBright, focusedLabelColor = Cyan, cursorColor = Cyan)
                )
                Button(
                    onClick = {
                        if (deviceName.trim().length < 2 || pairingCode.length != 10) {
                            message = "Enter a device name and the complete 10-character code."
                        } else {
                            busy = true
                            message = "Verifying one-time pairing code…"
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        ConnectPulseApi.pair(pairingCode, deviceName)
                                    }
                                    store.save(result)
                                    credentials = result
                                    pairingCode = ""
                                    scheduleHeartbeat()
                                    connectionStatus = "PENDING APPROVAL"
                                    message = "Pairing request sent. Open BingwaPulse and approve this device."
                                } catch (error: Exception) {
                                    message = error.message ?: "Pairing could not be completed."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White)
                ) {
                    Text(if (busy) "VERIFYING…" else "VERIFY & SEND PAIRING REQUEST", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
                }
            } else {
                Button(
                    onClick = {
                        val saved = credentials
                        if (saved != null) {
                            busy = true
                            scope.launch {
                                try {
                                    val status = withContext(Dispatchers.IO) { ConnectPulseApi.heartbeat(context, saved) }
                                    connectionStatus = status.replace("_", " ").uppercase()
                                    message = when (status) {
                                        "online" -> "Heartbeat received by BingwaPulse."
                                        "pending_pairing" -> "Waiting for the owner to approve this device."
                                        "paused" -> "This device is paused by the business owner."
                                        "revoked" -> "This device has been revoked. Contact the owner."
                                        else -> "Device status: " + status
                                    }
                                } catch (error: Exception) {
                                    message = error.message ?: "Heartbeat failed. It will retry later."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White)
                ) {
                    Text(if (busy) "CHECKING…" else "CHECK APPROVAL & SEND HEARTBEAT", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp)
                }
            }

            if (message.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = PanelBright), shape = RoundedCornerShape(16.dp)) {
                    Text(message, color = Cyan, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(15.dp))
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SECURE DEVICE MONITORING", color = Cyan, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    Text("Device-specific secret · encrypted local storage · periodic heartbeat", color = White, fontWeight = FontWeight.Bold)
                    Text("ConnectPulse reports its own app, battery and network state. This first connection stage does not yet inspect or remotely restart BingwaPulse, and it cannot restart a powered-off phone.", color = Muted, fontSize = 12.sp)
                }
            }
            Text("No payments, SMS or USSD purchases are initiated by ConnectPulse.", color = Muted, fontSize = 11.sp)
        }
    }
}
