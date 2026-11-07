package com.telvanta.app

import android.os.Bundle
import android.content.Context
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.content.pm.PackageManager
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFFF5FFF8)
private val NavyDeep = Color(0xFF010B06)
private val Teal = Color(0xFF00D66B)
private val Muted = Color(0xFF91AD9D)
private val Canvas = Color(0xFF010905)

class MainActivity : ComponentActivity() {
    private lateinit var localDb: LocalDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SecureSessionStore(this).hasSession()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        localDb = LocalDatabase(this)
        setContent { BingwaPulseApp(localDb) }
    }
    override fun onDestroy() { if (::localDb.isInitialized) localDb.close(); super.onDestroy() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BingwaPulseApp(db: LocalDatabase) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf("Dashboard") }
    var message by remember { mutableStateOf("") }
    val navGroups = listOf(
        "MAIN" to listOf("Dashboard", "Wallet", "Offers", "Transaction History"),
        "TOOLS" to listOf("Bulk Messenger", "Quick Dial", "Auto Renewals", "SiteLink", "Auto-Reply"),
        "MANAGEMENT" to listOf("Customers", "Signature", "Statistics", "Connect Pulse"),
        "SUPPORT" to listOf("Settings", "Updates", "Help & Support", "About BingwaPulse")
    )
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFF00D66B),secondary=Color(0xFFB7FF24),background=Canvas,surface=Color(0xFF082A1A),onPrimary=Color(0xFF001B0D),onSecondary=Color(0xFF102000),onBackground=Navy,onSurface=Navy), shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp),extraLarge=RoundedCornerShape(30.dp))) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Color(0xFF03150C), drawerContentColor = Color.White) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("BINGWAPULSE", color = Color(0xFFB7FF24), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        Text("POWER EVERY CONNECTION", color = Muted, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(12.dp))
                    }
                    HorizontalDivider(color = Color(0xFF16452B))
                    navGroups.forEach { group ->
                        Text(
                            group.first,
                            color = Color(0xFF7FAE96),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.1.sp,
                            modifier = Modifier.padding(start = 22.dp, top = 15.dp, bottom = 5.dp)
                        )
                        group.second.forEach { tab ->
                            NavigationDrawerItem(
                                label = { Text(tab, fontWeight = if (selected == tab) FontWeight.Bold else FontWeight.Normal) },
                                selected = selected == tab,
                                onClick = { selected = tab; message = ""; scope.launch { drawerState.close() } },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Color(0xFF0B4A28),
                                    selectedTextColor = Color(0xFFB7FF24),
                                    unselectedTextColor = Color(0xFFE7FFF2)
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFF16452B), modifier = Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(
                        label = { Text("Sign out", color = Color(0xFFFFB4AB), fontWeight = FontWeight.SemiBold) },
                        selected = false,
                        onClick = {
                            SecureSessionStore(context).clear()
                            context.startActivity(Intent(context, AuthActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color(0xFFFFB4AB)),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Account secured · local records remain on this device", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                }
            }
        ) {
        Scaffold(containerColor=Canvas,bottomBar={
            NavigationBar(containerColor = Color(0xFF03170D), contentColor = Teal) {
                val bottomItems = listOf("Dashboard", "Transaction History", "Wallet", "Account & Session")
                val bottomLabels = listOf("Home", "Transactions", "Wallet", "Profile")
                val bottomIcons = listOf("▦", "↔", "▣", "●")
                bottomItems.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selected == destination || (destination == "Transaction History" && selected == "Transactions"),
                        onClick = { selected = destination; message = "" },
                        icon = { Text(bottomIcons[index], fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                        label = { Text(bottomLabels[index], maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Teal,
                            selectedTextColor = Teal,
                            indicatorColor = Color(0xFF104329),
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        },topBar={ TopAppBar(
            navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Text("≡", color = Color(0xFFB7FF24), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium) } },
            title={Column(verticalArrangement=Arrangement.spacedBy(2.dp)) { Text("BINGWAPULSE",fontWeight=FontWeight.Black,color=Color.White,letterSpacing=1.3.sp,style=MaterialTheme.typography.titleLarge); Text(selected.uppercase(),style=MaterialTheme.typography.labelSmall,color=Color(0xFFB7FF24),letterSpacing=1.5.sp) }},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=NavyDeep)
        ) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(vertical=16.dp)) {
                item { Surface(color=Color(0xFF082A1A),shape=MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=11.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) { Surface(color=Teal,shape=MaterialTheme.shapes.small) { Text("●",color=Color.White,modifier=Modifier.padding(horizontal=7.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall) }; Column { Text("OFFLINE-READY WORKSPACE",color=Navy,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelMedium); Text("Your records stay on this device",color=Muted,style=MaterialTheme.typography.bodySmall) } } } }
                if (SecureSessionStore(context).isLocalWorkspace()) {
                    item { InfoCard("LOCAL PREVIEW MODE", "You are exploring the app locally. Sign in later to connect a real business account. Server sync and device pairing are unavailable in this mode.") }
                }
                item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when(selected) {
                        "Dashboard" -> BingwaDashboard(db) { destination -> selected = destination; message = "" }
                        "Offers" -> OffersScreen(db){message=it}
                        "Customers" -> CustomersScreen(db){message=it}
                        "Orders" -> OrdersScreen(db){message=it}
                        "Transactions", "Transaction History" -> TransactionsScreen(db){message=it}
                        "Bulk Messenger" -> BulkMessengerScreen(db)
                        "Auto-Reply" -> AutoReplyScreen()
                        "Help & Support" -> FeatureWorkspaceScreen("Help & Support")
                        "About BingwaPulse" -> FeatureWorkspaceScreen("About BingwaPulse")
                        "EngageBot" -> EngageBotScreen()
                        "Message Processing" -> FeatureWorkspaceScreen("Message Processing")
                        "Account & Session" -> AccountSessionScreen { message = it }
                        "SiteLink" -> SiteLinkScreen(db) { message = it }
                        "Signature Offers", "Signature" -> SignatureOffersScreen(db) { message = it }
                        "Statistics" -> StatisticsScreen(db)
                        "Wallet" -> WalletScreen()
                        "Quick Dial" -> FeatureWorkspaceScreen("Quick Dial")
                        "Auto Renewals" -> FeatureWorkspaceScreen("Auto Renewals")
                        "Signature" -> FeatureWorkspaceScreen("Signature")
                        "Connect Pulse" -> ConnectPulseScreen()
                        "Updates" -> UpdatesScreen()
                        "Settings" -> UssdAutomationSettingsScreen()
                        else -> FeatureWorkspaceScreen(selected)
                    }
                    }
                }
                if(message.isNotBlank()) item { Text(message,color=Teal,fontWeight=FontWeight.SemiBold) }
                item { Text("BingwaPulse · offline core · local-only data",color=Muted,style=MaterialTheme.typography.labelSmall) }
            }
        }
        }
    }
}



@Composable
private fun ConnectPulseScreen() {
    val context = LocalContext.current
    val store = remember { SecureSessionStore(context) }
    val api = remember { BingwaSupabaseApi() }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(store.load()) }
    var deviceLabel by remember { mutableStateOf("Shop phone") }
    var pairingCode by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf<List<BingwaDevice>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    if (store.isLocalWorkspace()) {
        Text("Connect Pulse", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Navy)
        InfoCard(
            "Connect Pulse is locked in local preview",
            "You can inspect the rest of BingwaPulse without signing in. Device pairing, server sync and account-backed controls require a real authenticated business account."
        )
        return
    }

    fun refreshDevices() {
        val current = session
        if (current == null) {
            notice = "Sign in to BingwaPulse before managing paired devices."
            return
        }
        scope.launch {
            busy = true
            try {
                val activeSession = current
                val businessId = activeSession.businessId
                    ?: withContext(Dispatchers.IO) { api.firstBusinessId(activeSession) }
                    ?: throw IllegalStateException("This account has no active business workspace.")
                if (activeSession.businessId == null) {
                    session = activeSession.copy(businessId = businessId)
                    store.save(activeSession.copy(businessId = businessId))
                }
                devices = withContext(Dispatchers.IO) { api.listDevices(activeSession, businessId) }
                notice = "Device list refreshed."
            } catch (error: Exception) {
                notice = error.message ?: "Could not load paired devices."
            } finally {
                busy = false
            }
        }
    }

    Text("Connect Pulse", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Navy)
    InfoCard(
        "Secure companion pairing",
        "Generate a one-time code here, enter it in ConnectPulse on the companion phone, then approve the device below. The code expires after 10 minutes and can be used once."
    )
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF082A1A)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PAIRING CONTROL", color = Teal, fontWeight = FontWeight.ExtraBold)
            Text("Only devices approved by a business manager become active.", color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = deviceLabel,
                onValueChange = { deviceLabel = it.take(100) },
                label = { Text("Companion device name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                enabled = !busy && deviceLabel.trim().length >= 2,
                onClick = {
                    val current = session
                    if (current == null) {
                        notice = "Sign in to BingwaPulse first."
                    } else {
                        busy = true
                        notice = "Creating a one-time pairing code…"
                        scope.launch {
                            try {
                                val businessId = current.businessId
                                    ?: withContext(Dispatchers.IO) { api.firstBusinessId(current) }
                                    ?: throw IllegalStateException("No business workspace is linked to this account.")
                                val activeSession = current.copy(businessId = businessId)
                                if (current.businessId == null) {
                                    session = activeSession
                                    store.save(activeSession)
                                }
                                val code = java.util.UUID.randomUUID().toString()
                                    .replace("-", "").take(10).uppercase(Locale.ROOT)
                                withContext(Dispatchers.IO) {
                                    api.createDevicePairingChallenge(activeSession, businessId, deviceLabel, code)
                                }
                                pairingCode = code
                                notice = "Code created. Enter it in ConnectPulse within 10 minutes, then approve the device below."
                                devices = withContext(Dispatchers.IO) { api.listDevices(activeSession, businessId) }
                            } catch (error: Exception) {
                                notice = error.message ?: "Could not create the pairing code."
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "PLEASE WAIT…" else "GENERATE PAIRING CODE") }
            if (pairingCode.isNotBlank()) {
                Text("ONE-TIME PAIRING CODE", color = Muted, style = MaterialTheme.typography.labelSmall)
                Text(pairingCode, color = Color(0xFFB7FF24), fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                Text("Share this code only with the ConnectPulse app you are pairing. It expires in 10 minutes.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Paired and pending devices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Navy)
        TextButton(onClick = { refreshDevices() }, enabled = !busy) { Text("Refresh") }
    }
    if (devices.isEmpty()) {
        InfoCard("No device records loaded", "Refresh to check devices registered to this business. A companion remains pending until a manager approves it.")
    } else {
        devices.forEach { device ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF082A1A)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(device.label, color = Navy, fontWeight = FontWeight.Bold)
                    Text(device.status.replace("_", " ").uppercase(Locale.ROOT), color = if (device.status == "online") Teal else Color(0xFFFFC857), fontWeight = FontWeight.ExtraBold)
                    Text("Last seen: " + (device.lastSeenAt ?: "No heartbeat received"), color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (device.status == "pending_pairing") {
                        Button(
                            enabled = !busy,
                            onClick = {
                                val current = session
                                val businessId = current?.businessId
                                if (current == null || businessId.isNullOrBlank()) {
                                    notice = "Sign in again before approving this device."
                                } else {
                                    scope.launch {
                                        busy = true
                                        try {
                                            withContext(Dispatchers.IO) { api.approveDevice(current, businessId, device.id) }
                                            devices = withContext(Dispatchers.IO) { api.listDevices(current, businessId) }
                                            notice = "Device approved. ConnectPulse can now send its heartbeat."
                                        } catch (error: Exception) {
                                            notice = error.message ?: "Could not approve this device."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("APPROVE DEVICE") }
                    }
                }
            }
        }
    }
    InfoCard(
        "What pairing enables",
        "ConnectPulse receives a device-specific secret stored in Android Keystore and sends a heartbeat after approval. A revoked or paused device cannot report as online. This first stage does not remotely restart a powered-off phone or perform USSD purchases."
    )
    if (notice.isNotBlank()) Text(notice, color = Teal, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FeatureWorkspaceScreen(title: String) {
    val context = LocalContext.current
    var fieldOne by remember(title) { mutableStateOf("") }
    var fieldTwo by remember(title) { mutableStateOf("") }
    var fieldThree by remember(title) { mutableStateOf("") }
    var option by remember(title) { mutableStateOf("Not configured") }
    var notice by remember(title) { mutableStateOf("") }
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Navy)
    InfoCard(title + " workspace", "Dedicated screen layout. This stage adds the interface only; data persistence and integrations are still pending. Live payments, SMS, USSD and remote sync remain disabled.")
    when (title) {
        "Wallet" -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("AVAILABLE TOKENS", 0, Modifier.weight(1f))
                StatCard("RECENT ACTIVITY", 0, Modifier.weight(1f))
            }
            InfoCard("Balance status", "Not connected. No wallet balance is being reported.")
            OutlinedTextField(fieldOne, { fieldOne = it }, label = { Text("Reference / transaction note") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fieldTwo, { fieldTwo = it }, label = { Text("Amount (KES) — note only") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { notice = "Preview only. No funds or tokens were moved." }, modifier = Modifier.fillMaxWidth()) { Text("Preview wallet entry") }
        }
        "Quick Dial" -> {
            OutlinedTextField(fieldOne, { fieldOne = it }, label = { Text("USSD code (e.g. *123#)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fieldTwo, { fieldTwo = it }, label = { Text("Action / offer label") }, modifier = Modifier.fillMaxWidth())
            InfoCard("SIM selection", "Choose SIM 1 or SIM 2 in the Android phone dialler or the carrier prompt. BingwaPulse does not override your phone's SIM setting.")
            Button(onClick = {
                val code = fieldOne.trim()
                val inner = if (code.length >= 2) code.substring(1, code.length - 1) else ""
                val valid = code.length in 3..20 &&
                    code.startsWith("*") && code.endsWith("#") &&
                    inner.all { it.isDigit() || it == '*' }
                if (!valid) {
                    notice = "Enter a valid USSD code beginning with * and ending with #."
                } else {
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))))
                        notice = "Phone dialler opened. Check the code and selected SIM, then tap Call yourself. No menu choice or purchase is submitted by BingwaPulse."
                    } catch (_: Exception) {
                        notice = "No compatible phone dialler was found."
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Open phone dialler") }
            InfoCard("Manual confirmation", "BingwaPulse opens the phone dialler with the USSD code. You must review it, choose the SIM and press Call. It will not automatically call or buy a bundle.")
        }
        "Auto Renewals" -> {
            OutlinedTextField(fieldOne, { fieldOne = it }, label = { Text("Customer phone") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fieldTwo, { fieldTwo = it }, label = { Text("Offer to renew") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fieldThree, { fieldThree = it }, label = { Text("Renewal interval / schedule") }, modifier = Modifier.fillMaxWidth())
            listOf("Paused", "Draft", "Ready for testing").forEach { item ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = option == item, onClick = { option = item })
                    Text(item, color = Navy)
                }
            }
            Button(onClick = { notice = "Renewal rule preview only. Automatic purchases remain disabled." }, modifier = Modifier.fillMaxWidth()) { Text("Preview renewal rule") }
        }
        "Signature" -> {
            OutlinedTextField(fieldOne, { fieldOne = it }, label = { Text("Agent / business display name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fieldTwo, { fieldTwo = it }, label = { Text("Signature text") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(fieldThree, { fieldThree = it }, label = { Text("Contact / support line") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { notice = "Preview only. Signature has not been saved." }, modifier = Modifier.fillMaxWidth()) { Text("Preview signature") }
        }
        "Connect Pulse" -> {
            InfoCard("BingwaPulse companion", "Connect Pulse is the companion connection app for BingwaPulse. It is intended to securely pair an authorized phone/device with the main BingwaPulse app.")
            InfoCard("Connection status", "Not paired")
            InfoCard("Last sync", "No sync recorded")
            OutlinedTextField(fieldOne, { fieldOne = it }, label = { Text("Pairing code (when pairing is available)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { notice = "Secure pairing is not implemented yet. No device was connected." }, modifier = Modifier.fillMaxWidth()) { Text("Check pairing") }
            Button(onClick = { notice = "Connection and sync are not implemented yet. No data was transferred." }, modifier = Modifier.fillMaxWidth()) { Text("Check connection / sync") }
            InfoCard("Safety", "This screen is a UI placeholder for now. It does not pair devices, authenticate a session, or sync data with BingwaPulse yet.")
        }
        "Message Processing" -> {
            OutlinedTextField(fieldOne, { fieldOne = it }, label = { Text("Message source (M-PESA / Till / Paybill / SiteLink)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fieldTwo, { fieldTwo = it }, label = { Text("Sender phone / sender ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fieldThree, { fieldThree = it }, label = { Text("Paste message for review") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
            InfoCard("Processing checks", "Authorized sender · duplicate detection · customer matching · order matching · classification")
            Button(onClick = { notice = if (fieldThree.isBlank()) "Paste a message before reviewing." else "Local preview only. No message was intercepted, no order was created and no reply was sent." }, modifier = Modifier.fillMaxWidth()) { Text("Review message draft") }
        }
        else -> {
            InfoCard("Account and security", "Authentication and server-side sessions are not connected in this local build.")
            listOf("Secure sign-in", "Session timeout", "Biometric lock", "Offline data protection", "Integration permissions", "Notifications").forEach { item ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = false, onCheckedChange = { notice = item + " is interface-only and is not enforced yet." })
                    Text(item, color = Navy)
                }
            }
            OutlinedTextField(fieldOne, { fieldOne = it }, label = { Text("Support contact / configuration note") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { notice = "Settings preview only. Preferences are not persisted yet." }, modifier = Modifier.fillMaxWidth()) { Text("Preview settings") }
        }
    }
    if (notice.isNotBlank()) InfoCard("Preview result", notice)
    InfoCard("Status", "Screen layout added. Local persistence, device integration and end-to-end behaviour remain pending.")
}

@Composable
private fun EngageBotScreen() {
    Text("EngageBot", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
    InfoCard("Automation control centre", "EngageBot is reserved for the message-automation workflow. The controls below are planning/status indicators only; no incoming messages are monitored and no replies are sent.")
    InfoCard("Message sources", "Planned: M-PESA, Till, Paybill and SiteLink messages, with sender authorization checks before processing.")
    InfoCard("Decision workflow", "Planned: identify the message type, match it to a customer/order, apply blacklist and duplicate checks, then prepare the appropriate response.")
    InfoCard("Safety controls", "Live messaging is disabled. Automation must remain paused until sender permissions, message parsing, audit records and explicit activation controls are implemented and tested.")
    InfoCard("Current status", "Feature entry added. Message monitoring, parsing, automated customer saving and live response delivery are not implemented in this screen yet.")
}

@Composable
private fun AccountSessionScreen(onMessage: (String) -> Unit) {
    Text("Account & Session", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
    InfoCard("Local-only account", "Authentication, multi-agent accounts and encrypted production synchronization are not implemented. This screen does not claim that a signed-in identity has been verified.")
    InfoCard("Security requirements", "Planned: authenticated profile, session expiry, secure sign-out, account access controls and clear device/session status.")
    InfoCard("Local data", "Business records currently remain on this device. Protect the phone and do not treat local records as synchronized backups.")
    Button(onClick = { onMessage("Sign out is not available yet because login and session management are not implemented. No session was ended.") }, modifier = Modifier.fillMaxWidth()) {
        Text("Sign out (not available yet)")
    }
}

@Composable
private fun SiteLinkScreen(db: LocalDatabase, onMessage: (String) -> Unit) {
    var reload by remember { mutableIntStateOf(0) }
    val offers = remember(reload) { db.offers().filter { it.active } }
    Text("SiteLink storefront", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Navy)
    InfoCard("Your shop uses My Offers", "SiteLink reads active offers directly from My Offers. Prices are shared automatically; there is no separate SiteLink price list. Deactivate an offer in My Offers to hide it here.")
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Active offers (${offers.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { reload++ }) { Text("Refresh") }
    }
    if (offers.isEmpty()) {
        InfoCard("Your storefront is empty", "Open My Offers and activate the offers you want customers to see. They will appear here at the exact same selling prices.")
    } else {
        offers.forEach { offer ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF082A1A)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(offer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Navy)
                    Text(offer.category, color = Muted)
                    Text("KES ${money(offer.price)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Teal)
                    if (!offer.verified) Text("Offer details not independently verified", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    HorizontalDivider(color = Color(0xFF16452B))
    Text("Customer checkout", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Navy)
    InfoCard("Not connected yet", "This screen is an in-app storefront preview. A public customer URL, secure M-PESA payment verification and automatic bundle delivery require a hosted backend and provider integration. Checkout is not live and no payment will be taken here.")
}

@Composable
private fun SignatureOffersScreen(db: LocalDatabase, onMessage: (String) -> Unit) {
    data class CandidateOffer(val id: String, val simProfile: String, val category: String, val name: String, val duration: String, val price: Int, val code: String, val tags: String, val note: String)
    val context = LocalContext.current
    val candidates = remember {
        runCatching {
            val raw = context.assets.open("signature_offers_seed.json").bufferedReader().use { it.readText() }
            val root = JSONObject(raw)
            val rows = root.getJSONArray("offers")
            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    add(CandidateOffer(
                        id = row.optString("id"),
                        simProfile = row.optString("simProfile", "any"),
                        category = row.optString("category", "OTHER"),
                        name = row.optString("name", "Unnamed offer"),
                        duration = row.optString("duration", "Duration not captured"),
                        price = row.optInt("amountKes", -1),
                        code = row.optString("entryCode", ""),
                        tags = row.optString("tags", ""),
                        note = row.optString("reviewNote", if (row.has("entryCode")) "Candidate code — not independently verified" else "Exact entry code not captured")
                    ))
                }
            }
        }.getOrElse { emptyList() }
    }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("ALL") }
    var simProfile by remember { mutableStateOf("ALL") }
    var showSaved by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var downloadCandidate by remember { mutableStateOf<CandidateOffer?>(null) }
    var downloadPrice by remember { mutableStateOf("") }
    val saved = remember(reload) { db.offers().filter { it.source.contains("signature", ignoreCase = true) } }
    val categories = listOf("ALL", "DATA", "VOICE", "SMS", "COMBO")
    val visible = candidates.filter {
        (category == "ALL" || it.category.equals(category, true)) &&
            (simProfile == "ALL" || it.simProfile.equals(simProfile, true)) &&
            (search.isBlank() || it.name.contains(search, true) || it.price.toString().contains(search) || it.code.contains(search, true))
    }
    Text("Signature Offers", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
    InfoCard("44 extracted Signature Offers", "Any SIM, Normal SIM and Notification SIM offers from your supplied extraction. Entries remain unverified candidates; no USSD action or purchase is triggered.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Candidates" to false, "Saved offers" to true).forEach { item ->
            FilterChip(selected = showSaved == item.second, onClick = { showSaved = item.second }, label = { Text(item.first) })
        }
    }
    if (!showSaved) {
        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search name, price or code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { item -> FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) }) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL" to "All SIMs", "any" to "Any SIM", "normal" to "Normal SIM", "notification" to "Notification SIM").forEach { item -> FilterChip(selected = simProfile == item.first, onClick = { simProfile = item.first }, label = { Text(item.second) }) }
        }
        Text("Showing ${visible.size} of ${candidates.size} candidate records", color = Muted, style = MaterialTheme.typography.labelMedium)
        if (candidates.isEmpty()) InfoCard("Catalogue unavailable", "The bundled candidate catalogue could not be read. No data was imported.")
        visible.forEach { offer ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF082A1A)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(offer.name, fontWeight = FontWeight.Bold, color = Navy)
                            Text("${offer.category} · ${offer.duration}", color = Teal, style = MaterialTheme.typography.labelSmall)
                            Text(when(offer.simProfile) { "normal" -> "NORMAL SIM"; "notification" -> "NOTIFICATION SIM"; else -> "ANY SIM" }, color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(if (offer.price >= 0) "KES ${offer.price}" else "Price unknown", color = Color(0xFFB7FF24), fontWeight = FontWeight.ExtraBold)
                    }
                    Text("Dial: " + if (offer.code.isBlank()) "Not captured" else offer.code, color = Navy)
                    if (offer.tags.isNotBlank()) Text("Tags: ${offer.tags}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(offer.note, color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("Status: CANDIDATE · NOT VERIFIED", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    Button(onClick = { downloadCandidate = offer; downloadPrice = if (offer.price >= 0) offer.price.toString() else "" }, modifier = Modifier.fillMaxWidth()) { Text("Download to My Offers") }
                }
            }
        }
    } else {
        Text("Saved signature offers (${saved.size})", fontWeight = FontWeight.Bold, color = Navy)
        if (saved.isEmpty()) InfoCard("No saved offers yet", "Candidate records are shown separately so unverified source entries do not silently become active offers.")
        saved.forEach { offer ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF082A1A)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(offer.name, fontWeight = FontWeight.Bold, color = Navy)
                    Text("KES " + money(offer.price) + " · " + offer.category, color = Muted)
                    Text("Source: " + offer.source + " · " + if (offer.verified) "Operator marked verified" else "Unverified", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    downloadCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { downloadCandidate = null },
            title = { Text("Set selling price") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(candidate.name, fontWeight = FontWeight.Bold)
                    Text(if (candidate.price >= 0) "Signature reference price: KES ${candidate.price}" else "Signature reference price was not captured.")
                    OutlinedTextField(
                        value = downloadPrice,
                        onValueChange = { downloadPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Your selling price (KES)") },
                        singleLine = true
                    )
                    Text("The offer will be added to My Offers using this price.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val price = downloadPrice.toDoubleOrNull()
                    if (price == null || price <= 0.0) onMessage("Enter a valid selling price greater than zero.")
                    else try {
                        db.saveOffer(null, candidate.name, when (candidate.category.uppercase()) { "VOICE" -> "Minutes"; "DATA" -> "Data"; "SMS" -> "SMS"; "COMBO" -> "Combo"; else -> candidate.category },
                            price, if (candidate.price >= 0) candidate.price.toDouble() else null,
                            "Signature import · ${candidate.simProfile} SIM", false, false,
                            ussdCode = candidate.code, duration = candidate.duration, sourceId = candidate.id,
                            simProfile = candidate.simProfile, tags = candidate.tags)
                        reload++
                        downloadCandidate = null
                        onMessage("${candidate.name} added to My Offers at KES ${money(price)}.")
                    } catch (e: Exception) {
                        onMessage(e.message ?: "Could not download this offer. Check whether its selling price is already used.")
                    }
                }) { Text("Download offer") }
            },
            dismissButton = { TextButton(onClick = { downloadCandidate = null }) { Text("Cancel") } }
        )
    }
}
@Composable
private fun UpdatesScreen() {
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var checkAttempt by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Checking for the latest published release…") }
    var latestVersion by remember { mutableStateOf("") }
    var releaseNotes by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("") }
    var currentVersion by remember { mutableStateOf("0.1.0") }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    fun installDownloadedApk(id: Long) {
        try {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = manager.getUriForDownloadedFile(id)
            if (uri == null) { status = "Download did not finish. Try again."; return }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                status = "Download complete. Allow BingwaPulse to install unknown apps in Android Settings, then tap Install downloaded update."
                return
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            status = "Android installer opened. Approve the update to finish."
        } catch (_: Exception) {
            status = "Download is ready, but Android could not open the installer. Check app-install permission in Settings."
        }
    }

    LaunchedEffect(checkAttempt) {
        currentVersion = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown" } catch (_: Exception) { "unknown" }
        checking = true
        val result = withContext(Dispatchers.IO) {
            try {
                val conn = (URL("https://api.github.com/repos/gabrieltonny16-alt/telvanta-android/releases/latest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000; readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "BingwaPulse-Android")
                }
                try {
                    if (conn.responseCode == 404) "NO_RELEASE" to ""
                    else if (conn.responseCode !in 200..299) "ERROR" to "Release service returned HTTP " + conn.responseCode
                    else "OK" to conn.inputStream.bufferedReader().use { it.readText() }
                } finally { conn.disconnect() }
            } catch (e: Exception) { "ERROR" to (e.message ?: "Network error") }
        }
        checking = false
        when (result.first) {
            "NO_RELEASE" -> status = "No published update is available yet. This app checks automatically when opened; publish a signed release to enable updates."
            "ERROR" -> status = "Could not check updates. Check your internet connection and try again."
            else -> try {
                val json = JSONObject(result.second)
                latestVersion = json.optString("tag_name", "")
                releaseNotes = json.optString("body", "No release notes provided.")
                val assets = json.optJSONArray("assets")
                if (assets != null) for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) { apkUrl = asset.optString("browser_download_url"); break }
                }
                status = when {
                    latestVersion.isBlank() -> "Release information is incomplete."
                    latestVersion.trimStart('v','V') == currentVersion.trimStart('v','V') -> "You’re using the latest published version ($currentVersion)."
                    apkUrl.isBlank() -> "Version $latestVersion is published, but it has no APK asset attached yet."
                    else -> "Version $latestVersion is available."
                }
            } catch (_: Exception) { status = "Could not read release information. Try again later." }
        }
    }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (id > 0 && id == downloadId) installDownloadedApk(id)
            }
        }
    }
    DisposableEffect(context) {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    Text("App updates", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
    InfoCard("Current version", "BingwaPulse $currentVersion")
    InfoCard("Update status", status)
    if (latestVersion.isNotBlank()) {
        InfoCard("Latest release", latestVersion)
        InfoCard("Release notes", releaseNotes.ifBlank { "No release notes provided." })
    }
    Button(onClick = {
        if (apkUrl.isBlank()) status = "No downloadable APK is available. Publish a signed release with an APK asset first."
        else try {
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("BingwaPulse update $latestVersion")
                .setDescription("Downloading app update")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "bingwapulse-update.apk")
            downloadId = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            status = "Downloading version $latestVersion. Android will notify you when it is ready."
        } catch (_: Exception) { status = "Could not start the update download. Try again." }
    }, enabled = apkUrl.isNotBlank() && !checking, modifier = Modifier.fillMaxWidth()) { Text("Update now") }
    OutlinedButton(onClick = { checkAttempt++ }, enabled = !checking, modifier = Modifier.fillMaxWidth()) { Text(if (checking) "Checking…" else "Check again") }
    InfoCard("Android security", "Android requires your approval to install an APK update. Updates must be signed with the same certificate as the installed app.")
}

@Composable
private fun PlannedFeatureScreen(title: String, details: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
    InfoCard("Not implemented yet", details)
    InfoCard("Build status", "This menu item is shown so the workspace is organized, but it is not an active integration. We will implement and test it before calling it ready.")
}

@Composable
private fun OffersScreen(db: LocalDatabase, onMessage: (String) -> Unit) {
    var selectedCategory by remember { mutableStateOf("All") }
    var reload by remember { mutableIntStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Data") }
    var duration by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    val categories = listOf("All", "Data", "Minutes", "SMS", "Combo")
    val offers = remember(reload) { db.offers() }
    val visible = offers.filter { selectedCategory == "All" || it.category.equals(selectedCategory, true) || (selectedCategory == "Minutes" && it.category.equals("VOICE", true)) }
    fun clearEditor() { editId = null; name = ""; category = "Data"; duration = ""; code = ""; price = ""; active = false }
    Text("My Offers", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Teal)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { item -> FilterChip(selected = selectedCategory == item, onClick = { selectedCategory = item }, label = { Text(item) }) }
    }
    if (offers.isEmpty()) InfoCard("No offers yet", "Download offers from Signature Offers or tap + to add one manually.")
    visible.forEach { offer ->
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101D16)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("✥", color = Teal, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(offer.name, color = Teal, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        if (offer.verified) Text("✿", color = Color(0xFF65B96E), fontWeight = FontWeight.Bold)
                    }
                    val detail = listOf(offer.duration, offer.ussdCode).filter { it.isNotBlank() }.joinToString(" · ")
                    Text(if (detail.isNotBlank()) detail else offer.category, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text("KES \${money(offer.price)}", color = Navy, fontWeight = FontWeight.Bold, maxLines = 1)
                Switch(checked = offer.active, onCheckedChange = { enabled ->
                    try { db.setOfferActive(offer.id, enabled); reload++; onMessage(if (enabled) "Offer activated on SiteLink." else "Offer deactivated on SiteLink.") }
                    catch (e: Exception) { onMessage(e.message ?: "Could not update offer.") }
                })
                TextButton(onClick = { db.deleteOffer(offer.id); reload++; onMessage("Offer deleted from My Offers.") }) { Text("▣", color = Color(0xFFFF5B55), fontSize = 20.sp) }
                TextButton(onClick = {
                    editId = offer.id; name = offer.name; category = offer.category; duration = offer.duration
                    code = offer.ussdCode; price = offer.price.toString(); active = offer.active; showEditor = true
                }) { Text("Edit", color = Teal) }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(onClick = { clearEditor(); showEditor = true }, shape = RoundedCornerShape(18.dp)) { Text("＋", fontSize = 26.sp) }
    }
    if (showEditor) AlertDialog(
        onDismissRequest = { showEditor = false; clearEditor() },
        title = { Text(if (editId == null) "Add offer" else "Edit offer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Offer name") }, singleLine = true)
                Text("Category")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Data", "Minutes", "SMS", "Combo").forEach { item -> FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) }) }
                }
                OutlinedTextField(duration, { duration = it }, label = { Text("Validity / duration") }, singleLine = true)
                OutlinedTextField(code, { code = it }, label = { Text("USSD code") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Selling price (KES)") }, singleLine = true)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = active, onCheckedChange = { active = it }); Text("Active on SiteLink")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = price.toDoubleOrNull()
                if (name.isBlank() || amount == null || amount <= 0.0) onMessage("Enter an offer name and a valid selling price.")
                else try {
                    val savedSource = if (editId == null) "Manual" else (offers.firstOrNull { it.id == editId }?.source ?: "Manual")
                    db.saveOffer(editId, name, category, amount, null, savedSource, false, active, ussdCode = code, duration = duration)
                    reload++; showEditor = false; clearEditor(); onMessage("Offer saved in My Offers.")
                } catch (e: Exception) { onMessage(e.message ?: "Could not save offer.") }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { showEditor = false; clearEditor() }) { Text("Cancel") } }
    )
}

@Composable
private fun CustomersScreen(db:LocalDatabase,onMessage:(String)->Unit) {
    var editId by remember { mutableStateOf<Long?>(null) }; var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var search by remember { mutableStateOf("") }; var reload by remember { mutableIntStateOf(0) }
    Text("Customers",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
    OutlinedTextField(name,{name=it},label={Text("Customer name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(phone,{phone=it},label={Text("Phone number")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(notes,{notes=it},label={Text("Notes (optional)")},modifier=Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick={if(name.isBlank()||phone.isBlank()) onMessage("Enter customer name and phone number.")
            else try{db.saveCustomer(editId,name,phone,notes);name="";phone="";notes="";editId=null;reload++;onMessage("Customer saved locally.")}
            catch(_:Exception){onMessage("Could not save: phone number may already be used.")}
        },modifier=Modifier.weight(1f)){Text(if(editId==null)"Save customer" else "Update customer")}
        OutlinedButton(onClick={editId=null;name="";phone="";notes=""}){Text("Clear")}
    }
    OutlinedTextField(search,{search=it},label={Text("Search name or phone")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    val customers=remember(reload,search){db.customers(search)}
    Text("Customer records (${customers.size})",fontWeight=FontWeight.Bold)
    customers.forEach { c -> Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF082A1A)),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(c.name,fontWeight=FontWeight.Bold);Text(c.phone,color=Muted);if(c.notes.isNotBlank())Text(c.notes)
            Row(Modifier.fillMaxWidth()) { TextButton(onClick={editId=c.id;name=c.name;phone=c.phone;notes=c.notes}){Text("Edit")};TextButton(onClick={db.deleteCustomer(c.id);reload++;onMessage("Customer deleted.")}){Text("Delete")} }
        }
    } }
}

@Composable
private fun OrdersScreen(db:LocalDatabase,onMessage:(String)->Unit) {
    var phone by remember { mutableStateOf("") };var offer by remember { mutableStateOf("") };var price by remember { mutableStateOf("") };var reload by remember { mutableIntStateOf(0) }
    Text("Order book",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
    Text("Creating an order does not collect payment or deliver a bundle.",color=Muted)
    OutlinedTextField(phone,{phone=it},label={Text("Customer phone")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(offer,{offer=it},label={Text("Exact offer name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(price,{price=it},label={Text("Selling price (KES)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    Button(onClick={val amount=price.toDoubleOrNull()
        if(phone.isBlank()||offer.isBlank()||amount==null||amount<0)onMessage("Enter phone, offer and valid price.")
        else try{db.createOrder(phone,offer,amount);phone="";offer="";price="";reload++;onMessage("Order recorded as PENDING. No payment was taken.")}
        catch(_:Exception){onMessage("Order could not be saved.")}
    },modifier=Modifier.fillMaxWidth()){Text("Create local order")}
    val orders=remember(reload){db.orders()}
    Text("Orders (${orders.size})",fontWeight=FontWeight.Bold)
    orders.forEach { o -> Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF082A1A)),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("Order #${o.id} · ${o.offer}",fontWeight=FontWeight.Bold);Text("${o.customer} · KES ${money(o.price)}",color=Muted);Text("Status: ${o.status}",fontWeight=FontWeight.SemiBold);Text("Created: ${dateText(o.createdAt)}",color=Muted,style=MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                TextButton(onClick={db.updateOrderStatus(o.id,"PAYMENT_UNVERIFIED");reload++;onMessage("Set to PAYMENT_UNVERIFIED — not verified.")}){Text("Payment unverified")}
                TextButton(onClick={db.updateOrderStatus(o.id,"CANCELLED");reload++;onMessage("Order cancelled locally.")}){Text("Cancel")}
                TextButton(onClick={db.updateOrderStatus(o.id,"FAILED");reload++;onMessage("Order marked failed locally.")}){Text("Mark failed")}
            }
        }
    } }
}

@Composable
private fun TransactionsScreen(db:LocalDatabase,onMessage:(String)->Unit) {
    var orderId by remember { mutableStateOf("") };var amount by remember { mutableStateOf("") };var reference by remember { mutableStateOf("") };var reload by remember { mutableIntStateOf(0) }
    Text("Transaction ledger",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
    InfoCard("Important","Entries here are UNVERIFIED notes only. This screen does not connect to M-PESA and cannot confirm that money was received.")
    OutlinedTextField(orderId,{orderId=it},label={Text("Existing order ID")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(amount,{amount=it},label={Text("Amount (KES)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(reference,{reference=it},label={Text("Reference / note (optional)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    Button(onClick={val id=orderId.toLongOrNull();val value=amount.toDoubleOrNull()
        if(id==null||db.orders().none{it.id==id}||value==null||value<0)onMessage("Enter an existing order ID and valid amount.")
        else try{db.recordTransaction(id,value,reference);orderId="";amount="";reference="";reload++;onMessage("Saved as UNVERIFIED. Confirm payment independently.")}
        catch(_:Exception){onMessage("Could not save transaction note.")}
    },modifier=Modifier.fillMaxWidth()){Text("Record unverified entry")}
    val records=remember(reload){db.transactions()}
    Text("Ledger entries (${records.size})",fontWeight=FontWeight.Bold)
    records.forEach { t -> Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF082A1A)),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Entry #${t.id} · Order #${t.orderId}",fontWeight=FontWeight.Bold);Text("KES ${money(t.amount)} · ${t.status}",color=Muted);if(t.reference.isNotBlank())Text(t.reference);Text(dateText(t.createdAt),color=Muted,style=MaterialTheme.typography.labelSmall)
        }
    } }
}

@Composable
private fun BulkMessengerScreen(db: LocalDatabase) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bingwapulse_bulk_messenger", Context.MODE_PRIVATE) }
    var audience by remember { mutableStateOf(prefs.getString("audience", "Selected customers") ?: "Selected customers") }
    var message by remember { mutableStateOf(prefs.getString("message", "Hello <firstName>, ") ?: "Hello <firstName>, ") }
    var campaignName by remember { mutableStateOf(prefs.getString("campaign", "") ?: "") }
    var notice by remember { mutableStateOf("") }
    val customerCount = remember { db.customers("").size }
    Text("Bulk Messenger", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Navy)
    InfoCard("Campaign workspace", "Create and save message drafts, choose an audience, and preview personalization. Live SMS, WhatsApp and Telegram sending are not connected or enabled.")
    OutlinedTextField(value = campaignName, onValueChange = { campaignName = it }, label = { Text("Campaign name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Text("Audience", fontWeight = FontWeight.Bold, color = Navy)
    listOf("Selected customers", "All customers", "Saved customer group").forEach { option ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected = audience == option, onClick = { audience = option })
            Column {
                Text(option, color = Navy)
                if (option == "All customers") Text("$customerCount customer records currently stored on this phone", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Message draft") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
    Text("Personalization placeholder: <firstName>", color = Muted, style = MaterialTheme.typography.bodySmall)
    InfoCard("Preview", message.replace("<firstName>", "Customer").ifBlank { "(empty message)" })
    Button(onClick = {
        if (campaignName.isBlank() || message.isBlank()) notice = "Add a campaign name and message before saving."
        else {
            prefs.edit().putString("campaign", campaignName.trim()).putString("audience", audience).putString("message", message).apply()
            notice = "Campaign draft saved on this device. No messages were sent."
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Save campaign draft") }
    OutlinedButton(onClick = { campaignName = ""; message = ""; notice = "Draft fields cleared. Previously saved draft remains until overwritten." }, modifier = Modifier.fillMaxWidth()) { Text("Clear fields") }
    if (notice.isNotBlank()) Text(notice, color = Teal, fontWeight = FontWeight.SemiBold)
    InfoCard("Sending safety", "Before live sending is enabled, the app needs verified channel setup, consent-aware recipients, duplicate prevention, rate limits, delivery/error reporting, and a stop control. This screen currently sends nothing.")
}

@Composable
private fun AutoReplyScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vecta_auto_reply_drafts", android.content.Context.MODE_PRIVATE) }
    val types = listOf(
        "Successful response" to "Hi <firstName>, thank you for purchasing from BingwaPulse.",
        "Failed request" to "Hello <firstName>, your request could not be completed. Please contact support.",
        "Unavailable offer" to "Hi <firstName>, we could not find an offer matching your payment. Please contact support.",
        "Offer already recommended" to "Hello <firstName>, this offer has already been recommended today.",
        "App paused" to "Hi <firstName>, processing is temporarily paused. We will update you when service resumes.",
        "Customer blacklisted" to "Hi <firstName>, please contact support about your account."
    )
    var refresh by remember { mutableIntStateOf(0) }
    Text("Auto-Reply Messages",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=Navy)
    InfoCard("Template workspace only","Edit and save message drafts on this phone. SMS sending and automatic replies are NOT connected or enabled in this build.")
    types.forEachIndexed { index, item ->
        var body by remember(refresh) { mutableStateOf(prefs.getString("body_$index", item.second) ?: item.second) }
        var enabled by remember(refresh) { mutableStateOf(prefs.getBoolean("enabled_$index", false)) }
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF082A1A)),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text(item.first,fontWeight=FontWeight.Bold,color=Navy,style=MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Draft enabled",color=Muted,modifier=Modifier.weight(1f))
                    Switch(checked=enabled,onCheckedChange={enabled=it;prefs.edit().putBoolean("enabled_$index",it).apply()})
                }
                OutlinedTextField(value=body,onValueChange={body=it},label={Text("Message template")},modifier=Modifier.fillMaxWidth(),minLines=2)
                Button(onClick={prefs.edit().putString("body_$index",body.trim()).putBoolean("enabled_$index",enabled).apply();refresh++},modifier=Modifier.fillMaxWidth()) { Text("Save template") }
            }
        }
    }
    Text("Placeholder available in drafts: <firstName>. Templates are stored locally; the app does not send them.",color=Muted,style=MaterialTheme.typography.bodySmall)
}

@Composable
private fun StatisticsScreen(db:LocalDatabase) {
    val orders = remember { db.orders() }
    val transactions = remember { db.transactions() }
    val totalSales = orders.filter { it.status == "DELIVERED_MANUALLY_RECORDED" }.sumOf { it.price }
    val pending = orders.count { it.status == "PENDING" }
    val failed = orders.count { it.status == "FAILED" }
    val cancelled = orders.count { it.status == "CANCELLED" }
    val unverified = transactions.count { it.status == "UNVERIFIED" }
    Text("Business statistics",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=Navy)
    InfoCard("Local records only","Figures are calculated from records on this phone. They are not live provider data, verified revenue, or proof of bundle delivery.")
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        StatCard("ALL ORDERS",orders.size,Modifier.weight(1f))
        StatCard("PENDING",pending,Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        StatCard("FAILED",failed,Modifier.weight(1f))
        StatCard("CANCELLED",cancelled,Modifier.weight(1f))
    }
    InfoCard("Manually recorded delivery value","KES ${money(totalSales)}")
    InfoCard("Unverified transaction notes","\$unverified entries — not counted as confirmed payments.")
}

@Composable
private fun DashboardMetric(label: String, count: Int, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF17291F))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(count.toString(), color = accent, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable
private fun QuickAction(symbol:String,label:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    Card(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF082A1A)),elevation=CardDefaults.cardElevation(defaultElevation=2.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical=16.dp,horizontal=13.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Surface(color=Color(0xFF0B4A28),shape=RoundedCornerShape(12.dp)) { Text(symbol,color=Color(0xFF00D66B),fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(horizontal=12.dp,vertical=6.dp)) }
            Text(label,color=Navy,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall)
        }
    }
}
@Composable
private fun StatCard(label:String,count:Int,modifier:Modifier=Modifier) {
    Card(modifier=modifier,shape=MaterialTheme.shapes.large,colors=CardDefaults.cardColors(containerColor=Color(0xFF082A1A)),elevation=CardDefaults.cardElevation(defaultElevation=2.dp)) { Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) { Text(count.toString(),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold,color=Navy);Text(label,color=Muted,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=.5.sp) } }
}
@Composable
private fun InfoCard(title:String,body:String) { Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF082A1A)),shape=MaterialTheme.shapes.large,elevation=CardDefaults.cardElevation(defaultElevation=1.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(title,fontWeight=FontWeight.Bold,color=Navy,style=MaterialTheme.typography.titleMedium);Text(body,color=Muted)} } }
private fun money(value:Double)=String.format(Locale.US,"%.2f",value)
private fun dateText(value:Long)=SimpleDateFormat("dd MMM yyyy, HH:mm",Locale.getDefault()).format(Date(value))


@Composable
private fun UssdAutomationSettingsScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var relayNotice by remember { mutableStateOf("") }
    val prefs = remember(refresh) {
        context.getSharedPreferences(UssdAccessibilityService.PREFS, Context.MODE_PRIVATE)
    }
    val enabled = remember(refresh) {
        val enabledServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val serviceName = context.packageName + "/" + UssdAccessibilityService::class.java.name
        enabledServices.split(':').any { it.equals(serviceName, ignoreCase = true) }
    }
    val notificationPermissionGranted = Build.VERSION.SDK_INT < 33 ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refresh++
    }
    val listenerAccessEnabled = remember(refresh) {
        val enabledListeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        enabledListeners.split(':').any { it.startsWith(context.packageName + "/") }
    }
    val mpesaPrefs = remember(refresh) {
        context.getSharedPreferences(MpesaNotificationListenerService.PREFS, Context.MODE_PRIVATE)
    }
    val lastMpesaTitle = mpesaPrefs.getString(MpesaNotificationListenerService.KEY_TITLE, "") ?: ""
    val lastMpesaText = mpesaPrefs.getString(MpesaNotificationListenerService.KEY_TEXT, "") ?: ""
    val lastMpesaPackage = mpesaPrefs.getString(MpesaNotificationListenerService.KEY_PACKAGE, "") ?: ""
    val lastMpesaTime = mpesaPrefs.getLong(MpesaNotificationListenerService.KEY_TIME, 0L)
    val mpesaReceiptCandidate = mpesaPrefs.getString(MpesaNotificationListenerService.KEY_RECEIPT, "").orEmpty()
    val mpesaAmountCandidate = mpesaPrefs.getString(MpesaNotificationListenerService.KEY_AMOUNT, "").orEmpty()
    val mpesaSenderCandidate = mpesaPrefs.getString(MpesaNotificationListenerService.KEY_SENDER_PHONE, "").orEmpty()
    val mpesaCandidateStatus = mpesaPrefs.getString(MpesaNotificationListenerService.KEY_CANDIDATE_STATUS, "NO_NOTIFICATION").orEmpty()
    val mpesaSyncStatus = mpesaPrefs.getString(MpesaNotificationListenerService.KEY_SYNC_STATUS, "NOT_SENT").orEmpty()
    val mpesaSyncError = mpesaPrefs.getString(MpesaNotificationListenerService.KEY_SYNC_ERROR, "").orEmpty()
    val mpesaSyncedAt = mpesaPrefs.getLong(MpesaNotificationListenerService.KEY_SYNCED_AT, 0L)
    val lastState = prefs.getString(UssdAccessibilityService.KEY_STATE, "NOT_STARTED") ?: "NOT_STARTED"
    val lastDetail = prefs.getString(UssdAccessibilityService.KEY_DETAIL, "No USSD screen has been observed yet.") ?: ""
    val updatedAt = prefs.getLong(UssdAccessibilityService.KEY_UPDATED_AT, 0L)

    Text("Permissions & automation", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Navy)
    InfoCard(
        "App notifications",
        if (notificationPermissionGranted) "ALLOWED. Android permits BingwaPulse to show its own notifications."
        else "BLOCKED. On Android 13 and newer, BingwaPulse must request permission before it can show notifications."
    )
    Button(
        onClick = {
            if (Build.VERSION.SDK_INT >= 33 && !notificationPermissionGranted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (notificationPermissionGranted) "Open app notification settings" else "Allow app notifications") }

    HorizontalDivider(color = Color(0xFF16452B))
    InfoCard(
        "Notification access (different permission)",
        if (listenerAccessEnabled) "ENABLED. BingwaPulse reads matching posted M-PESA notifications, extracts best-effort fields and relays unverified candidates to your signed-in business workspace."
        else "NOT ENABLED. Turn on Notification Access below to let BingwaPulse inspect posted confirmations. Android requires you to grant this special access manually."
    )
    OutlinedButton(
        onClick = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Open Notification Access settings") }
    Button(
        onClick = {
            context.sendBroadcast(
                Intent(MpesaNotificationListenerService.ACTION_RETRY_RELAY)
                    .setPackage(context.packageName)
            )
            relayNotice = "Retry requested for the latest saved M-PESA notification. Refresh the status below in a moment."
        },
        enabled = listenerAccessEnabled,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Retry latest M-PESA relay") }
    if (relayNotice.isNotBlank()) {
        Text(relayNotice, color = Teal, fontWeight = FontWeight.SemiBold)
    }

    InfoCard(
        "Latest M-PESA confirmation candidate",
        if (lastMpesaText.isBlank()) "No matching notification captured yet. Enable Notification Access, then let a Till confirmation arrive as a notification."
        else "Candidate status: $mpesaCandidateStatus\nRelay status: $mpesaSyncStatus\nReceipt: ${mpesaReceiptCandidate.ifBlank { "Not identified" }}\nAmount: ${mpesaAmountCandidate.ifBlank { "Not identified" }}\nSender phone: ${mpesaSenderCandidate.ifBlank { "Not identified in full" }}\nSource: $lastMpesaPackage\n$lastMpesaTitle\n$lastMpesaText\nReceived: ${dateText(lastMpesaTime)}" +
            (if (mpesaSyncedAt > 0L) "\nLast relayed: ${dateText(mpesaSyncedAt)}" else "") +
            (if (mpesaSyncError.isNotBlank()) "\nRelay note: $mpesaSyncError" else "")
    )
    Text(
        "A successful relay means only that an unverified notification candidate reached the database. It is not proof of payment. The backend links a candidate to a callback only when receipt and amount agree; conflicting phone numbers are flagged. No USSD delivery is triggered.",
        color = Muted,
        style = MaterialTheme.typography.bodySmall
    )

    HorizontalDivider(color = Color(0xFF16452B))
    InfoCard(
        "USSD accessibility service",
        if (enabled) "ENABLED. The current service observes supported visible USSD screens only."
        else "NOT ENABLED. Android requires you to turn it on manually in Accessibility settings."
    )
    Button(
        onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (enabled) "Open Accessibility settings" else "Enable Accessibility Service") }
    OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) { Text("Refresh all permission statuses") }
    InfoCard("Last observed USSD state", lastState)
    InfoCard("Last screen details", if (lastDetail.isBlank()) "No screen details recorded." else lastDetail)
    if (updatedAt > 0L) InfoCard("Last update", dateText(updatedAt))
    InfoCard(
        "What is not live yet",
        "This build does not yet read M-PESA notifications or SMS, verify payments, submit USSD menu choices, or deliver bundles. Granting Android permissions alone does not activate those features. No payment or bundle action will be triggered by these settings."
    )
}


@Composable
private fun BingwaDashboard(db: LocalDatabase, onNavigate: (String) -> Unit) {
    var tab by remember { mutableStateOf("Overview") }
    var refreshKey by remember { mutableIntStateOf(0) }
    val orders = remember(refreshKey) { db.orders() }
    val transactions = remember(refreshKey) { db.transactions() }
    val customers = remember(refreshKey) { db.count("customers") }
    val offers = remember(refreshKey) { db.count("offers") }
    val pending = orders.count { it.status == "PENDING" || it.status == "PAYMENT_UNVERIFIED" }
    val failed = orders.count { it.status == "FAILED" }
    val successful = orders.count { it.status == "DELIVERED_MANUALLY_RECORDED" }
    val processing = orders.count { it.status in setOf("QUEUED", "CLAIMED", "USSD_SENT", "PROCESSING") }
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = if (hour < 12) "Good Morning" else if (hour < 17) "Good Afternoon" else "Good Evening"
    val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    val recentTransactions = transactions.take(4)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF102018))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("$greeting, GABRIEL", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
                Text(date, color = Muted, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text("Subscription", color = Muted, style = MaterialTheme.typography.bodyLarge)
                Text("Tokens: —", color = Teal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Token balance is not connected to a subscription service yet.", color = Muted, style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardMetric("Successful", successful, Color(0xFF55C879), Modifier.weight(1f))
                    DashboardMetric("Failed", failed, Color(0xFFFF6B6B), Modifier.weight(1f))
                    DashboardMetric("Pending", pending, Color(0xFFFFA726), Modifier.weight(1f))
                }
                Text("This week's commission: KES —", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Text("Commission tracking is not available in the current local database.", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF102018))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(color = Color(0xFF103D25), shape = RoundedCornerShape(50)) {
                    Text("▣", color = Teal, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Airtime Balance", color = Muted, style = MaterialTheme.typography.bodyLarge)
                    Text("Ksh —", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                    Text("Airtime used today: KES —", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Text("Provider airtime balance is not connected.", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { refreshKey++ }) {
                    Text("↻", color = Teal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineLarge)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Overview", "Transactions", "Analytics").forEach { item ->
                val active = tab == item
                TextButton(onClick = { tab = item }, modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(item, color = if (active) Teal else Muted, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        if (active) HorizontalDivider(color = Teal, thickness = 2.dp)
                    }
                }
            }
        }

        when (tab) {
            "Transactions" -> TransactionsScreen(db) { }
            "Analytics" -> StatisticsScreen(db)
            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF102018))
                ) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Airtime used per day", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("Today and last 7 days", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        Text("No verified airtime-usage records are available yet.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF102018))
                ) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("This week's commission", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("KES —", color = Teal, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)
                        Text("Commission records are not available yet.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF102018))
                ) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Worker status", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("Processing · $processing", color = if (processing > 0) Teal else Muted, fontWeight = FontWeight.SemiBold)
                        Text("Automated delivery worker and pause/stop controls are not implemented in this build. No bundle purchases are triggered here.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF102018))
                ) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Business snapshot", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardMetric("Orders", orders.size, Teal, Modifier.weight(1f))
                            DashboardMetric("Customers", customers, Color(0xFF67DFA0), Modifier.weight(1f))
                            DashboardMetric("Offers", offers, Color(0xFF8CCBFF), Modifier.weight(1f))
                        }
                        TextButton(onClick = { onNavigate("Orders") }) { Text("View orders") }
                    }
                }
                if (recentTransactions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF102018))
                    ) {
                        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Recent transactions", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            recentTransactions.forEach { tx ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(tx.reference.ifBlank { "Order #${tx.orderId}" }, color = Color.White, modifier = Modifier.weight(1f))
                                    Text("KES ${money(tx.amount)}", color = Teal, fontWeight = FontWeight.SemiBold)
                                }
                                Text("${tx.status} · ${dateText(tx.createdAt)}", color = Muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun WalletScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bingwapulse_wallet_pricing", Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf("") }
    val subscriptions = listOf(
        Triple("Daily", "24 hours · Unlimited USSD access", 17),
        Triple("3 days", "3 days · Unlimited USSD access", 40),
        Triple("Weekly", "7 days · Unlimited USSD access", 99),
        Triple("Monthly", "30 days · Unlimited USSD access", 279)
    )
    val tokenPackages = listOf(
        Triple("Starter", "150 USSD requests", 15),
        Triple("Basic", "300 USSD requests", 29),
        Triple("Growth", "600 USSD requests", 45),
        Triple("Business", "1,500 USSD requests", 109)
    )
    @Composable
    fun priceRow(key: String, title: String, detail: String, defaultPrice: Int) {
        var value by remember(refresh) { mutableStateOf(prefs.getInt(key, defaultPrice).toString()) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF102018)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("KES", color = Teal, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Admin price") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val amount = value.toIntOrNull()
                        if (amount == null || amount <= 0) notice = "Enter a valid price for $title."
                        else {
                            prefs.edit().putInt(key, amount).apply()
                            notice = "$title price saved: KES $amount."
                            refresh++
                        }
                    }) { Text("Save") }
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Wallet", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B3A23)), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("YOUR WALLET", color = Teal, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                Text("Subscription: Not active", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("USSD tokens: Not connected", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Balances will appear after secure account and payment integration. No balance is being assumed.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("UNLIMITED SUBSCRIPTIONS", color = Teal, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
        subscriptions.forEachIndexed { i, item -> priceRow("sub_$i", item.first, item.second, item.third) }
        Text("USSD TOKEN PACKAGES", color = Teal, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
        tokenPackages.forEachIndexed { i, item -> priceRow("tokens_$i", item.first, item.second, item.third) }
        Text("PAYMENT METHODS", color = Teal, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
        InfoCard("M-PESA", "Payment integration is not active yet. No STK Push or charge will be initiated.")
        InfoCard("Airtime", "Airtime payment is not active yet. No USSD payment or airtime deduction will be initiated.")
        InfoCard("Purchase history", "No wallet purchases have been recorded. This section will show verified purchases after payment integration.")
        InfoCard("Pricing controls", "These prices are saved on this device for now. Server-wide admin pricing and customer checkout require backend integration.")
        if (notice.isNotBlank()) Text(notice, color = Teal, fontWeight = FontWeight.SemiBold)
    }
}
