package com.projectkaka.inventory.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.data.local.entity.ItemStatus
import com.projectkaka.inventory.data.local.entity.TransactionEntity
import com.projectkaka.inventory.data.repository.DueTaskRow
import com.projectkaka.inventory.data.repository.ResolvedTransaction
import com.projectkaka.inventory.data.triage.DueTask
import com.projectkaka.inventory.data.triage.TriageClock
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
    val cashRecovered: Double = 0.0,

    // ── Financial ──
    val dailyBudget: Double = 0.0,
    val totalCashBalance: Double = 0.0,
    val remainingDays: Int = 0,
    val todaySpending: Double = 0.0,
    val pendingTransaction: ResolvedTransaction? = null,
    val lastTransactionMessage: String? = null,
    
    // ── Settings ──
    val showQuickLog: Boolean = true,
    val qlDebitAcc: String = "Cash",
    val qlDebitCat: String = "Uncategorized Expense",
    val qlCreditAcc: String = "Cash",
    val qlCreditCat: String = "Uncategorized Income",
    val triggerEmergencyAlert: Boolean = false,
    val overspendStrikeCount: Int = 0,
    val accountBalances: List<com.projectkaka.inventory.data.local.dao.AccountBalanceRow> = emptyList()
)

private data class ListState(
    val query: String,
    val items: List<ItemEntity>,
    val error: String?
)

