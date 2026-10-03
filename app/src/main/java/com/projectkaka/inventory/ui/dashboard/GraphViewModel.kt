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

data class GraphUiState(
    val entryModel: ChartEntryModel? = null,
    val labels: Map<Int, String> = emptyMap(),
    val isLoading: Boolean = true,
    val bucketSize: BucketSize = BucketSize.WEEK,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<FinancialCategoryEntity> = emptyList(),
    val selectedAccounts: Set<Int> = emptySet(),
    val selectedCategories: Set<Int> = emptySet(),
    val compareByAccount: Boolean = true, // true = Overview/Accounts, false = Category Spending
    val seriesNames: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class GraphViewModel(application: Application) : AndroidViewModel(application) {
    private val financeRepo = getApplication<KakaApplication>().financeRepository

    private val _bucketSize = MutableStateFlow(BucketSize.WEEK)
    private val _selectedAccounts = MutableStateFlow<Set<Int>>(emptySet())
    private val _selectedCategories = MutableStateFlow<Set<Int>>(emptySet())
    private val _compareByAccount = MutableStateFlow(true)

    private val filterState = combine(
        _bucketSize,
        _selectedAccounts,
        _selectedCategories,
        _compareByAccount
    ) { bucket, selAcc, selCat, byAcc ->
        FilterState(bucket, selAcc, selCat, byAcc)
    }

    private data class FilterState(
        val bucket: BucketSize,
        val selAcc: Set<Int>,
        val selCat: Set<Int>,
        val byAcc: Boolean
    )

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
        val ledgers = args[4] as List<com.projectkaka.inventory.data.local.entity.LedgerEntryEntity>
        buildGraphState(txs, accs, cats, ledgers, filters.bucket, filters.selAcc, filters.selCat, filters.byAcc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GraphUiState())

    companion object {
        internal fun buildGraphState(
            transactions: List<TransactionEntity>,
            accs: List<AccountEntity>,
            cats: List<FinancialCategoryEntity>,
            ledgers: List<com.projectkaka.inventory.data.local.entity.LedgerEntryEntity>,
            bucketSize: BucketSize,
            selAcc: Set<Int>,
            selCat: Set<Int>,
            byAcc: Boolean,
            clock: java.time.Clock = java.time.Clock.systemDefaultZone()
        ): GraphUiState {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        
        if (transactions.isEmpty() && ledgers.isEmpty() && accs.all { it.openingBalance.minorUnits == 0L }) {
            return GraphUiState(
                isLoading = false,
                bucketSize = bucketSize,
                accounts = accs,
                categories = cats,
                selectedAccounts = selAcc,
                selectedCategories = selCat,
                compareByAccount = byAcc
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
        if (buckets.last() != todayBucket) {
            buckets.add(todayBucket)
        }

        val formatter = when (bucketSize) {
            BucketSize.DAY -> DateTimeFormatter.ofPattern("MMM dd")
            BucketSize.WEEK -> DateTimeFormatter.ofPattern("'W'W MMM")
            BucketSize.MONTH -> DateTimeFormatter.ofPattern("MMM yyyy")
        }

        val xLabels = buckets.mapIndexed { index, date ->
            index to date.format(formatter)
        }.toMap()

        val seriesMap = mutableMapOf<String, MutableList<FloatEntry>>()
        val seriesNames = mutableListOf<String>()

        if (byAcc) {
            val targetAccounts = if (selAcc.isEmpty()) accs else accs.filter { selAcc.contains(it.id) }
            val targetAccountIds = targetAccounts.map { it.id }.toSet()
            
            seriesNames.addAll(listOf("Assets", "Net Worth", "Liabilities", "Receivables"))
            
            val entriesAssets = mutableListOf<FloatEntry>()
            val entriesNetWorth = mutableListOf<FloatEntry>()
            val entriesLiab = mutableListOf<FloatEntry>()
            val entriesRec = mutableListOf<FloatEntry>()
            
            val assetAccIds = targetAccounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.CASH || it.type == com.projectkaka.inventory.data.local.entity.AccountType.ASSET }.map { it.id }.toSet()
            val liabAccIds = targetAccounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.LIABILITY }.map { it.id }.toSet()

            var runningAssetAccs = targetAccounts.filter { it.id in assetAccIds }.fold(Money(0)) { acc, account -> acc + account.openingBalance }
            var runningLiabAccs = targetAccounts.filter { it.id in liabAccIds }.fold(Money(0)) { acc, account -> acc + account.openingBalance }
            
            val txByBucket = transactions.groupBy { tx ->
                val date = Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()
                when (bucketSize) {
                    BucketSize.DAY -> date
                    BucketSize.WEEK -> date.with(java.time.DayOfWeek.MONDAY)
                    BucketSize.MONTH -> date.withDayOfMonth(1)
                }
            }

            buckets.forEachIndexed { index, bucket ->
                val bTxs = txByBucket[bucket] ?: emptyList()
                
                bTxs.forEach { tx ->
                    if (tx.accountId in targetAccountIds) {
                        if (tx.accountId in assetAccIds) {
                            if (tx.isCredit) runningAssetAccs += tx.amount
                            else runningAssetAccs -= tx.amount
                        }
                        if (tx.accountId in liabAccIds) {
                            if (tx.isCredit) runningLiabAccs += tx.amount
                            else runningLiabAccs -= tx.amount
                        }
                    }
                }
                
                val bucketEndLocalDate = when (bucketSize) {
                    BucketSize.DAY -> bucket
                    BucketSize.WEEK -> bucket.plusDays(6)
                    BucketSize.MONTH -> bucket.withDayOfMonth(bucket.lengthOfMonth())
                }
                val bucketEndMillis = bucketEndLocalDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                val outstandingPayables = ledgers.filter { 
                    it.type == com.projectkaka.inventory.data.local.entity.LedgerType.PAYABLE && 
                    !it.isSettled && 
                    it.createdAt <= bucketEndMillis 
                }.fold(Money(0)) { acc, entry -> acc + entry.amount }
                
                val outstandingReceivables = ledgers.filter { 
                    it.type == com.projectkaka.inventory.data.local.entity.LedgerType.RECEIVABLE && 
                    !it.isSettled && 
                    it.createdAt <= bucketEndMillis 
                }.fold(Money(0)) { acc, entry -> acc + entry.amount }
                
                val assets = runningAssetAccs
                val liabilities = runningLiabAccs + outstandingPayables
                val receivables = outstandingReceivables
                val netWorth = assets - liabilities + receivables
                
                entriesAssets.add(FloatEntry(index.toFloat(), assets.minorUnits / 100f))
                entriesNetWorth.add(FloatEntry(index.toFloat(), netWorth.minorUnits / 100f))
                entriesLiab.add(FloatEntry(index.toFloat(), liabilities.minorUnits / 100f))
                entriesRec.add(FloatEntry(index.toFloat(), receivables.minorUnits / 100f))
            }
            
            seriesMap["Assets"] = entriesAssets
            seriesMap["Net Worth"] = entriesNetWorth
            seriesMap["Liabilities"] = entriesLiab
            seriesMap["Receivables"] = entriesRec
            
        } else {
            val targetCats = if (selCat.isEmpty()) cats else cats.filter { selCat.contains(it.id) }
            
            targetCats.forEach { cat ->
                seriesNames.add(cat.name)
                var runningTotal = Money(0)
                val entries = mutableListOf<FloatEntry>()
                
                val txByBucket = transactions.groupBy { tx ->
                    val date = Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate()
                    when (bucketSize) {
                        BucketSize.DAY -> date
                        BucketSize.WEEK -> date.with(java.time.DayOfWeek.MONDAY)
                        BucketSize.MONTH -> date.withDayOfMonth(1)
                    }
                }

                buckets.forEachIndexed { index, bucket ->
                    val dayTxs = txByBucket[bucket]?.filter { it.categoryId == cat.id } ?: emptyList()
                    dayTxs.forEach { tx ->
                        if (tx.isCredit) runningTotal += tx.amount
                        else runningTotal -= tx.amount
                    }
                    entries.add(FloatEntry(index.toFloat(), runningTotal.minorUnits / 100f))
                }
                seriesMap[cat.name] = entries
            }
        }

        val validNames = seriesNames.filter { name ->
            seriesMap[name]?.any { it.y != 0f } == true
        }
        
        val validSeries = validNames.mapNotNull { seriesMap[it] }
            .takeIf { it.isNotEmpty() }
        
        val model = validSeries?.let { entryModelOf(*it.toTypedArray()) }

        return GraphUiState(
            entryModel = model,
            labels = xLabels,
            isLoading = false,
            bucketSize = bucketSize,
            accounts = accs,
            categories = cats,
            selectedAccounts = selAcc,
            selectedCategories = selCat,
            compareByAccount = byAcc,
            seriesNames = validNames
        )
    }
    }

    fun setBucketSize(size: BucketSize) { _bucketSize.value = size }
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
}
