package com.telvanta.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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

private val Navy = Color(0xFFE7FFF2)
private val NavyDeep = Color(0xFF03130D)
private val Teal = Color(0xFF28D879)
private val Muted = Color(0xFF9CB9AA)
private val Canvas = Color(0xFF061A12)

class MainActivity : ComponentActivity() {
    private lateinit var localDb: LocalDatabase
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); localDb = LocalDatabase(this); setContent { TelvantaApp(localDb) } }
    override fun onDestroy() { if (::localDb.isInitialized) localDb.close(); super.onDestroy() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelvantaApp(db: LocalDatabase) {
    var selected by remember { mutableStateOf("Dashboard") }
    var message by remember { mutableStateOf("") }
    val tabs = listOf(
        "Dashboard", "Wallet", "Offers", "Transaction History", "Quick Dial",
        "Auto Renewals", "Auto-Reply", "Customers", "Signature", "Statistics",
        "Vecta Connect", "Settings", "Updates"
    )
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFF28D879),secondary=Color(0xFFD9FF24),background=Canvas,surface=Color(0xFF102B20)), shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp),extraLarge=RoundedCornerShape(30.dp))) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Color(0xFF071F15), drawerContentColor = Color.White) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("BINGWA VECTA", color = Color(0xFFD9FF24), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        Text("POWER EVERY CONNECTION", color = Muted, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(12.dp))
                    }
                    HorizontalDivider(color = Color(0xFF234735))
                    tabs.forEach { tab ->
                        NavigationDrawerItem(
                            label = { Text(tab, fontWeight = if (selected == tab) FontWeight.Bold else FontWeight.Normal) },
                            selected = selected == tab,
                            onClick = { selected = tab; message = ""; scope.launch { drawerState.close() } },
                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFF17432F), selectedTextColor = Color(0xFFD9FF24), unselectedTextColor = Color(0xFFE7FFF2)),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Offline core · local records", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }
        ) {
        Scaffold(containerColor=Canvas,topBar={ TopAppBar(
            navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Text("≡", color = Color(0xFFD9FF24), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium) } },
            title={Column(verticalArrangement=Arrangement.spacedBy(2.dp)) { Text("Bingwa Vecta",fontWeight=FontWeight.ExtraBold,color=Color.White,letterSpacing=.5.sp,style=MaterialTheme.typography.titleLarge); Text(selected.uppercase(),style=MaterialTheme.typography.labelSmall,color=Color(0xFFB9F227),letterSpacing=1.2.sp) }},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=NavyDeep)
        ) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(vertical=16.dp)) {
                item { Surface(color=Color(0xFF12382A),shape=MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=11.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) { Surface(color=Teal,shape=MaterialTheme.shapes.small) { Text("●",color=Color.White,modifier=Modifier.padding(horizontal=7.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall) }; Column { Text("OFFLINE-READY WORKSPACE",color=Navy,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelMedium); Text("Your records stay on this device",color=Muted,style=MaterialTheme.typography.bodySmall) } } } }
                item {
                    when(selected) {
                        "Dashboard" -> {
                            Surface(color=NavyDeep,shape=MaterialTheme.shapes.extraLarge,modifier=Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
                                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Surface(color=Color(0xFF0D6B4B),shape=RoundedCornerShape(50)) { Text("●  READY OFFLINE",color=Color(0xFFB9F227),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=11.dp,vertical=7.dp)) }
                                        Spacer(Modifier.weight(1f))
                                        Text("v1 · LOCAL",color=Color(0xFFB7C8C1),style=MaterialTheme.typography.labelSmall)
                                    }
                                    Text("Your telecom business,\nunder control.",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold,color=Color.White,lineHeight=34.sp)
                                    Text("Offers, customers, orders and records — organised in one workspace.",color=Color(0xFFD2E2DB),style=MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                        Surface(color=Color(0xFFB9F227),shape=RoundedCornerShape(50)) { Text("LOCAL DATA",color=NavyDeep,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=10.dp,vertical=7.dp)) }
                                        Text("No live payments or fulfilment",color=Color(0xFFB7C8C1),style=MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Text("Workspace overview",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Navy)
                            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                StatCard("OFFERS",db.count("offers"),Modifier.weight(1f))
                                StatCard("ORDERS",db.count("orders"),Modifier.weight(1f))
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                StatCard("CUSTOMERS",db.count("customers"),Modifier.weight(1f))
                                StatCard("TRANSACTIONS",db.count("transactions"),Modifier.weight(1f))
                            }
                            Text("Quick actions",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Navy)
                            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                QuickAction("＋","Add offer",Modifier.weight(1f)){selected="Offers"}
                                QuickAction("＋","Customer",Modifier.weight(1f)){selected="Customers"}
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                QuickAction("↗","New order",Modifier.weight(1f)){selected="Orders"}
                                QuickAction("≡","Ledger",Modifier.weight(1f)){selected="Transactions"}
                            }
                            InfoCard("Clear and safe by design","Saved records are not proof of payment or bundle delivery.")
                            Button(onClick={selected="Orders"},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(vertical=16.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF087F5B),contentColor=Color.White)){Text("OPEN ORDER BOOK  →",fontWeight=FontWeight.Bold)}
                        }
                        "Offers" -> OffersScreen(db){message=it}
                        "Customers" -> CustomersScreen(db){message=it}
                        "Orders" -> OrdersScreen(db){message=it}
                        "Transactions", "Transaction History" -> TransactionsScreen(db){message=it}
                        "Auto-Reply" -> AutoReplyScreen()
                        "Statistics" -> StatisticsScreen(db)
                        "Wallet" -> PlannedFeatureScreen("Wallet", "Wallet balance and subscription tokens are not connected yet.")
                        "Quick Dial" -> PlannedFeatureScreen("Quick Dial", "SIM selection and USSD actions are not implemented. No calls or USSD sessions will be started.")
                        "Auto Renewals" -> PlannedFeatureScreen("Auto Renewals", "Renewal scheduling is not implemented yet.")
                        "Signature" -> PlannedFeatureScreen("Signature", "Signature import and agent signature management are not implemented yet.")
                        "Vecta Connect" -> PlannedFeatureScreen("Vecta Connect", "Phone pairing and sync are not implemented yet.")
                        "Updates" -> PlannedFeatureScreen("Updates", "In-app update checking is not implemented yet.")
                        else -> {
                            Text("Settings",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                            InfoCard("Workspace","Bingwa Vecta stores records locally on this phone. Multi-agent accounts and encrypted sync are not implemented yet.")
                            InfoCard("Live integrations","M-PESA collection, SMS, USSD and real bundle fulfilment are disabled. Do not treat manual records as verified payments.")
                            InfoCard("Data safety","Keep this device secure. Local records are not yet synced or backed up to another device.")
                        }
                    }
                }
                if(message.isNotBlank()) item { Text(message,color=Teal,fontWeight=FontWeight.SemiBold) }
                item { Text("Bingwa Vecta · offline core · local-only data",color=Muted,style=MaterialTheme.typography.labelSmall) }
            }
        }
        }
    }
}

