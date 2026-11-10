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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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

private val WalletAccent = Color(0xFF36B3EB)
private val WalletPanel = Color(0xFF111B38)
private val WalletMuted = Color(0xFF91A4C7)

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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Platform Wallet",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                "Subscriptions and USSD tokens for your BingwaPulse workspace",
                color = WalletMuted,
                style = MaterialTheme.typography.bodySmall
            )
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
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF14234A))
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("YOUR PLATFORM WALLET", color = WalletAccent, fontWeight = FontWeight.ExtraBold)
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
                                    Text("AVAILABLE TOKENS", color = WalletMuted, style = MaterialTheme.typography.labelSmall)
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
                        colors = CardDefaults.cardColors(containerColor = WalletPanel)
                    ) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(plan.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("KES ${plan.priceKes}", color = WalletAccent, fontWeight = FontWeight.ExtraBold)
                            }
                            Text(plan.description, color = WalletMuted, style = MaterialTheme.typography.bodySmall)
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

        item {
            WalletInfoCard(
                "Checkout is not activated",
                "The plans and token packages are now listed from the platform catalogue. Payment collection and wallet top-ups remain disabled until you approve and configure a verified payment method. No money is charged from this screen."
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
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = WalletAccent, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
        Text(subtitle, color = WalletMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WalletInfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WalletPanel)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(body, color = WalletMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun planName(plans: List<PlatformSubscriptionPlan>, code: String): String =
    plans.firstOrNull { it.code == code }?.name ?: code.replace('_', ' ').uppercase()

private fun formatWalletDate(value: String): String =
    runCatching {
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.of("Africa/Nairobi"))
            .format(Instant.parse(value))
    }.getOrDefault(value)
