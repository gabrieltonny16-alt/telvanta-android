package com.telvanta.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Navy = Color(0xFF101B35)
private val Teal = Color(0xFF20B995)
private val Muted = Color(0xFF69758A)

class MainActivity : ComponentActivity() {
    private lateinit var localDb: LocalDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localDb = LocalDatabase(this)
        setContent { TelvantaApp(localDb) }
    }
    override fun onDestroy() {
        if (::localDb.isInitialized) localDb.close()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelvantaApp(db: LocalDatabase) {
    var selected by remember { mutableStateOf("Dashboard") }
    var refresh by remember { mutableIntStateOf(0) }
    val tabs = listOf("Dashboard", "Offers", "Orders", "Customers", "Transactions", "Settings")
    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Teal, background = Color(0xFFF5F7FB))) {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("Telvanta", fontWeight = FontWeight.Bold)
                        Text("Your Bundles. Your Business. Automated.", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White))
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    tabs.take(4).forEach { tab ->
                        NavigationBarItem(selected = selected == tab, onClick = { selected = tab }, icon = {
                            Icon(when (tab) {
                                "Dashboard" -> Icons.Default.Dashboard
                                "Offers" -> Icons.Default.Inventory2
                                "Orders" -> Icons.Default.ReceiptLong
                                else -> Icons.Default.People
                            }, contentDescription = tab)
                        }, label = { Text(tab) })
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 18.dp)
            ) {
                item {
                    Surface(color = Teal.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium) {
                        Text("OFFLINE-READY FOUNDATION", color = Teal, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    when (selected) {
                        "Dashboard" -> {
                            Text("Good to see you.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Your workspace is stored on this device.", color = Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatCard("Offers", db.count("offers"), Modifier.weight(1f))
                                StatCard("Orders", db.count("orders"), Modifier.weight(1f))
                                StatCard("Customers", db.count("customers"), Modifier.weight(1f))
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = Navy), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Built for your business", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("Save your catalogue and keep records without relying on an internet connection.", color = Color(0xFFDCE4F2))
                                }
                            }
                        }
                        "Offers" -> {
                            Text("Offer catalogue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Add offers manually. Signature Offer downloads and source verification are planned.", color = Muted)
                            var name by remember { mutableStateOf("") }
                            var category by remember { mutableStateOf("Data") }
                            var price by remember { mutableStateOf("") }
                            OutlinedTextField(name, { name = it }, label = { Text("Offer name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(category, { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(price, { price = it }, label = { Text("Your selling price (KES)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Button(onClick = {
                                val amount = price.toDoubleOrNull()
                                if (name.isNotBlank() && category.isNotBlank() && amount != null && amount >= 0) {
                                    db.addOffer(name, category, null, amount)
                                    name = ""; price = ""; refresh++
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Save offer on this device") }
                            Text("Saved offers: ${db.count("offers")}", color = Muted)
                        }
                        "Orders" -> {
                            Text("Orders", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            InfoCard("No live purchases", "Order creation will follow after the safe order state machine is implemented. No USSD or payment actions are enabled.")
                        }
                        "Customers" -> {
                            Text("Customers", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            InfoCard("Customer records", "Customer management is planned for the next build step. Records will be stored locally and separated by agent.")
                        }
                        "Transactions" -> {
                            Text("Transactions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            InfoCard("No payment processing enabled", "No STK Push, payment collection, or live financial integration is included.")
                        }
                        else -> {
                            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            InfoCard("Connection status", "Local storage is available. Telvanta Connect pairing and remote sync are not implemented yet.")
                            InfoCard("Safety controls", "Live purchases and live messaging remain disabled.")
                        }
                    }
                }
                item { Text("Foundation build · v0.1.0 · Local-only data", color = Muted, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
            Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}
@Composable
private fun InfoCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = Muted)
        }
    }
}