@Composable
private fun PlannedFeatureScreen(title: String, details: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Navy)
    InfoCard("Not implemented yet", details)
    InfoCard("Build status", "This menu item is shown so the workspace is organized, but it is not an active integration. We will implement and test it before calling it ready.")
}

@Composable
private fun OffersScreen(db: LocalDatabase,onMessage:(String)->Unit) {
    var editId by remember { mutableStateOf<Long?>(null) }; var name by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Data") }; var price by remember { mutableStateOf("") }; var originalPrice by remember { mutableStateOf("") }; var source by remember { mutableStateOf("Manual") }; var verified by remember { mutableStateOf(false) }; var importText by remember { mutableStateOf("") }; var reload by remember { mutableIntStateOf(0) }
    Text("Offer catalogue",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
    Text("Create offers or tap Edit to change a saved offer.",color=Muted)
    OutlinedTextField(name,{name=it},label={Text("Offer name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(category,{category=it},label={Text("Category: Data, Minutes, SMS, Combo")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(price,{price=it},label={Text("Selling price (KES)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(originalPrice,{originalPrice=it},label={Text("Original/source price (optional, KES)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(source,{source=it},label={Text("Offer source (e.g. Manual, Signature)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Checkbox(checked=verified,onCheckedChange={verified=it}); Text("Marked verified by operator") }
    Text("Bulk import offers",fontWeight=FontWeight.Bold)
    Text("One offer per line: name | category | original price or blank | selling price | source. Imported offers remain unverified.",color=Muted,style=MaterialTheme.typography.bodySmall)
    OutlinedTextField(importText,{importText=it},label={Text("Paste offer rows")},modifier=Modifier.fillMaxWidth(),minLines=3)
    OutlinedButton(onClick={
        val lines=importText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val parsed=lines.map { line ->
            val parts=line.split("|").map { it.trim() }
            val original=parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
            val selling=parts.getOrNull(3)?.toDoubleOrNull()
            if(parts.size!=5 || parts[0].isBlank() || parts[1].isBlank() || parts[4].isBlank() || (parts[2].isNotBlank()&&original==null) || original?.let { it<0 }==true || selling==null || selling<0) null
            else ImportedOffer(parts[0],parts[1],original,selling,parts[4])
        }
        if(lines.isEmpty() || parsed.any { it==null }) onMessage("Import stopped: check every line has 5 fields separated by | and valid prices.")
        else try { val count=db.importOffers(parsed.filterNotNull());importText="";reload++;onMessage("Imported $count offers locally. All are marked unverified.") }
        catch(_:Exception) { onMessage("Import failed; no partial import was saved.") }
    },modifier=Modifier.fillMaxWidth()){Text("Import offers")}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick={val amount=price.toDoubleOrNull(); val original=if(originalPrice.isBlank()) null else originalPrice.toDoubleOrNull()
            if(name.isBlank()||category.isBlank()||amount==null||amount<0||source.isBlank()||(!originalPrice.isBlank()&&(original==null||original<0))) onMessage("Enter valid offer details and prices.")
            else try {db.saveOffer(editId,name,category,amount,original,source,verified);name="";category="Data";price="";originalPrice="";source="Manual";verified=false;editId=null;reload++;onMessage("Offer saved locally; verification is an operator-recorded flag.")}
            catch(_:Exception){onMessage("Could not save offer. Check details.")}
        },modifier=Modifier.weight(1f)){Text(if(editId==null)"Save offer" else "Update offer")}
        OutlinedButton(onClick={editId=null;name="";category="Data";price="";originalPrice="";source="Manual";verified=false}){Text("Clear")}
    }
    val offers=remember(reload){db.offers()}
    Text("Saved offers (${offers.size})",fontWeight=FontWeight.Bold)
    offers.forEach { o -> Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF12382A)),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(o.name,fontWeight=FontWeight.Bold);Text("${o.category} · Selling KES ${money(o.price)}",color=Muted)
            Text("Source: ${o.source} · ${if(o.verified) "Marked verified by operator" else "Unverified"}",color=Muted,style=MaterialTheme.typography.bodySmall)
            Text("Original price: ${o.originalPrice?.let { "KES ${money(it)}" } ?: "Not recorded"}",color=Muted,style=MaterialTheme.typography.bodySmall)
            Row { TextButton(onClick={editId=o.id;name=o.name;category=o.category;price=o.price.toString();originalPrice=o.originalPrice?.toString() ?: "";source=o.source;verified=o.verified}){Text("Edit")};TextButton(onClick={db.deleteOffer(o.id);reload++;onMessage("Offer deleted.")}){Text("Delete")} }
        }
    } }
}

