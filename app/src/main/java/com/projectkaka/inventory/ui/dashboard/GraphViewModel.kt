package com.projectkaka.inventory.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.core.entry.ChartEntryModel
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.LedgerType
import com.projectkaka.inventory.data.local.entity.TransactionEntity
import com.projectkaka.inventory.data.local.entity.TransactionType
import com.projectkaka.inventory.model.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class BucketSize(val label: String) {
    DAY("Daily"),
    WEEK("Weekly"),
    MONTH("Monthly")
}

enum class AnalyticsMode(val label: String) {
    OVERVIEW("Overview"),
    ACCOUNTS("Accounts"),
    CATEGORIES("Categories")
}

data class BucketTransaction(
    val id: Int,
    val accountName: String,
    val amount: Money,
    val isCredit: Boolean,
    val note: String,
    val type: TransactionType,
    val formattedTime: String
)

data class BucketDetail(
    val index: Int,
    val dateLabel: String,
    val fullDateRange: String,
    val netWorth: Money,
    val totalAssets: Money,
    val totalLiabilities: Money,
    val totalReceivables: Money,
    val accountBalances: Map<String, Money>,
    val categorySpend: Map<String, Money>,
    val transactions: List<BucketTransaction>
)

data class SummaryMetrics(
    val startBalance: Money = Money(0),
    val endBalance: Money = Money(0),
    val peakBalance: Money = Money(0),
    val lowBalance: Money = Money(0),
    val netChange: Money = Money(0)
)

