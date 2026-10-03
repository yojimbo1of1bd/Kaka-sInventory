package com.projectkaka.inventory.ui.liquidate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.LedgerType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LedgerUiState(
    val entries: List<LedgerEntryEntity> = emptyList(),
    val summaries: List<com.projectkaka.inventory.data.local.dao.ContactSummaryRow> = emptyList(),
    val accounts: List<com.projectkaka.inventory.data.local.entity.AccountEntity> = emptyList(),
    val totalReceivable: com.projectkaka.inventory.model.Money = com.projectkaka.inventory.model.Money(0),
    val totalPayable: com.projectkaka.inventory.model.Money = com.projectkaka.inventory.model.Money(0),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val filterSettled: Boolean? = false // null = all, false = unsettled, true = settled
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val financeRepo = getApplication<KakaApplication>().financeRepository
    private val prefs = getApplication<KakaApplication>().preferences

    val permContacts: StateFlow<Boolean> = prefs.permContacts
    val permSmsCalls: StateFlow<Boolean> = prefs.permSmsCalls
    val permMicrophone: StateFlow<Boolean> = prefs.permMicrophone

    val searchQuery = MutableStateFlow("")
    val filterSettled = MutableStateFlow<Boolean?>(false)
    val filterMinAmount = MutableStateFlow<Long?>(null)
    val filterMaxAmount = MutableStateFlow<Long?>(null)
    val filterMinDate = MutableStateFlow<Long?>(null)
    val filterMaxDate = MutableStateFlow<Long?>(null)

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val searchResults = combine(
        searchQuery.debounce(300),
        filterSettled, filterMinAmount, filterMaxAmount, filterMinDate, filterMaxDate
    ) { args -> args }
        .flatMapLatest { args ->
            val q = (args[0] as String).trim().takeIf { it.isNotEmpty() }
            val settled = args[1] as Boolean?
            val minA = args[2] as Long?
            val maxA = args[3] as Long?
            val minD = args[4] as Long?
            val maxD = args[5] as Long?
            financeRepo.searchLedgerEntries(q, settled, minA, maxA, minD, maxD)
        }

    val uiState: StateFlow<LedgerUiState> = combine(
        searchResults,
        financeRepo.observeContactSummaries(),
        financeRepo.getActiveAccounts(),
        financeRepo.observeTotalReceivable(),
        financeRepo.observeTotalPayable(),
        searchQuery,
        filterSettled
    ) { args ->
        LedgerUiState(
            entries = args[0] as List<LedgerEntryEntity>,
            summaries = args[1] as List<com.projectkaka.inventory.data.local.dao.ContactSummaryRow>,
            accounts = args[2] as List<com.projectkaka.inventory.data.local.entity.AccountEntity>,
            totalReceivable = args[3] as com.projectkaka.inventory.model.Money,
            totalPayable = args[4] as com.projectkaka.inventory.model.Money,
            isLoading = false,
            searchQuery = args[5] as String,
            filterSettled = args[6] as Boolean?
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LedgerUiState()
    )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateFilterSettled(settled: Boolean?) {
        filterSettled.value = settled
    }

    fun addEntry(contactName: String, contactPhone: String, amountStr: String, type: LedgerType, note: String, accountId: Int, dueDate: Long? = null) {
        if (contactName.isBlank()) return
        viewModelScope.launch {
            financeRepo.issueDebt(
                entry = LedgerEntryEntity(
                    contactName = contactName.trim(),
                    contactPhone = contactPhone.trim(),
                    amount = try { com.projectkaka.inventory.model.Money.fromDecimalString(amountStr) } catch (e: Exception) { com.projectkaka.inventory.model.Money.ZERO },
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
