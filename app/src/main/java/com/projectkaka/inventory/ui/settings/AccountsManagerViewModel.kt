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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsManagerUiState(
    val accountsWithCounts: List<AccountWithCounts> = emptyList(),
    val isLoading: Boolean = true
)

class AccountsManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val financeRepo = getApplication<KakaApplication>().financeRepository

    private val preferences = getApplication<KakaApplication>().preferences

    val uiState: StateFlow<AccountsManagerUiState> = combine(
        financeRepo.observeAccountsWithCounts(),
        preferences.businessMode
    ) { accounts, businessMode ->
        val filtered = if (businessMode) {
            accounts
        } else {
            accounts.filter {
                val n = it.account.name.lowercase()
                n != "prepaid expenses" && n != "unearned revenue" && n != "accounts receivable" && n != "accounts payable"
            }
        }
        AccountsManagerUiState(
            accountsWithCounts = filtered,
            isLoading = false
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AccountsManagerUiState()
        )

    fun createAccount(name: String, type: AccountType, initialBalanceStr: String, onResult: (Boolean, String) -> Unit) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            onResult(false, "Account name cannot be empty.")
            return
        }
        viewModelScope.launch {
            val balance = try {
                Money.fromDecimalString(initialBalanceStr.ifBlank { "0.0" })
            } catch (e: Exception) {
                onResult(false, "Invalid initial balance format.")
                return@launch
            }
            val formattedName = trimmedName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            
            try {
                val existing = financeRepo.resolveAccountsExact(formattedName)
                if (existing.isNotEmpty()) {
                    onResult(false, "An account named '$formattedName' already exists.")
                    return@launch
                }
                val acc = com.projectkaka.inventory.data.local.entity.AccountEntity(
                    name = formattedName,
                    type = type,
                    openingBalance = balance
                )
                financeRepo.insertAccount(acc)
                onResult(true, "Account '$formattedName' created successfully.")
            } catch (e: Exception) {
                android.util.Log.e("AccountsManager", "Error creating account", e)
                onResult(false, e.message ?: "Failed to create account.")
            }
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

    fun updateAccount(accountId: Int, newName: String, newType: AccountType, newOpeningBalanceStr: String, onResult: (Boolean, String) -> Unit) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            onResult(false, "Account name cannot be empty.")
            return
        }
        viewModelScope.launch {
            val balance = try {
                Money.fromDecimalString(newOpeningBalanceStr.ifBlank { "0.0" })
            } catch (e: Exception) {
                onResult(false, "Invalid balance format.")
                return@launch
            }
            val account = uiState.value.accountsWithCounts.find { it.account.id == accountId }?.account
            if (account == null) {
                onResult(false, "Account not found.")
                return@launch
            }
            val formattedName = trimmedName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            if (!formattedName.equals(account.name, ignoreCase = true)) {
                val existing = financeRepo.resolveAccountsExact(formattedName)
                if (existing.isNotEmpty()) {
                    onResult(false, "An account named '$formattedName' already exists.")
                    return@launch
                }
            }
            val updated = account.copy(name = formattedName, type = newType, openingBalance = balance)
            financeRepo.updateAccount(updated)
            onResult(true, "Account updated successfully.")
        }
    }

    fun updateOpeningBalance(accountId: Int, newBalanceStr: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val balance = try {
                Money.fromDecimalString(newBalanceStr)
            } catch (e: Exception) {
                onResult(false, "Invalid balance format.")
                return@launch
            }
            val account = uiState.value.accountsWithCounts.find { it.account.id == accountId }?.account
            if (account != null) {
                financeRepo.updateAccount(account.copy(openingBalance = balance))
                onResult(true, "Balance updated successfully.")
            } else {
                onResult(false, "Account not found.")
            }
        }
    }
}
