package com.telvanta.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

private const val BP_ALERT_CHANNEL = "bingwapulse_live_activity"
private const val BP_TEST_NOTIFICATION_ID = 7401

@Composable
fun PermissionSetupScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf("Grant the Android access below to activate live device monitoring.") }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notice = if (granted) "Notifications permission granted." else "Notifications permission was not granted."
        refresh++
    }

    val postNotificationsGranted = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val listenerEnabled = remember(refresh) { isNotificationListenerEnabled(context) }
    val accessibilityEnabled = remember(refresh) { isAccessibilityServiceEnabled(context) }

    LaunchedEffect(Unit) { createBingwaPulseNotificationChannel(context) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("DEVICE ACTIVATION", color = Color(0xFF00C9F2), style = MaterialTheme.typography.labelLarge)
        Text("Bring BingwaPulse online", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text("Android protects sensitive access. Each permission is requested by the operating system and you choose whether to grant it.", color = Color(0xFF91A4C7))
        PermissionStatusCard(
            "Notifications",
            "Allow BingwaPulse to show payment alerts, order progress and worker events.",
            postNotificationsGranted,
            if (postNotificationsGranted) "Granted" else "Required"
        ) {
            if (Build.VERSION.SDK_INT >= 33 && !postNotificationsGranted) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }
        PermissionStatusCard(
            "M-PESA notification access",
            "Lets the Android listener read incoming notification text from the payment app. It does not prove payment; the server must verify each receipt.",
            listenerEnabled,
            if (listenerEnabled) "Connected" else "Not connected"
        ) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        PermissionStatusCard(
            "USSD screen access",
            "Lets BingwaPulse observe supported USSD screens to identify menu and transaction states. It does not grant access to your M-PESA PIN.",
            accessibilityEnabled,
            if (accessibilityEnabled) "Connected" else "Not connected"
        ) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        Button(onClick = {
            refresh++
            notice = "Permission status refreshed."
        }, modifier = Modifier.fillMaxWidth()) { Text("Refresh device permissions") }
        OutlinedButton(
            onClick = {
                if (!postNotificationsGranted) {
                    notice = "Grant notification permission first."
                } else {
                    createBingwaPulseNotificationChannel(context)
                    val notification = NotificationCompat.Builder(context, BP_ALERT_CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("BingwaPulse is online")
                        .setContentText("Test alert delivered successfully. Device notifications are working.")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .build()
                    NotificationManagerCompat.from(context).notify(BP_TEST_NOTIFICATION_ID, notification)
                    notice = "Test notification sent. Check your notification shade."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Send me a test notification") }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111B38))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("LIVE DEVICE STATUS", color = Color(0xFF36B3EB), style = MaterialTheme.typography.labelLarge)
                Text("Notifications: " + if (postNotificationsGranted) "Ready" else "Permission needed", color = Color.White)
                Text("Payment alert reader: " + if (listenerEnabled) "Connected" else "Waiting for access", color = Color.White)
                Text("USSD screen observer: " + if (accessibilityEnabled) "Connected" else "Waiting for access", color = Color.White)
            }
        }
        Text(notice, color = Color(0xFF36B3EB))
    }
}

@Composable
private fun PermissionStatusCard(
    title: String,
    description: String,
    enabled: Boolean,
    status: String,
    onClick: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111B38))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(status, color = if (enabled) Color(0xFF36B3EB) else Color(0xFFFFB86B), style = MaterialTheme.typography.labelMedium)
            }
            Text(description, color = Color(0xFF91A4C7), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(if (enabled) "Open settings" else "Grant access")
            }
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    return enabled.split(':').any { it.equals(context.packageName + "/com.telvanta.app.MpesaNotificationListenerService", true) }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    return enabled.split(':').any { it.equals(context.packageName + "/com.telvanta.app.UssdAccessibilityService", true) }
}

private fun createBingwaPulseNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(BP_ALERT_CHANNEL, "BingwaPulse live activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Payment alerts, order progress and device activity"
            }
        )
    }
}
