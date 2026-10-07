package com.projectkaka.inventory.ui.dashboard

import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import com.projectkaka.inventory.data.local.entity.CategoryType
import com.projectkaka.inventory.data.local.entity.TransactionEntity
import com.projectkaka.inventory.data.local.entity.TransactionType
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.LedgerType
import com.projectkaka.inventory.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GraphViewModelTest {

    @Test
    fun testBuildGraphState_Overview_SumsCorrectly() {
        val zone = ZoneId.of("UTC")
        val fixedInstant = Instant.parse("2023-10-15T12:00:00Z")
        val clock = Clock.fixed(fixedInstant, zone)

        // Accounts
        val acc1 = AccountEntity(id = 1, name = "Cash", type = AccountType.CASH, openingBalance = Money(10000))
        val acc2 = AccountEntity(id = 2, name = "Bank", type = AccountType.ASSET, openingBalance = Money(50000))

        // Categories
        val cat1 = FinancialCategoryEntity(id = 1, name = "Food", type = CategoryType.EXPENSE)

        // Transactions
        // Day 1: -2000 (Expense) from Bank (credit Bank = money out)
        val t1 = TransactionEntity(
            id = 1,
            accountId = 2,
            categoryId = 1,
            amount = Money(2000),
            isCredit = true,
            note = "Groceries",
            type = TransactionType.EXPENSE,
            timestamp = Instant.parse("2023-10-10T10:00:00Z").toEpochMilli()
        )

        // Day 2: Transfer 5000 from Bank to Cash (no net asset change)
        val t2a = TransactionEntity(
            id = 2,
            accountId = 2,
            categoryId = null,
            amount = Money(5000),
            isCredit = true, // Credit Bank (transfer out)
            note = "Transfer out",
            type = TransactionType.TRANSFER,
            timestamp = Instant.parse("2023-10-11T10:00:00Z").toEpochMilli()
        )
        val t2b = TransactionEntity(
            id = 3,
            accountId = 1,
            categoryId = null,
            amount = Money(5000),
            isCredit = false, // Debit Cash (transfer in)
            note = "Transfer in",
            type = TransactionType.TRANSFER,
            timestamp = Instant.parse("2023-10-11T10:00:00Z").toEpochMilli()
        )

        // Day 3: Borrow 10000 (Payable) to Cash (debit Cash = money in)
        val t3 = TransactionEntity(
            id = 4,
            accountId = 1,
            categoryId = null,
            amount = Money(10000),
            isCredit = false, // We got money into Cash
            note = "Borrowed",
            type = TransactionType.DEBT_ISSUE,
            timestamp = Instant.parse("2023-10-12T10:00:00Z").toEpochMilli()
        )
        val l1 = LedgerEntryEntity(
            id = 1,
            contactName = "Alice",
            accountId = 1,
            amount = Money(10000),
            type = LedgerType.PAYABLE,
            isSettled = false,
            linkedTransactionId = 4,
            createdAt = Instant.parse("2023-10-12T10:00:00Z").toEpochMilli()
        )

        // Day 4: Lend 4000 (Receivable) from Cash (credit Cash = money out)
        val t4 = TransactionEntity(
            id = 5,
            accountId = 1,
            categoryId = null,
            amount = Money(4000),
            isCredit = true, // Money left Cash
            note = "Lent",
            type = TransactionType.DEBT_ISSUE,
            timestamp = Instant.parse("2023-10-13T10:00:00Z").toEpochMilli()
        )
        val l2 = LedgerEntryEntity(
            id = 2,
            contactName = "Bob",
            accountId = 1,
            amount = Money(4000),
            type = LedgerType.RECEIVABLE,
            isSettled = false,
            linkedTransactionId = 5,
            createdAt = Instant.parse("2023-10-13T10:00:00Z").toEpochMilli()
        )

        val transactions = listOf(t1, t2a, t2b, t3, t4)
        val accounts = listOf(acc1, acc2)
        val categories = listOf(cat1)
        val ledgers = listOf(l1, l2)

        val state = GraphViewModel.buildGraphState(
            transactions = transactions,
            accs = accounts,
            cats = categories,
            ledgers = ledgers,
            bucketSize = BucketSize.DAY,
            selAcc = emptySet(),
            selCat = emptySet(),
            byAcc = true,
            clock = clock
        )

        // Since earliest transaction is 2023-10-10 and today is 2023-10-15, we have 6 buckets
        // Index 0: 2023-10-10
        // Index 1: 2023-10-11
        // Index 2: 2023-10-12
        // Index 3: 2023-10-13
        // Index 4: 2023-10-14
        // Index 5: 2023-10-15

        // Check labels
        assertEquals(6, state.labels.size)

        // Get series
        val model = state.entryModel!!
        // Series names order: Assets, Net Worth, Liabilities, Receivables
        assertEquals(listOf("Assets", "Net Worth", "Liabilities", "Receivables"), state.seriesNames)

        val assets = model.entries[0]
        val netWorth = model.entries[1]
        val liab = model.entries[2]
        val rec = model.entries[3]

        // Opening total assets = 10000 + 50000 = 60000
        // Day 0: -2000 expense -> Assets = 58000, NW = 58000, L = 0, R = 0
        assertEquals(580f, assets[0].y)
        assertEquals(580f, netWorth[0].y)
        assertEquals(0f, liab[0].y)
        assertEquals(0f, rec[0].y)

        // Day 1: internal transfer -> Assets = 58000, NW = 58000, L = 0, R = 0
        assertEquals(580f, assets[1].y)
        assertEquals(580f, netWorth[1].y)
        assertEquals(0f, liab[1].y)
        assertEquals(0f, rec[1].y)

        // Day 2: borrow 10000 -> Assets = 68000, NW = 58000 (Assets - Liabilities), L = 10000, R = 0
        assertEquals(680f, assets[2].y)
        assertEquals(580f, netWorth[2].y)
        assertEquals(100f, liab[2].y)
        assertEquals(0f, rec[2].y)

        // Day 3: lend 4000 -> Assets = 64000, NW = 58000 (Assets - Liabilities + Receivables), L = 10000, R = 4000
        assertEquals(640f, assets[3].y)
        assertEquals(580f, netWorth[3].y)
        assertEquals(100f, liab[3].y)
        assertEquals(40f, rec[3].y)

        // Day 4: no changes
        assertEquals(640f, assets[4].y)
        assertEquals(580f, netWorth[4].y)
        assertEquals(100f, liab[4].y)
        assertEquals(40f, rec[4].y)

        // Day 5: no changes (final bucket reconciles with account and ledger totals)
        val outstandingPayableTotal = ledgers.filter { it.type == LedgerType.PAYABLE && !it.isSettled }.sumOf { it.amount.minorUnits } / 100f
        val outstandingReceivableTotal = ledgers.filter { it.type == LedgerType.RECEIVABLE && !it.isSettled }.sumOf { it.amount.minorUnits } / 100f
        
        assertEquals(640f, assets[5].y)
        assertEquals(580f, netWorth[5].y)
        assertEquals(outstandingPayableTotal, liab[5].y)
        assertEquals(outstandingReceivableTotal, rec[5].y)
    }

    @Test
    fun testBuildGraphState_CategoryMode_AggregatesSpending() {
        val zone = ZoneId.of("UTC")
        val fixedInstant = Instant.parse("2023-10-12T12:00:00Z")
        val clock = Clock.fixed(fixedInstant, zone)

        val acc = AccountEntity(id = 1, name = "Cash", type = AccountType.CASH, openingBalance = Money(10000))
        val catFood = FinancialCategoryEntity(id = 1, name = "Food", type = CategoryType.EXPENSE, aliases = "lunch,dinner")
        val catRent = FinancialCategoryEntity(id = 2, name = "Rent", type = CategoryType.EXPENSE)

        val t1 = TransactionEntity(
            id = 1,
            accountId = 1,
            categoryId = null,
            amount = Money(500),
            isCredit = false,
            note = "lunch with friend",
            type = TransactionType.EXPENSE,
            timestamp = Instant.parse("2023-10-10T10:00:00Z").toEpochMilli()
        )
        val t2 = TransactionEntity(
            id = 2,
            accountId = 1,
            categoryId = 1,
            amount = Money(800),
            isCredit = false,
            note = "groceries",
            type = TransactionType.EXPENSE,
            timestamp = Instant.parse("2023-10-11T10:00:00Z").toEpochMilli()
        )

        val state = GraphViewModel.buildGraphState(
            transactions = listOf(t1, t2),
            accs = listOf(acc),
            cats = listOf(catFood, catRent),
            ledgers = emptyList(),
            bucketSize = BucketSize.DAY,
            selAcc = emptySet(),
            selCat = emptySet(),
            byAcc = false,
            clock = clock
        )

        assertEquals(listOf("Food"), state.seriesNames)
        val foodEntries = state.entryModel!!.entries[0]
        // Day 0 (2023-10-10): 500 minor = 5.0f
        assertEquals(5f, foodEntries[0].y)
        // Day 1 (2023-10-11): 500 + 800 = 1300 minor = 13.0f
        assertEquals(13f, foodEntries[1].y)
        // Day 2 (2023-10-12): cumulative 13.0f
        assertEquals(13f, foodEntries[2].y)
    }

    @Test
    fun testBuildGraphState_AccountMode_PlotsDedicatedSeriesForBkashAndCash() {
        val zone = ZoneId.of("UTC")
        val fixedInstant = Instant.parse("2023-10-12T12:00:00Z")
        val clock = Clock.fixed(fixedInstant, zone)

        val accBkash = AccountEntity(id = 1, name = "Bkash", type = AccountType.CASH, openingBalance = Money(500000))
        val accCash = AccountEntity(id = 2, name = "Cash", type = AccountType.CASH, openingBalance = Money(100000))

        // Transfer 500 Taka from Bkash to Cash
        val t1 = TransactionEntity(
            id = 1,
            accountId = 1,
            categoryId = null,
            amount = Money(50000),
            isCredit = true, // Out from Bkash
            note = "Transfer out",
            type = TransactionType.TRANSFER,
            timestamp = Instant.parse("2023-10-11T10:00:00Z").toEpochMilli()
        )
        val t2 = TransactionEntity(
            id = 2,
            accountId = 2,
            categoryId = null,
            amount = Money(50000),
            isCredit = false, // In to Cash
            note = "Transfer in",
            type = TransactionType.TRANSFER,
            timestamp = Instant.parse("2023-10-11T10:00:00Z").toEpochMilli()
        )

        val state = GraphViewModel.buildGraphState(
            transactions = listOf(t1, t2),
            accs = listOf(accBkash, accCash),
            cats = emptyList(),
            ledgers = emptyList(),
            bucketSize = BucketSize.DAY,
            selAcc = setOf(1, 2),
            selCat = emptySet(),
            byAcc = true,
            clock = clock,
            mode = AnalyticsMode.ACCOUNTS
        )

        // Verifies dedicated account names are generated instead of generic "Assets / Net Worth"
        assertEquals(listOf("Bkash", "Cash"), state.seriesNames)
        assertEquals(2, state.entryModel!!.entries.size)

        val bkashSeries = state.entryModel!!.entries[0]
        val cashSeries = state.entryModel!!.entries[1]
        // Before transfer (earliest bucket)
        assertEquals(5000f, bkashSeries[0].y)
        assertEquals(1000f, cashSeries[0].y)

        // After transfer on Oct 11
        val oct11Detail = state.bucketDetails.find { it.dateLabel.contains("Oct 11") }
        org.junit.Assert.assertNotNull(oct11Detail)
        val oct11Idx = oct11Detail!!.index
        assertEquals(4500f, bkashSeries[oct11Idx].y)
        assertEquals(1500f, cashSeries[oct11Idx].y)

        // Check drill-down details exist
        org.junit.Assert.assertTrue(state.bucketDetails.isNotEmpty())
        assertEquals(Money(450000), oct11Detail.accountBalances["Bkash"])
        assertEquals(Money(150000), oct11Detail.accountBalances["Cash"])
        assertEquals(2, oct11Detail.transactions.size)
    }

    @Test
    fun testBuildGraphState_MonthlySmartLabelFormatting_NeverTruncates() {
        val zone = ZoneId.of("UTC")
        val fixedInstant = Instant.parse("2026-10-08T12:00:00Z")
        val clock = Clock.fixed(fixedInstant, zone)

        val acc = AccountEntity(id = 1, name = "Cash", type = AccountType.CASH, openingBalance = Money(100000))
        val t = TransactionEntity(
            id = 1,
            accountId = 1,
            categoryId = null,
            amount = Money(10000),
            isCredit = false,
            note = "Initial",
            type = TransactionType.INCOME,
            timestamp = Instant.parse("2026-05-01T10:00:00Z").toEpochMilli()
        )

        val state = GraphViewModel.buildGraphState(
            transactions = listOf(t),
            accs = listOf(acc),
            cats = emptyList(),
            ledgers = emptyList(),
            bucketSize = BucketSize.MONTH,
            selAcc = emptySet(),
            selCat = emptySet(),
            byAcc = true,
            clock = clock
        )

        // Labels should be short 3-letter month names like "May", "Jun", "Oct", NOT "May 2026"
        val labelValues = state.labels.values
        org.junit.Assert.assertTrue(labelValues.contains("May"))
        org.junit.Assert.assertTrue(labelValues.contains("Oct"))
        // None should exceed 4 characters in same year
        org.junit.Assert.assertTrue(labelValues.all { it.length <= 4 })
    }
}
