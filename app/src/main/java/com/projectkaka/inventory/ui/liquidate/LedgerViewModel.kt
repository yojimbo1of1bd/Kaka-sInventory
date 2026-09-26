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
    val summaries: List<com.projectkaka.inventory.data.local.dao.ContactSummaryRow> = emptyList(),
    val accounts: List<com.projectkaka.inventory.data.local.entity.AccountEntity> = emptyList(),
    val totalReceivable: com.projectkaka.inventory.model.Money = com.projectkaka.inventory.model.Money(0),
    val totalPayable: com.projectkaka.inventory.model.Money = com.projectkaka.inventory.model.Money(0),
    val isLoading: Boolean = true
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val financeRepo = getApplication<KakaApplication>().financeRepository
    private val prefs = getApplication<KakaApplication>().preferences

    val permContacts: StateFlow<Boolean> = prefs.permContacts
    val permSmsCalls: StateFlow<Boolean> = prefs.permSmsCalls
    val permMicrophone: StateFlow<Boolean> = prefs.permMicrophone

    val uiState: StateFlow<LedgerUiState> = combine(
        financeRepo.getUnsettledEntries(),
        financeRepo.observeContactSummaries(),
        financeRepo.getActiveAccounts(),
        financeRepo.observeTotalReceivable(),
        financeRepo.observeTotalPayable()
    ) { entries, summaries, accounts, receivable, payable ->
        LedgerUiState(
            entries = entries,
            summaries = summaries,
            accounts = accounts,
            totalReceivable = receivable,
            totalPayable = payable,
            isLoading = false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LedgerUiState()
    )

    fun addEntry(contactName: String, contactPhone: String, amount: Double, type: LedgerType, note: String, accountId: Int, dueDate: Long? = null) {
        if (contactName.isBlank() || amount <= 0.0) return
        viewModelScope.launch {
            financeRepo.issueDebt(
                entry = LedgerEntryEntity(
                    contactName = contactName.trim(),
                    contactPhone = contactPhone.trim(),
                    amount = com.projectkaka.inventory.model.Money((amount * 100).toLong()),
                    type = type,
                    note = note.trim(),
                    dueDate = dueDate
                ),
                accountId = accountId,
                note = note.trim()
            )
        }
    }

    fun markSettled(entry: LedgerEntryEntity, accountId: Int) {
        viewModelScope.launch {
            financeRepo.settleDebt(entry.id, accountId, "Settled ${entry.contactName}")
        }
    }

    fun deleteEntry(entry: LedgerEntryEntity) {
        viewModelScope.launch {
            financeRepo.deleteLedgerEntry(entry)
        }
    }
}
