package com.telvanta.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

data class PlatformSubscriptionPlan(
    val code: String,
    val name: String,
    val durationSeconds: Long,
    val priceKes: Int,
    val description: String
)

data class PlatformTokenPackage(
    val code: String,
    val tokens: Int,
    val priceKes: Int
)

data class PlatformSubscriptionRecord(
    val id: String,
    val planCode: String,
    val status: String,
    val startsAt: String,
    val endsAt: String
)

data class PlatformTokenLedgerEntry(
    val id: String,
    val entryType: String,
    val tokensDelta: Int,
    val sourceType: String,
    val note: String?,
    val createdAt: String
)

data class PlatformWalletSnapshot(
    val plans: List<PlatformSubscriptionPlan>,
    val packages: List<PlatformTokenPackage>,
    val subscriptions: List<PlatformSubscriptionRecord>,
    val tokenEntries: List<PlatformTokenLedgerEntry>,
    val tokenBalance: Long,
    val reservedTokens: Long
) {
    val availableTokens: Long get() = (tokenBalance - reservedTokens).coerceAtLeast(0)
    val activeSubscription: PlatformSubscriptionRecord?
        get() = subscriptions.firstOrNull {
            it.status == "active" &&
                runCatching { Instant.parse(it.startsAt).isBefore(Instant.now().plusSeconds(1)) }.getOrDefault(false) &&
                runCatching { Instant.parse(it.endsAt).isAfter(Instant.now()) }.getOrDefault(false)
        }
}

private val WalletAccent = Color(0xFF00F2FE)
private val WalletBlue = Color(0xFF0072FF)
private val WalletPanel = Color(0xFF0B0F19)
private val WalletMuted = Color(0xFF94A3B8)
private val WalletBorder = Color(0xFF1E3A5F)

