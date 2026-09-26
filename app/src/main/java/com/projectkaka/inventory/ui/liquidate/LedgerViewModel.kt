package com.projectkaka.inventory.ui.liquidate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.LedgerType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LedgerUiState(
    val entries: List<LedgerEntryEntity> = emptyList(),
    val totalReceivable: Double = 0.0,
    val totalPayable: Double = 0.0,
    val isLoading: Boolean = true
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val financeRepo = getApplication<KakaApplication>().financeRepository

    val uiState: StateFlow<LedgerUiState> = combine(
        financeRepo.getUnsettledEntries(),
        financeRepo.observeTotalReceivable(),
        financeRepo.observeTotalPayable()
    ) { entries, receivable, payable ->
        LedgerUiState(
            entries = entries,
            totalReceivable = receivable,
            totalPayable = payable,
            isLoading = false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LedgerUiState()
    )

    fun addEntry(contactName: String, contactPhone: String, amount: Double, type: LedgerType, note: String, dueDate: Long? = null) {
        if (contactName.isBlank() || amount <= 0.0) return
        viewModelScope.launch {
            financeRepo.insertLedgerEntry(
                LedgerEntryEntity(
                    contactName = contactName.trim(),
                    contactPhone = contactPhone.trim(),
                    amount = amount,
                    type = type,
                    note = note.trim(),
                    dueDate = dueDate
                )
            )
        }
    }

    fun markSettled(entry: LedgerEntryEntity) {
        viewModelScope.launch {
            financeRepo.updateLedgerEntry(entry.copy(isSettled = true))
        }
    }

    fun deleteEntry(entry: LedgerEntryEntity) {
        viewModelScope.launch {
            financeRepo.deleteLedgerEntry(entry)
        }
    }
}
