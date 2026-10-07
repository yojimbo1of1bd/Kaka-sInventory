package com.projectkaka.inventory.ui.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StatementsUiState(
    val assets: List<AccountEntity> = emptyList(),
    val liabilities: List<AccountEntity> = emptyList(),
    val equity: List<AccountEntity> = emptyList(),
    val revenues: List<AccountEntity> = emptyList(),
    val expenses: List<AccountEntity> = emptyList(),
    val totalAssets: Long = 0L,
    val totalLiabilities: Long = 0L,
    val totalEquity: Long = 0L,
    val totalRevenue: Long = 0L,
    val totalExpense: Long = 0L,
    val netIncome: Long = 0L
)

class StatementsViewModel(application: Application) : AndroidViewModel(application) {
    private val financeRepo = getApplication<KakaApplication>().financeRepository

    val uiState: StateFlow<StatementsUiState> = financeRepo.observeAllAccountBalances()
        .map { accounts ->
            val isZeroPlaceholder = { name: String, bal: Long ->
                (name.equals("Assets", ignoreCase = true) || name.equals("Liabilities", ignoreCase = true)) && bal == 0L
            }
            val assets = accounts.filter { (it.type == AccountType.ASSET || it.type == AccountType.CASH) && !isZeroPlaceholder(it.name, it.balance.minorUnits) }
            val liabilities = accounts.filter { it.type == AccountType.LIABILITY && !isZeroPlaceholder(it.name, it.balance.minorUnits) }
            val equity = accounts.filter { it.type == AccountType.CAPITAL }
            val revenues = accounts.filter { it.type == AccountType.REVENUE }
            val expenses = accounts.filter { it.type == AccountType.EXPENSE }

            val totalAssets = assets.sumOf { it.balance.minorUnits }
            val totalLiabilities = liabilities.sumOf { it.balance.minorUnits }
            val totalEquityAcc = equity.sumOf { it.balance.minorUnits }
            
            val totalRevenue = revenues.sumOf { it.balance.minorUnits }
            val totalExpense = expenses.sumOf { it.balance.minorUnits }
            val netIncome = totalRevenue - totalExpense
            
            val totalEquity = totalEquityAcc + netIncome

            StatementsUiState(
                assets = assets,
                liabilities = liabilities,
                equity = equity,
                revenues = revenues,
                expenses = expenses,
                totalAssets = totalAssets,
                totalLiabilities = totalLiabilities,
                totalEquity = totalEquity,
                totalRevenue = totalRevenue,
                totalExpense = totalExpense,
                netIncome = netIncome
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StatementsUiState()
        )
}
