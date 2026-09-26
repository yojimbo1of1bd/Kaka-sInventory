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

    fun executeFinancialCommand(
        command: com.projectkaka.inventory.search.FinancialCommand,
        onResult: (String, Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val account = if (command.accountToken != null) {
                financeRepo.resolveAccount(command.accountToken)
            } else {
                financeRepo.resolveAccount("Cash")
            }
            
            val category = if (command.categoryToken != null) {
                financeRepo.resolveCategory(command.categoryToken)
            } else {
                if (command.isCredit) financeRepo.resolveCategory("Uncategorized Income")
                else financeRepo.resolveCategory("Uncategorized Expense")
            }

            if (account != null && category != null) {
                val transaction = com.projectkaka.inventory.data.local.entity.TransactionEntity(
                    accountId = account.id,
                    categoryId = category.id,
                    amount = com.projectkaka.inventory.model.Money((command.amount * 100).toLong()),
                    isCredit = command.isCredit,
                    note = command.note,
                    timestamp = System.currentTimeMillis()
                )
                financeRepo.recordTransaction(transaction)
                onResult("Success: ${if (command.isCredit) "+" else "-"}৳${command.amount} ${account.name} -> ${category.name}", true)
            } else {
                val accName = command.accountToken ?: "Cash"
                val catName = command.categoryToken ?: "Uncategorized"
                onResult("Error: Unknown account '$accName' or category '$catName'", false)
            }
        }
    }

    fun initializeAccountBalance(accountAlias: String, targetBalance: Double) {
        viewModelScope.launch {
            financeRepo.initializeAccountBalance(accountAlias, com.projectkaka.inventory.model.Money((targetBalance * 100).toLong()))
        }
    }

    fun createAccount(name: String, type: com.projectkaka.inventory.data.local.entity.AccountType, initialBalance: Double) {
        viewModelScope.launch {
            val acc = AccountEntity(
                name = name.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() },
                type = type,
                openingBalance = com.projectkaka.inventory.model.Money((initialBalance * 100).toLong())
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
