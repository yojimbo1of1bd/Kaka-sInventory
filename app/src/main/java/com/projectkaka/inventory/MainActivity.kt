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
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

class MainActivity : FragmentActivity() {
    private var lockedState = mutableStateOf(false)
    private var lastStopMillis = 0L
    private val GRACE_PERIOD = 3000L // 3 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val app = application as KakaApplication
        
        // Lock on cold start if enabled, or restore and check grace period if process died
        if (savedInstanceState != null) {
            lockedState.value = savedInstanceState.getBoolean("is_locked", app.preferences.appLockEnabled.value)
            val stopTime = savedInstanceState.getLong("last_stop_time", 0L)
            if (app.preferences.appLockEnabled.value && stopTime > 0L && (System.currentTimeMillis() - stopTime > GRACE_PERIOD)) {
                lockedState.value = true
            }
        } else {
            lockedState.value = app.preferences.appLockEnabled.value
        }
        
        setContent {
            val isLocked by lockedState
            if (isLocked) {
                com.projectkaka.inventory.ui.settings.LockScreen(
                    onUnlock = {
                        lockedState.value = false
                    },
                    app = app
                )
            } else {
                KakaApp()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lastStopMillis = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        val app = application as KakaApplication
        if (app.preferences.appLockEnabled.value) {
            if (lastStopMillis > 0 && System.currentTimeMillis() - lastStopMillis > GRACE_PERIOD) {
                lockedState.value = true
            }
        }
        lastStopMillis = 0L

        // Startup self-check: verify Phase 1 invariant for account balances
        lifecycleScope.launch(Dispatchers.IO) {
            val mismatched = app.database.financeDao().getAccountsWithMismatchedBalances()
            if (mismatched.isNotEmpty()) {
                val names = mismatched.joinToString { it.name }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        app,
                        "CRITICAL: Balance mismatch detected in accounts: $names. Please contact support.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_locked", lockedState.value)
        outState.putLong("last_stop_time", System.currentTimeMillis())
    }
}
