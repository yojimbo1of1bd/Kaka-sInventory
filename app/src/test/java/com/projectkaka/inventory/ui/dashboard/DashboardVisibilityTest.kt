package com.projectkaka.inventory.ui.dashboard

import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DashboardVisibilityTest {

    @Test
    fun `hidden accounts do not contribute to total cash balance`() {
        val today = LocalDate.of(2023, 10, 15) // 31 days in October, 17 days remaining (31-15+1)

        val account1 = AccountEntity(id = 1, name = "Cash", openingBalance = Money(10000), type = AccountType.CASH)
        val account2 = AccountEntity(id = 2, name = "bKash", openingBalance = Money(20000), type = AccountType.CASH)
        val account3 = AccountEntity(id = 3, name = "Old Bank", openingBalance = Money(50000), type = AccountType.CASH)
        // Note: For simplicity of test, we are setting balance via openingBalance, but in actual code balance is what matters. 
        // We need to set balance for the test since sumOf uses balance.
        val acc1 = account1.copy(balance = Money(10000))
        val acc2 = account2.copy(balance = Money(20000))
        val acc3 = account3.copy(balance = Money(50000))

        val accounts = listOf(acc1, acc2, acc3)

        val hiddenIds = setOf(3) // Hide "Old Bank"

        val budgetState = DashboardViewModel.computeBudgetState(
            accounts = accounts,
            todaySpend = Money(0),
            hiddenIds = hiddenIds,
            today = today
        )

        // Total cash balance should be 10000 + 20000 = 30000
        assertEquals(30000L, budgetState.totalCashBalance.minorUnits)

        // Remaining days: 31 - 15 + 1 = 17
        // Budget = 30000 / 17 = 1764
        assertEquals(1764L, budgetState.dailyBudget.minorUnits)
    }

    @Test
    fun `all accounts contribute if hidden set is empty`() {
        val today = LocalDate.of(2023, 10, 15)

        val acc1 = AccountEntity(id = 1, name = "Cash", type = AccountType.CASH).copy(balance = Money(10000))
        val acc2 = AccountEntity(id = 2, name = "bKash", type = AccountType.CASH).copy(balance = Money(20000))
        val acc3 = AccountEntity(id = 3, name = "Old Bank", type = AccountType.CASH).copy(balance = Money(50000))

        val accounts = listOf(acc1, acc2, acc3)

        val budgetState = DashboardViewModel.computeBudgetState(
            accounts = accounts,
            todaySpend = Money(0),
            hiddenIds = emptySet(),
            today = today
        )

        // Total cash balance should be 80000
        assertEquals(80000L, budgetState.totalCashBalance.minorUnits)
    }
}
