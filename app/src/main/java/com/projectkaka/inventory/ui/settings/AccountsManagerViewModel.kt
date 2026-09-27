package com.projectkaka.inventory.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.dao.AccountWithCounts
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.model.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsManagerUiState(
    val accountsWithCounts: List<AccountWithCounts> = emptyList(),
    val isLoading: Boolean = true
)

class AccountsManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val financeRepo = getApplication<KakaApplication>().financeRepository

    val uiState: StateFlow<AccountsManagerUiState> = financeRepo.observeAccountsWithCounts()
        .map { accounts ->
            AccountsManagerUiState(
                accountsWithCounts = accounts,
                isLoading = false
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AccountsManagerUiState()
        )

    fun createAccount(name: String, type: AccountType, initialBalance: Double) {
        viewModelScope.launch {
            val acc = com.projectkaka.inventory.data.local.entity.AccountEntity(
                name = name.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() },
                type = type,
                openingBalance = Money.fromDouble(initialBalance)
            )
            financeRepo.insertAccount(acc)
        }
    }

    fun archiveAccount(accountId: Int, archive: Boolean) {
        viewModelScope.launch {
            val accounts = uiState.value.accountsWithCounts
            val account = accounts.find { it.account.id == accountId }?.account
            if (account != null) {
                financeRepo.updateAccount(account.copy(isActive = !archive))
            }
        }
    }

    fun deleteAccount(accountId: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val awc = uiState.value.accountsWithCounts.find { it.account.id == accountId }
            if (awc == null) {
                onResult(false, "Account not found.")
                return@launch
            }
            if (awc.transactionCount > 0 || awc.ledgerLinkCount > 0) {
                onResult(false, "Cannot delete account with existing transactions or ledger entries. Please archive it instead.")
                return@launch
            }
            financeRepo.deleteAccount(awc.account)
            onResult(true, "Account deleted successfully.")
        }
    }

    fun updateOpeningBalance(accountId: Int, newBalance: Double) {
        viewModelScope.launch {
            val account = uiState.value.accountsWithCounts.find { it.account.id == accountId }?.account
            if (account != null) {
                financeRepo.updateAccount(account.copy(openingBalance = Money.fromDouble(newBalance)))
            }
        }
    }
}
