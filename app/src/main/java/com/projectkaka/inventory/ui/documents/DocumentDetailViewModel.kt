package com.projectkaka.inventory.ui.documents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.relation.DocumentWithPages
import com.projectkaka.inventory.util.DocumentPdfGenerator
import com.projectkaka.inventory.util.GalleryHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DocumentDetailUiState(
    val isExporting: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null
)

class DocumentDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val documentRepo = getApplication<KakaApplication>().documentRepository
    val documentId: Int = checkNotNull(savedStateHandle["documentId"]).toString().toInt()

    val documentWithPages: StateFlow<DocumentWithPages?> =
        documentRepo.observeDocumentWithPages(documentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow(DocumentDetailUiState())
    val uiState: StateFlow<DocumentDetailUiState> = _uiState.asStateFlow()

    fun exportToPdf(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val doc = documentWithPages.value ?: return@launch
            _uiState.value = _uiState.value.copy(isExporting = true, error = null)
            val result = DocumentPdfGenerator.savePdfToDownloads(getApplication(), doc)
            result.onSuccess { fileName ->
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    successMessage = "Saved PDF to Downloads/ProjectKaka/Documents/$fileName"
                )
                onDone(fileName)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    error = e.localizedMessage ?: "Failed to generate PDF"
                )
            }
        }
    }

    fun savePagesToGallery() {
        viewModelScope.launch {
            val doc = documentWithPages.value ?: return@launch
            _uiState.value = _uiState.value.copy(isExporting = true, error = null)
            var savedCount = 0
            doc.sortedPages.forEachIndexed { idx, page ->
                val result = GalleryHelper.saveImageToGallery(
                    getApplication(),
                    page.imagePath,
                    "${doc.document.title}_p${idx + 1}"
                )
                if (result.isSuccess) savedCount++
            }
            _uiState.value = _uiState.value.copy(
                isExporting = false,
                successMessage = "Saved $savedCount page image(s) to Gallery/ProjectKaka"
            )
        }
    }

    fun deleteDocument(onDeleted: () -> Unit) {
        viewModelScope.launch {
            documentRepo.deleteDocument(documentId)
            onDeleted()
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(successMessage = null, error = null)
    }
}
