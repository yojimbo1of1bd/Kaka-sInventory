package com.projectkaka.inventory.ui.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.ExportWriter
import com.projectkaka.inventory.data.repository.ItemExportRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.projectkaka.inventory.data.local.relation.DocumentWithPages
import com.projectkaka.inventory.data.repository.FinancialExportData
import com.projectkaka.inventory.data.local.ImportReader
import com.projectkaka.inventory.data.local.RestoreResult

data class ExportUiState(
    val itemCount: Int = 0,
    val documentCount: Int = 0,
    val lastCsvName: String? = null,
    val lastJsonName: String? = null,
    val lastKakaName: String? = null,
    val lastPngName: String? = null,
    val availableCategories: List<String> = emptyList(),
    val working: Boolean = false,
    val error: String? = null,
    val restoreResult: RestoreResult? = null
)

/**
 * Data portability: the whole dataset already lives in one SQLite file plus one
 * image folder, so "export" is just a local file write — never a network upload.
 */
class ExportViewModel(application: Application) : AndroidViewModel(application) {

    private val db = getApplication<KakaApplication>().database
    private val repository = getApplication<KakaApplication>().repository
    private val financeRepository = getApplication<KakaApplication>().financeRepository
    private val documentRepository = getApplication<KakaApplication>().documentRepository

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val rows = repository.exportSnapshot()
            val docCount = documentRepository.getAllDocumentsWithPagesSnapshot().size
            val categories = rows.map { it.category.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
            _uiState.update { 
                it.copy(
                    itemCount = rows.size,
                    documentCount = docCount,
                    availableCategories = categories
                ) 
            }
        }
    }

    fun exportCsv() {
        export { rows, _, _ ->
            ExportWriter.writeCsv(getApplication(), rows)
        }
    }

    fun exportJson() {
        export { rows, docs, fin ->
            ExportWriter.writeJson(getApplication(), rows, docs, fin)
        }
    }

    fun exportKakaZip() {
        export { rows, docs, fin ->
            ExportWriter.writeKakaZip(getApplication(), rows, docs, fin)
        }
    }

    fun exportVisualMap(category: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching {
                val allRows = repository.exportSnapshot()
                val targetRows = if (category.isBlank() || category.equals("all", ignoreCase = true)) {
                    allRows
                } else {
                    allRows.filter { it.category.equals(category, ignoreCase = true) }
                }

                val bitmap = com.projectkaka.inventory.util.VisualMapGenerator.generateVisualMap(category, targetRows)
                val safeCat = if (category.isBlank() || category.equals("all", ignoreCase = true)) "all" else category.lowercase().replace(" ", "_")
                val filename = "visual_map_${safeCat}_${System.currentTimeMillis()}"
                val uri = com.projectkaka.inventory.util.GalleryHelper.saveBitmapToGallery(getApplication(), bitmap, filename).getOrNull()
                if (uri == null) throw IllegalStateException("Could not save visual map to gallery")
                "$filename.png"
            }.onSuccess { fileName ->
                _uiState.update {
                    it.copy(
                        working = false,
                        lastPngName = fileName
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(working = false, error = e.localizedMessage ?: "Visual map generation failed")
                }
            }
        }
    }

    private fun export(
        writer: suspend (List<ItemExportRow>, List<DocumentWithPages>, FinancialExportData) -> String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching {
                val rows = repository.exportSnapshot()
                val docs = documentRepository.getAllDocumentsWithPagesSnapshot()
                val financeData = financeRepository.exportFinancialSnapshot()
                writer(rows, docs, financeData)
            }.onSuccess { fileName ->
                val ext = fileName.substringAfterLast('.', "").lowercase()
                _uiState.update {
                    it.copy(
                        working = false,
                        lastCsvName = if (ext == "csv") fileName else it.lastCsvName,
                        lastJsonName = if (ext == "json") fileName else it.lastJsonName,
                        lastKakaName = if (ext == "kaka") fileName else it.lastKakaName
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(working = false, error = e.localizedMessage ?: "Export failed")
                }
            }
        }
    }

    fun importKakaZip(uri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null, restoreResult = null) }
            runCatching {
                ImportReader.restoreKakaZip(getApplication(), uri, db)
            }.onSuccess { result ->
                _uiState.update { it.copy(working = false, restoreResult = result) }
                // refresh counts
                val rows = repository.exportSnapshot()
                val docCount = documentRepository.getAllDocumentsWithPagesSnapshot().size
                _uiState.update { it.copy(itemCount = rows.size, documentCount = docCount) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(working = false, error = e.localizedMessage ?: "Import failed")
                }
            }
        }
    }
}
