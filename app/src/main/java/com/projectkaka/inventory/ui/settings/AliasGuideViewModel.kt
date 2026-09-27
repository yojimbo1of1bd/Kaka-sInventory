package com.projectkaka.inventory.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AliasGuideUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<FinancialCategoryEntity> = emptyList(),
    val hiddenAccountIds: Set<Int> = emptySet(),
    val isLoading: Boolean = true
)

class AliasGuideViewModel(application: Application) : AndroidViewModel(application) {
    private val financeRepo = getApplication<KakaApplication>().financeRepository
    private val prefs = getApplication<KakaApplication>().preferences

    private val terminalExecutor by lazy { com.projectkaka.inventory.search.TerminalExecutor(financeRepo) }

    val uiState: StateFlow<AliasGuideUiState> = combine(
        financeRepo.getAllAccounts(),
        financeRepo.getAllCategories(),
        prefs.hiddenAccountIds
    ) { accounts, categories, hiddenIds ->
        AliasGuideUiState(
            accounts = accounts,
            categories = categories,
            hiddenAccountIds = hiddenIds,
            isLoading = false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AliasGuideUiState()
    )

    fun toggleAccountVisibility(accountId: Int, isHidden: Boolean) {
        prefs.toggleAccountVisibility(accountId, isHidden)
    }

    fun executeTerminalCommand(
        input: String,
        onResult: (com.projectkaka.inventory.search.TerminalResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = terminalExecutor.execute(input)
            onResult(result)
        }
    }

    fun initializeAccountBalance(accountAlias: String, targetBalance: Double) {
        viewModelScope.launch {
            financeRepo.initializeAccountBalance(accountAlias, com.projectkaka.inventory.model.Money.fromDouble(targetBalance))
        }
    }

    fun createAccount(name: String, type: com.projectkaka.inventory.data.local.entity.AccountType, initialBalance: Double) {
        viewModelScope.launch {
            val acc = AccountEntity(
                name = name.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() },
                type = type,
                openingBalance = com.projectkaka.inventory.model.Money.fromDouble(initialBalance)
            )
            financeRepo.insertAccount(acc)
        }
    }

    fun alterAccount(accountAlias: String, newName: String) {
        viewModelScope.launch {
            val account = financeRepo.resolveAccount(accountAlias)
            if (account != null) {
                financeRepo.updateAccount(account.copy(name = newName))
            }
        }
    }

    fun deleteAccount(accountAlias: String) {
        viewModelScope.launch {
            val account = financeRepo.resolveAccount(accountAlias)
            if (account != null) {
                financeRepo.deleteAccount(account)
            }
        }
    }
}
