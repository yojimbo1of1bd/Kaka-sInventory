package com.projectkaka.inventory.ui.dashboard

import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SafeToSpendTest {

    @Test
    fun `test safe to spend with no payables in standard cycle`() {
        val today = LocalDate.of(2026, 10, 15) // 17 days until Nov 1 (Oct 15 to Nov 1)
        val accounts = listOf(
            AccountEntity(id = 1, name = "Cash", type = AccountType.CASH).copy(balance = Money(30000))
        )

        val state = DashboardViewModel.computeBudgetState(
            accounts = accounts,
            todaySpend = Money(0),
            hiddenIds = emptySet(),
            today = today,
            totalPayables = Money(0),
            incomeCycleDay = 1
        )

        assertEquals(30000L, state.totalCashBalance.minorUnits)
        assertEquals(0L, state.totalPayables.minorUnits)
        assertEquals(30000L, state.safeToSpendTotal.minorUnits)
        assertEquals(17, state.remainingDays)
        assertEquals(1764L, state.dailyBudget.minorUnits) // 30000 / 17
        assertEquals(0L, state.deficitAmount.minorUnits)
    }

    @Test
    fun `test accrued payables deduct from safe to spend and daily budget`() {
        val today = LocalDate.of(2026, 10, 15) // 17 days until Nov 1
        val accounts = listOf(
            AccountEntity(id = 1, name = "Cash", type = AccountType.CASH).copy(balance = Money(30000))
        )

        val state = DashboardViewModel.computeBudgetState(
            accounts = accounts,
            todaySpend = Money(0),
            hiddenIds = emptySet(),
            today = today,
            totalPayables = Money(10000), // Owe ৳100.00
            incomeCycleDay = 1
        )

        assertEquals(30000L, state.totalCashBalance.minorUnits)
        assertEquals(10000L, state.totalPayables.minorUnits)
        assertEquals(20000L, state.safeToSpendTotal.minorUnits) // 30000 - 10000 = 20000
        assertEquals(17, state.remainingDays)
        assertEquals(1176L, state.dailyBudget.minorUnits) // 20000 / 17
        assertEquals(0L, state.deficitAmount.minorUnits)
    }

    @Test
    fun `test payables exceeding cash produces deficit and zero daily budget`() {
        val today = LocalDate.of(2026, 10, 15)
        val accounts = listOf(
            AccountEntity(id = 1, name = "bKash", type = AccountType.CASH).copy(balance = Money(10000))
        )

        val state = DashboardViewModel.computeBudgetState(
            accounts = accounts,
            todaySpend = Money(0),
            hiddenIds = emptySet(),
            today = today,
            totalPayables = Money(25000), // Owe ৳250.00, only have ৳100.00
            incomeCycleDay = 1
        )

        assertEquals(10000L, state.totalCashBalance.minorUnits)
        assertEquals(25000L, state.totalPayables.minorUnits)
        assertEquals(0L, state.safeToSpendTotal.minorUnits)
        assertEquals(0L, state.dailyBudget.minorUnits) // Never negative!
        assertEquals(15000L, state.deficitAmount.minorUnits) // Short by ৳150.00
    }

    @Test
    fun `test custom income cycle day later in current month`() {
        val today = LocalDate.of(2026, 10, 5)
        val incomeCycleDay = 10 // Salary/stipend arrives on 10th
        val accounts = listOf(
            AccountEntity(id = 1, name = "Cash", type = AccountType.CASH).copy(balance = Money(5000))
        )

        val state = DashboardViewModel.computeBudgetState(
            accounts = accounts,
            todaySpend = Money(0),
            hiddenIds = emptySet(),
            today = today,
            totalPayables = Money(0),
            incomeCycleDay = incomeCycleDay
        )

        // Days between Oct 5 and Oct 10 = 5 days
        assertEquals(5, state.remainingDays)
        assertEquals(1000L, state.dailyBudget.minorUnits) // 5000 / 5
    }

    @Test
    fun `test custom income cycle day in next month`() {
        val today = LocalDate.of(2026, 10, 15)
        val incomeCycleDay = 10 // Next income is Nov 10
        val accounts = listOf(
            AccountEntity(id = 1, name = "Cash", type = AccountType.CASH).copy(balance = Money(26000))
        )

        val state = DashboardViewModel.computeBudgetState(
            accounts = accounts,
            todaySpend = Money(0),
            hiddenIds = emptySet(),
            today = today,
            totalPayables = Money(0),
            incomeCycleDay = incomeCycleDay
        )

        // Days between Oct 15 and Nov 10 = 16 days in Oct + 10 = 26 days
        assertEquals(26, state.remainingDays)
        assertEquals(1000L, state.dailyBudget.minorUnits) // 26000 / 26
    }

    @Test
    fun `test income cycle day today advances to next month`() {
        val today = LocalDate.of(2026, 10, 10)
        val incomeCycleDay = 10 // Income arrives today, cycle resets for next month Nov 10

        val days = DashboardViewModel.calculateDaysUntilNextIncome(today, incomeCycleDay)
        // Between Oct 10 and Nov 10 = 31 days (October has 31 days)
        assertEquals(31, days)
    }

    @Test
    fun `test non-cash accounts are excluded from liquid cash`() {
        val today = LocalDate.of(2026, 10, 15)
        val accounts = listOf(
            AccountEntity(id = 1, name = "Wallet", type = AccountType.CASH).copy(balance = Money(10000)),
            AccountEntity(id = 2, name = "Gold Jewelry", type = AccountType.ASSET).copy(balance = Money(500000)),
            AccountEntity(id = 3, name = "Opening Capital", type = AccountType.CAPITAL).copy(balance = Money(1000000)),
            AccountEntity(id = 4, name = "Loan", type = AccountType.LIABILITY).copy(balance = Money(50000))
        )

        val state = DashboardViewModel.computeBudgetState(
            accounts = accounts,
            todaySpend = Money(0),
            hiddenIds = emptySet(),
            today = today,
            totalPayables = Money(0),
            incomeCycleDay = 1
        )

        // Only "Wallet" (type CASH) contributes to totalCashBalance
        assertEquals(10000L, state.totalCashBalance.minorUnits)
    }
}
