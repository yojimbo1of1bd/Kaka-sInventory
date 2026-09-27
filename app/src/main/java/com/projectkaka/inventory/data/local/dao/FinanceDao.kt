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
import com.projectkaka.inventory.model.Money
import kotlinx.coroutines.flow.Flow

// ── Projection data classes ─────────────────────────────────────────────

/** Spending aggregated by category name, used by analytics charts. */
data class CategorySpending(val name: String, val total: Money)

/** Aggregated ledger outstanding per contact. */
data class ContactSummaryRow(
    @androidx.room.ColumnInfo(name = "contactName")
    val contactName: String,
    @androidx.room.ColumnInfo(name = "contactPhone")
    val contactPhone: String,
    @androidx.room.ColumnInfo(name = "netOutstanding")
    val netOutstanding: Money,
    @androidx.room.ColumnInfo(name = "entryCount")
    val entryCount: Int,
    @androidx.room.ColumnInfo(name = "earliestDueDate")
    val earliestDueDate: Long?
)

/** Aggregated account information with relation counts. */
data class AccountWithCounts(
    @androidx.room.Embedded val account: AccountEntity,
    @androidx.room.ColumnInfo(name = "transaction_count") val transactionCount: Int,
    @androidx.room.ColumnInfo(name = "ledger_link_count") val ledgerLinkCount: Int
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

    @Query("""
        SELECT a.*, 
               (SELECT COUNT(*) FROM financial_transactions t WHERE t.account_id = a.id) AS transaction_count,
               (SELECT COUNT(*) FROM ledger_entries l WHERE l.account_id = a.id) AS ledger_link_count
        FROM accounts a
        ORDER BY a.name ASC
    """)
    fun observeAccountsWithCounts(): Flow<List<AccountWithCounts>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Int): AccountEntity?

    @Query("SELECT * FROM accounts WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getAccountByName(name: String): AccountEntity?

    /** All accounts, for alias-matching in the parser (loaded once per session). */
    @Query("SELECT * FROM accounts WHERE is_active = 1")
    suspend fun getActiveAccountsSnapshot(): List<AccountEntity>

    /** All accounts, for mass recalculation or full snapshot. */
    @Query("SELECT * FROM accounts")
    suspend fun getAllAccountsSnapshot(): List<AccountEntity>

    /**
     * Stored balance for one account.
     */
    @Query("SELECT balance_minor FROM accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccountBalance(accountId: Int): Money?

    /**
     * Reactive version of the stored balance for a single account.
     */
    @Query("SELECT balance_minor FROM accounts WHERE id = :accountId LIMIT 1")
    fun observeAccountBalance(accountId: Int): Flow<Money?>

    /**
     * All active accounts with their balances — for dashboard display.
     */
    @Query("SELECT * FROM accounts WHERE is_active = 1 ORDER BY name ASC")
    fun observeAllAccountBalances(): Flow<List<AccountEntity>>

    /**
     * Daily budget denominator: sum of all CASH-type active account balances.
     * ASSET and CAPITAL accounts are explicitly excluded.
     */
    @Query("SELECT COALESCE(SUM(balance_minor), 0) FROM accounts WHERE is_active = 1 AND type = 'CASH'")
    fun observeTotalCashBalance(): Flow<Money>

    /**
     * Recalculate and update the stored balance for a specific account.
     * Call this inside a transaction whenever related financial_transactions change.
     */
    @Query(
        """
        UPDATE accounts
        SET balance_minor = opening_balance + 
            COALESCE((SELECT SUM(amount) FROM financial_transactions WHERE account_id = accounts.id AND is_credit = 1), 0) - 
            COALESCE((SELECT SUM(amount) FROM financial_transactions WHERE account_id = accounts.id AND is_credit = 0), 0)
        WHERE id = :accountId
        """
    )
    suspend fun recalculateAccountBalance(accountId: Int)

    /**
     * Audit query: returns any accounts where the stored balance_minor differs
     * from the dynamically computed balance of its transactions.
     */
    @Query(
        """
        SELECT a.*
        FROM accounts a
        WHERE a.balance_minor != (a.opening_balance + 
            COALESCE((SELECT SUM(t.amount) FROM financial_transactions t WHERE t.account_id = a.id AND t.is_credit = 1), 0) - 
            COALESCE((SELECT SUM(t.amount) FROM financial_transactions t WHERE t.account_id = a.id AND t.is_credit = 0), 0))
        """
    )
    suspend fun getAccountsWithMismatchedBalances(): List<AccountEntity>

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

    @androidx.room.Transaction
    suspend fun executeTransfer(
        fromAccountId: Int,
        toAccountId: Int,
        amount: Money,
        note: String,
        transferId: String,
        timestamp: Long
    ) {
        insertTransaction(TransactionEntity(amount = amount, accountId = fromAccountId, categoryId = null, transferId = transferId, counterAccountId = toAccountId, type = com.projectkaka.inventory.data.local.entity.TransactionType.TRANSFER, isCredit = false, note = note, timestamp = timestamp))
        insertTransaction(TransactionEntity(amount = amount, accountId = toAccountId, categoryId = null, transferId = transferId, counterAccountId = fromAccountId, type = com.projectkaka.inventory.data.local.entity.TransactionType.TRANSFER, isCredit = true, note = note, timestamp = timestamp))
    }

    @Query("DELETE FROM financial_transactions WHERE transfer_id = :transferId")
    suspend fun deleteTransfer(transferId: String)

    @Query("SELECT * FROM financial_transactions WHERE transfer_id = :transferId")
    suspend fun getTransactionsByTransferId(transferId: String): List<TransactionEntity>

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
        SELECT COALESCE(SUM(amount), 0) FROM financial_transactions
        WHERE is_credit = 0 AND type = 'EXPENSE' AND timestamp BETWEEN :dayStartMs AND :dayEndMs
        """
    )
    suspend fun getDaySpending(dayStartMs: Long, dayEndMs: Long): Money

    /** Rolling average daily spend for the current month — used for spike detection. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM financial_transactions
        WHERE is_credit = 0 AND type = 'EXPENSE' AND timestamp >= :monthStartMs
        """
    )
    fun observeMonthSpending(monthStartMs: Long): Flow<Money>

    /** Spending aggregated by category, for analytics charts. */
    @Query(
        """
        SELECT fc.name AS name, COALESCE(SUM(t.amount), 0) AS total
        FROM financial_transactions t
        INNER JOIN financial_categories fc ON fc.id = t.category_id
        WHERE t.is_credit = 0 AND t.type = 'EXPENSE' AND t.timestamp BETWEEN :startMs AND :endMs
        GROUP BY t.category_id
        ORDER BY total DESC
        """
    )
    fun getSpendingByCategory(startMs: Long, endMs: Long): Flow<List<CategorySpending>>

    /** Income aggregated by category, for analytics charts. */
    @Query(
        """
        SELECT fc.name AS name, COALESCE(SUM(t.amount), 0) AS total
        FROM financial_transactions t
        INNER JOIN financial_categories fc ON fc.id = t.category_id
        WHERE t.is_credit = 1 AND t.type = 'INCOME' AND t.timestamp BETWEEN :startMs AND :endMs
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
        SELECT COALESCE(SUM(amount), 0) FROM financial_transactions
        WHERE is_credit = 0 AND type = 'EXPENSE' AND timestamp >= :dayStartMs AND timestamp < :dayEndMs
        """
    )
    fun observeDaySpending(dayStartMs: Long, dayEndMs: Long): Flow<Money>

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
        SELECT COALESCE(SUM(amount), 0) FROM ledger_entries
        WHERE type = 'RECEIVABLE' AND is_settled = 0
        """
    )
    fun observeTotalReceivable(): Flow<Money>

    /** Sum of all unsettled payables — what you owe others. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM ledger_entries
        WHERE type = 'PAYABLE' AND is_settled = 0
        """
    )
    fun observeTotalPayable(): Flow<Money>

    /** Per-contact aggregation of outstanding ledger entries. */
    @Query(
        """
        SELECT 
            contact_name AS contactName,
            contact_phone AS contactPhone,
            SUM(CASE WHEN type = 'RECEIVABLE' THEN amount ELSE -amount END) AS netOutstanding,
            COUNT(*) AS entryCount,
            MIN(due_date) AS earliestDueDate
        FROM ledger_entries
        WHERE is_settled = 0
        GROUP BY contact_name, contact_phone
        ORDER BY contact_name ASC
        """
    )
    fun observeContactSummaries(): Flow<List<ContactSummaryRow>>

    /** Search ledger entries. */
    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE (:contactQuery IS NULL OR 
               contact_name LIKE '%' || :contactQuery || '%' ESCAPE '\' OR 
               aliases LIKE '%' || :contactQuery || '%' ESCAPE '\' OR 
               contact_phone LIKE '%' || :contactQuery || '%' ESCAPE '\')
          AND (:isSettled IS NULL OR is_settled = :isSettled)
          AND (:minAmount IS NULL OR amount >= :minAmount)
          AND (:maxAmount IS NULL OR amount <= :maxAmount)
          AND (:minDate IS NULL OR (due_date IS NOT NULL AND due_date >= :minDate))
          AND (:maxDate IS NULL OR (due_date IS NOT NULL AND due_date <= :maxDate))
        ORDER BY created_at DESC
        """
    )
    fun searchLedgerEntries(
        contactQuery: String?,
        isSettled: Boolean?,
        minAmount: Long?,
        maxAmount: Long?,
        minDate: Long?,
        maxDate: Long?
    ): kotlinx.coroutines.flow.Flow<List<LedgerEntryEntity>>

    // ── Export snapshot ──────────────────────────────────────────────────

    @Query("SELECT * FROM ledger_entries ORDER BY created_at DESC")
    suspend fun getAllLedgerEntriesExport(): List<LedgerEntryEntity>
}
