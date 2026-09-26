package com.projectkaka.inventory

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.projectkaka.inventory.ui.KakaApp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        val app = application as KakaApplication
        if (app.preferences.appLockEnabled.value) {
            showBiometricPrompt()
        } else {
            setContent { KakaApp() }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Project Kaka Locked")
            .setSubtitle("Authenticate to access your inventory")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    setContent { KakaApp() }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showLockedScreen()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    showLockedScreen()
                }
            })

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showLockedScreen() {
        val app = application as KakaApplication
        setContent {
            var pinInput by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            var error by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.OutlinedTextField(
                        value = pinInput,
                        onValueChange = { 
                            pinInput = it
                            if (it == app.preferences.appPin.value) {
                                setContent { KakaApp() }
                            } else if (it.length >= app.preferences.appPin.value.length) {
                                error = true
                            } else {
                                error = false
                            }
                        },
                        label = { Text("Enter App PIN") },
                        isError = error,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        singleLine = true
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                    Button(onClick = { showBiometricPrompt() }) {
                        Text("Use Biometrics")
                    }
                }
            }
        }
    }
}
