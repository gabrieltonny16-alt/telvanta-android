package com.telvanta.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val RuleCyan = Color(0xFF00E5FF)
private val RuleBg = Color(0xFF080C12)
private val RuleCard = Color(0xFF131B26)
private val RuleMuted = Color(0xFF8A99AD)

private fun normalizeRulePhone(value: String): String {
    val digits = value.filter(Char::isDigit)
    return when {
        digits.startsWith("254") && digits.length == 12 -> "+$digits"
        digits.startsWith("0") && digits.length == 10 -> "+254" + digits.drop(1)
        digits.length == 9 -> "+254$digits"
        else -> ""
    }
}

@Composable
fun MessageProcessingRulesScreen(title: String, onMessage: (String) -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bingwapulse_settings", Context.MODE_PRIVATE) }
    val key = if (title == "Blacklist") "blacklisted_senders" else "authorized_senders"
    var entries by remember { mutableStateOf(prefs.getStringSet(key, emptySet()).orEmpty().toList().sorted()) }
    var input by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    val isBlacklist = title == "Blacklist"

    fun reload() { entries = prefs.getStringSet(key, emptySet()).orEmpty().toList().sorted() }
    fun addEntry() {
        val phone = normalizeRulePhone(input)
        if (phone.isBlank()) { onMessage("Enter a valid Kenyan mobile number."); return }
        if (phone in entries) { onMessage("That number is already listed."); return }
        val updated = (entries + phone).toSet()
        prefs.edit().putStringSet(key, updated).apply()
        input = ""
        reload()
        onMessage(if (isBlacklist) "Number added to BlackList." else "Authorized sender added.")
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (isBlacklist) "Numbers on this list are excluded from payment-message processing and offer recommendations."
            else "When this list contains numbers, only listed senders are accepted for payment-message processing. An empty list keeps the existing all-senders behaviour.",
            color = RuleMuted, style = MaterialTheme.typography.bodyMedium
        )
        Card(colors = CardDefaults.cardColors(containerColor = RuleCard), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = input, onValueChange = { input = it.filter { ch -> ch.isDigit() || ch == '+' }.take(16) },
                    label = { Text("Kenyan phone number") }, placeholder = { Text("07XXXXXXXX or 2547XXXXXXXX") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { addEntry() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RuleCyan, contentColor = RuleBg)) {
                    Text(if (isBlacklist) "Add to BlackList" else "Authorize sender")
                }
            }
        }
        Text("Saved numbers · ${entries.size}", color = RuleMuted, fontWeight = FontWeight.SemiBold)
        if (entries.isEmpty()) {
            Text(if (isBlacklist) "No blocked numbers." else "No sender restrictions are active.", color = RuleMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it }) { phone ->
                    Card(colors = CardDefaults.cardColors(containerColor = RuleCard), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(phone, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = {
                                prefs.edit().putStringSet(key, entries.filterNot { it == phone }.toSet()).apply()
                                reload()
                                onMessage("Number removed.")
                            }) { Text("Remove", color = RuleCyan) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactNameTagScreen(onMessage: (String) -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bingwapulse_settings", Context.MODE_PRIVATE) }
    var prefix by remember { mutableStateOf(prefs.getString("contact_name_prefix", "").orEmpty()) }
    var suffix by remember { mutableStateOf(prefs.getString("contact_name_suffix", "Bingwa").orEmpty()) }
    var separator by remember { mutableStateOf(prefs.getString("contact_name_separator", " ").orEmpty()) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Contact name tag", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Choose how BingwaPulse labels a customer when a contact is saved. This format is stored on this device.", color = RuleMuted)
        Card(colors = CardDefaults.cardColors(containerColor = RuleCard), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(prefix, { prefix = it.take(24) }, label = { Text("Prefix (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(suffix, { suffix = it.take(24) }, label = { Text("Tag / suffix") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(separator, { separator = it.take(3) }, label = { Text("Separator") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Preview: ${listOf(prefix.trim(), "John Doe", suffix.trim()).filter { it.isNotBlank() }.joinToString(separator)}",
                    color = RuleCyan, fontWeight = FontWeight.Bold)
                Button(onClick = {
                    prefs.edit().putString("contact_name_prefix", prefix.trim())
                        .putString("contact_name_suffix", suffix.trim())
                        .putString("contact_name_separator", separator)
                        .apply()
                    onMessage("Contact name format saved on this device.")
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = RuleCyan, contentColor = RuleBg)) {
                    Text("Save name format")
                }
            }
        }
    }
}
