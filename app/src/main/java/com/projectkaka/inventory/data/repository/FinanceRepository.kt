package com.projectkaka.inventory.data.repository

import com.projectkaka.inventory.data.local.dao.AccountBalanceRow
import com.projectkaka.inventory.data.local.dao.CategorySpending
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

// ── Data classes ────────────────────────────────────────────────────────

/** A fully resolved financial command, ready to be persisted on user confirmation. */
data class ResolvedTransaction(
    val amount: Double,
    val isCredit: Boolean,
    val account: AccountEntity,
    val category: FinancialCategoryEntity,
    val note: String
)

/** Complete financial export bundle. */
data class FinancialExportData(
    val accounts: List<AccountEntity>,
    val categories: List<FinancialCategoryEntity>,
    val transactions: List<TransactionEntity>,
    val ledgerEntries: List<LedgerEntryEntity>
)

// ── Repository interface ────────────────────────────────────────────────

/**
 * Abstracts the financial data layer. Every ViewModel talks to this, never
 * to the DAO directly — following the same pattern as [InventoryRepository].
 */
interface FinanceRepository {

    // ── Accounts ────────────────────────────────────────────────────────

    fun getAllAccounts(): Flow<List<AccountEntity>>
    fun getActiveAccounts(): Flow<List<AccountEntity>>
    suspend fun getActiveAccountsSnapshot(): List<AccountEntity>
    fun observeAllAccountBalances(): Flow<List<AccountBalanceRow>>
    fun observeTotalCashBalance(): Flow<Double>
    suspend fun getAccountBalance(accountId: Int): Double?
    suspend fun insertAccount(account: AccountEntity): Long
    suspend fun updateAccount(account: AccountEntity)
    suspend fun deleteAccount(account: AccountEntity)

    // ── Categories ──────────────────────────────────────────────────────

    fun getAllCategories(): Flow<List<FinancialCategoryEntity>>
    suspend fun getAllCategoriesSnapshot(): List<FinancialCategoryEntity>
    suspend fun insertCategory(category: FinancialCategoryEntity): Long
    suspend fun updateCategory(category: FinancialCategoryEntity)
    suspend fun deleteCategory(category: FinancialCategoryEntity)

    // ── Transactions ────────────────────────────────────────────────────

    suspend fun recordTransaction(transaction: TransactionEntity): Long
    suspend fun deleteTransaction(transaction: TransactionEntity)
    fun getAllTransactions(): Flow<List<TransactionEntity>>
    fun getTransactionsInRange(startMs: Long, endMs: Long): Flow<List<TransactionEntity>>
    fun getTransactionsForAccount(accountId: Int): Flow<List<TransactionEntity>>
    fun getTransactionsForCategory(categoryId: Int): Flow<List<TransactionEntity>>
    fun getSpendingByCategory(startMs: Long, endMs: Long): Flow<List<CategorySpending>>
    fun getIncomeByCategory(startMs: Long, endMs: Long): Flow<List<CategorySpending>>
    fun observeMonthSpending(monthStartMs: Long): Flow<Double>
    fun observeDaySpending(dayStartMs: Long, dayEndMs: Long): Flow<Double>
    suspend fun getDaySpending(dayStartMs: Long, dayEndMs: Long): Double
    fun observeTransactionCount(): Flow<Int>

    // ── Alias resolution ────────────────────────────────────────────────

    /**
     * Finds an active account whose name or aliases match [token].
     * Used by the `f/` parser to map user input to DB entities.
     */
    suspend fun resolveAccount(token: String): AccountEntity?

    /**
     * Finds a category whose name or aliases match [token].
     * Used by the `f/` parser to map user input to DB entities.
     */
    suspend fun resolveCategory(token: String): FinancialCategoryEntity?
    
    suspend fun initializeAccountBalance(accountAlias: String, targetBalance: Double)

    // ── Ledger ──────────────────────────────────────────────────────────

    fun getUnsettledEntries(): Flow<List<LedgerEntryEntity>>
    fun getAllLedgerEntries(): Flow<List<LedgerEntryEntity>>
    suspend fun insertLedgerEntry(entry: LedgerEntryEntity): Long
    suspend fun updateLedgerEntry(entry: LedgerEntryEntity)
    suspend fun deleteLedgerEntry(entry: LedgerEntryEntity)
    fun observeTotalReceivable(): Flow<Double>
    fun observeTotalPayable(): Flow<Double>

    // ── Export ───────────────────────────────────────────────────────────

    suspend fun exportFinancialSnapshot(): FinancialExportData
}
