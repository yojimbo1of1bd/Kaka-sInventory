package com.projectkaka.inventory.ui.settings

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.util.CryptoUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LockScreen(onUnlock: () -> Unit, app: KakaApplication) {
    val activity = LocalContext.current as FragmentActivity
    var showPinScreen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!showPinScreen) {
            val executor = ContextCompat.getMainExecutor(activity)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Project Kaka Locked")
                .setSubtitle("Authenticate to access your inventory")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        handleUnlock(app)
                        onUnlock()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        showPinScreen = true
                    }
                    // Do not override onAuthenticationFailed to eject to PIN screen!
                })
            biometricPrompt.authenticate(promptInfo)
        }
    }

    if (showPinScreen) {
        PinUnlockScreen(
            onUnlock = {
                handleUnlock(app)
                onUnlock()
            },
            app = app,
            onUseBiometrics = { showPinScreen = false }
        )
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Text("Authenticating...")
        }
    }
}

@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
private fun handleUnlock(app: KakaApplication) {
    // Reset lockout
    app.preferences.setLockoutAttempts(0)
    app.preferences.setLockoutUntil(0L)

    // Migration 8.5: Hash plaintext PIN on first unlock
    val plaintextPin = app.preferences.appPin.value
    if (plaintextPin.isNotEmpty()) {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Default) {
            val salt = CryptoUtils.generateSalt()
            val hash = CryptoUtils.hashPin(plaintextPin, salt, 100000)
            app.preferences.setAppPinHash(hash, salt, 100000)
            app.preferences.clearPlaintextAppPin()
        }
    }
}

@Composable
fun PinUnlockScreen(onUnlock: () -> Unit, app: KakaApplication, onUseBiometrics: () -> Unit) {
    var pinInput by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var error by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    val lockoutAttempts = app.preferences.lockoutAttempts.collectAsState()
    val lockoutUntil = app.preferences.lockoutUntil.collectAsState()
    
    var remainingSeconds by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(lockoutUntil.value) {
        while (true) {
            val remaining = (lockoutUntil.value - System.currentTimeMillis()) / 1000
            if (remaining > 0) {
                remainingSeconds = remaining
            } else {
                remainingSeconds = 0
            }
            delay(1000)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            if (remainingSeconds > 0) {
                Text(
                    "Too many failed attempts. Try again in $remainingSeconds seconds.",
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 6) {
                            pinInput = input
                            error = false
                        }
                    },
                    label = { Text("Enter App PIN") },
                    isError = error,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            if (isVerifying) return@KeyboardActions
                            isVerifying = true
                            coroutineScope.launch {
                                val plaintextPin = app.preferences.appPin.value
                                val hash = app.preferences.appPinHash.value
                                val salt = app.preferences.appPinSalt.value
                                val iter = app.preferences.appPinIterations.value
                                
                                var isValid = false
                                if (plaintextPin.isNotEmpty() && pinInput == plaintextPin) {
                                    isValid = true
                                } else if (hash.isNotEmpty() && pinInput.length >= 4) {
                                    isValid = withContext(Dispatchers.Default) {
                                        CryptoUtils.verifyPin(pinInput, hash, salt, iter)
                                    }
                                }
                                
                                if (isValid) {
                                    onUnlock()
                                } else {
                                    error = true
                                    val attempts = app.preferences.lockoutAttempts.value + 1
                                    app.preferences.setLockoutAttempts(attempts)
                                    if (attempts >= 5) {
                                        val backoff = Math.pow(2.0, (attempts - 5).toDouble()).toLong() * 30_000L
                                        app.preferences.setLockoutUntil(System.currentTimeMillis() + backoff)
                                    }
                                    pinInput = ""
                                }
                                isVerifying = false
                            }
                        }
                    ),
                    singleLine = true,
                    enabled = remainingSeconds <= 0
                )
                if (error) {
                    Text("Incorrect PIN", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUseBiometrics) {
                Text("Use Biometrics")
            }
        }
    }
}
