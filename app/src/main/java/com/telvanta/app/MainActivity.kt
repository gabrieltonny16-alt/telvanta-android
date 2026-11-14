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
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionInfo
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFFF8FAFC)
private val NavyDeep = Color(0xFF05070D)
private val Cyan = Color(0xFF00F2FE)
private val Muted = Color(0xFF94A3B8)
private val Canvas = Color(0xFF05070D)

class MainActivity : ComponentActivity() {
    private lateinit var localDb: LocalDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 6, 10)
        window.navigationBarColor = android.graphics.Color.rgb(5, 6, 10)
        window.decorView.systemUiVisibility = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
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
        "MAIN" to listOf("Dashboard", "Wallet", "Offers", "Payment Inbox", "Transaction History"),
        "TOOLS" to listOf("Bulk Messenger", "Quick Dial", "Auto Renewals", "SiteLink", "Auto-Reply"),
        "MANAGEMENT" to listOf("Customers", "Signature", "Statistics", "Connect Pulse"),
        "SUPPORT" to listOf("Settings", "Updates", "Help & Support", "About BingwaPulse")
    )
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFF00F2FE),secondary=Color(0xFF8A2BE2),background=Canvas,surface=Color(0xFF0B0F19),onPrimary=Color(0xFF05070D),onSecondary=Color(0xFF0B0F19),onBackground=Navy,onSurface=Navy), shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp),extraLarge=RoundedCornerShape(30.dp))) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Color(0xFF05070D), drawerContentColor = Color.White) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("BINGWAPULSE", color = Color(0xFF8A2BE2), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        Text("POWER EVERY CONNECTION", color = Muted, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(12.dp))
                    }
                    HorizontalDivider(color = Color(0xFF1E3A5F))
                    navGroups.forEach { group ->
                        Text(
                            group.first,
                            color = Color(0xFF94A3B8),
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
                                    selectedContainerColor = Color(0xFF12345A),
                                    selectedTextColor = Color(0xFF8A2BE2),
                                    unselectedTextColor = Color(0xFFF8FAFC)
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFF1E3A5F), modifier = Modifier.padding(vertical = 8.dp))
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
            NavigationBar(containerColor = Color(0xFF05070D), contentColor = Cyan) {
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
                            selectedIconColor = Cyan,
                            selectedTextColor = Cyan,
                            indicatorColor = Color(0xFF12345A),
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        },topBar={ TopAppBar(
            navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Text("≡", color = Cyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium) } },
            title={Column(verticalArrangement=Arrangement.spacedBy(2.dp)) { Text("BINGWAPULSE",fontWeight=FontWeight.Black,color=Navy,letterSpacing=1.3.sp,style=MaterialTheme.typography.titleLarge); Text(selected.uppercase(),style=MaterialTheme.typography.labelSmall,color=Muted,letterSpacing=1.5.sp) }},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=Color(0xFF05070D))
        ) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(top=10.dp,bottom=12.dp)) {
                if (SecureSessionStore(context).isLocalWorkspace()) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.End) {
                            Surface(color=Color(0xFF0B0F19), shape=RoundedCornerShape(50)) {
                                Text("LOCAL PREVIEW", color=Muted, style=MaterialTheme.typography.labelSmall, modifier=Modifier.padding(horizontal=10.dp, vertical=5.dp))
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when(selected) {
                        "Dashboard" -> BingwaDashboard(db) { destination -> selected = destination; message = "" }
                        "Offers" -> OffersScreen(db){message=it}
                        "Customers" -> CustomersScreen(db){message=it}
                        "Orders" -> OrdersScreen(db){message=it}
                        "Payment Inbox" -> PaymentInboxScreen()
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
                        "Quick Dial" -> QuickDialScreen(db) { message = it }
                        "Auto Renewals" -> FeatureWorkspaceScreen("Auto Renewals")
                        "Signature" -> FeatureWorkspaceScreen("Signature")
                        "Connect Pulse" -> ConnectPulseScreen()
                        "Updates" -> UpdatesScreen()
                        "Device Activation" -> PermissionSetupScreen()
                        "Settings" -> BingwaPulseSettingsScreen { destination -> selected = destination }
                        "Authorized Senders" -> MessageProcessingRulesScreen("Authorized Senders") { message = it }
                        "Blacklist" -> MessageProcessingRulesScreen("Blacklist") { message = it }
                        "Contact Name Tag" -> ContactNameTagScreen { message = it }
                        else -> FeatureWorkspaceScreen(selected)
                    }
                    }
                }
                if(message.isNotBlank()) item { Text(message,color=Cyan,fontWeight=FontWeight.SemiBold) }
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
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PAIRING CONTROL", color = Cyan, fontWeight = FontWeight.ExtraBold)
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
                Text(pairingCode, color = Color(0xFF8A2BE2), fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
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
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(device.label, color = Navy, fontWeight = FontWeight.Bold)
                    Text(device.status.replace("_", " ").uppercase(Locale.ROOT), color = if (device.status == "online") Cyan else Color(0xFFFFC857), fontWeight = FontWeight.ExtraBold)
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
    if (notice.isNotBlank()) Text(notice, color = Cyan, fontWeight = FontWeight.SemiBold)
}


