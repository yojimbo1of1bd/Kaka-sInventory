package com.projectkaka.inventory.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.core.entry.ChartEntryModel
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import com.projectkaka.inventory.data.local.entity.TransactionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class TimelineFilter(val label: String) {
    DAYS_7("7 Days"),
    DAYS_30("30 Days"),
    MONTHS_3("3 Months"),
    ALL_TIME("All Time")
}

data class GraphUiState(
    val entryModel: ChartEntryModel? = null,
    val labels: Map<Float, String> = emptyMap(),
    val isLoading: Boolean = true,
    val timelineFilter: TimelineFilter = TimelineFilter.DAYS_30,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<FinancialCategoryEntity> = emptyList(),
    val selectedAccounts: Set<Int> = emptySet(),
    val selectedCategories: Set<Int> = emptySet(),
    val compareByAccount: Boolean = false, // false = compare by Category
    
    // ── Summary data for display ──
    val seriesNames: List<String> = emptyList(),
    val legendColors: List<Int> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class GraphViewModel(application: Application) : AndroidViewModel(application) {
    private val financeRepo = getApplication<KakaApplication>().financeRepository

    private val _timelineFilter = MutableStateFlow(TimelineFilter.DAYS_30)
    private val _selectedAccounts = MutableStateFlow<Set<Int>>(emptySet())
    private val _selectedCategories = MutableStateFlow<Set<Int>>(emptySet())
    private val _compareByAccount = MutableStateFlow(false)

    private val filterState = combine(
        _timelineFilter,
        _selectedAccounts,
        _selectedCategories,
        _compareByAccount
    ) { filter, selAcc, selCat, byAcc ->
        FilterState(filter, selAcc, selCat, byAcc)
    }

    private data class FilterState(
        val filter: TimelineFilter,
        val selAcc: Set<Int>,
        val selCat: Set<Int>,
        val byAcc: Boolean
    )

    val uiState: StateFlow<GraphUiState> = combine(
        filterState,
        financeRepo.getActiveAccounts(),
        financeRepo.getAllCategories()
    ) { filters, accs, cats ->
        Triple(filters, accs, cats)
    }.flatMapLatest { (filters, accs, cats) ->
        val filter = filters.filter
        val selAcc = filters.selAcc
        val selCat = filters.selCat
        val byAcc = filters.byAcc
        
        val (startMs, endMs) = getRangeForFilter(filter)
        
        financeRepo.getTransactionsInRange(startMs, endMs).map { transactions ->
            buildGraphState(transactions, accs, cats, filter, selAcc, selCat, byAcc, startMs, endMs)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GraphUiState())
    
    /**
     * Builds the graph state showing running balance trajectories per account/category.
     * 
     * The key insight: instead of just showing expense bars (which may all be zero),
     * we show how each account's/category's balance MOVES over time. For example:
     *   - Yesterday balance was 500
     *   - Spent 250 → balance drops to 250
     *   - Someone gifted 200 → balance rises to 450
     * Each data point is marked on the chart, showing the trajectory.
     */
    private fun buildGraphState(
        transactions: List<TransactionEntity>,
        accs: List<AccountEntity>,
        cats: List<FinancialCategoryEntity>,
        filter: TimelineFilter,
        selAcc: Set<Int>,
        selCat: Set<Int>,
        byAcc: Boolean,
        startMs: Long,
        endMs: Long
    ): GraphUiState {
        val zone = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("MMM dd")
        val today = LocalDate.now()
        
        // Filter transactions by selected accounts/categories
        val filtered = transactions.filter { t ->
            val accOk = selAcc.isEmpty() || selAcc.contains(t.accountId)
            val catOk = selCat.isEmpty() || selCat.contains(t.categoryId)
            accOk && catOk
        }

        // Generate date range safely ensuring at least 2 points
        val txByDay = filtered.groupBy { t ->
            Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalDate()
        }
        val allDates = txByDay.keys.toMutableSet()
        allDates.add(today)
        if (filter != TimelineFilter.ALL_TIME && startMs > 0L) {
            allDates.add(Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate())
        }
        if (allDates.size == 1) {
            allDates.add(today.minusDays(7)) // fallback
        }
        val sorted = allDates.sorted()
        val minDate = sorted.first()
        val maxDate = sorted.last()
        val dateRange = generateSequence(minDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(maxDate) }
            .toList()

        val xLabels = mutableMapOf<Float, String>()
        dateRange.forEachIndexed { index, date ->
            xLabels[index.toFloat()] = date.format(formatter)
        }
        
        // ── When comparing by account, include opening balances ──
        if (byAcc) {
            val targetAccounts = if (selAcc.isEmpty()) accs else accs.filter { selAcc.contains(it.id) }
            
            // If no transactions AND no accounts have opening balances, show empty
            val hasAnyData = filtered.isNotEmpty() || targetAccounts.any { it.openingBalance != 0.0 }
            if (!hasAnyData) {
                return GraphUiState(
                    entryModel = null,
                    labels = emptyMap(),
                    isLoading = false,
                    timelineFilter = filter,
                    accounts = accs,
                    categories = cats,
                    selectedAccounts = selAcc,
                    selectedCategories = selCat,
                    compareByAccount = byAcc
                )
            }
            
            val seriesMap = mutableMapOf<String, MutableList<FloatEntry>>()
            val seriesNames = mutableListOf<String>()
            
            targetAccounts.forEach { acc ->
                seriesNames.add(acc.name)
                // Start with opening balance
                var runningBalance = acc.openingBalance
                val entries = mutableListOf<FloatEntry>()
                
                dateRange.forEachIndexed { index, date ->
                    val dayTxs = txByDay[date]?.filter { it.accountId == acc.id } ?: emptyList()
                    dayTxs.forEach { tx ->
                        if (tx.isCredit) runningBalance += tx.amount
                        else runningBalance -= tx.amount
                    }
                    entries.add(FloatEntry(index.toFloat(), runningBalance.toFloat()))
                }
                seriesMap[acc.name] = entries
            }
            
            // Keep series that have any non-zero values
            val validNames = seriesMap.entries
                .filter { (_, list) -> list.any { it.y != 0f } }
                .map { it.key }
            
            val validSeries = validNames.mapNotNull { seriesMap[it] }
                .takeIf { it.isNotEmpty() }
            
            val model = validSeries?.let { entryModelOf(*it.toTypedArray()) }
            
            return GraphUiState(
                entryModel = model,
                labels = xLabels,
                isLoading = false,
                timelineFilter = filter,
                accounts = accs,
                categories = cats,
                selectedAccounts = selAcc,
                selectedCategories = selCat,
                compareByAccount = byAcc,
                seriesNames = validNames
            )
        }
        
        // ── Category view ──
        if (filtered.isEmpty()) {
            return GraphUiState(
                entryModel = null,
                labels = emptyMap(),
                isLoading = false,
                timelineFilter = filter,
                accounts = accs,
                categories = cats,
                selectedAccounts = selAcc,
                selectedCategories = selCat,
                compareByAccount = byAcc
            )
        }
        
        val seriesMap = mutableMapOf<String, MutableList<FloatEntry>>()
        val seriesNames = mutableListOf<String>()
        
        val targetCats = if (selCat.isEmpty()) cats else cats.filter { selCat.contains(it.id) }
        targetCats.forEach { cat ->
            seriesNames.add(cat.name)
            var runningTotal = 0.0
            val entries = mutableListOf<FloatEntry>()
            
            dateRange.forEachIndexed { index, date ->
                val dayTxs = txByDay[date]?.filter { it.categoryId == cat.id } ?: emptyList()
                dayTxs.forEach { tx ->
                    if (tx.isCredit) runningTotal += tx.amount
                    else runningTotal -= tx.amount
                }
                entries.add(FloatEntry(index.toFloat(), runningTotal.toFloat()))
            }
            seriesMap[cat.name] = entries
        }
        
        // Keep series that have any non-zero values (even negative)
        val validNames = seriesMap.entries
            .filter { (_, list) -> list.any { it.y != 0f } }
            .map { it.key }
        
        val validSeries = validNames.mapNotNull { seriesMap[it] }
            .takeIf { it.isNotEmpty() }
        
        val model = validSeries?.let { entryModelOf(*it.toTypedArray()) }
        
        return GraphUiState(
            entryModel = model,
            labels = xLabels,
            isLoading = false,
            timelineFilter = filter,
            accounts = accs,
            categories = cats,
            selectedAccounts = selAcc,
            selectedCategories = selCat,
            compareByAccount = byAcc,
            seriesNames = validNames
        )
    }
    
    fun setTimelineFilter(filter: TimelineFilter) { _timelineFilter.value = filter }
    fun toggleAccount(id: Int) {
        val current = _selectedAccounts.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedAccounts.value = current
    }
    fun toggleCategory(id: Int) {
        val current = _selectedCategories.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedCategories.value = current
    }
    fun setCompareMode(byAccount: Boolean) { _compareByAccount.value = byAccount }

    private fun getRangeForFilter(filter: TimelineFilter): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val start = when (filter) {
            TimelineFilter.DAYS_7 -> LocalDate.now().minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            TimelineFilter.DAYS_30 -> LocalDate.now().minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
            TimelineFilter.MONTHS_3 -> LocalDate.now().minusMonths(3).atStartOfDay(zone).toInstant().toEpochMilli()
            TimelineFilter.ALL_TIME -> 0L
        }
        return start to end
    }
}
