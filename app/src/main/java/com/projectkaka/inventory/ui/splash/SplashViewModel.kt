package com.projectkaka.inventory.ui.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.LocalImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SplashUiState {
    data class Loading(val progress: Float, val statusMessage: String) : SplashUiState
    data object Ready : SplashUiState
    data class Error(val error: String) : SplashUiState
}

class SplashViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<SplashUiState>(
        SplashUiState.Loading(0.05f, "Loading Kaka's Project...")
    )
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        initializeApplication()
    }

    private fun initializeApplication() {
        val app = getApplication<KakaApplication>()
        viewModelScope.launch {
            try {
                _uiState.value = SplashUiState.Loading(0.25f, "Verifying local storage...")
                withContext(Dispatchers.IO) { LocalImageStore.imageDir(app) }
                delay(350)

                _uiState.value = SplashUiState.Loading(0.65f, "Initializing SQLite WAL Engine...")
                withContext(Dispatchers.IO) { app.database.openHelper.writableDatabase.version }
                delay(100)

                _uiState.value = SplashUiState.Loading(0.85f, "Reconciling financial balances...")
                val repaired = withContext(Dispatchers.IO) { app.financeRepository.reconcileBalances() }
                if (repaired > 0) {
                    delay(300) // Give user a chance to see that a repair happened
                }
                
                _uiState.value = SplashUiState.Loading(1.0f, "Ready!")
                delay(250)
                _uiState.value = SplashUiState.Ready
            } catch (e: Exception) {
                _uiState.value = SplashUiState.Error(e.localizedMessage ?: "Initialization Failed")
            }
        }
    }
}
