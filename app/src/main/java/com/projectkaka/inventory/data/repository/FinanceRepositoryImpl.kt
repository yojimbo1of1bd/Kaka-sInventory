package com.projectkaka.inventory.data.repository

import com.projectkaka.inventory.data.local.AppDatabase
import com.projectkaka.inventory.data.local.dao.CategorySpending
import com.projectkaka.inventory.data.local.dao.ContactSummaryRow
import com.projectkaka.inventory.data.local.dao.FinanceDao
import androidx.room.withTransaction
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.TransactionEntity
import com.projectkaka.inventory.model.Money
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class FinanceRepositoryImpl(
    private val db: AppDatabase,
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

    override fun observeAllAccountBalances(): Flow<List<AccountEntity>> =
        financeDao.observeAllAccountBalances()

    override fun observeTotalCashBalance(): Flow<Money> =
        financeDao.observeTotalCashBalance()

    override suspend fun getAccountBalance(accountId: Int): Money? =
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
        withContext(ioDispatcher) { 
            db.withTransaction {
                val id = financeDao.insertTransaction(transaction)
                financeDao.recalculateAccountBalance(transaction.accountId)
                id
            }
        }

    override suspend fun deleteTransaction(transaction: TransactionEntity) =
        withContext(ioDispatcher) { 
            db.withTransaction {
                financeDao.deleteTransaction(transaction)
                financeDao.recalculateAccountBalance(transaction.accountId)
            }
        }

    override suspend fun transfer(fromAccountId: Int, toAccountId: Int, amount: Money, note: String, timestamp: Long) =
        withContext(ioDispatcher) {
            db.withTransaction {
                val transferId = UUID.randomUUID().toString()
                financeDao.executeTransfer(fromAccountId, toAccountId, amount, note, transferId, timestamp)
                financeDao.recalculateAccountBalance(fromAccountId)
                financeDao.recalculateAccountBalance(toAccountId)
            }
        }

    override suspend fun deleteTransfer(transferId: String) =
        withContext(ioDispatcher) { 
            db.withTransaction {
                val txs = financeDao.getTransactionsByTransferId(transferId)
                financeDao.deleteTransfer(transferId)
                txs.map { it.accountId }.distinct().forEach {
                    financeDao.recalculateAccountBalance(it)
                }
            }
        }

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

    override fun observeMonthSpending(monthStartMs: Long): Flow<Money> =
        financeDao.observeMonthSpending(monthStartMs)

    override fun observeDaySpending(dayStartMs: Long, dayEndMs: Long): Flow<Money> =
        financeDao.observeDaySpending(dayStartMs, dayEndMs)

    override suspend fun getDaySpending(dayStartMs: Long, dayEndMs: Long): Money =
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

    override suspend fun initializeAccountBalance(accountAlias: String, targetBalance: Money) {
        withContext(ioDispatcher) {
            val account = resolveAccount(accountAlias)
            if (account != null) {
                val currentBalance = financeDao.getAccountBalance(account.id) ?: Money(0L)
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
        withContext(ioDispatcher) {
            db.withTransaction {
                financeDao.updateLedgerEntry(entry)
                if (entry.linkedTransactionId != null) {
                    val tx = financeDao.getTransactionById(entry.linkedTransactionId)
                    if (tx != null) {
                        val isCredit = entry.type == com.projectkaka.inventory.data.local.entity.LedgerType.PAYABLE
                        financeDao.insertTransaction(tx.copy(
                            amount = entry.amount,
                            isCredit = isCredit,
                            accountId = entry.accountId ?: tx.accountId
                        ))
                        financeDao.recalculateAccountBalance(entry.accountId ?: tx.accountId)
                        if (entry.accountId != null && entry.accountId != tx.accountId) {
                            financeDao.recalculateAccountBalance(tx.accountId)
                        }
                    }
                }
            }
        }

    override suspend fun deleteLedgerEntry(entry: LedgerEntryEntity) =
        withContext(ioDispatcher) {
            db.withTransaction {
                financeDao.deleteLedgerEntry(entry)
                if (entry.linkedTransactionId != null) {
                    val tx = financeDao.getTransactionById(entry.linkedTransactionId)
                    if (tx != null) {
                        financeDao.deleteTransaction(tx)
                        financeDao.recalculateAccountBalance(tx.accountId)
                    }
                }
            }
        }

    override fun observeTotalReceivable(): Flow<Money> =
        financeDao.observeTotalReceivable()

    override fun observeTotalPayable(): Flow<Money> =
        financeDao.observeTotalPayable()

    override fun observeContactSummaries(): Flow<List<ContactSummaryRow>> =
        financeDao.observeContactSummaries()

    override suspend fun searchLedgerEntries(
        contactName: String?,
        isSettled: Boolean?,
        minAmount: Long?,
        maxAmount: Long?,
        minDate: Long?,
        maxDate: Long?
    ): List<LedgerEntryEntity> = withContext(ioDispatcher) {
        financeDao.searchLedgerEntries(contactName, isSettled, minAmount, maxAmount, minDate, maxDate)
    }

    override suspend fun issueDebt(
        entry: LedgerEntryEntity,
        accountId: Int,
        note: String
    ): Long = withContext(ioDispatcher) {
        db.withTransaction {
            val isCredit = entry.type == com.projectkaka.inventory.data.local.entity.LedgerType.PAYABLE

            val tx = TransactionEntity(
                amount = entry.amount,
                accountId = accountId,
                categoryId = null,
                type = com.projectkaka.inventory.data.local.entity.TransactionType.DEBT_ISSUE,
                isCredit = isCredit,
                note = note
            )
            val txId = financeDao.insertTransaction(tx)
            financeDao.recalculateAccountBalance(accountId)

            val newEntry = entry.copy(
                accountId = accountId,
                linkedTransactionId = txId.toInt()
            )
            financeDao.insertLedgerEntry(newEntry)
        }
    }

    override suspend fun settleDebt(
        entryId: Int,
        accountId: Int,
        note: String
    ) = withContext(ioDispatcher) {
        db.withTransaction {
            val entry = financeDao.getLedgerEntryById(entryId) ?: return@withTransaction
            if (entry.isSettled) return@withTransaction

            val isCredit = entry.type == com.projectkaka.inventory.data.local.entity.LedgerType.RECEIVABLE

            val tx = TransactionEntity(
                amount = entry.amount,
                accountId = accountId,
                categoryId = null,
                type = com.projectkaka.inventory.data.local.entity.TransactionType.DEBT_SETTLE,
                isCredit = isCredit,
                note = note
            )
            financeDao.insertTransaction(tx)
            financeDao.recalculateAccountBalance(accountId)

            financeDao.updateLedgerEntry(entry.copy(isSettled = true))
        }
    }

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
