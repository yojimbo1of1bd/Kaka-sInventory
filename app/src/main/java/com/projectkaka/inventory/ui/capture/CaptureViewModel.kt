package com.projectkaka.inventory.ui.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.ImageProcessor
import com.projectkaka.inventory.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CaptureUiState(
    val isProcessing: Boolean = false,
    val capturedCount: Int = 0,
    val lastMessage: String? = null,
    val error: String? = null
)

class CaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = getApplication<KakaApplication>().repository
    private val prefs = getApplication<KakaApplication>().preferences

    val permCamera: StateFlow<Boolean> = prefs.permCamera

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    /**
     * Called once per shutter press. Runs the WebP pipeline off the main thread,
     * then persists an ItemEntity draft pointing at the compressed file.
     * The camera viewfinder stays open the whole time (burst mode).
     */
    fun onPhotoCaptured(sourceJpeg: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            runCatching {
                val result = ImageProcessor.compressToWebp(getApplication(), sourceJpeg)
                repository.saveItem(
                    ItemEntity(
                        imagePath = result.file.absolutePath,
                        isDraft = true
                    )
                )
                result
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        capturedCount = it.capturedCount + 1,
                        lastMessage = "Draft saved (${result.sizeBytes / 1024} KB WebP)"
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isProcessing = false, error = e.localizedMessage ?: "Capture failed")
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(lastMessage = null, error = null) }
    }
}
