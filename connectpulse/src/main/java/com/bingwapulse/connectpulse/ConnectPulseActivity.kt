package com.bingwapulse.connectpulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF061A12)
private val Green = Color(0xFF28D879)
private val Lime = Color(0xFFD9FF24)
private val White = Color(0xFFE7FFF2)
private val Muted = Color(0xFF9CB9AA)

class ConnectPulseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Green, secondary = Lime, background = Background, surface = Color(0xFF102B20))) {
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
            modifier = Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("CONNECT PULSE", color = Lime, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp)
            Text("BingwaPulse companion", color = Muted)
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF102B20)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CONNECTION STATUS", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("NOT CONNECTED", color = Lime, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                    Text("No pairing or data sync is active on this device.", color = Muted)
                }
            }
            Text("Pair with BingwaPulse", color = White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            OutlinedTextField(value = deviceName, onValueChange = { deviceName = it }, label = { Text("Device name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = pairingCode, onValueChange = { pairingCode = it.filter(Char::isLetterOrDigit).take(8) }, label = { Text("Pairing code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = {
                message = if (deviceName.isBlank() || pairingCode.length < 6) {
                    "Enter a device name and a code with at least 6 characters."
                } else {
                    "Pairing service is not connected yet. No device was paired and no data was transferred."
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Verify and connect")
            }
            if (message.isNotBlank()) Text(message, color = Green, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Secure pairing · device status · controlled sync", color = White, fontWeight = FontWeight.Bold)
            Text("This first build provides the companion interface only. Secure pairing, remote sync, and background services must be implemented before connection can become active.", color = Muted)
            Text("No payments, SMS, USSD, or background data transfer are enabled.", color = Muted, fontSize = 12.sp)
        }
    }
}
