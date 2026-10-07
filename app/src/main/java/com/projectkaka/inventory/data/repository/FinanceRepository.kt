package com.projectkaka.inventory.data.repository


import com.projectkaka.inventory.data.local.dao.CategorySpending
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import com.projectkaka.inventory.data.local.entity.JournalEntryEntity
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.PostingEntity
import com.projectkaka.inventory.data.local.entity.TransactionEntity
import com.projectkaka.inventory.model.Money
import kotlinx.coroutines.flow.Flow

// ── Data classes ────────────────────────────────────────────────────────

/** A fully resolved financial command, ready to be persisted on user confirmation. */
data class ResolvedTransaction(
    val amount: Money,
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
    fun observeAccountsWithCounts(): Flow<List<com.projectkaka.inventory.data.local.dao.AccountWithCounts>>
    fun getActiveAccounts(): Flow<List<AccountEntity>>
    suspend fun getActiveAccountsSnapshot(): List<AccountEntity>
    suspend fun getAllAccountsSnapshot(): List<AccountEntity>
    suspend fun runInvariantCheck()
    fun observeAllAccountBalances(): Flow<List<AccountEntity>>
    fun observeTotalCashBalance(): Flow<Money>
    suspend fun getAccountBalance(accountId: Int): Money?
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
    suspend fun recordSplitTransaction(entry: JournalEntryEntity, postings: List<PostingEntity>): Long
    suspend fun deleteTransaction(transaction: TransactionEntity)
    suspend fun transfer(fromAccountId: Int, toAccountId: Int, amount: Money, note: String, timestamp: Long = System.currentTimeMillis())
    suspend fun deleteTransfer(transferId: String)
    fun getAllTransactions(): Flow<List<TransactionEntity>>
    fun getTransactionsInRange(startMs: Long, endMs: Long): Flow<List<TransactionEntity>>
    fun getTransactionsForAccount(accountId: Int): Flow<List<TransactionEntity>>
    fun getTransactionsForCategory(categoryId: Int): Flow<List<TransactionEntity>>
    fun getSpendingByCategory(startMs: Long, endMs: Long): Flow<List<CategorySpending>>
    fun getIncomeByCategory(startMs: Long, endMs: Long): Flow<List<CategorySpending>>
    fun observeMonthSpending(monthStartMs: Long): Flow<Money>
    fun observeDaySpending(dayStartMs: Long, dayEndMs: Long): Flow<Money>
    suspend fun getDaySpending(dayStartMs: Long, dayEndMs: Long): Money
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
    
    // Returns exact matches (or by alias) without guessing
    suspend fun resolveAccountsExact(token: String): List<AccountEntity>
    suspend fun resolveCategoriesExact(token: String): List<FinancialCategoryEntity>

    suspend fun initializeAccountBalance(accountAlias: String, targetBalance: Money)

    /**
     * Reconciliation routine that verifies all stored balances match the invariant
     * (opening_balance + SUM(credits) - SUM(debits)) and repairs any that drift out of sync.
     * Returns the number of accounts repaired.
     */
    suspend fun reconcileBalances(): Int

    // ── Ledger ──────────────────────────────────────────────────────────

    fun getUnsettledEntries(): Flow<List<LedgerEntryEntity>>
    fun getAllLedgerEntries(): Flow<List<LedgerEntryEntity>>
    suspend fun insertLedgerEntry(entry: LedgerEntryEntity): Long
    suspend fun updateLedgerEntry(entry: LedgerEntryEntity)
    suspend fun deleteLedgerEntry(entry: LedgerEntryEntity)
    fun observeTotalReceivable(): Flow<Money>
    fun observeTotalPayable(): Flow<Money>
    fun observeContactSummaries(): Flow<List<com.projectkaka.inventory.data.local.dao.ContactSummaryRow>>
    fun searchLedgerEntries(
        contactQuery: String? = null,
        isSettled: Boolean? = null,
        minAmount: Long? = null,
        maxAmount: Long? = null,
        minDate: Long? = null,
        maxDate: Long? = null
    ): kotlinx.coroutines.flow.Flow<List<LedgerEntryEntity>>

    suspend fun issueDebt(
        entry: LedgerEntryEntity,
        accountId: Int,
        note: String
    ): Long

    suspend fun settleDebt(
        entryId: Int,
        accountId: Int,
        note: String
    )

    suspend fun settleDebtPartial(
        entryId: Int,
        accountId: Int,
        paidAmount: Money,
        note: String
    ): LedgerEntryEntity?

    suspend fun getDistinctContactNames(): List<String>
    suspend fun getUnsettledEntriesForContact(contactQuery: String): List<LedgerEntryEntity>
    suspend fun getUnsettledEntriesForExactContact(contact: String): List<LedgerEntryEntity>
    suspend fun getTransactionsInRangeSnapshot(startMs: Long, endMs: Long): List<TransactionEntity>

    // ── Export ───────────────────────────────────────────────────────────

    suspend fun exportFinancialSnapshot(): FinancialExportData
}
