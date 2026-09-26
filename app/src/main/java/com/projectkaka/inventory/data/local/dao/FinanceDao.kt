package com.projectkaka.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

// ── Projection data classes ─────────────────────────────────────────────

/** Spending aggregated by category name, used by analytics charts. */
data class CategorySpending(val name: String, val total: Double)

/** Account row with its computed balance, used by the dashboard and budget engine. */
data class AccountBalanceRow(
    val id: Int,
    val name: String,
    val type: String,
    val balance: Double
)

/**
 * Full financial data-access layer.
 *
 * Account balances are **never stored** — every balance is computed on read:
 *   `opening_balance + SUM(credits) − SUM(debits)`
 * This is the cornerstone of the double-entry design: no mutable balance
 * field means the numbers can never drift out of sync.
 */
@Dao
interface FinanceDao {

    // ════════════════════════════════════════════════════════════════════
    //  ACCOUNTS
    // ════════════════════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE is_active = 1 ORDER BY name ASC")
    fun getActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Int): AccountEntity?

    @Query("SELECT * FROM accounts WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getAccountByName(name: String): AccountEntity?

    /** All accounts, for alias-matching in the parser (loaded once per session). */
    @Query("SELECT * FROM accounts WHERE is_active = 1")
    suspend fun getActiveAccountsSnapshot(): List<AccountEntity>

    /**
     * Computed balance for one account:
     *   opening_balance + credits − debits
     */
    @Query(
        """
        SELECT a.opening_balance
            + COALESCE((SELECT SUM(t.amount) FROM financial_transactions t
                        WHERE t.account_id = a.id AND t.is_credit = 1), 0.0)
            - COALESCE((SELECT SUM(t.amount) FROM financial_transactions t
                        WHERE t.account_id = a.id AND t.is_credit = 0), 0.0)
        FROM accounts a
        WHERE a.id = :accountId
        """
    )
    suspend fun getAccountBalance(accountId: Int): Double?

    /**
     * Reactive version of the balance for a single account.
     * Fires whenever any transaction touching this account changes.
     */
    @Query(
        """
        SELECT a.opening_balance
            + COALESCE((SELECT SUM(t.amount) FROM financial_transactions t
                        WHERE t.account_id = a.id AND t.is_credit = 1), 0.0)
            - COALESCE((SELECT SUM(t.amount) FROM financial_transactions t
                        WHERE t.account_id = a.id AND t.is_credit = 0), 0.0)
        FROM accounts a
        WHERE a.id = :accountId
        """
    )
    fun observeAccountBalance(accountId: Int): Flow<Double?>

    /**
     * All active accounts with their computed balances — for dashboard display.
     */
    @Query(
        """
        SELECT
            a.id    AS id,
            a.name  AS name,
            a.type  AS type,
            (a.opening_balance
                + COALESCE((SELECT SUM(t.amount) FROM financial_transactions t
                            WHERE t.account_id = a.id AND t.is_credit = 1), 0.0)
                - COALESCE((SELECT SUM(t.amount) FROM financial_transactions t
                            WHERE t.account_id = a.id AND t.is_credit = 0), 0.0)
            ) AS balance
        FROM accounts a
        WHERE a.is_active = 1
        ORDER BY a.name ASC
        """
    )
    fun observeAllAccountBalances(): Flow<List<AccountBalanceRow>>

    /**
     * Daily budget denominator: sum of all CASH-type active account balances.
     * ASSET and CAPITAL accounts are explicitly excluded.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            a.opening_balance
                + COALESCE((SELECT SUM(t.amount) FROM financial_transactions t
                            WHERE t.account_id = a.id AND t.is_credit = 1), 0.0)
                - COALESCE((SELECT SUM(t.amount) FROM financial_transactions t
                            WHERE t.account_id = a.id AND t.is_credit = 0), 0.0)
        ), 0.0)
        FROM accounts a
        WHERE a.is_active = 1 AND a.type = 'CASH'
        """
    )
    fun observeTotalCashBalance(): Flow<Double>

    // ════════════════════════════════════════════════════════════════════
    //  FINANCIAL CATEGORIES
    // ════════════════════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: FinancialCategoryEntity): Long

    @Update
    suspend fun updateCategory(category: FinancialCategoryEntity)

    @Delete
    suspend fun deleteCategory(category: FinancialCategoryEntity)

    @Query("SELECT * FROM financial_categories ORDER BY type ASC, name ASC")
    fun getAllCategories(): Flow<List<FinancialCategoryEntity>>

    @Query("SELECT * FROM financial_categories WHERE type = :type ORDER BY name ASC")
    fun getCategoriesByType(type: String): Flow<List<FinancialCategoryEntity>>

    @Query("SELECT * FROM financial_categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Int): FinancialCategoryEntity?

    @Query("SELECT * FROM financial_categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getCategoryByName(name: String): FinancialCategoryEntity?

    /** All categories, for alias-matching in the parser (loaded once per session). */
    @Query("SELECT * FROM financial_categories")
    suspend fun getAllCategoriesSnapshot(): List<FinancialCategoryEntity>

    // ════════════════════════════════════════════════════════════════════
    //  TRANSACTIONS
    // ════════════════════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM financial_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Int): TransactionEntity?

    @Query("SELECT * FROM financial_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM financial_transactions
        WHERE timestamp BETWEEN :startMs AND :endMs
        ORDER BY timestamp DESC
        """
    )
    fun getTransactionsInRange(startMs: Long, endMs: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM financial_transactions
        WHERE account_id = :accountId
        ORDER BY timestamp DESC
        """
    )
    fun getTransactionsForAccount(accountId: Int): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM financial_transactions
        WHERE category_id = :categoryId
        ORDER BY timestamp DESC
        """
    )
    fun getTransactionsForCategory(categoryId: Int): Flow<List<TransactionEntity>>

    /** Day's total debit (expenses). Used for overspending detection. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0) FROM financial_transactions
        WHERE is_credit = 0 AND timestamp BETWEEN :dayStartMs AND :dayEndMs
        """
    )
    suspend fun getDaySpending(dayStartMs: Long, dayEndMs: Long): Double

    /** Rolling average daily spend for the current month — used for spike detection. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0) FROM financial_transactions
        WHERE is_credit = 0 AND timestamp >= :monthStartMs
        """
    )
    fun observeMonthSpending(monthStartMs: Long): Flow<Double>

    /** Spending aggregated by category, for analytics charts. */
    @Query(
        """
        SELECT fc.name AS name, COALESCE(SUM(t.amount), 0.0) AS total
        FROM financial_transactions t
        INNER JOIN financial_categories fc ON fc.id = t.category_id
        WHERE t.is_credit = 0 AND t.timestamp BETWEEN :startMs AND :endMs
        GROUP BY t.category_id
        ORDER BY total DESC
        """
    )
    fun getSpendingByCategory(startMs: Long, endMs: Long): Flow<List<CategorySpending>>

    /** Income aggregated by category, for analytics charts. */
    @Query(
        """
        SELECT fc.name AS name, COALESCE(SUM(t.amount), 0.0) AS total
        FROM financial_transactions t
        INNER JOIN financial_categories fc ON fc.id = t.category_id
        WHERE t.is_credit = 1 AND t.timestamp BETWEEN :startMs AND :endMs
        GROUP BY t.category_id
        ORDER BY total DESC
        """
    )
    fun getIncomeByCategory(startMs: Long, endMs: Long): Flow<List<CategorySpending>>

    /** Total transaction count — used by export screen. */
    @Query("SELECT COUNT(*) FROM financial_transactions")
    fun observeTransactionCount(): Flow<Int>

    /** Reactive day spending — fires when any transaction for the day changes. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0) FROM financial_transactions
        WHERE is_credit = 0 AND timestamp >= :dayStartMs AND timestamp < :dayEndMs
        """
    )
    fun observeDaySpending(dayStartMs: Long, dayEndMs: Long): Flow<Double>

    // ── Export read-only snapshots ───────────────────────────────────────

    @Query("SELECT * FROM financial_transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsExport(): List<TransactionEntity>

    // ════════════════════════════════════════════════════════════════════
    //  LEDGER
    // ════════════════════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntry(entry: LedgerEntryEntity): Long

    @Update
    suspend fun updateLedgerEntry(entry: LedgerEntryEntity)

    @Delete
    suspend fun deleteLedgerEntry(entry: LedgerEntryEntity)

    @Query("SELECT * FROM ledger_entries WHERE is_settled = 0 ORDER BY created_at DESC")
    fun getUnsettledEntries(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries ORDER BY created_at DESC")
    fun getAllLedgerEntries(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun getLedgerEntryById(id: Int): LedgerEntryEntity?

    /** Sum of all unsettled receivables — what others owe you. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0) FROM ledger_entries
        WHERE type = 'RECEIVABLE' AND is_settled = 0
        """
    )
    fun observeTotalReceivable(): Flow<Double>

    /** Sum of all unsettled payables — what you owe others. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0) FROM ledger_entries
        WHERE type = 'PAYABLE' AND is_settled = 0
        """
    )
    fun observeTotalPayable(): Flow<Double>

    // ── Export snapshot ──────────────────────────────────────────────────

    @Query("SELECT * FROM ledger_entries ORDER BY created_at DESC")
    suspend fun getAllLedgerEntriesExport(): List<LedgerEntryEntity>
}
