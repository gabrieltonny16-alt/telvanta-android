package com.telvanta.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SettingsBg = Color(0xFF080C12)
private val SettingsCard = Color(0xFF131B26)
private val SettingsCyan = Color(0xFF00E5FF)
private val SettingsMuted = Color(0xFF8A99AD)

@Composable
fun BingwaPulseSettingsScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bingwapulse_settings", Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    var processMpesa by remember { mutableStateOf(prefs.getBoolean("process_mpesa", true)) }
    var processTill by remember { mutableStateOf(prefs.getBoolean("process_till", true)) }
    var processPaybill by remember { mutableStateOf(prefs.getBoolean("process_paybill", false)) }
    var processSiteLink by remember { mutableStateOf(prefs.getBoolean("process_sitelink", true)) }
    var autoSave by remember { mutableStateOf(prefs.getBoolean("auto_save_contacts", true)) }
    var engageBot by remember { mutableStateOf(prefs.getBoolean("engage_bot", true)) }
    var mode by remember { mutableStateOf(prefs.getString("processing_mode", "express") ?: "express") }
    val notificationGranted = Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val listenerEnabled = remember(refresh) {
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            .split(':').any { it.startsWith(context.packageName + "/") }
    }
    val accessibilityEnabled = remember(refresh) {
        val expected = context.packageName + "/" + UssdAccessibilityService::class.java.name
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').any { it.equals(expected, true) }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    fun save(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    @Composable fun sectionTitle(text: String) {
        Text(text.uppercase(), color = SettingsMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp))
    }
    @Composable fun settingRow(icon: String, title: String, detail: String, action: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(color = Color(0xFF103642), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(icon, color = SettingsCyan, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(detail, color = SettingsMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = SettingsMuted, style = MaterialTheme.typography.headlineSmall)
        }
    }
    @Composable fun settingToggle(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(detail, color = SettingsMuted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = change)
        }
    }
    @Composable fun settingsGroup(content: @Composable ColumnScope.() -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = SettingsCard), shape = RoundedCornerShape(17.dp),
            modifier = Modifier.fillMaxWidth(), content = { Column(content = content) })
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Settings", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)
                    Text("BINGWAPULSE CONTROL CENTRE", color = SettingsMuted, letterSpacing = 1.4.sp, style = MaterialTheme.typography.labelSmall)
                }
                Surface(color = Color(0xFF103642), shape = RoundedCornerShape(50)) {
                    Text("BingwaPulse", color = SettingsCyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = SettingsCard), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    Surface(color = Color(0xFF103642), shape = RoundedCornerShape(50), modifier = Modifier.size(58.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("BP", color = SettingsCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Your BingwaPulse account", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Manage your profile and security", color = SettingsMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("✎", color = SettingsCyan, modifier = Modifier.clickable { onNavigate("Account & Session") }.padding(8.dp))
                }
            }
        }
        item { sectionTitle("Agent tools") }
        item { settingsGroup {
            settingRow("▣", "EngageBot", "Recover sales and engage customers", { onNavigate("EngageBot") })
            HorizontalDivider(color = Color(0xFF202B38))
            item("▤", "SIM Setup", "Choose SIMs for payments and USSD", { onNavigate("Quick Dial") })
            HorizontalDivider(color = Color(0xFF202B38))
            item("↩", "Auto-Reply Messages", "Edit customer SMS reply rules", { onNavigate("Auto-Reply") })
            HorizontalDivider(color = Color(0xFF202B38))
            item("♙", "My Customers", "View and manage customer records", { onNavigate("Customers") })
            HorizontalDivider(color = Color(0xFF202B38))
            item("◷", "Transaction History", "Review your transactions", { onNavigate("Transaction History") })
        } }
        item { heading("Message processing") }
        item { group {
            settingToggle("Process M-PESA Messages", "Read matching payment notifications", processMpesa) { processMpesa = it; save("process_mpesa", it) }
            HorizontalDivider(color = Color(0xFF202B38))
            toggle("Process Till Messages", "Process Till / Buy Goods confirmations", processTill) { processTill = it; save("process_till", it) }
            HorizontalDivider(color = Color(0xFF202B38))
            toggle("Process Paybill Messages", "Process Paybill confirmations", processPaybill) { processPaybill = it; save("process_paybill", it) }
            HorizontalDivider(color = Color(0xFF202B38))
            toggle("Process SiteLink Messages", "Process online-store sales notifications", processSiteLink) { processSiteLink = it; save("process_sitelink", it) }
            HorizontalDivider(color = Color(0xFF202B38))
            item("⬡", "Authorized Senders", "Manage senders allowed for processing", { onNavigate("Authorized Senders") })
            HorizontalDivider(color = Color(0xFF202B38))
            toggle("Auto-Save Contacts", "Save paying customers to your contacts", autoSave) { autoSave = it; save("auto_save_contacts", it) }
            HorizontalDivider(color = Color(0xFF202B38))
            item("◇", "Contact name tag", "Configure the saved customer name format", { onNavigate("Contact Name Tag") })
            HorizontalDivider(color = Color(0xFF202B38))
            item("⊘", "BlackList", "Manage customers excluded from offers", { onNavigate("Blacklist") })
        } }
        item { heading("Customer tools") }
        item { group { toggle("EngageBot", "Engage customers on eligible recommended offers", engageBot) { engageBot = it; save("engage_bot", it) } } }
        item { heading("Connect Pulse") }
        item { group { item("⌁", "Connect Pulse", "Pair and manage your M-PESA companion phone", { onNavigate("Connect Pulse") }) } }
        item { heading("Offline readiness") }
        item { group { item("☁", "Offline Status", "Inspect offline offer mirror and sync readiness", { onNavigate("Offline Status") }) } }
        item { heading("Processing mode") }
        item { group {
            Row(Modifier.fillMaxWidth().clickable { mode = "express"; prefs.edit().putString("processing_mode", mode).apply() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == "express", onClick = { mode = "express"; prefs.edit().putString("processing_mode", mode).apply() }, colors = RadioButtonDefaults.colors(selectedColor = SettingsCyan))
                Column(Modifier.padding(start = 8.dp)) { Text("Express", color = Color.White, fontWeight = FontWeight.SemiBold); Text("Direct USSD dial (default)", color = SettingsMuted, style = MaterialTheme.typography.bodySmall) }
            }
            Row(Modifier.fillMaxWidth().clickable { mode = "advanced"; prefs.edit().putString("processing_mode", mode).apply() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == "advanced", onClick = { mode = "advanced"; prefs.edit().putString("processing_mode", mode).apply() }, colors = RadioButtonDefaults.colors(selectedColor = SettingsCyan))
                Column(Modifier.padding(start = 8.dp)) { Text("Advanced", color = Color.White, fontWeight = FontWeight.SemiBold); Text("Menu navigation (requires Accessibility)", color = SettingsMuted, style = MaterialTheme.typography.bodySmall) }
            }
        } }
        item { heading("Permissions & automation") }
        item { group {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App notifications · ${if (notificationGranted) "ALLOWED" else "BLOCKED"}", color = Color.White, fontWeight = FontWeight.SemiBold)
                Button(onClick = { if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }, modifier = Modifier.fillMaxWidth()) { Text(if (notificationGranted) "Open notification settings" else "Allow app notifications") }
                HorizontalDivider(color = Color(0xFF202B38))
                Text("Notification access · ${if (listenerEnabled) "ENABLED" else "NOT ENABLED"}", color = Color.White, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }, modifier = Modifier.fillMaxWidth()) { Text("Grant Notification Access") }
                HorizontalDivider(color = Color(0xFF202B38))
                Text("USSD accessibility · ${if (accessibilityEnabled) "ENABLED" else "NOT ENABLED"}", color = Color.White, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Enable Accessibility Service") }
                OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) { Text("Refresh permission status") }
            }
        } }
        item { heading("Account settings") }
        item { group {
            item("♙", "Profile Information", "Update your personal details", { onNavigate("Account & Session") })
            HorizontalDivider(color = Color(0xFF202B38))
            item("▣", "Security", "Manage your account security and PIN", { onNavigate("Account & Session") })
        } }
        item { heading("Support") }
        item { group {
            item("↓", "Updates", "Check the latest app version", { onNavigate("Updates") })
            HorizontalDivider(color = Color(0xFF202B38))
            item("ⓘ", "About BingwaPulse", "App version and information", { onNavigate("About BingwaPulse") })
        } }
        item { Text("Settings are saved on this device. Android special permissions must be granted in system settings.", color = SettingsMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp)) }
    }
}
