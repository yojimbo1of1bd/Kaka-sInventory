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

class MainActivity : FragmentActivity() {
    private var lockedState = mutableStateOf(false)
    private var lastStopMillis = 0L
    private val GRACE_PERIOD = 3000L // 3 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val app = application as KakaApplication
        
        // Lock on cold start if enabled
        if (savedInstanceState == null) {
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
    }
}
