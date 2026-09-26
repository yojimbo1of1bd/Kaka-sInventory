package com.projectkaka.inventory.data.repository

import com.projectkaka.inventory.data.local.dao.AccountBalanceRow
import com.projectkaka.inventory.data.local.dao.CategorySpending
import com.projectkaka.inventory.data.local.dao.FinanceDao
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.TransactionEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FinanceRepositoryImpl(
    private val financeDao: FinanceDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FinanceRepository {

    // ── Accounts ────────────────────────────────────────────────────────

    override fun getAllAccounts(): Flow<List<AccountEntity>> =
        financeDao.getAllAccounts()

    override fun getActiveAccounts(): Flow<List<AccountEntity>> =
        financeDao.getActiveAccounts()

    override suspend fun getActiveAccountsSnapshot(): List<AccountEntity> =
        withContext(ioDispatcher) { financeDao.getActiveAccountsSnapshot() }

    override fun observeAllAccountBalances(): Flow<List<AccountBalanceRow>> =
        financeDao.observeAllAccountBalances()

    override fun observeTotalCashBalance(): Flow<Double> =
        financeDao.observeTotalCashBalance()

    override suspend fun getAccountBalance(accountId: Int): Double? =
        withContext(ioDispatcher) { financeDao.getAccountBalance(accountId) }

    override suspend fun insertAccount(account: AccountEntity): Long =
        withContext(ioDispatcher) { financeDao.insertAccount(account) }

    override suspend fun updateAccount(account: AccountEntity) =
        withContext(ioDispatcher) { financeDao.updateAccount(account) }

    override suspend fun deleteAccount(account: AccountEntity) =
        withContext(ioDispatcher) { financeDao.deleteAccount(account) }

    // ── Categories ──────────────────────────────────────────────────────

    override fun getAllCategories(): Flow<List<FinancialCategoryEntity>> =
        financeDao.getAllCategories()

    override suspend fun getAllCategoriesSnapshot(): List<FinancialCategoryEntity> =
        withContext(ioDispatcher) { financeDao.getAllCategoriesSnapshot() }

    override suspend fun insertCategory(category: FinancialCategoryEntity): Long =
        withContext(ioDispatcher) { financeDao.insertCategory(category) }

    override suspend fun updateCategory(category: FinancialCategoryEntity) =
        withContext(ioDispatcher) { financeDao.updateCategory(category) }

    override suspend fun deleteCategory(category: FinancialCategoryEntity) =
        withContext(ioDispatcher) { financeDao.deleteCategory(category) }

    // ── Transactions ────────────────────────────────────────────────────

    override suspend fun recordTransaction(transaction: TransactionEntity): Long =
        withContext(ioDispatcher) { financeDao.insertTransaction(transaction) }

    override suspend fun deleteTransaction(transaction: TransactionEntity) =
        withContext(ioDispatcher) { financeDao.deleteTransaction(transaction) }

    override fun getAllTransactions(): Flow<List<TransactionEntity>> =
        financeDao.getAllTransactions()

    override fun getTransactionsInRange(startMs: Long, endMs: Long): Flow<List<TransactionEntity>> =
        financeDao.getTransactionsInRange(startMs, endMs)

    override fun getTransactionsForAccount(accountId: Int): Flow<List<TransactionEntity>> =
        financeDao.getTransactionsForAccount(accountId)

    override fun getTransactionsForCategory(categoryId: Int): Flow<List<TransactionEntity>> =
        financeDao.getTransactionsForCategory(categoryId)

    override fun getSpendingByCategory(startMs: Long, endMs: Long): Flow<List<CategorySpending>> =
        financeDao.getSpendingByCategory(startMs, endMs)

    override fun getIncomeByCategory(startMs: Long, endMs: Long): Flow<List<CategorySpending>> =
        financeDao.getIncomeByCategory(startMs, endMs)

    override fun observeMonthSpending(monthStartMs: Long): Flow<Double> =
        financeDao.observeMonthSpending(monthStartMs)

    override fun observeDaySpending(dayStartMs: Long, dayEndMs: Long): Flow<Double> =
        financeDao.observeDaySpending(dayStartMs, dayEndMs)

    override suspend fun getDaySpending(dayStartMs: Long, dayEndMs: Long): Double =
        withContext(ioDispatcher) { financeDao.getDaySpending(dayStartMs, dayEndMs) }

    override fun observeTransactionCount(): Flow<Int> =
        financeDao.observeTransactionCount()

    // ── Alias resolution ────────────────────────────────────────────────

    override suspend fun resolveAccount(token: String): AccountEntity? =
        withContext(ioDispatcher) {
            // Try exact name match first (fast)
            financeDao.getAccountByName(token)
                // Then fallback to alias matching (scan all active accounts)
                ?: financeDao.getActiveAccountsSnapshot()
                    .firstOrNull { it.matchesInput(token) }
        }

    override suspend fun resolveCategory(token: String): FinancialCategoryEntity? =
        withContext(ioDispatcher) {
            financeDao.getCategoryByName(token)
                ?: financeDao.getAllCategoriesSnapshot()
                    .firstOrNull { it.matchesInput(token) }
        }

    override suspend fun initializeAccountBalance(accountAlias: String, targetBalance: Double) {
        withContext(ioDispatcher) {
            val account = resolveAccount(accountAlias)
            if (account != null) {
                val currentBalance = financeDao.getAccountBalance(account.id) ?: 0.0
                val diff = targetBalance - currentBalance
                val newOpening = account.openingBalance + diff
                financeDao.updateAccount(account.copy(openingBalance = newOpening))
            } else {
                val newAccount = com.projectkaka.inventory.data.local.entity.AccountEntity(
                    name = accountAlias.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() },
                    openingBalance = targetBalance
                )
                financeDao.insertAccount(newAccount)
            }
        }
    }

    // ── Ledger ──────────────────────────────────────────────────────────

    override fun getUnsettledEntries(): Flow<List<LedgerEntryEntity>> =
        financeDao.getUnsettledEntries()

    override fun getAllLedgerEntries(): Flow<List<LedgerEntryEntity>> =
        financeDao.getAllLedgerEntries()

    override suspend fun insertLedgerEntry(entry: LedgerEntryEntity): Long =
        withContext(ioDispatcher) { financeDao.insertLedgerEntry(entry) }

    override suspend fun updateLedgerEntry(entry: LedgerEntryEntity) =
        withContext(ioDispatcher) { financeDao.updateLedgerEntry(entry) }

    override suspend fun deleteLedgerEntry(entry: LedgerEntryEntity) =
        withContext(ioDispatcher) { financeDao.deleteLedgerEntry(entry) }

    override fun observeTotalReceivable(): Flow<Double> =
        financeDao.observeTotalReceivable()

    override fun observeTotalPayable(): Flow<Double> =
        financeDao.observeTotalPayable()

    // ── Export ───────────────────────────────────────────────────────────

    override suspend fun exportFinancialSnapshot(): FinancialExportData =
        withContext(ioDispatcher) {
            FinancialExportData(
                accounts = financeDao.getActiveAccountsSnapshot(),
                categories = financeDao.getAllCategoriesSnapshot(),
                transactions = financeDao.getAllTransactionsExport(),
                ledgerEntries = financeDao.getAllLedgerEntriesExport()
            )
        }
}