private data class BudgetState(
    val totalCashBalance: Double,
    val dailyBudget: Double,
    val remainingDays: Int,
    val todaySpending: Double
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
    private val _pendingTransaction = MutableStateFlow<ResolvedTransaction?>(null)
    private val _lastTransactionMsg = MutableStateFlow<String?>(null)
    
    private val _pendingAction = MutableStateFlow<String?>(null)
    val pendingAction: StateFlow<String?> = _pendingAction

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
                _pendingTransaction.value = null
                repository.getActiveItems()
            } else {
                when (val result = MagicInputParser.parse(text)) {
                    is ParseResult.Success -> {
                        _searchError.value = null
                        _pendingTransaction.value = null
                        repository.searchItems(result.value.query)
                    }
                    is ParseResult.Financial -> {
                        _searchError.value = null
                        resolveFinancialCommand(result.command)
                        flowOf(emptyList())
                    }
                    is ParseResult.Error -> {
                        _searchError.value = result.message
                        _pendingTransaction.value = null
                        flowOf(emptyList())
                    }
                    is ParseResult.Action -> {
                        _searchError.value = null
                        _pendingTransaction.value = null
                        _pendingAction.value = result.actionType
                        _query.value = "" // clear query so it doesn't try to search for the command
                        flowOf(emptyList())
                    }
                    is ParseResult.InitAccount -> {
                        _searchError.value = null
                        _pendingTransaction.value = null
                        viewModelScope.launch {
                            financeRepo.initializeAccountBalance(result.accountAlias, result.amount)
                            _lastTransactionMsg.value = "Initialized ${result.accountAlias} to ৳${result.amount}"
                            _query.value = ""
                        }
                        flowOf(emptyList())
                    }
                    is ParseResult.AlterAccount -> {
                        _searchError.value = null
                        _pendingTransaction.value = null
                        viewModelScope.launch {
                            val account = financeRepo.resolveAccount(result.accountAlias)
                            if (account != null) {
                                financeRepo.updateAccount(account.copy(name = result.newName))
                                _lastTransactionMsg.value = "Renamed ${account.name} → ${result.newName}"
                            } else {
                                _searchError.value = "Account \"${result.accountAlias}\" not found."
                            }
                            _query.value = ""
                        }
                        flowOf(emptyList())
                    }
                    is ParseResult.DeleteAccount -> {
                        _searchError.value = null
                        _pendingTransaction.value = null
                        viewModelScope.launch {
                            val account = financeRepo.resolveAccount(result.accountAlias)
                            if (account != null) {
                                financeRepo.deleteAccount(account)
                                _lastTransactionMsg.value = "Deleted account: ${account.name}"
                            } else {
                                _searchError.value = "Account \"${result.accountAlias}\" not found."
                            }
                            _query.value = ""
                        }
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
            financeRepo.observeTotalCashBalance(),
            financeRepo.observeDaySpending(dayStartMs, dayEndMs)
        ) { cashBalance, todaySpend ->
            val remainingDays = today.lengthOfMonth() - today.dayOfMonth + 1
            val budget = if (remainingDays > 0) cashBalance / remainingDays else 0.0
            BudgetState(cashBalance, budget, remainingDays, todaySpend)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BudgetState(0.0, 0.0, 0, 0.0)
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
            combine(_pendingTransaction, _lastTransactionMsg, quickLogState) { pending, msg, ql -> Triple(pending, msg, ql) },
            combine(_triggerEmergencyAlert, _overspendStrikeCount, financeRepo.observeAllAccountBalances()) { alert, strikes, balances -> Triple(alert, strikes, balances) }
        ) { list, due, statsAndBudget, pendingGroup, alertGroup ->
            val stats = statsAndBudget.first
            val budget = statsAndBudget.second
            val pending = pendingGroup.first
            val msg = pendingGroup.second
            val ql = pendingGroup.third
            val alert = alertGroup.first
            val strikes = alertGroup.second
            val balances = alertGroup.third
            
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
                pendingTransaction = pending,
                lastTransactionMessage = msg,
                showQuickLog = ql.showQuickLog,
                qlDebitAcc = ql.qlDebitAcc,
                qlDebitCat = ql.qlDebitCat,
                qlCreditAcc = ql.qlCreditAcc,
                qlCreditCat = ql.qlCreditCat,
                triggerEmergencyAlert = alert,
                overspendStrikeCount = strikes,
                accountBalances = balances
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
        _pendingTransaction.value = null
    }

    fun clearAction() {
        _pendingAction.value = null
        _query.value = ""
    }

    fun liquidate(item: ItemEntity, status: ItemStatus, recovered: Double) {
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

    // ── Financial commands ───────────────────────────────────────────────

    /**
     * Called when the user taps the ✓ Record button on the confirmation card.
     * Inserts the transaction and shows a success message.
     */
    fun confirmTransaction() {
        val resolved = _pendingTransaction.value ?: return
        viewModelScope.launch {
            financeRepo.recordTransaction(
                TransactionEntity(
                    amount = resolved.amount,
                    accountId = resolved.account.id,
                    categoryId = resolved.category.id,
                    isCredit = resolved.isCredit,
                    note = resolved.note
                )
            )
            val direction = if (resolved.isCredit) "to" else "from"
            _lastTransactionMsg.value =
                "Recorded ৳${resolved.amount} $direction ${resolved.account.name} → ${resolved.category.name}"
            
            // ── 3-Strike Overspend Check ──
            if (!resolved.isCredit) {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now()
                val dayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEndMs = today.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
                
                val todayTotal = financeRepo.getDaySpending(dayStartMs, dayEndMs)
                val cashBalance = budgetState.value.totalCashBalance
                val remainingDays = budgetState.value.remainingDays
                val dailyBudget = if (remainingDays > 0) cashBalance / remainingDays else 0.0
                
                if (todayTotal > dailyBudget && dailyBudget > 0) {
                    val strikes = prefs.incrementOverspendStrike()
                    _overspendStrikeCount.value = strikes
                    
                    if (strikes >= 3) {
                        _triggerEmergencyAlert.value = true
                        prefs.resetOverspendStrikes()
                    }
                }
            }
            
            _pendingTransaction.value = null
            _query.value = ""
        }
    }

    fun clearEmergencyAlert() {
        _triggerEmergencyAlert.value = false
    }

    /** Called when the user taps Cancel on the financial confirmation card. */
    fun cancelTransaction() {
        _pendingTransaction.value = null
        _query.value = ""
        _searchError.value = null
    }

    /** Dismiss the "transaction recorded" snackbar message. */
    fun clearTransactionMessage() {
        _lastTransactionMsg.value = null
    }

    // ── Private helpers ─────────────────────────────────────────────────

    /**
     * Resolves account + category aliases from the raw parsed command.
     * If both resolve, shows a confirmation card. Otherwise shows an error.
     */
    private suspend fun resolveFinancialCommand(
        command: com.projectkaka.inventory.search.FinancialCommand
    ) {
        val account = financeRepo.resolveAccount(command.accountToken)
        val category = financeRepo.resolveCategory(command.categoryToken)

        if (account != null && category != null) {
            _pendingTransaction.value = ResolvedTransaction(
                amount = command.amount,
                isCredit = command.isCredit,
                account = account,
                category = category,
                note = command.note
            )
            _searchError.value = null
        } else {
            _pendingTransaction.value = null
            _searchError.value = buildString {
                if (account == null) append("Unknown account: \"${command.accountToken}\". ")
                if (category == null) append("Unknown category: \"${command.categoryToken}\".")
                append("\nTip: kaka show alias to see all shortcuts")
            }
        }
    }
}
