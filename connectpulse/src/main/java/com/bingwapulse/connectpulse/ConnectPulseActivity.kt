package com.bingwapulse.connectpulse

import android.os.Bundle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    var deviceName by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CONNECTPULSE", color = White, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = 2.sp)
                Text("MOVE. CONNECT. CONTROL.", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.8.sp)
                Text("BingwaPulse companion", color = Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) { index -> Spacer(Modifier.size(width = (22 - index * 4).dp, height = 3.dp).clip(CircleShape).background(if (index == 0) Cyan else ElectricBlue)) }
                }
            }

            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Surface(color = ElectricBlue.copy(alpha = 0.18f), shape = CircleShape, modifier = Modifier.size(34.dp)) {
                            Spacer(Modifier.size(1.dp))
                        }
                        Text("DEVICE LINK", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                    }
                    Text("Connection status", color = Muted, fontSize = 13.sp)
                    Text("NOT CONNECTED", color = White, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 0.5.sp)
                    Text("No pairing or data sync is active on this device.", color = Muted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) { index ->
                            Spacer(
                                Modifier.weight(1f).height(4.dp)
                                    .clip(CircleShape)
                                    .background(if (index == 0) ElectricBlue else PanelBright)
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Pair your device", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                Text("Link this companion to your BingwaPulse workspace when secure pairing is available.", color = Muted, fontSize = 13.sp)
            }

            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Device name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = PanelBright,
                    focusedLabelColor = Cyan,
                    cursorColor = Cyan
                )
            )
            OutlinedTextField(
                value = pairingCode,
                onValueChange = { pairingCode = it.filter(Char::isLetterOrDigit).take(8) },
                label = { Text("Pairing code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = PanelBright,
                    focusedLabelColor = Cyan,
                    cursorColor = Cyan
                )
            )
            Button(
                onClick = {
                    message = if (deviceName.isBlank() || pairingCode.length < 6) {
                        "Enter a device name and a code with at least 6 characters."
                    } else {
                        "Pairing service is not connected yet. No device was paired and no data was transferred."
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White)
            ) {
                Text("VERIFY & CONNECT", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
            if (message.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = PanelBright), shape = RoundedCornerShape(16.dp)) {
                    Text(message, color = Cyan, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(15.dp))
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("BUILT FOR MOVEMENT", color = Cyan, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    Text("Secure pairing · device status · controlled sync", color = White, fontWeight = FontWeight.Bold)
                    Text("The companion interface is ready for its pairing service. Secure pairing, remote sync and background services must be implemented before connection can become active.", color = Muted, fontSize = 12.sp)
                }
            }
            Text("No payments, SMS, USSD or background data transfer are enabled.", color = Muted, fontSize = 11.sp)
        }
    }
}
