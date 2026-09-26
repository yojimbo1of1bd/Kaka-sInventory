package com.projectkaka.inventory.ui.dashboard

import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import com.projectkaka.inventory.data.local.entity.CategoryType
import com.projectkaka.inventory.data.local.entity.TransactionEntity
import com.projectkaka.inventory.data.local.entity.TransactionType
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
        // Day 1: -2000 (Expense) from Bank
        val t1 = TransactionEntity(
            id = 1,
            accountId = 2,
            categoryId = 1,
            amount = Money(2000),
            isCredit = false,
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
            isCredit = false,
            note = "Transfer out",
            type = TransactionType.TRANSFER,
            timestamp = Instant.parse("2023-10-11T10:00:00Z").toEpochMilli()
        )
        val t2b = TransactionEntity(
            id = 3,
            accountId = 1,
            categoryId = null,
            amount = Money(5000),
            isCredit = true,
            note = "Transfer in",
            type = TransactionType.TRANSFER,
            timestamp = Instant.parse("2023-10-11T10:00:00Z").toEpochMilli()
        )

        // Day 3: Borrow 10000 (Payable) to Cash
        val t3 = TransactionEntity(
            id = 4,
            accountId = 1,
            categoryId = null,
            amount = Money(10000),
            isCredit = true, // We got money
            note = "Borrowed",
            type = TransactionType.DEBT_ISSUE,
            timestamp = Instant.parse("2023-10-12T10:00:00Z").toEpochMilli()
        )

        // Day 4: Lend 4000 (Receivable) from Cash
        val t4 = TransactionEntity(
            id = 5,
            accountId = 1,
            categoryId = null,
            amount = Money(4000),
            isCredit = false, // Money left
            note = "Lent",
            type = TransactionType.DEBT_ISSUE,
            timestamp = Instant.parse("2023-10-13T10:00:00Z").toEpochMilli()
        )

        val transactions = listOf(t1, t2a, t2b, t3, t4)
        val accounts = listOf(acc1, acc2)
        val categories = listOf(cat1)

        val state = GraphViewModel.buildGraphState(
            transactions = transactions,
            accs = accounts,
            cats = categories,
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

        // Day 3: lend 4000 -> Assets = 64000, NW = 54000 (Assets - Liabilities), L = 10000, R = 4000
        assertEquals(640f, assets[3].y)
        assertEquals(540f, netWorth[3].y)
        assertEquals(100f, liab[3].y)
        assertEquals(40f, rec[3].y)

        // Day 4: no changes
        assertEquals(640f, assets[4].y)
        assertEquals(540f, netWorth[4].y)
        assertEquals(100f, liab[4].y)
        assertEquals(40f, rec[4].y)

        // Day 5: no changes
        assertEquals(640f, assets[5].y)
        assertEquals(540f, netWorth[5].y)
        assertEquals(100f, liab[5].y)
        assertEquals(40f, rec[5].y)
    }
}
