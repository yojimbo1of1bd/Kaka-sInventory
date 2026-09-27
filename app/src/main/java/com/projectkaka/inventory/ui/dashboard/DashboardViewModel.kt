package com.projectkaka.inventory.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.data.local.entity.ItemStatus
import com.projectkaka.inventory.data.repository.DueTaskRow
import com.projectkaka.inventory.data.triage.DueTask
import com.projectkaka.inventory.data.triage.TriageClock
import com.projectkaka.inventory.model.Money
import com.projectkaka.inventory.search.MagicInputParser
import com.projectkaka.inventory.search.ParseResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class DashboardUiState(
    val query: String = "",
    val items: List<ItemEntity> = emptyList(),
    val searchError: String? = null,
    val isFiltering: Boolean = false,
    val hasEmergency: Boolean = false,
    val dueTasks: List<DueTask> = emptyList(),
    val totalCleared: Int = 0,
    val cashRecovered: Money = Money(0),

    // ── Financial ──
    val dailyBudget: Money = Money(0),
    val totalCashBalance: Money = Money(0),
    val remainingDays: Int = 0,
    val todaySpending: Money = Money(0),
    
    // ── Settings ──
    val showQuickLog: Boolean = true,
    val qlDebitAcc: String = "Cash",
    val qlDebitCat: String = "Uncategorized Expense",
    val qlCreditAcc: String = "Cash",
    val qlCreditCat: String = "Uncategorized Income",
    val triggerEmergencyAlert: Boolean = false,
    val overspendStrikeCount: Int = 0,
    val accountBalances: List<com.projectkaka.inventory.data.local.entity.AccountEntity> = emptyList(),
    val categories: List<com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity> = emptyList()
)

private data class ListState(
    val query: String,
    val items: List<ItemEntity>,
    val error: String?
)

data class BudgetState(
    val totalCashBalance: Money,
    val dailyBudget: Money,
    val remainingDays: Int,
    val todaySpending: Money
)

