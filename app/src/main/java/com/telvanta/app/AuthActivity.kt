package com.telvanta.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
                    BingwaAuthScreen(
                        store = store,
                        api = api,
                        onComplete = {
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BingwaAuthScreen(
    store: SecureSessionStore,
    api: BingwaSupabaseApi,
    onComplete: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Sign in to connect BingwaPulse to your business workspace.") }
    var busy by remember { mutableStateOf(false) }
    var createAccount by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<BingwaSession?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val saved = store.load()
        if (saved != null) {
            busy = true
            try {
                val active = withContext(Dispatchers.IO) {
                    if (saved.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                        api.refreshSession(saved)
                    } else saved
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
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("BINGWAPULSE", color = Color(0xFFB7FF24), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("YOUR BUSINESS. ONE CONNECTED PLATFORM.", color = Color(0xFF91AD9D), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(28.dp))

        if (session == null) {
            Text(if (createAccount) "Create your account" else "Welcome back", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() },
                label = { Text("Email address") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    if (email.isBlank() || password.length < 8) {
                        message = "Enter a valid email and a password of at least 8 characters."
                    } else {
                        busy = true
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    if (createAccount) api.signUp(email, password) else api.signIn(email, password)
                                }
                                if (result == null) {
                                    message = "Account request submitted. Check your email to confirm the account, then sign in."
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
                            } catch (error: Exception) {
                                message = error.message ?: "Could not complete the account request."
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator() else Text(if (createAccount) "Create account" else "Sign in")
            }
            Button(
                onClick = { createAccount = !createAccount; message = if (createAccount) "Create a new BingwaPulse account." else "Sign in to your existing account." },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (createAccount) "I already have an account" else "Create a new account")
            }
        } else {
            Text("Set up your business", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = businessName, onValueChange = { businessName = it },
                label = { Text("Business name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val name = businessName.trim()
                    val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(49)
                    if (name.length < 2 || slug.length < 3) {
                        message = "Enter a business name with at least 3 usable letters or numbers."
                    } else {
                        busy = true
                        scope.launch {
                            try {
                                val id = withContext(Dispatchers.IO) { api.createBusiness(session!!, name, slug) }
                                store.save(session!!.copy(businessId = id))
                                onComplete()
                            } catch (error: Exception) {
                                message = error.message ?: "Could not create the business workspace."
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator() else Text("Create business workspace")
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(message, color = Color(0xFFB7D4C4), style = MaterialTheme.typography.bodyMedium)
    }
}