data class GraphUiState(
    val entryModel: ChartEntryModel? = null,
    val labels: Map<Int, String> = emptyMap(),
    val isLoading: Boolean = true,
    val bucketSize: BucketSize = BucketSize.WEEK,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<FinancialCategoryEntity> = emptyList(),
    val selectedAccounts: Set<Int> = emptySet(),
    val selectedCategories: Set<Int> = emptySet(),
    val analyticsMode: AnalyticsMode = AnalyticsMode.OVERVIEW,
    val compareByAccount: Boolean = true, // Backward compatibility
    val seriesNames: List<String> = emptyList(),
    val bucketDetails: List<BucketDetail> = emptyList(),
    val selectedBucketIndex: Int? = null,
    val summaryMetrics: SummaryMetrics = SummaryMetrics()
) {
    val selectedBucketDetail: BucketDetail?
        get() = selectedBucketIndex?.let { idx -> bucketDetails.getOrNull(idx) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GraphViewModel(application: Application) : AndroidViewModel(application) {
    private val financeRepo = getApplication<KakaApplication>().financeRepository

    private val _bucketSize = MutableStateFlow(BucketSize.WEEK)
    private val _selectedAccounts = MutableStateFlow<Set<Int>>(emptySet())
    private val _selectedCategories = MutableStateFlow<Set<Int>>(emptySet())
    private val _analyticsMode = MutableStateFlow(AnalyticsMode.OVERVIEW)
    private val _selectedBucketIndex = MutableStateFlow<Int?>(null)

    private data class FilterState(
        val bucket: BucketSize,
        val selAcc: Set<Int>,
        val selCat: Set<Int>,
        val mode: AnalyticsMode,
        val selBucketIdx: Int?
    )

    private val filterState = combine(
        _bucketSize,
        _selectedAccounts,
        _selectedCategories,
        _analyticsMode,
        _selectedBucketIndex
    ) { bucket, selAcc, selCat, mode, selBucketIdx ->
        FilterState(bucket, selAcc, selCat, mode, selBucketIdx)
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<GraphUiState> = combine(
        filterState,
        financeRepo.getAllTransactions(),
        financeRepo.getAllAccounts(),
        financeRepo.getAllCategories(),
        financeRepo.getAllLedgerEntries()
    ) { args ->
        val filters = args[0] as FilterState
        val txs = args[1] as List<TransactionEntity>
        val accs = args[2] as List<AccountEntity>
        val cats = args[3] as List<FinancialCategoryEntity>
        val ledgers = args[4] as List<LedgerEntryEntity>
        buildGraphState(
            transactions = txs,
            accs = accs,
            cats = cats,
            ledgers = ledgers,
            bucketSize = filters.bucket,
            selAcc = filters.selAcc,
            selCat = filters.selCat,
            byAcc = filters.mode != AnalyticsMode.CATEGORIES,
            clock = java.time.Clock.systemDefaultZone(),
            mode = filters.mode,
            selectedBucketIndex = filters.selBucketIdx
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GraphUiState())

    companion object {
        internal fun buildGraphState(
            transactions: List<TransactionEntity>,
            accs: List<AccountEntity>,
            cats: List<FinancialCategoryEntity>,
            ledgers: List<LedgerEntryEntity>,
            bucketSize: BucketSize,
            selAcc: Set<Int>,
            selCat: Set<Int>,
            byAcc: Boolean,
            clock: java.time.Clock = java.time.Clock.systemDefaultZone(),
            mode: AnalyticsMode? = null,
            selectedBucketIndex: Int? = null
        ): GraphUiState {
            val zone = clock.zone
            val today = LocalDate.now(clock)

            val effectiveMode = mode ?: when {
                !byAcc -> AnalyticsMode.CATEGORIES
                selAcc.isEmpty() -> AnalyticsMode.OVERVIEW
                else -> AnalyticsMode.ACCOUNTS
            }

            val excludedSystemAccountNames = setOf(
                "Assets", "Capital", "Cost of Goods Sold", "COGS",
                "Operating Expenses", "Expenses", "Sales Revenue", "Inventory", "Loss"
            )
            val displayAccs = accs.filter {
                it.name !in excludedSystemAccountNames &&
                (it.type == AccountType.CASH ||
                 it.type == AccountType.ASSET ||
                 it.type == AccountType.LIABILITY)
            }

            if (transactions.isEmpty() && ledgers.isEmpty() && accs.all { it.openingBalance.minorUnits == 0L }) {
                return GraphUiState(
                    isLoading = false,
                    bucketSize = bucketSize,
                    accounts = displayAccs,
                    categories = cats,
                    selectedAccounts = selAcc,
                    selectedCategories = selCat,
                    analyticsMode = effectiveMode,
                    compareByAccount = effectiveMode != AnalyticsMode.CATEGORIES,
                    selectedBucketIndex = selectedBucketIndex
                )
            }

            // Find earliest date
            val earliestTxDate = transactions.minOfOrNull {
                Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
            } ?: today
            
            val minDate = minOf(earliestTxDate, today)

            // Generate buckets from minDate to today
            val buckets = mutableListOf<LocalDate>()
            var current = minDate
            while (!current.isAfter(today)) {
                val bucketDate = when (bucketSize) {
                    BucketSize.DAY -> current
                    BucketSize.WEEK -> current.with(java.time.DayOfWeek.MONDAY)
                    BucketSize.MONTH -> current.withDayOfMonth(1)
                }
                if (buckets.isEmpty() || buckets.last() != bucketDate) {
                    buckets.add(bucketDate)
                }
                current = current.plusDays(1)
            }

            // Ensure today's bucket is present
            val todayBucket = when (bucketSize) {
                BucketSize.DAY -> today
                BucketSize.WEEK -> today.with(java.time.DayOfWeek.MONDAY)
                BucketSize.MONTH -> today.withDayOfMonth(1)
            }
            if (buckets.isEmpty() || buckets.last() != todayBucket) {
                buckets.add(todayBucket)
            }

            // If buckets are too sparse (< 3), pad backwards so timeline breathes naturally
            if (buckets.size < 3) {
                buckets.clear()
                val paddedStart = when (bucketSize) {
                    BucketSize.DAY -> today.minusDays(6)
                    BucketSize.WEEK -> today.minusWeeks(3)
                    BucketSize.MONTH -> today.minusMonths(5)
                }
                var cur = paddedStart
                while (!cur.isAfter(today)) {
                    val bucketDate = when (bucketSize) {
                        BucketSize.DAY -> cur
                        BucketSize.WEEK -> cur.with(java.time.DayOfWeek.MONDAY)
                        BucketSize.MONTH -> cur.withDayOfMonth(1)
                    }
                    if (buckets.isEmpty() || buckets.last() != bucketDate) {
                        buckets.add(bucketDate)
                    }
                    cur = cur.plusDays(1)
                }
                if (buckets.isEmpty() || buckets.last() != todayBucket) {
                    buckets.add(todayBucket)
                }
            }

            // Smart unclipped X-Axis Label formatting
            val allSameYear = buckets.map { it.year }.distinct().size <= 1
            val xFormatter = when (bucketSize) {
                BucketSize.DAY -> DateTimeFormatter.ofPattern("MMM d")
                BucketSize.WEEK -> DateTimeFormatter.ofPattern("d MMM")
                BucketSize.MONTH -> if (allSameYear) {
                    DateTimeFormatter.ofPattern("MMM") // Short 3-char format: "May", "Jun", etc.
                } else {
                    DateTimeFormatter.ofPattern("MMM ''yy") // e.g. "May '26"
                }
            }

            val xLabels = buckets.mapIndexed { index, date ->
                index to date.format(xFormatter)
            }.toMap()

            // Group transactions by bucket
            val txByBucket = transactions.groupBy { tx ->
                val date = Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()
                when (bucketSize) {
                    BucketSize.DAY -> date
                    BucketSize.WEEK -> date.with(java.time.DayOfWeek.MONDAY)
                    BucketSize.MONTH -> date.withDayOfMonth(1)
                }
            }

            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a").withZone(zone)

            // Track per-account balances across timeline
            val accountRunningBalances = accs.associate { it.id to it.openingBalance }.toMutableMap()
            val bucketDetails = mutableListOf<BucketDetail>()

            val seriesMap = mutableMapOf<String, MutableList<FloatEntry>>()
            val seriesNames = mutableListOf<String>()

            when (effectiveMode) {
                AnalyticsMode.OVERVIEW -> {
                    seriesNames.addAll(listOf("Assets", "Net Worth", "Liabilities", "Receivables"))
                    seriesMap["Assets"] = mutableListOf()
                    seriesMap["Net Worth"] = mutableListOf()
                    seriesMap["Liabilities"] = mutableListOf()
                    seriesMap["Receivables"] = mutableListOf()
                }
                AnalyticsMode.ACCOUNTS -> {
                    val targetAccounts = if (selAcc.isEmpty()) displayAccs else accs.filter { selAcc.contains(it.id) }
                    targetAccounts.forEach { acc ->
                        seriesNames.add(acc.name)
                        seriesMap[acc.name] = mutableListOf()
                    }
                }
                AnalyticsMode.CATEGORIES -> {
                    val targetCats = if (selCat.isEmpty()) cats else cats.filter { selCat.contains(it.id) }
                    targetCats.forEach { cat ->
                        seriesNames.add(cat.name)
                        seriesMap[cat.name] = mutableListOf()
                    }
                }
            }

            val categoryCumulativeSpend = cats.associate { it.id to Money(0) }.toMutableMap()

            buckets.forEachIndexed { index, bucket ->
                val bTxs = txByBucket[bucket] ?: emptyList()

                // Update account balances with transactions in this bucket
                bTxs.forEach { tx ->
                    val currentBal = accountRunningBalances[tx.accountId] ?: Money(0)
                    val acc = accs.find { it.id == tx.accountId }
                    if (acc != null) {
                        val newBal = if (acc.type == AccountType.LIABILITY) {
                            if (tx.isCredit) currentBal + tx.amount else currentBal - tx.amount
                        } else {
                            if (!tx.isCredit) currentBal + tx.amount else currentBal - tx.amount
                        }
                        accountRunningBalances[tx.accountId] = newBal
                    }

                    // Update category spend
                    cats.forEach { cat ->
                        val isCatType = if (cat.type == com.projectkaka.inventory.data.local.entity.CategoryType.INCOME) {
                            tx.type == TransactionType.INCOME
                        } else {
                            tx.type == TransactionType.EXPENSE
                        }
                        if (isCatType && (
                            tx.categoryId == cat.id ||
                            cat.matchesInput(tx.note) ||
                            tx.note.split(" ").any { it.isNotBlank() && cat.matchesInput(it) } ||
                            (cat.name.isNotBlank() && tx.note.contains(cat.name, ignoreCase = true))
                        )) {
                            categoryCumulativeSpend[cat.id] = (categoryCumulativeSpend[cat.id] ?: Money(0)) + tx.amount
                        }
                    }
                }

                // Compute snapshot figures for this bucket date
                val bucketEndLocalDate = when (bucketSize) {
                    BucketSize.DAY -> bucket
                    BucketSize.WEEK -> bucket.plusDays(6)
                    BucketSize.MONTH -> bucket.withDayOfMonth(bucket.lengthOfMonth())
                }
                val bucketEndMillis = bucketEndLocalDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                val outstandingPayables = ledgers.filter {
                    it.type == LedgerType.PAYABLE &&
                    !it.isSettled &&
                    it.createdAt <= bucketEndMillis
                }.fold(Money(0)) { sum, entry -> sum + entry.amount }

                val outstandingReceivables = ledgers.filter {
                    it.type == LedgerType.RECEIVABLE &&
                    !it.isSettled &&
                    it.createdAt <= bucketEndMillis
                }.fold(Money(0)) { sum, entry -> sum + entry.amount }

                val totalAssets = accs.filter { it.type == AccountType.CASH || it.type == AccountType.ASSET }
                    .fold(Money(0)) { sum, acc -> sum + (accountRunningBalances[acc.id] ?: Money(0)) }

                val totalLiabilities = accs.filter { it.type == AccountType.LIABILITY }
                    .fold(Money(0)) { sum, acc -> sum + (accountRunningBalances[acc.id] ?: Money(0)) } + outstandingPayables

                val netWorth = totalAssets - totalLiabilities + outstandingReceivables

                // Map transactions for bucket drill-down detail
                val bucketTxItems = bTxs.map { tx ->
                    val accName = accs.find { it.id == tx.accountId }?.name ?: "Account #${tx.accountId}"
                    val timeStr = timeFormatter.format(Instant.ofEpochMilli(tx.timestamp))
                    BucketTransaction(
                        id = tx.id,
                        accountName = accName,
                        amount = tx.amount,
                        isCredit = tx.isCredit,
                        note = tx.note.ifBlank { "Transaction" },
                        type = tx.type,
                        formattedTime = timeStr
                    )
                }

                val fullRangeTitle = when (bucketSize) {
                    BucketSize.DAY -> bucket.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))
                    BucketSize.WEEK -> "${bucket.format(DateTimeFormatter.ofPattern("MMM d"))} – ${bucket.plusDays(6).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
                    BucketSize.MONTH -> bucket.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                }

                val accBalancesMap = accs.associate { it.name to (accountRunningBalances[it.id] ?: Money(0)) }
                val catSpendMap = cats.associate { it.name to (categoryCumulativeSpend[it.id] ?: Money(0)) }

                bucketDetails.add(
                    BucketDetail(
                        index = index,
                        dateLabel = xLabels[index] ?: "",
                        fullDateRange = fullRangeTitle,
                        netWorth = netWorth,
                        totalAssets = totalAssets,
                        totalLiabilities = totalLiabilities,
                        totalReceivables = outstandingReceivables,
                        accountBalances = accBalancesMap,
                        categorySpend = catSpendMap,
                        transactions = bucketTxItems
                    )
                )

                // Fill series data points for chart
                when (effectiveMode) {
                    AnalyticsMode.OVERVIEW -> {
                        seriesMap["Assets"]?.add(FloatEntry(index.toFloat(), totalAssets.minorUnits / 100f))
                        seriesMap["Net Worth"]?.add(FloatEntry(index.toFloat(), netWorth.minorUnits / 100f))
                        seriesMap["Liabilities"]?.add(FloatEntry(index.toFloat(), totalLiabilities.minorUnits / 100f))
                        seriesMap["Receivables"]?.add(FloatEntry(index.toFloat(), outstandingReceivables.minorUnits / 100f))
                    }
                    AnalyticsMode.ACCOUNTS -> {
                        seriesNames.forEach { accName ->
                            val bal = accBalancesMap[accName] ?: Money(0)
                            seriesMap[accName]?.add(FloatEntry(index.toFloat(), bal.minorUnits / 100f))
                        }
                    }
                    AnalyticsMode.CATEGORIES -> {
                        seriesNames.forEach { catName ->
                            val spend = catSpendMap[catName] ?: Money(0)
                            seriesMap[catName]?.add(FloatEntry(index.toFloat(), spend.minorUnits / 100f))
                        }
                    }
                }
            }

            // Filter series with points
            val validNames = when (effectiveMode) {
                AnalyticsMode.OVERVIEW -> seriesNames
                AnalyticsMode.ACCOUNTS -> seriesNames.filter { name ->
                    seriesMap[name]?.isNotEmpty() == true
                }
                AnalyticsMode.CATEGORIES -> seriesNames.filter { name ->
                    seriesMap[name]?.any { it.y != 0f } == true
                }.takeIf { it.isNotEmpty() } ?: seriesNames.take(1)
            }

            val validSeries = validNames.mapNotNull { seriesMap[it] }.takeIf { it.isNotEmpty() }
            val model = validSeries?.let { entryModelOf(*it.toTypedArray()) }

            // Compute summary metrics (Starting, Ending, Peak, Low, Net Change)
            val primaryValues = when (effectiveMode) {
                AnalyticsMode.OVERVIEW -> bucketDetails.map { it.netWorth }
                AnalyticsMode.ACCOUNTS -> {
                    val firstAcc = validNames.firstOrNull()
                    bucketDetails.map { it.accountBalances[firstAcc] ?: Money(0) }
                }
                AnalyticsMode.CATEGORIES -> {
                    val firstCat = validNames.firstOrNull()
                    bucketDetails.map { it.categorySpend[firstCat] ?: Money(0) }
                }
            }

            val summary = if (primaryValues.isNotEmpty()) {
                val start = primaryValues.first()
                val end = primaryValues.last()
                val peak = primaryValues.maxByOrNull { it.minorUnits } ?: Money(0)
                val low = primaryValues.minByOrNull { it.minorUnits } ?: Money(0)
                val diff = end - start
                SummaryMetrics(
                    startBalance = start,
                    endBalance = end,
                    peakBalance = peak,
                    lowBalance = low,
                    netChange = diff
                )
            } else {
                SummaryMetrics()
            }

            return GraphUiState(
                entryModel = model,
                labels = xLabels,
                isLoading = false,
                bucketSize = bucketSize,
                accounts = displayAccs,
                categories = cats,
                selectedAccounts = selAcc,
                selectedCategories = selCat,
                analyticsMode = effectiveMode,
                compareByAccount = effectiveMode != AnalyticsMode.CATEGORIES,
                seriesNames = validNames,
                bucketDetails = bucketDetails,
                selectedBucketIndex = selectedBucketIndex,
                summaryMetrics = summary
            )
        }
    }

    fun setAnalyticsMode(mode: AnalyticsMode) {
        _analyticsMode.value = mode
    }

    fun setBucketSize(size: BucketSize) {
        _bucketSize.value = size
    }

    fun toggleAccount(id: Int) {
        if (_analyticsMode.value != AnalyticsMode.ACCOUNTS) {
            _analyticsMode.value = AnalyticsMode.ACCOUNTS
        }
        val current = _selectedAccounts.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedAccounts.value = current
    }

    fun toggleCategory(id: Int) {
        if (_analyticsMode.value != AnalyticsMode.CATEGORIES) {
            _analyticsMode.value = AnalyticsMode.CATEGORIES
        }
        val current = _selectedCategories.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedCategories.value = current
    }

    fun selectBucket(index: Int?) {
        _selectedBucketIndex.value = index
    }

    fun clearSelectedBucket() {
        _selectedBucketIndex.value = null
    }

    fun setCompareMode(byAccount: Boolean) {
        _analyticsMode.value = if (byAccount) {
            if (_selectedAccounts.value.isNotEmpty()) AnalyticsMode.ACCOUNTS else AnalyticsMode.OVERVIEW
        } else {
            AnalyticsMode.CATEGORIES
        }
    }
}