@Composable
internal fun WalletScreen() {
    val context = LocalContext.current
    val store = remember { SecureSessionStore(context) }
    val api = remember { BingwaSupabaseApi() }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(store.load()) }
    var snapshot by remember { mutableStateOf<PlatformWalletSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var paymentNotice by remember { mutableStateOf("") }
    var paymentBusy by remember { mutableStateOf(false) }
    var pendingPlan by remember { mutableStateOf<PlatformSubscriptionPlan?>(null) }
    val processAirtimePayment: (PlatformSubscriptionPlan) -> Unit = { plan ->
        paymentBusy = true
        paymentNotice = "Preparing your subscription request..."
        scope.launch {
            try {
                val current = session ?: throw IllegalStateException("Sign in before purchasing a subscription.")
                val businessId = current.businessId
                    ?: withContext(Dispatchers.IO) { api.firstBusinessId(current) }
                    ?: throw IllegalStateException("No business workspace is linked to this account.")
                val activeSession = if (current.businessId == businessId) current else current.copy(businessId = businessId)
                if (activeSession !== current) {
                    session = activeSession
                    store.save(activeSession)
                }
                val attemptId = withContext(Dispatchers.IO) {
                    api.createSambazaSubscriptionIntent(activeSession, businessId, plan.code)
                }
                paymentNotice = "Processing airtime payment..."
                requestAirtimeSubscription(context, plan.priceKes) { result ->
                    scope.launch {
                        try {
                            when (result) {
                                is AirtimeSubscriptionResult.Response -> {
                                    val confirmation = parseSambazaConfirmation(result.message)
                                    val matches = confirmation != null &&
                                        confirmation.recipient == SAMBAZA_RECEIVER &&
                                        kotlin.math.abs(confirmation.amountKes - plan.priceKes.toDouble()) < 0.01
                                    if (!matches) {
                                        val resultNotice = describeAirtimeResult(result, plan.priceKes)
                                        if (resultNotice.startsWith("INSUFFICIENT:")) {
                                            withContext(Dispatchers.IO) {
                                                api.cancelSambazaSubscriptionIntent(activeSession, businessId, attemptId)
                                            }
                                            paymentNotice = resultNotice.removePrefix("INSUFFICIENT:")
                                        } else {
                                            paymentNotice = "Safaricom's response could not be verified against the selected amount and destination. Do not retry yet; check the airtime balance and transfer history first. No subscription was activated."
                                        }
                                    } else {
                                        val activationStatus = withContext(Dispatchers.IO) {
                                            api.recordSambazaSenderResponse(
                                                activeSession, businessId, attemptId, result.message
                                            )
                                        }
                                        if (activationStatus == "active") {
                                            snapshot = withContext(Dispatchers.IO) {
                                                api.getPlatformWalletSnapshot(activeSession, businessId)
                                            }
                                            paymentNotice = "Subscription active. The sender-side Safaricom confirmation was verified and your business wallet has been updated."
                                        } else {
                                            paymentNotice = "The sender confirmation was received, but the server did not confirm activation. Do not retry yet; check the airtime balance and transfer history."
                                        }
                                    }
                                }
                                is AirtimeSubscriptionResult.Failed -> {
                                    withContext(Dispatchers.IO) {
                                        api.cancelSambazaSubscriptionIntent(activeSession, businessId, attemptId)
                                    }
                                    paymentNotice = describeAirtimeResult(result, plan.priceKes)
                                }
                                is AirtimeSubscriptionResult.Error -> {
                                    paymentNotice = "The transfer outcome is unconfirmed. Check the receiving account before trying again. " + result.message
                                }
                            }
                        } catch (error: Exception) {
                            paymentNotice = error.message ?: "The transfer result could not be saved. Check the receiving account before retrying."
                        } finally {
                            paymentBusy = false
                        }
                    }
                }
            } catch (error: Exception) {
                paymentBusy = false
                paymentNotice = error.message ?: "Could not prepare the subscription request."
            }
        }
    }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val plan = pendingPlan
        pendingPlan = null
        if (granted && plan != null) {
            processAirtimePayment(plan)
        } else if (!granted) {
            paymentNotice = "Allow phone access so BingwaPulse can process the airtime payment."
        }
    }
    val startAirtimePayment: (PlatformSubscriptionPlan) -> Unit = { plan ->
        paymentNotice = ""
        pendingPlan = plan
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
        ) {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        } else {
            pendingPlan = null
            processAirtimePayment(plan)
        }
    }
    val localPreview = remember { store.isLocalWorkspace() }

    fun refreshWallet() {
        val current = session
        if (current == null) {
            notice = "Sign in to load your business wallet."
            return
        }
        scope.launch {
            loading = true
            notice = ""
            try {
                val businessId = current.businessId
                    ?: withContext(Dispatchers.IO) { api.firstBusinessId(current) }
                    ?: throw IllegalStateException("No business workspace is linked to this account.")
                val activeSession = if (current.businessId == businessId) current else current.copy(businessId = businessId)
                if (activeSession !== current) {
                    session = activeSession
                    store.save(activeSession)
                }
                snapshot = withContext(Dispatchers.IO) {
                    api.getPlatformWalletSnapshot(activeSession, businessId)
                }
                notice = "Wallet data refreshed."
            } catch (error: Exception) {
                notice = error.message ?: "Could not load the platform wallet."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!localPreview) refreshWallet()
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Platform Wallet",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineMedium
            )
            Text("BALANCE · PLANS · ACTIVITY", color = WalletMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }

        if (localPreview) {
            item {
                WalletInfoCard(
                    "Preview workspace",
                    "This is local preview mode. Sign in with a business account to load real subscription status, token balances and wallet activity."
                )
            }
        } else if (session == null) {
            item {
                WalletInfoCard("Sign-in required", "Your platform wallet is available after you sign in to your BingwaPulse business account.")
            }
        } else if (loading && snapshot == null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(color = WalletAccent)
                    Text("Loading your wallet…", color = WalletMuted)
                }
            }
        } else {
            val wallet = snapshot
            if (wallet == null) {
                item {
                    WalletInfoCard("Wallet not loaded", notice.ifBlank { "Refresh to try loading your wallet." })
                    OutlinedButton(onClick = { refreshWallet() }, enabled = !loading) {
                        Text(if (loading) "Refreshing…" else "Refresh wallet")
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WalletPanel),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WalletAccent.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("WALLET BALANCE", color = WalletAccent, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
                            Text(
                                wallet.activeSubscription?.let { planName(wallet.plans, it.planCode) } ?: "No active subscription",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                wallet.activeSubscription?.let { "Active until ${formatWalletDate(it.endsAt)}" }
                                    ?: "Choose a subscription below for unlimited USSD access.",
                                color = WalletMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column {
                                    Text("AVAILABLE", color = WalletMuted, style = MaterialTheme.typography.labelSmall)
                                    Text(wallet.availableTokens.toString(), color = WalletAccent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                                }
                                Column {
                                    Text("RESERVED", color = WalletMuted, style = MaterialTheme.typography.labelSmall)
                                    Text(wallet.reservedTokens.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                                }
                            }
                        }
                    }
                }

                item {
                    WalletSectionTitle("SUBSCRIPTIONS", "An active plan covers USSD requests without using tokens.")
                }
                items(wallet.plans, key = { "plan_${it.code}" }) { plan ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WalletPanel),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WalletBorder)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(plan.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("KES ${plan.priceKes}", color = WalletAccent, fontWeight = FontWeight.ExtraBold)
                            }
                            Text(plan.description, color = WalletMuted, style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { startAirtimePayment(plan) },
                                enabled = !paymentBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (paymentBusy) "Processing airtime payment…" else "Pay KES ${plan.priceKes} with Airtime")
                            }
                        }
                    }
                }

                item {
                    WalletSectionTitle("USSD TOKEN PACKAGES", "Tokens are separate from subscriptions and remain available after a plan expires.")
                }
                items(wallet.packages, key = { "pack_${it.code}" }) { pack ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WalletPanel)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(15.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${pack.tokens} USSD tokens", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Prepaid request balance", color = WalletMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("KES ${pack.priceKes}", color = WalletAccent, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                item {
                    WalletSectionTitle("RECENT TOKEN ACTIVITY", "A token is charged only after a successful USSD delivery.")
                    if (wallet.tokenEntries.isEmpty()) {
                        WalletInfoCard("No token activity yet", "Token credits and successful-use deductions will appear here.")
                    }
                }
                items(wallet.tokenEntries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WalletPanel)
                    ) {
                        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(entry.note ?: entry.sourceType.replace('_', ' ').uppercase(), color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (entry.tokensDelta > 0) "+${entry.tokensDelta}" else entry.tokensDelta.toString(),
                                    color = if (entry.tokensDelta > 0) WalletAccent else Color(0xFFFFB4AB),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text("${entry.entryType.uppercase()} · ${formatWalletDate(entry.createdAt)}", color = WalletMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (paymentNotice.isNotBlank()) {
            item {
                WalletInfoCard(
                    if (paymentNotice.startsWith("INSUFFICIENT:")) "Insufficient Airtime" else "Airtime Payment Status",
                    paymentNotice.removePrefix("INSUFFICIENT:")
                )
            }
        }
        item {
            WalletInfoCard(
                "Airtime payment verification",
                "Your subscription activates only after Safaricom confirms the exact amount and intended recipient. An unconfirmed transfer does not activate a plan."
            )
        }
        if (notice.isNotBlank() && !localPreview) {
            item { Text(notice, color = WalletAccent, style = MaterialTheme.typography.bodySmall) }
        }
        if (snapshot != null && !localPreview) {
            item {
                OutlinedButton(onClick = { refreshWallet() }, enabled = !loading) {
                    Text(if (loading) "Refreshing…" else "Refresh wallet")
                }
            }
        }
    }
}

@Composable
private fun WalletSectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = WalletAccent, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
        Text(subtitle, color = WalletMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
}

@Composable
private fun WalletInfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WalletPanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, WalletBorder)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(body, color = WalletMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun planName(plans: List<PlatformSubscriptionPlan>, code: String): String =
    plans.firstOrNull { it.code == code }?.name ?: code.replace('_', ' ').uppercase()

internal fun formatWalletDate(value: String): String =
    runCatching {
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.of("Africa/Nairobi"))
            .format(Instant.parse(value))
    }.getOrDefault(value)


private sealed interface AirtimeSubscriptionResult {
    data class Response(val message: String) : AirtimeSubscriptionResult
    data class Failed(val code: Int) : AirtimeSubscriptionResult
    data class Error(val message: String) : AirtimeSubscriptionResult
}

private fun requestAirtimeSubscription(
    context: Context,
    amountKes: Int,
    onResult: (AirtimeSubscriptionResult) -> Unit
) {
    if (amountKes <= 0) {
        onResult(AirtimeSubscriptionResult.Error("Invalid subscription amount."))
        return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        onResult(AirtimeSubscriptionResult.Error("This Android version does not support background USSD requests."))
        return
    }
    val callbackHandler = Handler(Looper.getMainLooper())
    val completed = AtomicBoolean(false)
    val timeout = Runnable {
        if (completed.compareAndSet(false, true)) {
            onResult(
                AirtimeSubscriptionResult.Error(
                    "Safaricom did not return a response in time. The transfer outcome is unconfirmed. Check the airtime balance and receiving account before trying again."
                )
            )
        }
    }
    fun finish(result: AirtimeSubscriptionResult) {
        if (completed.compareAndSet(false, true)) {
            callbackHandler.removeCallbacks(timeout)
            onResult(result)
        }
    }

    try {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
            throw IllegalStateException("This device does not support SIM-based USSD requests.")
        }
        // Android documents this getter without a READ_PHONE_STATE permission requirement.
        // CALL_PHONE remains the permission required by sendUssdRequest.
        val defaultVoiceSubscriptionId = SubscriptionManager.getDefaultVoiceSubscriptionId()
        if (defaultVoiceSubscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            throw IllegalStateException("No default voice SIM is configured. Set your Safaricom SIM as the default voice SIM and try again.")
        }
        val telephony = context.getSystemService(TelephonyManager::class.java)
            ?.createForSubscriptionId(defaultVoiceSubscriptionId)
            ?: throw IllegalStateException("Telephony service is unavailable.")
        // Internal network instruction: never display or log this value in the agent UI.
        val request = "*140*${amountKes}*0140980957#"
        callbackHandler.postDelayed(timeout, 30_000L)
        telephony.sendUssdRequest(
            request,
            object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager,
                    request: String,
                    response: CharSequence
                ) {
                    finish(AirtimeSubscriptionResult.Response(response.toString()))
                }

                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager,
                    request: String,
                    failureCode: Int
                ) {
                    finish(AirtimeSubscriptionResult.Failed(failureCode))
                }
            },
            callbackHandler
        )
    } catch (error: SecurityException) {
        finish(AirtimeSubscriptionResult.Error("Android denied permission to send the USSD request."))
    } catch (error: Exception) {
        finish(AirtimeSubscriptionResult.Error(error.message ?: "Could not start the airtime request."))
    }
}

