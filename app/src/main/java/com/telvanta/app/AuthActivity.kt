package com.telvanta.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SecureSessionStore(this)
        val api = BingwaSupabaseApi()
        setContent {
            MaterialTheme {
                Surface(color = Color(0xFF010905), modifier = Modifier.fillMaxSize()) {
                    BingwaAuthScreen(store, api) {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    }
                }
            }
        }
    }
}

private enum class AuthMethod { EMAIL, PHONE }

@Composable
private fun BingwaAuthScreen(store: SecureSessionStore, api: BingwaSupabaseApi, onComplete: () -> Unit) {
    var method by remember { mutableStateOf(AuthMethod.EMAIL) }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Sign in to connect BingwaPulse to your business workspace.") }
    var busy by remember { mutableStateOf(false) }
    var createAccount by remember { mutableStateOf(false) }
    var awaitingPhoneOtp by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<BingwaSession?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val saved = store.load()
        if (saved != null) {
            busy = true
            try {
                val active = withContext(Dispatchers.IO) {
                    if (saved.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) api.refreshSession(saved) else saved
                }
                store.save(active)
                val businessId = active.businessId ?: withContext(Dispatchers.IO) { api.firstBusinessId(active) }
                if (businessId != null) {
                    store.save(active.copy(businessId = businessId))
                    onComplete()
                } else {
                    session = active
                    message = "Your account is signed in. Create your business workspace to continue."
                }
            } catch (_: Exception) {
                store.clear()
                message = "Your saved session needs a fresh sign-in."
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("BINGWAPULSE", color = Color(0xFFB7FF24), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("YOUR BUSINESS. ONE CONNECTED PLATFORM.", color = Color(0xFF91AD9D), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(28.dp))

        if (session == null) {
            Text(if (awaitingPhoneOtp) "Verify your phone" else if (createAccount) "Create your account" else "Welcome back",
                color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            if (!awaitingPhoneOtp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = method == AuthMethod.EMAIL, onClick = { method = AuthMethod.EMAIL; identifier = ""; message = "Use your email address." }, label = { Text("Email") })
                    FilterChip(selected = method == AuthMethod.PHONE, onClick = { method = AuthMethod.PHONE; identifier = ""; message = "Use your phone number. Kenya format: +2547XXXXXXXX." }, label = { Text("Phone number") })
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = identifier, onValueChange = { identifier = it.trim() },
                    label = { Text(if (method == AuthMethod.EMAIL) "Email address" else "Phone number") },
                    placeholder = { if (method == AuthMethod.PHONE) Text("+2547XXXXXXXX") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(8) },
                    label = { Text("SMS verification code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = {
                busy = true
                scope.launch {
                    try {
                        if (awaitingPhoneOtp) {
                            if (code.length !in 6..8) throw IllegalArgumentException("Enter the verification code sent to your phone.")
                            val verified = withContext(Dispatchers.IO) { api.verifyPhoneOtp(identifier, code) }
                            store.save(verified)
                            val businessId = withContext(Dispatchers.IO) { api.firstBusinessId(verified) }
                            if (businessId != null) {
                                store.save(verified.copy(businessId = businessId))
                                onComplete()
                            } else {
                                session = verified
                                message = "Phone verified. Set up your business workspace."
                            }
                        } else {
                            if (identifier.isBlank() || password.length < 8) {
                                val label = if (method == AuthMethod.EMAIL) "email address" else "phone number"
                                throw IllegalArgumentException("Enter your $label and a password of at least 8 characters.")
                            }
                            val result = withContext(Dispatchers.IO) {
                                when (method) {
                                    AuthMethod.EMAIL -> if (createAccount) api.signUp(identifier, password) else api.signIn(identifier, password)
                                    AuthMethod.PHONE -> if (createAccount) { api.signUpPhone(identifier, password); null } else api.signInPhone(identifier, password)
                                }
                            }
                            if (method == AuthMethod.PHONE && createAccount) {
                                awaitingPhoneOtp = true
                                message = "A verification code was requested. Enter it below to verify your phone."
                            } else if (result == null) {
                                message = "Account request submitted. Check your email inbox and spam folder, then confirm before signing in. You can request another confirmation email below."
                            } else {
                                store.save(result)
                                val businessId = withContext(Dispatchers.IO) { api.firstBusinessId(result) }
                                if (businessId != null) {
                                    store.save(result.copy(businessId = businessId))
                                    onComplete()
                                } else {
                                    session = result
                                    message = "Account ready. Set up your business workspace."
                                }
                            }
                        }
                    } catch (error: Exception) {
                        message = error.message ?: "Could not complete the account request."
                    } finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator() else Text(if (awaitingPhoneOtp) "Verify phone number" else if (createAccount) "Create account" else "Sign in")
            }
            if (awaitingPhoneOtp) {
                TextButton(onClick = { awaitingPhoneOtp = false; code = ""; message = "You can change the phone number or try again." }, enabled = !busy) { Text("Change phone number") }
            } else {
                if (method == AuthMethod.EMAIL) {
                    TextButton(onClick = {
                        if (identifier.isBlank()) message = "Enter your email address first."
                        else {
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { api.resendEmailConfirmation(identifier) }
                                    message = "A new confirmation email was requested. Check your inbox and spam folder."
                                } catch (error: Exception) { message = error.message ?: "Could not resend the email." }
                                finally { busy = false }
                            }
                        }
                    }, enabled = !busy) { Text("Resend confirmation email") }
                }
                TextButton(onClick = { createAccount = !createAccount; message = if (createAccount) "Create a new BingwaPulse account." else "Sign in to your existing account." }, enabled = !busy) {
                    Text(if (createAccount) "I already have an account" else "Create a new account")
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    store.enableLocalWorkspace()
                    onComplete()
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Open local workspace (no account needed)")
                }
            }
        } else {
            Text("Set up your business", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = businessName, onValueChange = { businessName = it }, label = { Text("Business name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Button(onClick = {
                val name = businessName.trim()
                val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(49)
                if (name.length < 2 || slug.length < 3) message = "Enter a business name with at least 3 usable letters or numbers."
                else {
                    busy = true
                    scope.launch {
                        try {
                            val id = withContext(Dispatchers.IO) { api.createBusiness(session!!, name, slug) }
                            store.save(session!!.copy(businessId = id))
                            onComplete()
                        } catch (error: Exception) { message = error.message ?: "Could not create the business workspace." }
                        finally { busy = false }
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator() else Text("Create business workspace")
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(message, color = Color(0xFFB7D4C4), style = MaterialTheme.typography.bodyMedium)
    }
}
