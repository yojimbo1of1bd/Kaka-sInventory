package com.projectkaka.inventory.ui.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.DocumentImageStore
import com.projectkaka.inventory.data.local.ImageProcessor
import com.projectkaka.inventory.data.local.entity.DocumentEntity
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
    val error: String? = null,
    val isDocumentMode: Boolean = false,
    val documentPages: List<String> = emptyList(),
    val isDocumentSaved: Boolean = false
)

class CaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = getApplication<KakaApplication>().repository
    private val documentRepo = getApplication<KakaApplication>().documentRepository
    private val prefs = getApplication<KakaApplication>().preferences

    val permCamera: StateFlow<Boolean> = prefs.permCamera
    val hasSeenDocMode: StateFlow<Boolean> = prefs.hasSeenDocMode

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun setDocumentMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isDocumentMode = enabled,
                capturedCount = if (enabled) it.documentPages.size else 0,
                lastMessage = if (enabled) "Document mode active" else null
            )
        }
    }

    fun dismissDocModeOnboarding() {
        prefs.setHasSeenDocMode(true)
    }

    /**
     * Called once per shutter press.
     * In Item mode: compresses to 75% WebP and saves an ItemEntity draft.
     * In Document mode: compresses to 95% WebP via DocumentImageStore and accumulates pages into the session.
     */
    fun onPhotoCaptured(sourceJpeg: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            val isDoc = _uiState.value.isDocumentMode

            if (isDoc) {
                runCatching {
                    DocumentImageStore.compressPageToWebp(getApplication(), sourceJpeg)
                }.onSuccess { pageResult ->
                    _uiState.update { current ->
                        val updatedPages = current.documentPages + pageResult.file.absolutePath
                        current.copy(
                            isProcessing = false,
                            documentPages = updatedPages,
                            capturedCount = updatedPages.size,
                            lastMessage = "Page ${updatedPages.size} captured (${pageResult.sizeBytes / 1024} KB)"
                        )
                    }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(isProcessing = false, error = e.localizedMessage ?: "Page capture failed")
                    }
                }
            } else {
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
    }

    fun removePage(index: Int) {
        val current = _uiState.value.documentPages
        if (index in current.indices) {
            val path = current[index]
            runCatching { File(path).delete() }
            val updated = current.toMutableList().apply { removeAt(index) }
            _uiState.update {
                it.copy(
                    documentPages = updated,
                    capturedCount = updated.size,
                    lastMessage = "Page ${index + 1} removed"
                )
            }
        }
    }

    fun saveDocument(
        title: String,
        docType: String,
        issueDate: Long,
        expiryDate: Long?,
        notes: String,
        linkedItemId: Int? = null
    ) {
        viewModelScope.launch {
            val pages = _uiState.value.documentPages
            if (pages.isEmpty()) {
                _uiState.update { it.copy(error = "No document pages captured yet") }
                return@launch
            }
            _uiState.update { it.copy(isProcessing = true, error = null) }
            runCatching {
                val doc = DocumentEntity(
                    title = title.ifBlank { "Untitled Document" },
                    docType = docType,
                    pageCount = pages.size,
                    coverImagePath = pages.first(),
                    notes = notes,
                    issueDate = issueDate,
                    expiryDate = expiryDate,
                    linkedItemId = linkedItemId
                )
                documentRepo.saveDocumentWithPages(doc, pages)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        documentPages = emptyList(),
                        capturedCount = 0,
                        isDocumentSaved = true,
                        lastMessage = "Document '$title' saved successfully!"
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isProcessing = false, error = e.localizedMessage ?: "Failed to save document")
                }
            }
        }
    }

    fun resetDocumentSaved() {
        _uiState.update { it.copy(isDocumentSaved = false) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(lastMessage = null, error = null) }
    }
}