private const val SAMBAZA_RECEIVER = "0140980957"

private data class ParsedSambazaConfirmation(
    val amountKes: Double,
    val recipient: String
)

private fun parseSambazaConfirmation(message: String): ParsedSambazaConfirmation? {
    val match = Regex(
        """(?i)transferred\s+([0-9]+(?:\.[0-9]{1,2})?)\s*KSH.*?\bto\s+(0[0-9]{9,11})\b"""
    ).find(message) ?: return null
    val amount = match.groupValues[1].toDoubleOrNull() ?: return null
    val recipient = match.groupValues[2]
    return ParsedSambazaConfirmation(amountKes = amount, recipient = recipient)
}

private fun describeAirtimeResult(result: AirtimeSubscriptionResult, expectedAmountKes: Int): String = when (result) {
    is AirtimeSubscriptionResult.Response -> {
        val normalized = result.message.lowercase()
        when {
            listOf("insufficient airtime", "not enough airtime", "insufficient balance", "low balance")
                .any { normalized.contains(it) } ->
                "INSUFFICIENT:You don't have enough Safaricom airtime for this subscription. Top up and try again."
            listOf("confirm", "press 1", "reply 1", "select 1", "are you sure")
                .any { normalized.contains(it) } ->
                "Safaricom requires another confirmation step. No subscription has been activated."
            else -> {
                val confirmation = parseSambazaConfirmation(result.message)
                when {
                    confirmation == null ->
                        "Safaricom returned a response that BingwaPulse could not verify. No subscription has been activated."
                    confirmation.recipient != SAMBAZA_RECEIVER ->
                        "The airtime transfer destination did not match the subscription receiver. No subscription has been activated."
                    kotlin.math.abs(confirmation.amountKes - expectedAmountKes.toDouble()) >= 0.01 ->
                        "The transferred amount did not match the selected plan. No subscription has been activated."
                    else ->
                        "Airtime transfer initiated. Verifying the receiving account before activating your subscription."
                }
            }
        }
    }
    is AirtimeSubscriptionResult.Failed ->
        "The Safaricom USSD request did not complete. No subscription has been activated. Check the airtime balance and receiving account before trying again."
    is AirtimeSubscriptionResult.Error -> result.message
}