@Composable
private fun CustomersScreen(db:LocalDatabase,onMessage:(String)->Unit) {
    var editId by remember { mutableStateOf<Long?>(null) }; var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var search by remember { mutableStateOf("") }; var reload by remember { mutableIntStateOf(0) }
    Text("Customers",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
    OutlinedTextField(name,{name=it},label={Text("Customer name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(phone,{phone=it},label={Text("Phone number")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(notes,{notes=it},label={Text("Notes (optional)")},modifier=Modifier.fillMaxWidth())
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick={if(name.isBlank()||phone.isBlank()) onMessage("Enter customer name and phone number.")
            else try{db.saveCustomer(editId,name,phone,notes);name="";phone="";notes="";editId=null;reload++;onMessage("Customer saved locally.")}
            catch(_:Exception){onMessage("Could not save: phone number may already be used.")}
        },modifier=Modifier.weight(1f)){Text(if(editId==null)"Save customer" else "Update customer")}
        OutlinedButton(onClick={editId=null;name="";phone="";notes=""}){Text("Clear")}
    }
    OutlinedTextField(search,{search=it},label={Text("Search name or phone")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    val customers=remember(reload,search){db.customers(search)}
    Text("Customer records (${customers.size})",fontWeight=FontWeight.Bold)
    customers.forEach { c -> Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF12382A)),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(c.name,fontWeight=FontWeight.Bold);Text(c.phone,color=Muted);if(c.notes.isNotBlank())Text(c.notes)
            Row { TextButton(onClick={editId=c.id;name=c.name;phone=c.phone;notes=c.notes}){Text("Edit")};TextButton(onClick={db.deleteCustomer(c.id);reload++;onMessage("Customer deleted.")}){Text("Delete")} }
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
    orders.forEach { o -> Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF12382A)),modifier=Modifier.fillMaxWidth()) {
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
    records.forEach { t -> Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF12382A)),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Entry #${t.id} · Order #${t.orderId}",fontWeight=FontWeight.Bold);Text("KES ${money(t.amount)} · ${t.status}",color=Muted);if(t.reference.isNotBlank())Text(t.reference);Text(dateText(t.createdAt),color=Muted,style=MaterialTheme.typography.labelSmall)
        }
    } }
}