@Composable
private fun QuickDialScreen(db: LocalDatabase, onMessage: (String) -> Unit) {
    var customerPhone by remember { mutableStateOf("") }
    var selectedOfferId by remember { mutableStateOf<Long?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secureStore = remember { SecureSessionStore(context) }
    val api = remember { BingwaSupabaseApi() }
    var pendingDialCode by remember { mutableStateOf<String?>(null) }
    var pendingWorkerBusinessId by remember { mutableStateOf<String?>(null) }
    var pendingWorkerDeviceId by remember { mutableStateOf<String?>(null) }

    fun openDialler(code: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))))
        }.onFailure {
            onMessage("The order was saved, but Android could not open the phone dialler.")
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val code = pendingDialCode
        val businessId = pendingWorkerBusinessId
        val deviceId = pendingWorkerDeviceId
        if (granted && !businessId.isNullOrBlank() && !deviceId.isNullOrBlank()) {
            try {
                val workerIntent = Intent(context, FulfilmentWorkerService::class.java)
                    .putExtra(FulfilmentWorkerService.EXTRA_BUSINESS_ID, businessId)
                    .putExtra(FulfilmentWorkerService.EXTRA_DEVICE_ID, deviceId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(workerIntent)
                else context.startService(workerIntent)
                onMessage("Order recorded. Automatic USSD fulfilment has been started. Wait for a carrier-confirmed result; a started call is not proof of delivery.")
            } catch (error: Exception) {
                onMessage("Order saved, but the automatic worker could not start: ${error.message ?: "unknown error"}.")
                if (!code.isNullOrBlank()) openDialler(code)
            }
        } else if (!granted) {
            onMessage("Phone permission was not granted. The order remains recorded. Review the USSD code in the dialler and press Call yourself.")
            if (!code.isNullOrBlank()) openDialler(code)
        }
        pendingDialCode = null
        pendingWorkerBusinessId = null
        pendingWorkerDeviceId = null
    }

    val offers = remember(refresh) { db.offers() }
    val selectedOffer = offers.firstOrNull { it.id == selectedOfferId }
    val dialableOffers = offers.filter { it.active && it.ussdCode.isNotBlank() }
    val gridColumns = if (LocalConfiguration.current.screenWidthDp < 380) 2 else 3
    val rows = dialableOffers.chunked(gridColumns)
    val normalizedRecipient = normalizeKenyanMobileNumber(customerPhone).orEmpty()
    val phoneValid = normalizedRecipient.isNotBlank()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("QUICK DIAL", color = Color.White, fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge, letterSpacing = 1.1.sp)
            Text("Choose an active offer and enter the recipient number.", color = Muted,
                style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = customerPhone,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(12)
                customerPhone = if (input.trimStart().startsWith("+")) "+$digits" else digits
            },
            label = { Text("Recipient phone") },
            placeholder = { Text("07XXXXXXXX or +2547XXXXXXXX") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (customerPhone.isNotBlank()) {
                    IconButton(onClick = { customerPhone = "" }) {
                        Text("×", color = Muted, fontSize = 22.sp)
                    }
                }
            },
            isError = customerPhone.isNotBlank() && !phoneValid,
            supportingText = {
                Text(
                    when {
                        customerPhone.isBlank() -> "Kenyan mobile number"
                        phoneValid -> "Recipient: $normalizedRecipient"
                        else -> "Enter a valid 07…, 01…, 254… or +254… number."
                    },
                    color = if (customerPhone.isNotBlank() && !phoneValid) Color(0xFFF87171) else Muted
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF263244),
                focusedContainerColor = Color(0xFF101622), unfocusedContainerColor = Color(0xFF101622),
                errorBorderColor = Color(0xFFF87171)
            )
        )
        if (dialableOffers.isEmpty()) {
            InfoCard("No active dialable offers", "Quick Dial only displays active offers with a saved USSD code. Open Offers, activate the correct offer and save its verified USSD entry code.")
        } else {
            Text("AVAILABLE OFFERS", color = Muted, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall, letterSpacing = 1.1.sp)
            rows.forEach { rowOffers ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    rowOffers.forEach { offer ->
                        val chosen = selectedOfferId == offer.id
                        val symbol = when (offer.category.uppercase(Locale.ROOT)) {
                            "SMS" -> "▤"
                            "MINUTES", "VOICE" -> "☎"
                            "DATA" -> "ϟ"
                            else -> "▣"
                        }
                        Card(
                            modifier = Modifier.weight(1f).heightIn(min = if (gridColumns == 2) 112.dp else 124.dp)
                                .clickable { selectedOfferId = if (chosen) null else offer.id },
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(if (chosen) 2.dp else 1.dp,
                                if (chosen) Cyan else Color(0xFF202B3C)),
                            colors = CardDefaults.cardColors(containerColor =
                                if (chosen) Color(0xFF0C2633) else Color(0xFF101622))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 12.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(symbol, color = Cyan, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text(offer.name, color = Color(0xFFF8FAFC), fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall, maxLines = 3, minLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("KES ${money(offer.price)}", color = Cyan, fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                if (chosen) Text("SELECTED", color = Cyan,
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    repeat(gridColumns - rowOffers.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (selectedOffer != null) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1724)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF155E75)),
                shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("SELECTED BUNDLE", color = Muted, style = MaterialTheme.typography.labelSmall)
                        Text(selectedOffer.name, color = Color.White, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                    Text("KES ${money(selectedOffer.price)}", color = Cyan,
                        fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Button(
            onClick = {
                val offer = selectedOffer
                if (!phoneValid) {
                    onMessage("Enter a valid Kenyan recipient number before continuing.")
                    return@Button
                }
                if (offer == null) {
                    onMessage("Select an active offer before dialling.")
                    return@Button
                }
                val recipient = normalizedRecipient
                val code = offer.ussdCode.trim()
                val inner = if (code.length >= 2) code.substring(1, code.length - 1) else ""
                val valid = code.length in 3..30 && code.startsWith("*") && code.endsWith("#") &&
                    inner.all { it.isDigit() || it == '*' || it == '#' }
                if (!valid) {
                    onMessage("This offer's saved USSD code is invalid. Edit it in Offers before dialling.")
                    return@Button
                }
                if (busy) return@Button
                busy = true
                scope.launch {
                    try {
                        var active = secureStore.load()
                            ?: throw IllegalStateException("Sign in to your BingwaPulse business workspace before using Quick Dial.")
                        if (secureStore.isLocalWorkspace()) {
                            throw IllegalStateException("Quick Dial order tracking requires a connected business workspace.")
                        }
                        if (active.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                            active = withContext(Dispatchers.IO) { api.refreshSession(active) }
                            secureStore.save(active)
                        }
                        val businessId = active.businessId
                            ?: withContext(Dispatchers.IO) { api.firstBusinessId(active) }
                            ?: throw IllegalStateException("No business workspace is connected.")
                        if (active.businessId == null) {
                            active = active.copy(businessId = businessId)
                            secureStore.save(active)
                        }
                        val controls = withContext(Dispatchers.IO) { api.getFulfilmentControls(active, businessId) }
                        if (!controls.optBoolean("fulfilment_enabled", false) ||
                            controls.optBoolean("fulfilment_paused", true)) {
                            throw IllegalStateException("Automatic fulfilment is disabled or paused. Review the fulfilment controls in Settings first; Quick Dial will not change those business settings automatically.")
                        }
                        withContext(Dispatchers.IO) { api.syncLocalOfferToCloud(active, businessId, offer) }
                        val cloudOffer = withContext(Dispatchers.IO) {
                            api.listFulfilmentOffers(active, businessId).firstOrNull {
                                it.name.equals(offer.name, ignoreCase = true) &&
                                    (it.entryCode.isNullOrBlank() || it.entryCode == offer.ussdCode)
                            }
                        } ?: throw IllegalStateException("This offer could not be matched to the connected business catalogue.")
                        withContext(Dispatchers.IO) {
                            api.createQuickDialOrder(active, businessId, cloudOffer.id, recipient, offer.price.toInt())
                        }
                        val device = withContext(Dispatchers.IO) {
                            api.listDevices(active, businessId).firstOrNull {
                                it.assignedUserId == active.userId && it.status == "online"
                            } ?: api.registerCurrentWorkerDevice(active, businessId,
                                listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }
                                    .joinToString(" ").ifBlank { "BingwaPulse Android worker" }.take(100))
                        }
                        val phonePermissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                        if (phonePermissionGranted) {
                            try {
                                val workerIntent = Intent(context, FulfilmentWorkerService::class.java)
                                    .putExtra(FulfilmentWorkerService.EXTRA_BUSINESS_ID, businessId)
                                    .putExtra(FulfilmentWorkerService.EXTRA_DEVICE_ID, device.id)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(workerIntent)
                                else context.startService(workerIntent)
                            } catch (error: Exception) {
                                onMessage("Order recorded. The automatic worker could not start: ${error.message ?: "unknown error"}. You can still review the USSD dialler.")
                            }
                            onMessage("Order recorded for $recipient. Automatic USSD fulfilment has started. Keep the phone available and wait for a carrier-confirmed result; the call starting does not confirm delivery.")
                        } else {
                            pendingDialCode = code
                            pendingWorkerBusinessId = businessId
                            pendingWorkerDeviceId = device.id
                            callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                        }
                    } catch (error: Exception) {
                        onMessage(error.message ?: "Quick Dial could not prepare this order.")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = selectedOffer != null && phoneValid && !busy,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E2937),
                contentColor = Color.White, disabledContainerColor = Color(0xFF202733),
                disabledContentColor = Muted)
        ) {
            Text(when {
                busy -> "Preparing order…"
                selectedOffer == null -> "Select an offer"
                !phoneValid -> "Enter recipient number"
                else -> "☎   Dial • KES ${money(selectedOffer.price)}"
            }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Text("With Phone permission, the fulfilment worker starts the saved USSD code. Without permission, BingwaPulse opens the Android dialler for you to review the code, choose a SIM and press Call. A call attempt is never proof of bundle delivery.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
    }
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
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(offer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Navy)
                    Text(offer.category, color = Muted)
                    Text("KES ${money(offer.price)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Cyan)
                    if (!offer.verified) Text("Offer details not independently verified", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    HorizontalDivider(color = Color(0xFF1E3A5F))
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
    CloudOfferSyncEffect(db, reload, onMessage)
    val categories = listOf("ALL", "DATA", "VOICE", "SMS", "COMBO")
    val visible = candidates.filter {
        (category == "ALL" || it.category.equals(category, true)) &&
            (simProfile == "ALL" || it.simProfile.equals(simProfile, true)) &&
            (search.isBlank() || it.name.contains(search, true) || it.price.toString().contains(search) || it.code.contains(search, true))
    }
    Text("Signature Offers", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Cyan)
    Surface(
        color = Color(0xFF08232B),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF00B8D4).copy(alpha = 0.32f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Surface(color = Color(0xFF00B8D4).copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp)) {
                Text("✦", color = Cyan, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Original Signature catalogue", color = Navy, fontWeight = FontWeight.Bold)
                Text("44 extracted offer records · Any SIM, Normal SIM and Notification SIM", color = Cyan, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Surface(
        color = Color(0xFF0A1B22),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF00B8D4).copy(alpha = 0.24f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("ⓘ", color = Cyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("These are your previously supplied offer records. Prices and USSD codes remain candidate details until checked against the live Safaricom menu. No purchase or USSD action is triggered here.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
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
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(offer.name, fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text("${offer.category} · ${offer.duration}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            Text(when(offer.simProfile) { "normal" -> "NORMAL SIM"; "notification" -> "NOTIFICATION SIM"; else -> "ANY SIM" }, color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(if (offer.price >= 0) "KES ${offer.price}" else "Price unknown", color = Cyan, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                    if (offer.code.isNotBlank()) Surface(color = Color(0xFF05070D), shape = RoundedCornerShape(8.dp)) { Text(offer.code, color = Muted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) } else Text("USSD code not captured", color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (offer.tags.isNotBlank()) Text("Tags: ${offer.tags}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(offer.note, color = Muted, style = MaterialTheme.typography.bodySmall)
                    Surface(color = Color(0xFF3A2C18), shape = RoundedCornerShape(50)) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("●", color = Color(0xFFFFC857), style = MaterialTheme.typography.labelSmall)
                            Text("CANDIDATE · NOT VERIFIED", color = Color(0xFFFFD58A), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Button(onClick = { downloadCandidate = offer; downloadPrice = if (offer.price >= 0) offer.price.toString() else "" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Download to My Offers") }
                }
            }
        }
    } else {
        Text("Saved signature offers (${saved.size})", fontWeight = FontWeight.Bold, color = Navy)
        if (saved.isEmpty()) InfoCard("No saved offers yet", "Candidate records are shown separately so unverified source entries do not silently become active offers.")
        saved.forEach { offer ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(offer.name, fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(offer.category + " · " + offer.duration, color = Muted, style = MaterialTheme.typography.bodySmall); Text("KES " + money(offer.price), color = Cyan, fontWeight = FontWeight.ExtraBold) }
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
private fun CloudOfferSyncEffect(db: LocalDatabase, revision: Int, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { SecureSessionStore(context) }
    val api = remember { BingwaSupabaseApi() }
    LaunchedEffect(revision) {
        if (store.isLocalWorkspace()) return@LaunchedEffect
        val saved = store.load() ?: return@LaunchedEffect
        try {
            var active = if (saved.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                withContext(Dispatchers.IO) { api.refreshSession(saved) }.also(store::save)
            } else saved
            val businessId = active.businessId
                ?: withContext(Dispatchers.IO) { api.firstBusinessId(active) }
                ?: throw IllegalStateException("No business workspace is connected.")
            if (active.businessId != businessId) {
                active = active.copy(businessId = businessId)
                store.save(active)
            }
            val localOffers = db.offers()
            withContext(Dispatchers.IO) {
                localOffers.forEach { offer -> api.syncLocalOfferToCloud(active, businessId, offer) }
            }
            if (localOffers.isNotEmpty()) onMessage("${localOffers.size} local offer(s) synchronized with the cloud catalogue.")
        } catch (error: Exception) {
            onMessage(error.message ?: "Could not synchronize local offers with the cloud catalogue.")
        }
    }
}

@Composable
private fun OffersScreen(db: LocalDatabase, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val secureStore = remember { SecureSessionStore(context) }
    val api = remember { BingwaSupabaseApi() }
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    var verifiedOnly by remember { mutableStateOf(false) }
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
    // Existing locally saved offers remain the source of truth and are never replaced by demo data.
    val offers = remember(reload) { db.offers() }
    CloudOfferSyncEffect(db, reload, onMessage)
    val visible = offers.filter { offer ->
        val categoryMatches = selectedCategory == "All" ||
            offer.category.equals(selectedCategory, true) ||
            (selectedCategory == "Minutes" && offer.category.equals("VOICE", true))
        val query = search.trim()
        val searchMatches = query.isBlank() ||
            offer.name.contains(query, true) ||
            offer.category.contains(query, true) ||
            offer.ussdCode.contains(query, true) ||
            money(offer.price).contains(query, true) ||
            "KES ${money(offer.price)}".contains(query, true)
        categoryMatches && searchMatches && (!verifiedOnly || offer.verified)
    }
    val activeCount = offers.count { it.active }
    fun clearEditor() { editId = null; name = ""; category = "Data"; duration = ""; code = ""; price = ""; active = false }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("OFFERS", color = Cyan, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp, style = MaterialTheme.typography.labelMedium)
                Text("My catalogue", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
                Text("${offers.size} offers · $activeCount active", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Surface(color = Color(0xFF092B36), shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF15566A))) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("●", color = Cyan, style = MaterialTheme.typography.labelSmall)
                    Text("$activeCount Active", color = Color(0xFF8DEFFF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        OutlinedTextField(
            value = search, onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search offer, price or USSD…", color = Muted) },
            leadingIcon = { Text("⌕", color = Cyan, style = MaterialTheme.typography.titleLarge) },
            trailingIcon = { if (search.isNotBlank()) TextButton(onClick = { search = "" }) { Text("CLEAR", color = Cyan, style = MaterialTheme.typography.labelSmall) } },
            singleLine = true,
            shape = RoundedCornerShape(15.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF253044),
                focusedContainerColor = Color(0xFF111622), unfocusedContainerColor = Color(0xFF111622),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                cursorColor = Cyan
            )
        )

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            categories.forEach { item ->
                FilterChip(
                    selected = selectedCategory == item,
                    onClick = { selectedCategory = item },
                    label = { Text(item, fontWeight = if (selectedCategory == item) FontWeight.Bold else FontWeight.Medium) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cyan,
                        selectedLabelColor = Color(0xFF041018),
                        containerColor = Color(0xFF111622),
                        labelColor = Color(0xFFD1D9E6)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = selectedCategory == item,
                        borderColor = Color(0xFF253044), selectedBorderColor = Cyan
                    )
                )
            }
            FilterChip(
                selected = verifiedOnly,
                onClick = { verifiedOnly = !verifiedOnly },
                label = { Text("✓ Verified") },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF123A48),
                    selectedLabelColor = Color(0xFF8DEFFF),
                    containerColor = Color(0xFF111622),
                    labelColor = Color(0xFFD1D9E6)
                )
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(if (selectedCategory == "All") "ALL OFFERS" else selectedCategory.uppercase(), color = Muted,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, style = MaterialTheme.typography.labelSmall)
            Text("${visible.size} shown", color = Muted, style = MaterialTheme.typography.labelSmall)
        }

        if (offers.isEmpty()) {
            InfoCard("No offers yet", "Your existing catalogue is empty. Download offers from Signature Offers or tap Add offer to create your first one.")
        } else if (visible.isEmpty()) {
            InfoCard("No matching offers", "Try another category or clear your search and verification filter.")
        }

        visible.forEach { offer ->
            val categoryGlyph = when (offer.category.uppercase(Locale.ROOT)) {
                "DATA" -> "◎"
                "MINUTES", "VOICE" -> "◖"
                "SMS" -> "▱"
                "COMBO" -> "▧"
                else -> "◈"
            }
            val categoryTint = when (offer.category.uppercase(Locale.ROOT)) {
                "MINUTES", "VOICE" -> Color(0xFF103B35)
                "SMS" -> Color(0xFF123447)
                "COMBO" -> Color(0xFF29234A)
                else -> Color(0xFF123548)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111622)),
                border = BorderStroke(1.dp, Color(0xFF202A3B)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(color = categoryTint, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFF24536A))) {
                            Box(Modifier.size(43.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Text(categoryGlyph, color = Cyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(offer.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                                if (offer.verified) Text("✓", color = Cyan, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
                            }
                            Text(offer.category.uppercase(Locale.ROOT), color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("KES ${money(offer.price)}", color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            Text(if (offer.verified) "VERIFIED" else "NOT VERIFIED", color = if (offer.verified) Cyan else Color(0xFFFFD58A),
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(offer.ussdCode.ifBlank { "USSD not set" }, color = Muted, maxLines = 1,
                                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                            if (offer.duration.isNotBlank()) Text(offer.duration, color = Muted, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (offer.active) "ACTIVE" else "INACTIVE", color = if (offer.active) Cyan else Muted,
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = offer.active,
                                onCheckedChange = { enabled ->
                                    try {
                                        db.setOfferActive(offer.id, enabled); reload++
                                        onMessage(if (enabled) "Offer activated on SiteLink." else "Offer deactivated on SiteLink.")
                                    } catch (e: Exception) { onMessage(e.message ?: "Could not update offer.") }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF06131B), checkedTrackColor = Cyan,
                                    uncheckedThumbColor = Color(0xFF9AA6B8), uncheckedTrackColor = Color(0xFF344054),
                                    checkedBorderColor = Cyan, uncheckedBorderColor = Color(0xFF475467)
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF202A3B))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                editId = offer.id; name = offer.name; category = offer.category; duration = offer.duration
                                code = offer.ussdCode; price = offer.price.toString(); active = offer.active; showEditor = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) { Text("EDIT OFFER", color = Cyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        if (!secureStore.isLocalWorkspace()) {
                                            var session = secureStore.load() ?: throw IllegalStateException("Sign in before deleting a cloud-published offer.")
                                            if (session.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                                                session = withContext(Dispatchers.IO) { api.refreshSession(session) }
                                                secureStore.save(session)
                                            }
                                            val businessId = session.businessId ?: withContext(Dispatchers.IO) { api.firstBusinessId(session) }
                                                ?: throw IllegalStateException("No business workspace is connected.")
                                            withContext(Dispatchers.IO) { api.syncLocalOfferToCloud(session, businessId, offer.copy(active = false)) }
                                        }
                                        db.deleteOffer(offer.id); reload++
                                        onMessage("Offer unpublished from the cloud catalogue and removed locally.")
                                    } catch (error: Exception) { onMessage(error.message ?: "Could not safely remove the offer.") }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) { Text("DELETE", color = Color(0xFFE88BA4), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }

        Button(
            onClick = { clearEditor(); showEditor = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color(0xFF041018))
        ) { Text("＋   Add offer", fontWeight = FontWeight.ExtraBold) }
        Text("Changes continue to use your saved catalogue and existing SiteLink sync.", color = Muted,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
    }

    if (showEditor) AlertDialog(
        onDismissRequest = { showEditor = false; clearEditor() },
        title = { Text(if (editId == null) "Add offer" else "Edit offer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Offer name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Category")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Data", "Minutes", "SMS", "Combo").forEach { item ->
                        FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                    }
                }
                OutlinedTextField(duration, { duration = it }, label = { Text("Validity / duration") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(code, { code = it }, label = { Text("USSD code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(price, { price = it }, label = { Text("Selling price (KES)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = active, onCheckedChange = { active = it })
                    Text("Active on SiteLink")
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
            }) { Text("Save", color = Cyan) }
        },
        dismissButton = { TextButton(onClick = { showEditor = false; clearEditor() }) { Text("Cancel") } },
        containerColor = Color(0xFF111622),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFD1D9E6)
    )
}

@Composable
private fun CustomersScreen(db: LocalDatabase, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    var editId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }

    val customers = remember(reload, search) { db.customers(search.trim()) }
    val orders = remember(reload) { db.orders() }
    val phoneKey: (String) -> String = { value -> value.filter { it.isDigit() }.takeLast(9) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()) }
    val totalOrders = orders.size
    val totalCustomers = db.count("customers")

    fun clearEditor() {
        editId = null
        name = ""
        phone = ""
        notes = ""
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("CUSTOMER MANAGEMENT", color = Cyan, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, style = MaterialTheme.typography.labelMedium)
                Text("Your customers", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
                Text("$totalCustomers saved contacts · $totalOrders recorded orders", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Surface(color = Color(0xFF102C36), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF1D5969))) {
                Text("＋ ADD", color = Cyan, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable { clearEditor(); showEditor = true }.padding(horizontal = 13.dp, vertical = 11.dp))
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search customer or phone…", color = Muted) },
            leadingIcon = { Text("⌕", color = Cyan, style = MaterialTheme.typography.titleLarge) },
            trailingIcon = { if (search.isNotBlank()) TextButton(onClick = { search = "" }) { Text("CLEAR", color = Cyan, style = MaterialTheme.typography.labelSmall) } },
            singleLine = true,
            shape = RoundedCornerShape(15.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF253044),
                focusedContainerColor = Color(0xFF111622), unfocusedContainerColor = Color(0xFF111622),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Cyan
            )
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CONTACTS" to customers.size.toString(), "ORDERS" to totalOrders.toString()).forEach { metric ->
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111622)),
                    border = BorderStroke(1.dp, Color(0xFF202A3B)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(metric.first, color = Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        Text(metric.second, color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("CUSTOMER LIST", color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, style = MaterialTheme.typography.labelSmall)
            Text("${customers.size} shown", color = Muted, style = MaterialTheme.typography.labelSmall)
        }

        if (customers.isEmpty()) {
            InfoCard(
                if (search.isBlank()) "No customers saved yet" else "No matching customers",
                if (search.isBlank()) "Add a customer to keep their contact details and see their recorded order history here."
                else "Try another name or phone number, or clear your search."
            )
        }

        customers.forEach { customer ->
            val key = phoneKey(customer.phone)
            val customerOrders = orders.filter { phoneKey(it.customer) == key && key.isNotBlank() }
            val latestOrder = customerOrders.maxByOrNull { it.createdAt }
            val orderValue = customerOrders.sumOf { it.price }
            val initials = customer.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111622)),
                border = BorderStroke(1.dp, Color(0xFF202A3B)),
                shape = RoundedCornerShape(17.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = Color(0xFF123548),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, Color(0xFF24536A))
                        ) {
                            Box(Modifier.size(43.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Text(initials.ifBlank { "?" }, color = Cyan, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(customer.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            Text(customer.phone, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(color = if (customerOrders.isNotEmpty()) Color(0xFF123A32) else Color(0xFF252B38), shape = RoundedCornerShape(50)) {
                            Text(
                                if (customerOrders.isEmpty()) "NEW" else "RETURNING",
                                color = if (customerOrders.isEmpty()) Muted else Color(0xFF80E6BC),
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("ORDERS" to customerOrders.size.toString(), "ORDER VALUE" to "KES ${money(orderValue)}").forEach { metric ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF0B111B),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF202A3B))
                            ) {
                                Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(metric.first, color = Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                    Text(metric.second, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                }
                            }
                        }
                    }

                    if (latestOrder != null) {
                        Text(
                            "Last order · ${latestOrder.offer} · ${dateFormat.format(Date(latestOrder.createdAt))}",
                            color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 2
                        )
                        Text("Latest status · ${latestOrder.status}", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    if (customer.notes.isNotBlank()) {
                        Text(customer.notes, color = Color(0xFFD1D9E6), style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                    HorizontalDivider(color = Color(0xFF202A3B))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(
                            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", customer.phone, null))) }
                                .onFailure { onMessage("Could not open the phone dialler.") } },
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)
                        ) { Text("☎ CALL", color = Cyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            editId = customer.id; name = customer.name; phone = customer.phone; notes = customer.notes; showEditor = true
                        }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)) {
                            Text("EDIT", color = Cyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            runCatching { db.deleteCustomer(customer.id); reload++; onMessage("Customer deleted.") }
                                .onFailure { onMessage(it.message ?: "Could not delete customer.") }
                        }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)) {
                            Text("DELETE", color = Color(0xFFE88BA4), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (customerOrders.isNotEmpty()) {
                        Text("Order value is based on recorded orders; it is not a verified payment total.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (showEditor) AlertDialog(
        onDismissRequest = { showEditor = false; clearEditor() },
        title = { Text(if (editId == null) "Add customer" else "Edit customer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Customer name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || phone.isBlank()) onMessage("Enter customer name and phone number.")
                else try {
                    db.saveCustomer(editId, name, phone, notes)
                    showEditor = false; clearEditor(); reload++
                    onMessage("Customer saved locally.")
                } catch (_: Exception) {
                    onMessage("Could not save customer. Check that the phone number is not already used.")
                }
            }) { Text(if (editId == null) "Save customer" else "Update customer", color = Cyan) }
        },
        dismissButton = { TextButton(onClick = { showEditor = false; clearEditor() }) { Text("Cancel") } },
        containerColor = Color(0xFF111622),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFD1D9E6)
    )
}

@Composable
private fun PaymentInboxScreen() {
    val context = LocalContext.current
    val store = remember { SecureSessionStore(context) }
    val api = remember { BingwaSupabaseApi() }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(store.load()) }
    var candidates by remember { mutableStateOf<List<MpesaNotificationReviewCandidate>>(emptyList()) }
    var notice by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingVerification by remember { mutableStateOf<MpesaNotificationReviewCandidate?>(null) }
    var tillNumber by remember { mutableStateOf("1561763") }
    var tillBusy by remember { mutableStateOf(false) }
    var tillNotice by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("GABRIEL'S INTERNET SERVICES") }
    var workspaceExists by remember { mutableStateOf(session?.businessId != null) }
    var workspaceBusy by remember { mutableStateOf(false) }
    var workspaceNotice by remember { mutableStateOf("") }

    fun createBusinessWorkspace() {
        val current = session
        if (current == null) {
            workspaceNotice = "Sign in to BingwaPulse before creating a business workspace."
            return
        }
        val name = businessName.trim()
        if (name.length !in 2..120) {
            workspaceNotice = "Business name must contain 2–120 characters."
            return
        }
        scope.launch {
            workspaceBusy = true
            try {
                var active = current
                if (active.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                    active = withContext(Dispatchers.IO) { api.refreshSession(active) }
                    store.save(active)
                }
                val existingId = active.businessId
                    ?: withContext(Dispatchers.IO) { api.firstBusinessId(active) }
                val businessId = existingId ?: run {
                    val baseSlug = name.lowercase()
                        .replace(Regex("[^a-z0-9]+"), "-")
                        .trim('-')
                        .take(40)
                        .ifBlank { "bingwapulse-business" }
                    val slug = "$baseSlug-${active.userId.takeLast(6).lowercase()}"
                    withContext(Dispatchers.IO) { api.createBusiness(active, name, slug) }
                }
                active = active.copy(businessId = businessId)
                store.save(active)
                session = active
                workspaceExists = true
                workspaceNotice = if (existingId == null) {
                    "Business workspace created. Next, save the Till and sync your Signature offers."
                } else {
                    "Your existing business workspace is connected."
                }
                candidates = withContext(Dispatchers.IO) {
                    api.listMpesaNotificationCandidates(active, businessId)
                }
            } catch (error: Exception) {
                workspaceNotice = error.message ?: "Could not create the business workspace."
            } finally {
                workspaceBusy = false
            }
        }
    }

    fun saveTillConfiguration() {
        val current = session
        if (current == null) {
            tillNotice = "Sign in to a business workspace first."
            return
        }
        if (!tillNumber.trim().matches(Regex("^[0-9]{5,8}$"))) {
            tillNotice = "Enter the exact 5–8 digit M-PESA Till number."
            return
        }
        scope.launch {
            tillBusy = true
            try {
                var active = current
                if (active.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                    active = withContext(Dispatchers.IO) { api.refreshSession(active) }
                    store.save(active)
                    session = active
                }
                val businessId = active.businessId
                    ?: withContext(Dispatchers.IO) { api.firstBusinessId(active) }
                    ?: throw IllegalStateException("Create your business workspace first.")
                withContext(Dispatchers.IO) { api.savePaymentIngressTill(active, businessId, tillNumber) }
                tillNotice = "Till saved. Safaricom C2B URL registration and the callback token are the remaining payment-verification setup."
            } catch (error: Exception) {
                tillNotice = error.message ?: "Could not save the Till configuration."
            } finally {
                tillBusy = false
            }
        }
    }

    fun refreshCandidates() {
        if (store.isLocalWorkspace()) {
            notice = "Sign in to a real business workspace to review payment candidates."
            return
        }
        val current = session
        if (current == null) {
            notice = "Sign in to BingwaPulse before reviewing M-PESA payments."
            return
        }
        scope.launch {
            busy = true
            try {
                var activeSession = current
                if (activeSession.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                    activeSession = withContext(Dispatchers.IO) { api.refreshSession(activeSession) }
                    store.save(activeSession)
                    session = activeSession
                }
                val businessId = activeSession.businessId
                    ?: withContext(Dispatchers.IO) { api.firstBusinessId(activeSession) }
                    ?: throw IllegalStateException("This account has no active business workspace.")
                if (activeSession.businessId == null) {
                    activeSession = activeSession.copy(businessId = businessId)
                    store.save(activeSession)
                    session = activeSession
                }
                candidates = withContext(Dispatchers.IO) {
                    api.listMpesaNotificationCandidates(activeSession, businessId)
                }
                notice = if (candidates.isEmpty()) {
                    "No M-PESA notification candidates have reached this business inbox yet."
                } else {
                    "Loaded ${candidates.size} payment candidate(s)."
                }
            } catch (error: Exception) {
                notice = error.message ?: "Could not load the payment inbox."
            } finally {
                busy = false
            }
        }
    }

    fun retryCandidate(candidate: MpesaNotificationReviewCandidate) {
        val current = session ?: run {
            notice = "Your session has expired. Sign in again."
            return
        }
        scope.launch {
            busy = true
            try {
                var activeSession = current
                if (activeSession.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                    activeSession = withContext(Dispatchers.IO) { api.refreshSession(activeSession) }
                    store.save(activeSession)
                    session = activeSession
                }
                val businessId = activeSession.businessId
                    ?: withContext(Dispatchers.IO) { api.firstBusinessId(activeSession) }
                    ?: throw IllegalStateException("This account has no active business workspace.")
                withContext(Dispatchers.IO) {
                    api.retryMpesaNotificationCandidate(activeSession, businessId, candidate.id)
                }
                notice = "Retry requested. The server will re-check the Till and offer configuration."
                refreshCandidates()
            } catch (error: Exception) {
                notice = error.message ?: "The candidate could not be retried."
            } finally {
                busy = false
            }
        }
    }

    fun submitReview(candidate: MpesaNotificationReviewCandidate, decision: String) {
        val current = session ?: run {
            notice = "Your session has expired. Sign in again."
            return
        }
        scope.launch {
            busy = true
            try {
                var activeSession = current
                if (activeSession.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                    activeSession = withContext(Dispatchers.IO) { api.refreshSession(activeSession) }
                    store.save(activeSession)
                    session = activeSession
                }
                val businessId = activeSession.businessId
                    ?: withContext(Dispatchers.IO) { api.firstBusinessId(activeSession) }
                    ?: throw IllegalStateException("This account has no active business workspace.")
                withContext(Dispatchers.IO) {
                    api.reviewMpesaNotificationCandidate(activeSession, businessId, candidate.id, decision)
                }
                notice = if (decision == "verify") {
                    "Payment verified. Its order is now PAID and awaits the separate fulfilment worker."
                } else {
                    "Payment candidate rejected and sent to reconciliation."
                }
                pendingVerification = null
                refreshCandidates()
            } catch (error: Exception) {
                notice = error.message ?: "The payment review could not be saved."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshCandidates()
        val current = session
        if (current != null && !store.isLocalWorkspace()) {
            runCatching {
                var active = current
                val businessId = active.businessId ?: withContext(Dispatchers.IO) { api.firstBusinessId(active) }
                if (businessId != null) {
                    active = active.copy(businessId = businessId)
                    store.save(active)
                    session = active
                    workspaceExists = true
                    val savedTill = withContext(Dispatchers.IO) { api.getPaymentIngressTill(active, businessId) }
                    if (!savedTill.isNullOrBlank()) tillNumber = savedTill
                } else {
                    workspaceExists = false
                }
            }
        }
    }

    Text("M-PESA Payment Inbox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Navy)
    InfoCard(
        "Payment evidence and order staging",
        "Only strict incoming M-PESA Till confirmations qualify for automatic matching: the M-PESA sender title, receipt, date/time, amount, payer and new account balance must all be present. The business must have exactly one active Till configured. Generic wallet, outgoing, incomplete or ambiguous messages remain held. Trusted Safaricom C2B callbacks are also supported."
    )
    if (store.isLocalWorkspace()) {
        InfoCard("Account connection required", "Payment review is unavailable in local preview mode. Sign in and connect a business workspace to load its candidates.")
        return
    }
    if (session == null) {
        InfoCard("Sign in required", "Sign in to your BingwaPulse account before creating the business workspace and connecting its Till.")
        return
    }
    if (!workspaceExists) {
        Text("BUSINESS WORKSPACE", color = Navy, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        InfoCard(
            "Create your business workspace",
            "This creates the owner workspace for your offers, agent phone, Till payments and customer orders. It does not initiate a payment."
        )
        OutlinedTextField(
            value = businessName,
            onValueChange = { businessName = it.take(120) },
            label = { Text("Business name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { createBusinessWorkspace() },
            enabled = !workspaceBusy && businessName.trim().length in 2..120,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (workspaceBusy) "Creating workspace…" else "Create business workspace")
        }
        if (workspaceNotice.isNotBlank()) Text(workspaceNotice, color = Cyan, fontWeight = FontWeight.SemiBold)
        return
    }
    Text("TRUSTED TILL PAYMENT SETUP", color = Navy, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
    InfoCard(
        "Automatic Till verification",
        "Save the receiving Till here. Notification matching requires exactly one active Till and the strict incoming confirmation format described above. Trusted C2B callbacks can also verify receipts; anything incomplete or ambiguous remains held for reconciliation."
    )
    OutlinedTextField(
        value = tillNumber,
        onValueChange = { tillNumber = it.filter(Char::isDigit).take(8) },
        label = { Text("M-PESA Buy Goods Till number") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Button(onClick = { saveTillConfiguration() }, enabled = !tillBusy && tillNumber.length in 5..8, modifier = Modifier.fillMaxWidth()) {
        Text(if (tillBusy) "Saving Till…" else "Save Till for automatic verification")
    }
    if (tillNotice.isNotBlank()) Text(tillNotice, color = Cyan, style = MaterialTheme.typography.bodySmall)
    InfoCard(
        "Safaricom C2B registration URLs",
        "Validation: https://tuuhucisljlaaovtnbeu.supabase.co/functions/v1/mpesa-c2b-callback?mode=validate&token=YOUR_SECRET_TOKEN\n\nConfirmation: https://tuuhucisljlaaovtnbeu.supabase.co/functions/v1/mpesa-c2b-callback?mode=confirm&token=YOUR_SECRET_TOKEN\n\nSet the same strong token as the Supabase Edge Function secret MPESA_C2B_CALLBACK_TOKEN, then register both URLs for this Till in Safaricom Daraja. This is C2B confirmation only; it does not send STK prompts."
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Button(onClick = { refreshCandidates() }, enabled = !busy, modifier = Modifier.weight(1f)) {
            Text(if (busy) "Loading…" else "Refresh inbox")
        }
        if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Cyan, strokeWidth = 2.dp)
    }
    if (notice.isNotBlank()) Text(notice, color = Cyan, fontWeight = FontWeight.SemiBold)
    if (candidates.isEmpty() && !busy) {
        InfoCard("Nothing to review yet", "New matching M-PESA notifications will appear here after the agent phone has Notification Access and successfully syncs to this business.")
    }
    candidates.forEach { candidate ->
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("KES ${candidate.amountMinor?.div(100) ?: "—"}", color = Navy, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    Text(candidate.stagingStatus.replace('_', ' ').uppercase(), color = if (candidate.stagingStatus == "staged_for_review") Color(0xFFFFD166) else Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
                Text("Receipt: ${candidate.receiptCode ?: "Not identified"}", color = Navy, fontWeight = FontWeight.SemiBold)
                Text("Payer: ${candidate.senderPhone ?: "Not identified"}", color = Muted)
                if (!candidate.senderName.isNullOrBlank()) Text("Name: ${candidate.senderName}", color = Muted)
                Text("Parser: ${candidate.candidateStatus.replace('_', ' ')} · Match: ${candidate.matchStatus.uppercase()}", color = Muted, style = MaterialTheme.typography.labelSmall)
                if (!candidate.stagingReason.isNullOrBlank()) Text(candidate.stagingReason, color = Muted, style = MaterialTheme.typography.bodySmall)
                if (!candidate.stagedOrderId.isNullOrBlank()) Text("Order: ${candidate.stagedOrderId}", color = Muted, style = MaterialTheme.typography.labelSmall)
                if (candidate.stagingStatus == "blocked") {
                    OutlinedButton(onClick = { retryCandidate(candidate) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry after fixing configuration")
                    }
                }
                if (candidate.stagingStatus == "staged_for_review") {
                    InfoCard(
                        "Automatic match not completed",
                        "No single recent Quick Dial order matched this receipt. The payment is held safely; no bundle will be sent until the order can be matched."
                    )
                }
                if (candidate.stagingStatus == "verified") {
                    Text("Automatically matched · payment marked PAID · fulfilment queued for the worker.", color = Cyan, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    pendingVerification?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingVerification = null },
            title = { Text("Verify this M-PESA payment?") },
            text = {
                Text("Only continue after checking the transaction in the actual Lipa na M-PESA Till record. Confirm receipt ${candidate.receiptCode ?: "—"} for KES ${candidate.amountMinor?.div(100) ?: "—"} and payer ${candidate.senderPhone ?: "—"}. This marks the linked order PAID; it does not itself start a bundle purchase.")
            },
            confirmButton = {
                TextButton(onClick = { submitReview(candidate, "verify") }, enabled = !busy) {
                    Text("I checked the Till record")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingVerification = null }, enabled = !busy) {
                    Text("Not yet")
                }
            }
        )
    }
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
    orders.forEach { o -> Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF0B0F19)),modifier=Modifier.fillMaxWidth()) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionsScreen(db: LocalDatabase, onMessage: (String) -> Unit) {
    var selectedStatus by remember { mutableStateOf("Live") }
    var selectedDate by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<TransactionHistoryRow?>(null) }
    val context = LocalContext.current
    val history = remember(reload) { db.transactionHistory() }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.getDefault()) }
    val statuses = listOf("Live", "All", "Successful", "Awaiting Commission", "Failed", "Pending", "Unmatched", "Scheduled", "SiteLink", "EngageBot")
    val dates = listOf("All", "Today", "Yesterday", "Last 7 days", "Last 30 days")

    fun label(row: TransactionHistoryRow): String {
        val tx = row.transactionStatus.uppercase(Locale.ROOT)
        val order = row.orderStatus.uppercase(Locale.ROOT)
        return when {
            tx in setOf("SUCCESS", "SUCCESSFUL", "DELIVERED", "COMPLETED") || order == "DELIVERED_MANUALLY_RECORDED" -> "Successful"
            tx == "AWAITING_COMMISSION" || order == "AWAITING_COMMISSION" -> "Awaiting Commission"
            tx == "FAILED" || order == "FAILED" -> "Failed"
            tx == "SCHEDULED" || order == "SCHEDULED" -> "Scheduled"
            tx in setOf("UNMATCHED", "UNVERIFIED") || order == "PAYMENT_UNVERIFIED" -> "Unmatched"
            else -> "Pending"
        }
    }
    fun matchesStatus(row: TransactionHistoryRow): Boolean = when (selectedStatus) {
        "Live", "All" -> true
        "SiteLink", "EngageBot" -> row.source.equals(selectedStatus, true)
        else -> label(row) == selectedStatus
    }
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val today = cal.timeInMillis
    val yesterday = today - 86400000L
    val week = today - 6L * 86400000L
    val month = today - 29L * 86400000L
    val visible = history.filter { row ->
        val dateOk = when (selectedDate) {
            "Today" -> row.createdAt >= today
            "Yesterday" -> row.createdAt in yesterday until today
            "Last 7 days" -> row.createdAt >= week
            "Last 30 days" -> row.createdAt >= month
            else -> true
        }
        val q = search.trim()
        dateOk && matchesStatus(row) && (q.isBlank() || row.customer.contains(q, true) ||
            row.offer.contains(q, true) || row.reference.contains(q, true) || row.amount.toString().contains(q))
    }
    val csv = {
        val header = "Customer,Offer,Amount KES,Status,Date and time,Source,Reference"
        val rows = visible.map { row ->
            listOf(row.customer, row.offer, row.amount.toString(), label(row), dateFormat.format(Date(row.createdAt)), row.source, row.reference)
                .joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        }
        (listOf(header) + rows).joinToString("\n")
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv()) }
            onMessage("Transaction history exported.")
        } catch (e: Exception) { onMessage("Could not export transaction history: ${e.message ?: "file error"}") }
    }
    val volume = visible.sumOf { it.amount }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("BINGWAPULSE", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Text("TRANSACTION HISTORY  •  TILL 1561763", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, maxLines = 1)
            }
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) search = "" }) { Text("⌕", color = Cyan, fontSize = 25.sp) }
            IconButton(onClick = { reload++ }) { Text("↻", color = Cyan, fontSize = 24.sp) }
            IconButton(onClick = { export.launch("bingwapulse-transactions.csv") }) { Text("⇩", color = Color(0xFF34D399), fontSize = 23.sp) }
        }
        if (searchOpen) OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, placeholder = { Text("Search phone, name, offer or receipt") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF263244),
                focusedContainerColor = Color(0xFF111622), unfocusedContainerColor = Color(0xFF111622))
        )
        Column(Modifier.fillMaxWidth().background(Color(0xFF0A0E17), RoundedCornerShape(17.dp)).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 9.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                statuses.forEach { item ->
                    FilterChip(selected = selectedStatus == item, onClick = { selectedStatus = item }, label = { Text(item, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan, selectedLabelColor = Color(0xFF061018),
                            containerColor = Color(0xFF111622), labelColor = Color(0xFFCBD5E1)))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 9.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                dates.forEach { item ->
                    FilterChip(selected = selectedDate == item, onClick = { selectedDate = item }, label = { Text(item, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF34D399), selectedLabelColor = Color(0xFF052017),
                            containerColor = Color(0xFF111622), labelColor = Muted))
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF0C111D), RoundedCornerShape(10.dp)).padding(horizontal = 11.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${visible.size}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                Text("transactions", color = Muted, fontSize = 12.sp)
                Text("($selectedStatus • $selectedDate)", color = Color(0xFF64748B), fontSize = 9.sp, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Volume:", color = Muted, fontSize = 10.sp)
                Text("Ksh ${money(volume)}", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
        if (visible.isEmpty()) {
            InfoCard("No transactions found", if (history.isEmpty())
                "Customer purchases appear here when real transaction records are created. No sample transactions are inserted."
                else "No recorded transactions match these filters. Try All or change the date range.")
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101521)), shape = RoundedCornerShape(19.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF202B3C)), modifier = Modifier.fillMaxWidth()) {
                Column {
                    visible.forEachIndexed { index, row ->
                        val status = label(row)
                        val tint = when (status) {
                            "Successful" -> Color(0xFF34D399)
                            "Failed" -> Color(0xFFFB7185)
                            "Unmatched" -> Color(0xFFCBD5E1)
                            "Pending", "Scheduled" -> Color(0xFF38BDF8)
                            else -> Color(0xFFFBBF24)
                        }
                        Row(Modifier.fillMaxWidth().clickable { detail = row }.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            Surface(shape = RoundedCornerShape(50), color = tint.copy(alpha = .14f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .35f)), modifier = Modifier.size(38.dp)) {
                                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Text(when (status) { "Successful" -> "✓"; "Failed" -> "×"; "Unmatched" -> "?"; else -> "◷" },
                                        color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(row.customer, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                Text(row.offer, color = Cyan, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(dateFormat.format(Date(row.createdAt)), color = Muted, fontSize = 9.sp, maxLines = 1)
                                Text("Ksh ${money(row.amount)}", color = tint, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                        if (index < visible.lastIndex) HorizontalDivider(color = Color(0xFF202B3C).copy(alpha = .7f), thickness = .7.dp)
                    }
                }
            }
        }
    }
    detail?.let { row ->
        AlertDialog(onDismissRequest = { detail = null }, containerColor = Color(0xFF111622),
            title = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.customer, color = Color.White, fontWeight = FontWeight.Bold)
                Text(label(row).uppercase(Locale.ROOT), color = Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            } },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.offer, color = Cyan, fontWeight = FontWeight.SemiBold)
                Text("Amount  ·  Ksh ${money(row.amount)}", color = Color.White)
                Text("Date  ·  ${dateFormat.format(Date(row.createdAt))}", color = Muted)
                Text("Source  ·  ${row.source}", color = Muted)
                Text("Reference  ·  ${row.reference.ifBlank { "Not available" }}", color = Muted)
                Text("Order status  ·  ${row.orderStatus}", color = Muted)
                Text("Transaction status  ·  ${row.transactionStatus}", color = Muted)
            } },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Close", color = Cyan) } }
        )
    }
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
    if (notice.isNotBlank()) Text(notice, color = Cyan, fontWeight = FontWeight.SemiBold)
    InfoCard("Sending safety", "Before live sending is enabled, the app needs verified channel setup, consent-aware recipients, duplicate prevention, rate limits, delivery/error reporting, and a stop control. This screen currently sends nothing.")
}

private data class AutoReplyDraft(
    val id: String,
    val title: String,
    val message: String,
    val enabled: Boolean,
    val isDefault: Boolean
)

@Composable
fun AutoReplyScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bingwapulse_auto_reply_templates_v3", Context.MODE_PRIVATE) }
    val defaults = remember {
        listOf(
            AutoReplyDraft("offer-recommended", "Offer Already Recommended", "Hello <firstName>, you have already purchased this offer today. Please try again tomorrow.", true, true),
            AutoReplyDraft("app-paused", "App Paused", "Hi <firstName>, there is an issue affecting our systems. We will update you as soon as service resumes.", true, true),
            AutoReplyDraft("customer-blacklisted", "Customer Blacklisted", "Hi <firstName>, there is an issue affecting your account. Please contact support for assistance.", true, true),
            AutoReplyDraft("failed-request", "Failed Request", "Hello <firstName>, your request failed. Please contact support while we investigate.", false, true),
            AutoReplyDraft("unavailable-offer", "Unavailable Offer", "Hi <firstName>, there is no offer matching the amount received. Please check the offer amount and contact support.", true, true),
            AutoReplyDraft("successful-response", "Successful Response", "Hi <firstName>, thank you for purchasing from BingwaPulse.", true, true)
        )
    }
    fun readDrafts(): List<AutoReplyDraft> {
        val raw = prefs.getString("templates", null) ?: return defaults
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(AutoReplyDraft(
                        item.optString("id", "custom-$i"),
                        item.optString("title", "Untitled reply"),
                        item.optString("message", ""),
                        item.optBoolean("enabled", false),
                        item.optBoolean("isDefault", false)
                    ))
                }
            }.ifEmpty { defaults }
        } catch (_: Exception) { defaults }
    }
    val drafts = remember { mutableStateListOf<AutoReplyDraft>().apply { addAll(readDrafts()) } }
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var sampleName by remember { mutableStateOf("John") }
    var notice by remember { mutableStateOf("") }

    fun persist() {
        val array = JSONArray()
        drafts.forEach { draft ->
            array.put(JSONObject().put("id", draft.id).put("title", draft.title)
                .put("message", draft.message).put("enabled", draft.enabled).put("isDefault", draft.isDefault))
        }
        prefs.edit().putString("templates", array.toString()).apply()
    }
    fun openDraft(draft: AutoReplyDraft?) {
        editingId = draft?.id
        title = draft?.title ?: ""
        message = draft?.message ?: "Hi <firstName>, "
        enabled = draft?.enabled ?: true
        editorOpen = true
    }
    fun saveDraft() {
        val cleanTitle = title.trim()
        val cleanMessage = message.trim()
        if (cleanTitle.isBlank() || cleanMessage.isBlank()) {
            notice = "Add a title and message before saving."
            return
        }
        val index = drafts.indexOfFirst { it.id == editingId }
        val updated = AutoReplyDraft(
            editingId ?: "custom-${System.currentTimeMillis()}",
            cleanTitle, cleanMessage, enabled,
            drafts.getOrNull(index)?.isDefault ?: false
        )
        if (index >= 0) drafts[index] = updated else drafts.add(0, updated)
        persist()
        editorOpen = false
        notice = "Template saved on this phone."
    }

    Column(verticalArrangement = Arrangement.spacedBy(13.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Auto-Reply Messages", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
                Text("Manage the messages your customers may receive.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { openDraft(null) }, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 10.dp)) {
                Text("+ Add", fontWeight = FontWeight.Bold)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF102333)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("DRAFT WORKSPACE", color = Cyan, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
                Text("Templates are saved locally on this phone. This screen does not send SMS or activate automatic replies.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        drafts.forEach { draft ->
            Card(
                onClick = { openDraft(draft) },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131B26)),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (draft.enabled) Color(0xFF1E3547) else Color(0xFF202A36)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(draft.title, color = if (draft.enabled) Navy else Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (!draft.isDefault) Surface(color = Color(0xFF123B4B), shape = RoundedCornerShape(30.dp)) {
                                Text("CUSTOM", color = Cyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        Text(draft.message, color = Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(if (draft.enabled) "ENABLED DRAFT" else "PAUSED DRAFT", color = if (draft.enabled) Cyan else Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = draft.enabled,
                        onCheckedChange = { value ->
                            val index = drafts.indexOfFirst { it.id == draft.id }
                            if (index >= 0) { drafts[index] = draft.copy(enabled = value); persist() }
                        }
                    )
                }
            }
        }
        Text("Placeholder supported: <firstName> · Tap a card to edit its message.", color = Muted, style = MaterialTheme.typography.bodySmall)
        if (notice.isNotBlank()) Text(notice, color = Cyan, style = MaterialTheme.typography.bodySmall)
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { editorOpen = false },
            containerColor = Color(0xFF101923),
            titleContentColor = Navy,
            textContentColor = Muted,
            title = { Text(if (editingId == null) "Create auto-reply" else "Edit auto-reply", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Trigger title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Draft enabled", color = Navy, fontWeight = FontWeight.SemiBold)
                            Text("Enable or pause this template draft", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Message template") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = {
                        val tag = "<firstName>"
                        val cursor = message.length
                        message = message + tag
                    }, modifier = Modifier.fillMaxWidth()) { Text("+ Insert <firstName>") }
                    OutlinedTextField(value = sampleName, onValueChange = { sampleName = it }, label = { Text("Sample customer name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF080C12)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("LIVE MESSAGE PREVIEW · ${message.length} characters", color = Cyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Text(message.replace(Regex("<firstName>", RegexOption.IGNORE_CASE), sampleName.ifBlank { "John" }).ifBlank { "Your message preview will appear here." }, color = Navy, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (editingId != null && drafts.firstOrNull { it.id == editingId }?.isDefault == false) {
                        TextButton(onClick = {
                            drafts.removeAll { it.id == editingId }
                            persist()
                            editorOpen = false
                            notice = "Custom template deleted."
                        }) { Text("Delete custom template", color = Color(0xFFFF7185)) }
                    }
                }
            },
            confirmButton = { Button(onClick = { saveDraft() }) { Text("Save template") } },
            dismissButton = { TextButton(onClick = { editorOpen = false }) { Text("Cancel") } }
        )
    }
}
 
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsScreen(db: LocalDatabase) {
    var section by remember { mutableStateOf("Commission") }
    var period by remember { mutableStateOf("7 days") }
    val allOrders = remember { db.orders() }
    val allOffers = remember { db.offers() }
    val customers = remember { db.customers() }
    val now = System.currentTimeMillis()
    val windowMs = when (period) {
        "30 days" -> 30L * 24 * 60 * 60 * 1000
        "This month" -> {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            now - cal.timeInMillis
        }
        else -> 7L * 24 * 60 * 60 * 1000
    }
    val since = now - windowMs
    val orders = allOrders.filter { it.createdAt >= since }
    val delivered = orders.filter { it.status == "DELIVERED_MANUALLY_RECORDED" }
    val salesByOffer = delivered.groupBy { it.offer }.entries.sortedByDescending { it.value.size }
    val uniquePhones = orders.map { it.customer }.distinct().size
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val dailySales = delivered.groupBy { dateFormat.format(Date(it.createdAt)) }
        .entries.sortedBy { it.value.minOfOrNull { row -> row.createdAt } ?: 0L }
    val cardColor = Color(0xFF0B0F19)
    val accent = Color(0xFF00E5FF)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Statistics", color = Navy, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)
                Text("Business performance · local records", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            var periodMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { periodMenu = true }) { Text(period, color = accent) }
                DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                    listOf("7 days", "30 days", "This month").forEach { choice ->
                        DropdownMenuItem(text = { Text("Last".let { if (choice == "This month") choice else "$it $choice" }) }, onClick = { period = choice; periodMenu = false })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Commission", "Offers", "Customers", "SIM cards").forEach { item ->
                TextButton(onClick = { section = item }, modifier = Modifier.weight(1f)) {
                    Text(item, color = if (section == item) accent else Muted, fontWeight = if (section == item) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        when (section) {
            "Commission" -> {
                val recordedValue = delivered.sumOf { it.price }
                StatCard("DELIVERED ORDERS", delivered.size, Modifier.fillMaxWidth())
                Card(colors = CardDefaults.cardColors(containerColor = cardColor), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Recorded delivered value", color = Muted)
                        Text("KES " + money(recordedValue), color = accent, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)
                        Text("Commission rates have not been configured in the current offer records. No commission estimate is invented.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = cardColor), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Daily delivered value", color = Navy, fontWeight = FontWeight.Bold)
                        if (dailySales.isEmpty()) Text("No delivered orders recorded in this period.", color = Muted)
                        dailySales.forEach { (day, rows) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(day, color = Navy)
                                Text("KES " + money(rows.sumOf { it.price }), color = accent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            "Offers" -> {
                val verifiedActive = allOffers.count { it.verified && it.active }
                StatCard("RECORDED OFFER TYPES SOLD", salesByOffer.size, Modifier.fillMaxWidth())
                Card(colors = CardDefaults.cardColors(containerColor = cardColor), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Best sellers", color = Navy, fontWeight = FontWeight.Bold)
                        if (salesByOffer.isEmpty()) Text("No delivered offer sales recorded in this period.", color = Muted)
                        salesByOffer.forEach { entry ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(entry.key, color = Navy, modifier = Modifier.weight(1f))
                                Text(entry.value.size.toString() + " sales", color = accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                InfoCard("Signature catalogue", "${allOffers.size} imported offers · $verifiedActive verified and active. Imported offers remain unverified until checked.")
            }
            "Customers" -> {
                StatCard("CUSTOMERS IN PERIOD", uniquePhones, Modifier.fillMaxWidth())
                StatCard("CUSTOMER CONTACTS SAVED", customers.size, Modifier.fillMaxWidth())
                Card(colors = CardDefaults.cardColors(containerColor = cardColor), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Recent customer activity", color = Navy, fontWeight = FontWeight.Bold)
                        val customerRows = orders.groupBy { it.customer }.entries.sortedByDescending { it.value.maxOfOrNull { row -> row.createdAt } ?: 0L }.take(8)
                        if (customerRows.isEmpty()) Text("No customer activity recorded in this period.", color = Muted)
                        customerRows.forEach { entry ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(entry.key, color = Navy, modifier = Modifier.weight(1f))
                                Text("${entry.value.size} orders", color = accent)
                            }
                        }
                    }
                }
                Text("First-time versus returning status needs complete historical purchase records; this view does not guess.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                Card(colors = CardDefaults.cardColors(containerColor = cardColor), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SIM performance", color = Navy, fontWeight = FontWeight.Bold)
                        Text("SIM assignment is not currently stored on each order. SIM 1 / SIM 2 sales and percentages cannot be calculated reliably yet.", color = Muted)
                    }
                }
            }
        }
        Text("Figures use this device's saved records only. Pending, failed and unverified records are not counted as delivered sales or confirmed payments.", color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DashboardMetric(label: String, count: Int, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2544))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(count.toString(), color = accent, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable
private fun QuickAction(symbol:String,label:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    Card(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF0B0F19)),elevation=CardDefaults.cardElevation(defaultElevation=2.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical=16.dp,horizontal=13.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Surface(color=Color(0xFF12345A),shape=RoundedCornerShape(12.dp)) { Text(symbol,color=Color(0xFF00F2FE),fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(horizontal=12.dp,vertical=6.dp)) }
            Text(label,color=Navy,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall)
        }
    }
}
@Composable
private fun StatCard(label:String,count:Int,modifier:Modifier=Modifier) {
    Card(modifier=modifier,shape=MaterialTheme.shapes.large,colors=CardDefaults.cardColors(containerColor=Color(0xFF0B0F19)),elevation=CardDefaults.cardElevation(defaultElevation=2.dp)) { Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) { Text(count.toString(),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold,color=Navy);Text(label,color=Muted,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=.5.sp) } }
}
@Composable
private fun InfoCard(title:String,body:String) { Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF0B0F19)),shape=MaterialTheme.shapes.large,elevation=CardDefaults.cardElevation(defaultElevation=1.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(title,fontWeight=FontWeight.Bold,color=Navy,style=MaterialTheme.typography.titleMedium);Text(body,color=Muted)} } }
private fun money(value:Double)=String.format(Locale.US,"%.2f",value)
private fun dateText(value:Long)=SimpleDateFormat("dd MMM yyyy, HH:mm",Locale.getDefault()).format(Date(value))


@Composable
private fun UssdAutomationSettingsScreen() {
    val context = LocalContext.current
    val secureStore = remember { SecureSessionStore(context) }
    val api = remember { BingwaSupabaseApi() }
    val scope = rememberCoroutineScope()
    var fulfilmentEnabled by remember { mutableStateOf(false) }
    var fulfilmentPaused by remember { mutableStateOf(true) }
    var fulfilmentBusy by remember { mutableStateOf(false) }
    var fulfilmentNotice by remember { mutableStateOf("") }
    var workerDevices by remember { mutableStateOf<List<BingwaDevice>>(emptyList()) }
    var selectedWorkerDevice by remember { mutableStateOf("") }
    var fulfilmentOffers by remember { mutableStateOf<List<FulfilmentOffer>>(emptyList()) }
    var fulfilmentRules by remember { mutableStateOf<List<FulfilmentRuleSummary>>(emptyList()) }
    var fulfilmentOrders by remember { mutableStateOf<List<FulfilmentOrderSummary>>(emptyList()) }
    var pendingRetryOrder by remember { mutableStateOf<FulfilmentOrderSummary?>(null) }
    var retryReason by remember { mutableStateOf("") }
    var retryBusy by remember { mutableStateOf(false) }
    var selectedOfferId by remember { mutableStateOf("") }
    var offerMenuExpanded by remember { mutableStateOf(false) }
    var amountKesText by remember { mutableStateOf("") }
    var ussdEntryCode by remember { mutableStateOf("") }
    var menuStepsJson by remember { mutableStateOf("""[
  {"match_text":"[EXACT OFFER NAME] [EXACT VALIDITY]","action":"tap_text","value":"[EXACT OFFER NAME] [EXACT VALIDITY]"},
  {"match_text":"Buy with Airtime","action":"tap_text","value":"Buy with Airtime"},
  {"match_text":"Enter recipient phone","action":"reply","value":"{recipient_phone}"}
]""") }
    var confirmMenuVerified by remember { mutableStateOf(false) }
    var menuVerificationNote by remember { mutableStateOf("") }
    var ruleBusy by remember { mutableStateOf(false) }
    val accountSession = remember { secureStore.load() }

    fun startFulfilmentWorker() {
        val active = accountSession ?: run { fulfilmentNotice = "Sign in to start the worker."; return }
        if (selectedWorkerDevice.isBlank()) { fulfilmentNotice = "Select an approved online worker device first."; return }
        scope.launch {
            try {
                val refreshed = if (active.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L)
                    withContext(Dispatchers.IO) { api.refreshSession(active) } else active
                val businessId = refreshed.businessId ?: withContext(Dispatchers.IO) { api.firstBusinessId(refreshed) }
                    ?: throw IllegalStateException("No business workspace is connected.")
                val intent = Intent(context, FulfilmentWorkerService::class.java)
                    .putExtra(FulfilmentWorkerService.EXTRA_BUSINESS_ID, businessId)
                    .putExtra(FulfilmentWorkerService.EXTRA_DEVICE_ID, selectedWorkerDevice)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                fulfilmentNotice = "Worker started. It will only claim eligible paid orders while fulfilment is enabled and unpaused."
            } catch (error: Exception) {
                fulfilmentNotice = error.message ?: "Could not start the worker."
            }
        }
    }
    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startFulfilmentWorker() else fulfilmentNotice = "Phone permission is required for automatic USSD dialling."
    }
    LaunchedEffect(accountSession?.userId) {
        if (accountSession != null && !secureStore.isLocalWorkspace()) {
            try {
                var active = accountSession
                if (active.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                    active = withContext(Dispatchers.IO) { api.refreshSession(active) }
                    secureStore.save(active)
                }
                val businessId = active.businessId ?: withContext(Dispatchers.IO) { api.firstBusinessId(active) }
                if (businessId != null) {
                    val settings = withContext(Dispatchers.IO) { api.getFulfilmentControls(active, businessId) }
                    fulfilmentEnabled = settings.optBoolean("fulfilment_enabled", false)
                    fulfilmentPaused = settings.optBoolean("fulfilment_paused", true)
                    workerDevices = withContext(Dispatchers.IO) { api.listDevices(active, businessId) }
                        .filter { it.status == "online" && it.assignedUserId == active.userId }
                    fulfilmentOffers = withContext(Dispatchers.IO) { api.listFulfilmentOffers(active, businessId) }
                    fulfilmentRules = withContext(Dispatchers.IO) { api.listFulfilmentRules(active, businessId) }
                    fulfilmentOrders = withContext(Dispatchers.IO) { api.listFulfilmentOrders(active, businessId) }
                    if (selectedWorkerDevice.isBlank()) selectedWorkerDevice = workerDevices.firstOrNull()?.id.orEmpty()
                    if (selectedOfferId.isBlank()) selectedOfferId = fulfilmentOffers.firstOrNull()?.id.orEmpty()
                    if (ussdEntryCode.isBlank()) ussdEntryCode = fulfilmentOffers.firstOrNull()?.entryCode.orEmpty()
                }
            } catch (error: Exception) {
                fulfilmentNotice = error.message ?: "Could not load fulfilment controls."
            }
        }
    }
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

    HorizontalDivider(color = Color(0xFF1E3A5F))
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
        Text(relayNotice, color = Cyan, fontWeight = FontWeight.SemiBold)
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

    HorizontalDivider(color = Color(0xFF1E3A5F))
    InfoCard(
        "USSD accessibility service",
        if (enabled) "ENABLED. The service reads visible USSD menus and performs only the screen-matched actions configured for the selected offer in the Admin Fulfilment controls."
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
        "Payment notifications are parsed as unverified evidence. A manager must verify the Till transaction before an order becomes PAID. The foreground worker then claims only eligible orders with an enabled, verified offer menu rule. Accessibility follows configured screen matches; unknown screens are not guessed, and uncertain outcomes require manager reconciliation. No live order is processed until fulfilment is enabled and unpaused."
    )

    HorizontalDivider(color = Color(0xFF1E3A5F))
    Text("AUTOMATIC FULFILMENT CONTROL", color = Navy, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
    InfoCard(
        "Manager-controlled order delivery",
        "Verified PAID orders can be claimed only by an approved online Android device with a verified offer rule and menu flow. Automation is disabled and paused by default."
    )
    if (accountSession == null || secureStore.isLocalWorkspace()) {
        InfoCard("Business account required", "Sign in to a real business workspace to configure automatic fulfilment.")
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Enable automatic fulfilment", color = Navy, fontWeight = FontWeight.Bold)
                Text("Allow eligible paid orders to enter the worker queue.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = fulfilmentEnabled, onCheckedChange = { fulfilmentEnabled = it })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pause fulfilment", color = Navy, fontWeight = FontWeight.Bold)
                Text("Pause new order claims immediately.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = fulfilmentPaused, onCheckedChange = { fulfilmentPaused = it })
        }
        InfoCard(
            "PIN safety",
            "BingwaPulse never stores, reads or automatically enters the SIM PIN. If a provider unexpectedly requests a PIN, the session pauses for you to handle it on the phone."
        )

        HorizontalDivider(color = Color(0xFF1E3A5F))
        Text("OFFER-TO-USSD MENU RULES", color = Navy, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        InfoCard(
            "Configure the actual provider menu here",
            "Choose the exact Signature offer and validity, enter its saved USSD entry code, then configure the provider screens. The recipe must match the offer name and validity and explicitly choose Buy with Airtime."
        )
        val selectedOffer = fulfilmentOffers.firstOrNull { it.id == selectedOfferId }
        Box {
            OutlinedButton(onClick = { offerMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedOffer?.let { "${it.name} · ${it.validityLabel ?: "validity not set"}" } ?: "Choose an active offer")
            }
            DropdownMenu(expanded = offerMenuExpanded, onDismissRequest = { offerMenuExpanded = false }) {
                fulfilmentOffers.forEach { offer ->
                    DropdownMenuItem(
                        text = { Text("${offer.name} · ${offer.validityLabel ?: "validity not set"}") },
                        onClick = { selectedOfferId = offer.id; ussdEntryCode = offer.entryCode.orEmpty(); offerMenuExpanded = false }
                    )
                }
            }
        }
        OutlinedTextField(
            value = amountKesText, onValueChange = { amountKesText = it.filter(Char::isDigit).take(7) },
            label = { Text("Exact customer payment amount (KES)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = ussdEntryCode, onValueChange = { ussdEntryCode = it.filter { ch -> ch.isDigit() || ch == '*' || ch == '#' }.take(32) },
            label = { Text("Provider / agent USSD code") }, placeholder = { Text("*xxx#") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = menuStepsJson, onValueChange = { menuStepsJson = it },
            label = { Text("Menu steps (JSON array)") }, minLines = 5, maxLines = 10,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "The first offer-selection step must match the exact offer name and validity shown by the provider. A separate step must select Buy with Airtime. Use {recipient_phone} for the delivery number. PINs are never stored or entered by BingwaPulse.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = confirmMenuVerified, onCheckedChange = { confirmMenuVerified = it })
            Text("I tested this exact menu flow on the target SIM and verified every step.", color = Navy, style = MaterialTheme.typography.bodySmall)
        }
        if (confirmMenuVerified) {
            OutlinedTextField(
                value = menuVerificationNote,
                onValueChange = { menuVerificationNote = it.take(500) },
                label = { Text("Verification note (what you tested and confirmed)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Button(
            onClick = {
                val amount = amountKesText.toLongOrNull()
                val selected = selectedOffer
                val steps = runCatching { JSONArray(menuStepsJson) }.getOrNull()
                val rawPinInScript = steps?.let { array ->
                    (0 until array.length()).any { index ->
                        val step = array.optJSONObject(index)
                        step?.optString("action") == "reply" && step.optString("value").matches(Regex("^[0-9]{4,8}$"))
                    }
                } == true
                val exactOfferValidityStep = selected?.validityLabel?.takeIf { it.isNotBlank() }?.let { validity ->
                    steps?.let { array ->
                        (0 until array.length()).any { index ->
                            val match = array.optJSONObject(index)?.optString("match_text").orEmpty()
                            match.contains(selected?.name.orEmpty(), ignoreCase = true) && match.contains(validity, ignoreCase = true)
                        }
                    } == true
                } == true
                val buyWithAirtimeStep = steps?.let { array ->
                    (0 until array.length()).any { index ->
                        val step = array.optJSONObject(index)
                        step?.optString("match_text").orEmpty().contains("Buy with Airtime", ignoreCase = true) &&
                            step?.optString("action") in setOf("reply", "tap_text")
                    }
                } == true
                if (selected == null || selected.validityLabel.isNullOrBlank() || amount == null || amount <= 0L ||
                    !ussdEntryCode.matches(Regex("^\\\\*[0-9*]+#$")) || steps == null || steps.length() == 0 ||
                    rawPinInScript || !exactOfferValidityStep || !buyWithAirtimeStep || (confirmMenuVerified && menuVerificationNote.trim().length < 12)) {
                    fulfilmentNotice = when {
                        rawPinInScript -> "A SIM PIN must never be placed in menu steps."
                        !exactOfferValidityStep -> "Add a screen step that matches the exact offer name and validity."
                        !buyWithAirtimeStep -> "Add a separate screen-matched step that selects Buy with Airtime."
                        confirmMenuVerified && menuVerificationNote.trim().length < 12 -> "Describe the successful test in a verification note (at least 12 characters)."
                        else -> "Choose an offer with a validity label and enter a valid amount, USSD code and menu steps."
                    }
                } else {
                    ruleBusy = true
                    scope.launch {
                        try {
                            var active = accountSession ?: throw IllegalStateException("Sign in to a business account.")
                            if (active.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                                active = withContext(Dispatchers.IO) { api.refreshSession(active) }
                                secureStore.save(active)
                            }
                            val businessId = active.businessId ?: withContext(Dispatchers.IO) { api.firstBusinessId(active) }
                                ?: throw IllegalStateException("No business workspace is connected.")
                            withContext(Dispatchers.IO) {
                                api.saveFulfilmentRule(active, businessId, amount, selected.id, ussdEntryCode.trim(), steps, confirmMenuVerified, menuVerificationNote)
                            }
                            fulfilmentRules = withContext(Dispatchers.IO) { api.listFulfilmentRules(active, businessId) }
                            fulfilmentNotice = if (confirmMenuVerified) "Menu rule saved and enabled after your verification." else "Menu rule saved disabled. Verify the exact provider flow before enabling it."
                        } catch (error: Exception) {
                            fulfilmentNotice = error.message ?: "Could not save the offer menu rule."
                        } finally { ruleBusy = false }
                    }
                }
            },
            enabled = !ruleBusy && fulfilmentOffers.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (ruleBusy) "Saving menu rule…" else "Save offer menu rule") }

        HorizontalDivider(color = Color(0xFF1E3A5F))
        Text("FULFILMENT ACTIVITY & RECOVERY", color = Navy, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        InfoCard(
            "No blind retries",
            "DELIVERED is recorded only when the USSD screen shows a recognised carrier success. FAILED and UNKNOWN orders require a manager to check the provider account before retrying."
        )
        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        val active = accountSession ?: throw IllegalStateException("Sign in to view order activity.")
                        val businessId = active.businessId ?: withContext(Dispatchers.IO) { api.firstBusinessId(active) }
                            ?: throw IllegalStateException("No business workspace is connected.")
                        fulfilmentOrders = withContext(Dispatchers.IO) { api.listFulfilmentOrders(active, businessId) }
                    } catch (error: Exception) {
                        fulfilmentNotice = error.message ?: "Could not refresh fulfilment activity."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Refresh fulfilment activity") }
        if (fulfilmentOrders.isEmpty()) {
            Text("No server-side orders found for this business yet.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        fulfilmentOrders.forEach { order ->
            val offerName = fulfilmentOffers.firstOrNull { it.id == order.offerId }?.name ?: "Bundle offer"
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("KES ${order.amountKes}", color = Navy, fontWeight = FontWeight.ExtraBold)
                        Text(order.status.replace('_', ' '), color = if (order.status == "DELIVERED") Cyan else Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(offerName, color = Navy, fontWeight = FontWeight.SemiBold)
                    Text("Recipient: ${order.recipientPhone}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (order.status in setOf("FAILED", "UNKNOWN")) {
                        OutlinedButton(onClick = { pendingRetryOrder = order; retryReason = "" }, modifier = Modifier.fillMaxWidth()) {
                            Text("Reconcile before retry")
                        }
                    }
                }
            }
        }
        Text("Saved menu rules: ${fulfilmentRules.size}", color = Navy, fontWeight = FontWeight.Bold)
        fulfilmentRules.forEach { rule ->
            val offerName = fulfilmentOffers.firstOrNull { it.id == rule.offerId }?.name ?: "Offer"
            InfoCard(
                "KES ${rule.amountMinor / 100} · $offerName",
                "${rule.menuEntryCode} · ${if (rule.isEnabled && rule.verifiedAt != null && rule.verifiedBy != null) "VERIFIED / ENABLED" else "DISABLED / NOT VERIFIED"} · v${rule.version}" +
                    (rule.verificationNote?.let { "\\nManager test: $it" } ?: "")
            )
        }

        OutlinedButton(
            onClick = {
                val active = accountSession
                fulfilmentBusy = true
                scope.launch {
                    try {
                        var refreshed = active ?: throw IllegalStateException("Sign in to register this phone.")
                        if (refreshed.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                            refreshed = withContext(Dispatchers.IO) { api.refreshSession(refreshed) }
                            secureStore.save(refreshed)
                        }
                        val businessId = refreshed.businessId ?: withContext(Dispatchers.IO) { api.firstBusinessId(refreshed) }
                            ?: throw IllegalStateException("No business workspace is connected.")
                        val label = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ").take(100)
                        val device = withContext(Dispatchers.IO) { api.registerCurrentWorkerDevice(refreshed, businessId, label.ifBlank { "BingwaPulse host phone" }) }
                        workerDevices = (workerDevices.filterNot { it.id == device.id } + device)
                        selectedWorkerDevice = device.id
                        fulfilmentNotice = "This phone is registered as an approved worker device."
                    } catch (error: Exception) {
                        fulfilmentNotice = error.message ?: "Could not register this phone as a worker."
                    } finally { fulfilmentBusy = false }
                }
            },
            enabled = !fulfilmentBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Register this phone as a worker") }
        Text("Eligible worker devices: ${workerDevices.size}", color = Navy, fontWeight = FontWeight.Bold)
        workerDevices.forEach { device ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = selectedWorkerDevice == device.id, onClick = { selectedWorkerDevice = device.id })
                Column {
                    Text(device.label, color = Navy, fontWeight = FontWeight.SemiBold)
                    Text("Online · assigned to this account", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val callPermissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        Button(
            onClick = {
                if (!callPermissionGranted) callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                else startFulfilmentWorker()
            },
            enabled = selectedWorkerDevice.isNotBlank() && !fulfilmentBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Start automatic fulfilment worker") }
        OutlinedButton(
            onClick = {
                context.stopService(Intent(context, FulfilmentWorkerService::class.java))
                fulfilmentNotice = "Worker stop requested. Any in-progress USSD result must be reconciled if interrupted."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Stop worker") }
        Text(
            if (callPermissionGranted) "Phone permission is available." else "Phone permission has not been granted.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
        Button(
            onClick = {
                val active = accountSession
                fulfilmentBusy = true
                scope.launch {
                    try {
                        val refreshed = if (active.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L)
                            withContext(Dispatchers.IO) { api.refreshSession(active) } else active
                        val businessId = refreshed.businessId ?: withContext(Dispatchers.IO) { api.firstBusinessId(refreshed) }
                            ?: throw IllegalStateException("No business workspace is connected.")
                        withContext(Dispatchers.IO) {
                            api.setFulfilmentControls(refreshed, businessId, fulfilmentEnabled, fulfilmentPaused, true)
                        }
                        fulfilmentNotice = "Fulfilment controls saved. New worker claims follow these settings."
                    } catch (error: Exception) {
                        fulfilmentNotice = error.message ?: "Could not save fulfilment controls."
                    } finally { fulfilmentBusy = false }
                }
            },
            enabled = !fulfilmentBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (fulfilmentBusy) "Saving…" else "Save fulfilment controls") }
        Text("The worker polls the server while running. It will not retry uncertain transactions automatically. A manager must reconcile UNKNOWN results before any retry.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }

    pendingRetryOrder?.let { order ->
        AlertDialog(
            onDismissRequest = { if (!retryBusy) pendingRetryOrder = null },
            title = { Text("Reconcile order before retry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Check the Safaricom/provider transaction history first. Only retry if you have confirmed the bundle was NOT delivered. This action puts the verified order back into the PAID queue and records your reason.")
                    OutlinedTextField(
                        value = retryReason,
                        onValueChange = { retryReason = it.take(500) },
                        label = { Text("Reconciliation reason (minimum 10 characters)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !retryBusy && retryReason.trim().length >= 10,
                    onClick = {
                        val active = accountSession
                        if (active == null) { fulfilmentNotice = "Sign in again."; return@TextButton }
                        retryBusy = true
                        scope.launch {
                            try {
                                val businessId = active.businessId ?: withContext(Dispatchers.IO) { api.firstBusinessId(active) }
                                    ?: throw IllegalStateException("No business workspace is connected.")
                                withContext(Dispatchers.IO) {
                                    api.retryFulfilmentOrderAfterReconciliation(active, businessId, order.id, retryReason)
                                }
                                fulfilmentOrders = withContext(Dispatchers.IO) { api.listFulfilmentOrders(active, businessId) }
                                fulfilmentNotice = "Order returned to PAID queue after manager reconciliation."
                                pendingRetryOrder = null
                            } catch (error: Exception) {
                                fulfilmentNotice = error.message ?: "Order could not be reconciled."
                            } finally { retryBusy = false }
                        }
                    }
                ) { Text(if (retryBusy) "Saving…" else "I checked — retry order") }
            },
            dismissButton = { TextButton(onClick = { pendingRetryOrder = null }, enabled = !retryBusy) { Text("Cancel") } }
        )
    }
    if (fulfilmentNotice.isNotBlank()) Text(fulfilmentNotice, color = Cyan, fontWeight = FontWeight.SemiBold)

}

@Composable
private fun BingwaDashboard(db: LocalDatabase, onNavigate: (String) -> Unit) {
    var tab by remember { mutableStateOf("Overview") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var airtimeBalance by remember { mutableStateOf<String?>(null) }
    var airtimeResponse by remember { mutableStateOf("Tap refresh to check the Safaricom SIM balance.") }
    var airtimeBusy by remember { mutableStateOf(false) }
    var simOptions by remember { mutableStateOf<List<SubscriptionInfo>>(emptyList()) }
    var selectedSimId by remember { mutableIntStateOf(SubscriptionManager.INVALID_SUBSCRIPTION_ID) }
    var simMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val sessionStore = remember { SecureSessionStore(context) }
    val walletApi = remember { BingwaSupabaseApi() }
    var walletSnapshot by remember { mutableStateOf<PlatformWalletSnapshot?>(null) }
    LaunchedEffect(refreshKey) {
        val saved = sessionStore.load()
        if (saved != null && !sessionStore.isLocalWorkspace()) {
            runCatching {
                val businessId = saved.businessId ?: withContext(Dispatchers.IO) { walletApi.firstBusinessId(saved) }
                    ?: throw IllegalStateException("No business workspace is linked.")
                val active = if (saved.businessId == businessId) saved else saved.copy(businessId = businessId)
                if (saved.businessId == null) sessionStore.save(active)
                withContext(Dispatchers.IO) { walletApi.getPlatformWalletSnapshot(active, businessId) }
            }.onSuccess { walletSnapshot = it }
        }
    }
    val orders = remember(refreshKey) { db.orders() }

    val balancePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) airtimeResponse = "Permissions granted. Tap refresh to check the Safaricom balance."
        else airtimeResponse = "Phone and SIM permissions are needed for a USSD balance check."
    }

    fun checkAirtimeBalance() {
        val needed = arrayOf(android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.CALL_PHONE)
        val missing = needed.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            airtimeResponse = "Allow Phone and SIM information permissions to check the balance."
            balancePermissionLauncher.launch(missing.toTypedArray())
            return
        }
        try {
            val manager = context.getSystemService(SubscriptionManager::class.java)
            val active = manager.activeSubscriptionInfoList.orEmpty()
            simOptions = active
            if (active.isEmpty()) {
                airtimeResponse = "No active SIM was found. Insert or enable a Safaricom SIM and try again."
                return
            }
            val chosenId = if (selectedSimId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
                active.any { it.subscriptionId == selectedSimId }) selectedSimId
                else active.firstOrNull { it.subscriptionId == SubscriptionManager.getDefaultVoiceSubscriptionId() }?.subscriptionId
                    ?: active.first().subscriptionId
            selectedSimId = chosenId
            val telephony = context.getSystemService(TelephonyManager::class.java).createForSubscriptionId(chosenId)
            airtimeBusy = true
            airtimeResponse = "Contacting Safaricom…"
            telephony.sendUssdRequest("*144#", object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager,
                    request: String,
                    response: CharSequence
                ) {
                    airtimeBusy = false
                    val result = response.toString().trim()
                    airtimeBalance = Regex("(?i)(?:KES|KSH|KSHS?)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
                        .find(result)?.groupValues?.getOrNull(1)?.replace(",", "")?.let { "KES $it" }
                    airtimeResponse = if (airtimeBalance != null) result else
                        "Safaricom replied, but the balance format could not be recognised. Carrier response: $result"
                }
                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager,
                    request: String,
                    failureCode: Int
                ) {
                    airtimeBusy = false
                    airtimeResponse = "Safaricom balance check failed (code $failureCode). Check network/SIM and retry."
                }
            }, Handler(Looper.getMainLooper()))
        } catch (error: Exception) {
            airtimeBusy = false
            airtimeResponse = error.message ?: "Could not start the Safaricom balance check."
        }
    }

    val transactions = remember(refreshKey) { db.transactions() }
    val customers = remember(refreshKey) { db.count("customers") }
    val offers = remember(refreshKey) { db.count("offers") }
    val delivered = orders.filter { it.status == "DELIVERED_MANUALLY_RECORDED" }
    val failed = orders.count { it.status == "FAILED" || it.status == "CANCELLED" }
    val pending = orders.count { it.status in setOf("PENDING", "PAYMENT_UNVERIFIED", "QUEUED", "CLAIMED", "USSD_SENT", "PROCESSING") }
    val completed = delivered.size + failed
    val successRate = if (completed > 0) delivered.size * 100 / completed else null
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = if (hour < 12) "Good Morning" else if (hour < 17) "Good Afternoon" else "Good Evening"
    val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    val calendar = java.util.Calendar.getInstance()
    val startToday = (calendar.clone() as java.util.Calendar).apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
    val startWeek = startToday - 6L * 86400000L
    val todayUsage = delivered.filter { it.createdAt >= startToday }.sumOf { it.price }
    val weekUsage = delivered.filter { it.createdAt >= startWeek }.sumOf { it.price }
    val dailyUsage = (0..6).map { day ->
        val from = startWeek + day * 86400000L
        delivered.filter { it.createdAt >= from && it.createdAt < from + 86400000L }.sumOf { it.price }
    }
    val recent = transactions.take(4)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A5F))) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(greeting + ", GABRIEL", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        Text(date, color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(color = Color(0xFF10243A), shape = RoundedCornerShape(50)) {
                        Text("BUSINESS", color = Color(0xFF00F2FE), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Text("SUBSCRIPTION", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(
                    walletSnapshot?.activeSubscription?.let { planName(walletSnapshot?.plans.orEmpty(), it.planCode) }
                        ?: if (walletSnapshot == null) "Loading subscription…" else "No active subscription",
                    color = Color(0xFF00F2FE), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "Tokens: " + (walletSnapshot?.availableTokens?.toString() ?: "—"),
                    color = Color(0xFF00F2FE), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                )
                walletSnapshot?.activeSubscription?.let { active ->
                    Text("Active until " + formatWalletDate(active.endsAt), color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    DashboardMetric("Successful", delivered.size, Color(0xFF4FACFE), Modifier.weight(1f))
                    DashboardMetric("Failed", failed, Color(0xFFE44848), Modifier.weight(1f))
                    DashboardMetric("Pending", pending, Color(0xFFFF9418), Modifier.weight(1f))
                }
                Text("Commission", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                Text("Not available yet", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A5F))) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(color = Color(0xFF10243A), shape = RoundedCornerShape(50)) { Text("▣", color = Color(0xFF00F2FE), modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.headlineSmall) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Airtime Balance", color = Color(0xFF94A3B8))
                    Text(airtimeBalance ?: "KES —", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                    Text("Airtime used today: KES " + money(todayUsage), color = Color(0xFF94A3B8))
                    Text(airtimeResponse, color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    if (simOptions.size > 1) {
                        Box {
                            TextButton(onClick = { simMenuExpanded = true }) {
                                val selectedSim = simOptions.firstOrNull { it.subscriptionId == selectedSimId }
                                Text("SIM: " + (selectedSim?.displayName?.toString() ?: "Choose SIM"), color = Color(0xFF00F2FE))
                            }
                            DropdownMenu(expanded = simMenuExpanded, onDismissRequest = { simMenuExpanded = false }) {
                                simOptions.forEach { sim ->
                                    DropdownMenuItem(
                                        text = { Text(sim.displayName?.toString() ?: "SIM " + sim.simSlotIndex) },
                                        onClick = { selectedSimId = sim.subscriptionId; simMenuExpanded = false; airtimeBalance = null; airtimeResponse = "Selected " + (sim.displayName?.toString() ?: "SIM " + sim.simSlotIndex) + ". Tap refresh to check its balance." }
                                    )
                                }
                            }
                        }
                    }
                }
                IconButton(onClick = { refreshKey++; checkAirtimeBalance() }, enabled = !airtimeBusy) { Text(if (airtimeBusy) "…" else "↻", color = Color(0xFF00F2FE), style = MaterialTheme.typography.headlineLarge) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Overview", "Transactions", "Analytics").forEach { label ->
                TextButton(onClick = { tab = label }, modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(label, color = if (tab == label) Color(0xFF00F2FE) else Color(0xFF94A3B8), fontWeight = if (tab == label) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                        if (tab == label) HorizontalDivider(color = Color(0xFF00F2FE), thickness = 2.dp)
                    }
                }
            }
        }
        when (tab) {
            "Transactions" -> TransactionsScreen(db) { }
            "Analytics" -> {
                Text("Your performance", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardInsightCard("KES " + money(todayUsage), "Airtime used today", Color(0xFF0072FF), Modifier.weight(1f))
                    DashboardInsightCard("—", "Today commission", Color(0xFF4FACFE), Modifier.weight(1f))
                }
                DashboardInsightCard(successRate?.let { it.toString() + "%" } ?: "—", "Success rate", Color(0xFF00A6C7), Modifier.fillMaxWidth())
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Text("Transaction performance", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        DashboardStatusRow("Successful", delivered.size, Color(0xFF4FACFE), orders.size)
                        DashboardStatusRow("Failed", failed, Color(0xFFE44848), orders.size)
                        DashboardStatusRow("Pending", pending, Color(0xFFFF9418), orders.size)
                    }
                }
                Text("Airtime used in the last 7 days: KES " + money(weekUsage), color = Color(0xFF94A3B8))
            }
            else -> {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19))) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Airtime used per day", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("Today KES " + money(todayUsage) + "  •  Last 7 days KES " + money(weekUsage), color = Color(0xFF94A3B8))
                        DashboardUsageChart(dailyUsage)
                        Text("Calculated from locally recorded delivered orders.", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19))) {
                    Column(Modifier.fillMaxWidth().height(155.dp).padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("This week commission", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("No verified commission records available yet", color = Color(0xFF94A3B8)) }
                    }
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Business snapshot", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardMetric("Orders", orders.size, Color(0xFF00F2FE), Modifier.weight(1f))
                            DashboardMetric("Customers", customers, Color(0xFF00F2FE), Modifier.weight(1f))
                            DashboardMetric("Offers", offers, Color(0xFF00F2FE), Modifier.weight(1f))
                        }
                        TextButton(onClick = { onNavigate("Orders") }) { Text("View orders", color = Color(0xFF00F2FE)) }
                    }
                }
                if (recent.isNotEmpty()) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Recent transactions", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        recent.forEach { tx ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(tx.reference.ifBlank { "Order #" + tx.orderId }, color = Color.White, modifier = Modifier.weight(1f))
                                Text("KES " + money(tx.amount), color = Color(0xFF00F2FE), fontWeight = FontWeight.SemiBold)
                            }
                            Text(tx.status + " · " + dateText(tx.createdAt), color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardInsightCard(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, color = accent, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
            Text(label, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DashboardStatusRow(label: String, count: Int, accent: Color, total: Int) {
    val fraction = if (total > 0) count.toFloat() / total.toFloat() else 0f
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Color.White, modifier = Modifier.width(100.dp))
        Box(Modifier.weight(1f).height(9.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1E293B))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(accent, RoundedCornerShape(20.dp)))
        }
        Text(count.toString(), color = accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DashboardUsageChart(values: List<Double>) {
    val labels = listOf("6d", "5d", "4d", "3d", "2d", "1d", "Today")
    val maxValue = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(165.dp)) {
            val left = 18.dp.toPx(); val right = size.width - 10.dp.toPx(); val top = 12.dp.toPx(); val bottom = size.height - 18.dp.toPx()
            for (i in 0..3) { val y = top + (bottom - top) * i / 3f; drawLine(Color(0xFF334155), androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y), strokeWidth = 1.dp.toPx()) }
            val points = values.mapIndexed { index, value -> androidx.compose.ui.geometry.Offset(left + (right - left) * index / (values.size - 1).coerceAtLeast(1), bottom - (value.toFloat() / maxValue.toFloat()) * (bottom - top)) }
            for (i in 0 until points.lastIndex) drawLine(Color(0xFF0072FF), points[i], points[i + 1], strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            points.forEach { drawCircle(Color(0xFF0072FF), 4.dp.toPx(), it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { labels.forEach { Text(it, color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall) } }
    }
}