private data class QuickLogState(
    val showQuickLog: Boolean,
    val qlDebitAcc: String,
    val qlDebitCat: String,
    val qlCreditAcc: String,
    val qlCreditCat: String
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = getApplication<KakaApplication>().repository
    private val financeRepo = getApplication<KakaApplication>().financeRepository
    private val prefs = getApplication<KakaApplication>().preferences

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _searchError = MutableStateFlow<String?>(null)

    private val _triggerEmergencyAlert = MutableStateFlow(false)
    private val _overspendStrikeCount = MutableStateFlow(0)

    /**
     * Single running clock for all triage math. Emits immediately, then hourly,
     * so the Red Ring reflects the current day without aggressive polling.
     */
    private val clock: StateFlow<Long> = TriageClock.ticks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    // ════════════════════════════════════════════════════════════════════
    //  Magic Input Bar: query -> parse -> @RawQuery  OR  f/ -> financial
    // ════════════════════════════════════════════════════════════════════

    private val searchResults: StateFlow<List<ItemEntity>> = _query
        .debounce(180)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (text.isEmpty()) {
                _searchError.value = null
                repository.getActiveItems()
            } else {
                when (val result = MagicInputParser.parse(text)) {
                    is ParseResult.Success -> {
                        _searchError.value = null
                        repository.searchItems(result.value.query)
                    }
                    is ParseResult.Error -> {
                        _searchError.value = result.message
                        flowOf(emptyList())
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val listState: StateFlow<ListState> =
        combine(_query, searchResults, _searchError) { query, items, error ->
            ListState(query, items, error)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ListState("", emptyList(), null)
        )

    // ════════════════════════════════════════════════════════════════════
    //  Triage: the joined rows are re-queried whenever the clock ticks
    // ════════════════════════════════════════════════════════════════════

    private val dueTaskRows: StateFlow<List<DueTaskRow>> = clock
        .flatMapLatest { now -> repository.getDueTaskDetail(now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val dueTasks: StateFlow<List<DueTask>> =
        combine(dueTaskRows, clock) { rows, now ->
            rows.map { row ->
                val task = CareTaskEntity(
                    id = row.id,
                    itemId = row.itemId,
                    taskName = row.taskName,
                    frequencyDays = row.frequencyDays,
                    lastCompletedDate = row.lastCompletedDate
                )
                DueTask(
                    task = task,
                    itemName = row.itemName,
                    itemImagePath = row.itemImagePath,
                    daysOverdue = DueTask.overdueDays(task, now)
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val statsState = combine(
        repository.getClearedItemsCount(),
        repository.getTotalCashRecovered()
    ) { cleared, cash -> cleared to cash }

    // ════════════════════════════════════════════════════════════════════
    //  Budget: total CASH balance / remaining days in month
    // ════════════════════════════════════════════════════════════════════

    private val budgetState: StateFlow<BudgetState> = run {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val dayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEndMs = today.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        val monthStartMs = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

        combine(
            financeRepo.observeAllAccountBalances(),
            financeRepo.observeDaySpending(dayStartMs, dayEndMs),
            prefs.hiddenAccountIds
        ) { accounts, todaySpend, hiddenIds ->
            computeBudgetState(accounts, todaySpend, hiddenIds, today)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BudgetState(Money(0), Money(0), 0, Money(0))
        )
    }

    // ════════════════════════════════════════════════════════════════════
    //  Combined UI state
    // ════════════════════════════════════════════════════════════════════

    private val quickLogState = combine(
        prefs.showQuickLog,
        prefs.defaultQuickLogDebitAccount,
        prefs.defaultQuickLogDebitCategory,
        prefs.defaultQuickLogCreditAccount,
        prefs.defaultQuickLogCreditCategory
    ) { show, dAcc, dCat, cAcc, cCat ->
        QuickLogState(show, dAcc, dCat, cAcc, cCat)
    }

    val uiState: StateFlow<DashboardUiState> =
        combine(
            listState,
            dueTasks,
            combine(statsState, budgetState) { s, b -> Pair(s, b) },
            quickLogState,
            combine(_triggerEmergencyAlert, _overspendStrikeCount, financeRepo.observeAllAccountBalances(), financeRepo.getAllCategories(), prefs.hiddenAccountIds) { alert, strikes, balances, cats, hiddenIds -> 
                listOf(alert, strikes, balances, cats, hiddenIds) 
            }
        ) { list, due, statsAndBudget, ql, alertGroup ->
            val stats = statsAndBudget.first
            val budget = statsAndBudget.second
            val alert = alertGroup[0] as Boolean
            val strikes = alertGroup[1] as Int
            val balances = alertGroup[2] as List<com.projectkaka.inventory.data.local.entity.AccountEntity>
            val cats = alertGroup[3] as List<com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity>
            val hiddenIds = alertGroup[4] as Set<Int>
            val visibleBalances = balances.filter { it.id !in hiddenIds }
            
            DashboardUiState(
                query = list.query,
                items = list.items,
                searchError = list.error,
                isFiltering = list.query.isNotBlank(),
                hasEmergency = due.isNotEmpty(),
                dueTasks = due,
                totalCleared = stats.first,
                cashRecovered = stats.second,
                dailyBudget = budget.dailyBudget,
                totalCashBalance = budget.totalCashBalance,
                remainingDays = budget.remainingDays,
                todaySpending = budget.todaySpending,
                showQuickLog = ql.showQuickLog,
                qlDebitAcc = ql.qlDebitAcc,
                qlDebitCat = ql.qlDebitCat,
                qlCreditAcc = ql.qlCreditAcc,
                qlCreditCat = ql.qlCreditCat,
                triggerEmergencyAlert = alert,
                overspendStrikeCount = strikes,
                accountBalances = visibleBalances,
                categories = cats
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    // ════════════════════════════════════════════════════════════════════
    //  Public actions
    // ════════════════════════════════════════════════════════════════════

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clearQuery() {
        _query.value = ""
        _searchError.value = null
    }

    private val terminalExecutor by lazy {
        com.projectkaka.inventory.search.TerminalExecutor(financeRepo)
    }

    fun executeTerminalCommand(input: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = terminalExecutor.execute(input)
            val msg = when (result) {
                is com.projectkaka.inventory.search.TerminalResult.Success -> result.message
                is com.projectkaka.inventory.search.TerminalResult.Failure -> result.reason
                is com.projectkaka.inventory.search.TerminalResult.NeedsInput -> result.question
                is com.projectkaka.inventory.search.TerminalResult.Pending -> "Command pending"
            }
            onResult(msg)
        }
    }

    fun liquidate(item: ItemEntity, status: ItemStatus, recovered: Money) {
        viewModelScope.launch { repository.liquidateItem(item.id, status, recovered) }
    }

    /** Mark a care task done: resets the timer and clears it from the due list. */
    fun completeTask(task: CareTaskEntity) {
        viewModelScope.launch { repository.completeCareTask(task) }
    }

    /** Attach a recurring maintenance task to an item. */
    fun addCareTask(itemId: Int, taskName: String, frequencyDays: Int) {
        viewModelScope.launch {
            repository.saveCareTask(
                CareTaskEntity(
                    itemId = itemId,
                    taskName = taskName.trim(),
                    frequencyDays = frequencyDays.coerceAtLeast(1)
                )
            )
        }
    }

    fun clearEmergencyAlert() {
        _triggerEmergencyAlert.value = false
    }

    companion object {
        fun computeBudgetState(
            accounts: List<com.projectkaka.inventory.data.local.entity.AccountEntity>,
            todaySpend: Money,
            hiddenIds: Set<Int>,
            today: java.time.LocalDate
        ): BudgetState {
            val visibleAccounts = accounts.filter { it.id !in hiddenIds }
            val cashBalance = Money(visibleAccounts.sumOf { it.balance.minorUnits })
            val remainingDays = today.lengthOfMonth() - today.dayOfMonth + 1
            val budget = if (remainingDays > 0) Money(cashBalance.minorUnits / remainingDays) else Money(0)
            return BudgetState(cashBalance, budget, remainingDays, todaySpend)
        }
    }
}