@Composable
private fun AutoReplyScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vecta_auto_reply_drafts", android.content.Context.MODE_PRIVATE) }
    val types = listOf(
        "Successful response" to "Hi <firstName>, thank you for purchasing from Bingwa Vecta.",
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
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF102B20)),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text(item.first,fontWeight=FontWeight.Bold,color=Navy,style=MaterialTheme.typography.titleMedium)
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
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
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        StatCard("ALL ORDERS",orders.size,Modifier.weight(1f))
        StatCard("PENDING",pending,Modifier.weight(1f))
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        StatCard("FAILED",failed,Modifier.weight(1f))
        StatCard("CANCELLED",cancelled,Modifier.weight(1f))
    }
    InfoCard("Manually recorded delivery value","KES \${money(totalSales)}")
    InfoCard("Unverified transaction notes","\$unverified entries — not counted as confirmed payments.")
}

@Composable
private fun QuickAction(symbol:String,label:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    Card(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF12382A)),elevation=CardDefaults.cardElevation(defaultElevation=2.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical=16.dp,horizontal=13.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Surface(color=Color(0xFF17432F),shape=RoundedCornerShape(12.dp)) { Text(symbol,color=Color(0xFF28D879),fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(horizontal=12.dp,vertical=6.dp)) }
            Text(label,color=Navy,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall)
        }
    }
}
@Composable
private fun StatCard(label:String,count:Int,modifier:Modifier=Modifier) {
    Card(modifier=modifier,shape=MaterialTheme.shapes.large,colors=CardDefaults.cardColors(containerColor=Color(0xFF12382A)),elevation=CardDefaults.cardElevation(defaultElevation=2.dp)) { Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) { Text(count.toString(),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold,color=Navy);Text(label,color=Muted,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=.5.sp) } }
}
@Composable
private fun InfoCard(title:String,body:String) { Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF12382A)),shape=MaterialTheme.shapes.large,elevation=CardDefaults.cardElevation(defaultElevation=1.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(title,fontWeight=FontWeight.Bold,color=Navy,style=MaterialTheme.typography.titleMedium);Text(body,color=Muted)} } }
private fun money(value:Double)=String.format(Locale.US,"%.2f",value)
private fun dateText(value:Long)=SimpleDateFormat("dd MMM yyyy, HH:mm",Locale.getDefault()).format(Date(value))
