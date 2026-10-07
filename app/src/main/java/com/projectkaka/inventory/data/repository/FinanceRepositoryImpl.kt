package com.projectkaka.inventory.data.repository

import com.projectkaka.inventory.data.local.AppDatabase
import com.projectkaka.inventory.data.local.dao.CategorySpending
import com.projectkaka.inventory.data.local.dao.ContactSummaryRow
import com.projectkaka.inventory.data.local.dao.FinanceDao
import androidx.room.withTransaction
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.data.local.entity.CategoryType
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import com.projectkaka.inventory.data.local.entity.JournalEntryEntity
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.PostingEntity
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
    private val journalDao: com.projectkaka.inventory.data.local.dao.JournalDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FinanceRepository {

    // ── Accounts ────────────────────────────────────────────────────────

    override fun getAllAccounts(): Flow<List<AccountEntity>> =
        financeDao.getAllAccounts()

    override fun observeAccountsWithCounts(): Flow<List<com.projectkaka.inventory.data.local.dao.AccountWithCounts>> =
        financeDao.observeAccountsWithCounts()

    override fun getActiveAccounts(): Flow<List<AccountEntity>> =
        financeDao.getActiveAccounts()

    override suspend fun getActiveAccountsSnapshot(): List<AccountEntity> =
        withContext(ioDispatcher) { financeDao.getActiveAccountsSnapshot() }
        
    override suspend fun getAllAccountsSnapshot(): List<AccountEntity> =
        withContext(ioDispatcher) { financeDao.getAllAccountsSnapshot() }

    private val liquidWalletNames = setOf("bkash", "bikash", "nagad", "rocket", "upay", "cellfin", "tap")

    override suspend fun runInvariantCheck() =
        withContext(ioDispatcher) {
            autoRepairLiquidWalletTypes()
            val mismatches = financeDao.getAccountsWithMismatchedBalances()
            if (mismatches.isNotEmpty()) {
                val msg = "KAKA_INVARIANT_ERROR: Found ${mismatches.size} accounts with mismatched balances: ${mismatches.joinToString { it.name }}"
                android.util.Log.e("KAKA_INVARIANT_ERROR", msg)
            }
        }

    private suspend fun autoRepairLiquidWalletTypes() {
        val accounts = financeDao.getAllAccountsSnapshot()
        var updated = false
        for (acc in accounts) {
            val lower = acc.name.lowercase().trim()
            val aliases = acc.aliases.lowercase().split(",").map { it.trim() }
            val isLiquid = liquidWalletNames.contains(lower) || aliases.any { liquidWalletNames.contains(it) }
            if (isLiquid && acc.type != AccountType.CASH) {
                financeDao.updateAccount(acc.copy(type = AccountType.CASH))
                financeDao.recalculateAccountBalance(acc.id)
                updated = true
            }
        }
        if (updated) {
            syncCapitalOpeningBalance()
        }
    }

    override fun observeAllAccountBalances(): Flow<List<AccountEntity>> =
        financeDao.observeAllAccountBalances()

    override fun observeTotalCashBalance(): Flow<Money> =
        financeDao.observeTotalCashBalance()

    override suspend fun getAccountBalance(accountId: Int): Money? =
        withContext(ioDispatcher) { financeDao.getAccountBalance(accountId) }

    override suspend fun insertAccount(account: AccountEntity): Long =
        withContext(ioDispatcher) { 
            db.withTransaction {
                val id = financeDao.insertAccount(account)
                financeDao.recalculateAccountBalance(id.toInt())
                syncCapitalOpeningBalance()
                id
            }
        }

    override suspend fun updateAccount(account: AccountEntity) =
        withContext(ioDispatcher) { 
            db.withTransaction {
                financeDao.updateAccount(account)
                financeDao.recalculateAccountBalance(account.id)
                syncCapitalOpeningBalance()
            }
        }

    private suspend fun syncCapitalOpeningBalance() {
        val capitalAcc = financeDao.getAccountByName("Capital") ?: return
        val allAccounts = financeDao.getAllAccountsSnapshot().filter { it.id != capitalAcc.id }
        val assetsOpening = allAccounts.filter { it.type == AccountType.ASSET || it.type == AccountType.CASH }.sumOf { it.openingBalance.minorUnits }
        val liabOpening = allAccounts.filter { it.type == AccountType.LIABILITY }.sumOf { it.openingBalance.minorUnits }
        val netCapital = assetsOpening - liabOpening
        financeDao.updateAccount(capitalAcc.copy(openingBalance = Money(netCapital)))
        financeDao.recalculateAccountBalance(capitalAcc.id)
    }

    override suspend fun deleteAccount(account: AccountEntity) =
        withContext(ioDispatcher) { 
            db.withTransaction {
                val txCount = financeDao.getTransactionCountForAccount(account.id)
                val ledgerCount = financeDao.getLedgerCountForAccount(account.id)
                if (txCount > 0 || ledgerCount > 0) {
                    throw IllegalStateException("Cannot delete account: it has existing transactions or ledger entries.")
                }
                financeDao.deleteAccount(account)
            }
        }

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
                val counterAccountId = if (transaction.categoryId != null) {
                    val category = financeDao.getCategoryById(transaction.categoryId!!)
                    if (category != null) {
                        financeDao.getAccountByName(category.name)?.id
                            ?: run {
                                val accType = if (category.type == CategoryType.INCOME) AccountType.REVENUE else AccountType.EXPENSE
                                val newAcc = AccountEntity(
                                    name = category.name,
                                    type = accType,
                                    aliases = category.aliases,
                                    openingBalance = Money(0L)
                                )
                                val id = financeDao.insertAccount(newAcc)
                                financeDao.recalculateAccountBalance(id.toInt())
                                id.toInt()
                            }
                    } else null
                } else if (transaction.counterAccountId != null) {
                    transaction.counterAccountId
                } else {
                    // For adjustments, default to 'Capital' account if we can find it
                    financeDao.getAccountByName("Capital")?.id
                        ?: throw IllegalStateException("No Capital account found for adjustment")
                }
                
                if (counterAccountId == null) throw IllegalStateException("Cannot resolve counter account")

                val entryId = journalDao.insertJournalEntry(
                    com.projectkaka.inventory.data.local.entity.JournalEntryEntity(
                        timestamp = transaction.timestamp,
                        description = transaction.note,
                        status = com.projectkaka.inventory.data.local.entity.JournalStatus.DRAFT,
                        approvalStatus = com.projectkaka.inventory.data.local.entity.ApprovalStatus.APPROVED
                    )
                ).toInt()

                // Main account posting (Asset/Cash)
                // In Kaka, transaction.isCredit = true means INCOME (increases cash).
                // In accounting, increasing an asset is a DEBIT (isCredit = false).
                // Therefore, posting.isCredit = !transaction.isCredit.
                journalDao.insertPosting(
                    com.projectkaka.inventory.data.local.entity.PostingEntity(
                        journalEntryId = entryId,
                        accountId = transaction.accountId,
                        amount = transaction.amount,
                        isCredit = !transaction.isCredit,
                        note = transaction.note
                    )
                )

                // Counter account posting (Expense/Income)
                journalDao.insertPosting(
                    com.projectkaka.inventory.data.local.entity.PostingEntity(
                        journalEntryId = entryId,
                        accountId = counterAccountId,
                        amount = transaction.amount,
                        isCredit = transaction.isCredit,
                        note = transaction.note
                    )
                )

                journalDao.commitJournalEntry(entryId)
                
                financeDao.recalculateAccountBalance(transaction.accountId)
                financeDao.recalculateAccountBalance(counterAccountId)
                
                entryId.toLong() // Return journal entry ID
            }
        }

    override suspend fun recordSplitTransaction(entry: JournalEntryEntity, postings: List<PostingEntity>): Long =
        withContext(ioDispatcher) {
            db.withTransaction {
                val entryId = journalDao.insertJournalEntry(entry).toInt()
                val updatedPostings = postings.map { it.copy(journalEntryId = entryId) }
                journalDao.insertPostings(updatedPostings)
                journalDao.commitJournalEntry(entryId)
                
                updatedPostings.forEach { financeDao.recalculateAccountBalance(it.accountId) }
                return@withTransaction entryId.toLong()
            }
        }

    override suspend fun deleteTransaction(transaction: TransactionEntity) =
        withContext(ioDispatcher) { 
            db.withTransaction {
                // transaction.id is actually the posting ID in the new queries
                val posting = journalDao.getPostingById(transaction.id)
                if (posting != null) {
                    val entryId = posting.journalEntryId
                    journalDao.deleteJournalEntryById(entryId)
                    financeDao.recalculateAccountBalance(transaction.accountId)
                    if (transaction.counterAccountId != null) {
                        financeDao.recalculateAccountBalance(transaction.counterAccountId!!)
                    }
                }
            }
        }

    override suspend fun transfer(fromAccountId: Int, toAccountId: Int, amount: Money, note: String, timestamp: Long) =
        withContext(ioDispatcher) {
            db.withTransaction {
                val transferId = UUID.randomUUID().toString()
                
                val entryId = journalDao.insertJournalEntry(
                    com.projectkaka.inventory.data.local.entity.JournalEntryEntity(
                        timestamp = timestamp,
                        description = note,
                        status = com.projectkaka.inventory.data.local.entity.JournalStatus.DRAFT,
                        approvalStatus = com.projectkaka.inventory.data.local.entity.ApprovalStatus.APPROVED
                    )
                ).toInt()

                journalDao.insertPosting(
                    com.projectkaka.inventory.data.local.entity.PostingEntity(
                        journalEntryId = entryId,
                        accountId = fromAccountId,
                        amount = amount,
                        isCredit = true, // Money leaves fromAccount
                        note = note
                    )
                )

                journalDao.insertPosting(
                    com.projectkaka.inventory.data.local.entity.PostingEntity(
                        journalEntryId = entryId,
                        accountId = toAccountId,
                        amount = amount,
                        isCredit = false, // Money enters toAccount
                        note = note
                    )
                )

                journalDao.commitJournalEntry(entryId)

                financeDao.recalculateAccountBalance(fromAccountId)
                financeDao.recalculateAccountBalance(toAccountId)
            }
        }

    override suspend fun deleteTransfer(transferId: String) =
        withContext(ioDispatcher) { 
            db.withTransaction {
                // transferId in queries is mapped to CAST(journal_entries.id AS TEXT)
                val entryId = transferId.toIntOrNull()
                if (entryId != null) {
                    val postings = journalDao.getPostingsForJournalEntry(entryId)
                    journalDao.deleteJournalEntryById(entryId)
                    val accountIds = postings.map { it.accountId }.distinct()
                    for (accountId in accountIds) {
                        financeDao.recalculateAccountBalance(accountId)
                    }
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

    override suspend fun resolveAccountsExact(token: String): List<AccountEntity> =
        withContext(ioDispatcher) {
            financeDao.getActiveAccountsSnapshot().filter { it.matchesExactInput(token) }
        }

    override suspend fun resolveCategoriesExact(token: String): List<FinancialCategoryEntity> =
        withContext(ioDispatcher) {
            financeDao.getAllCategoriesSnapshot().filter { it.matchesExactInput(token) }
        }

    override suspend fun initializeAccountBalance(accountAlias: String, targetBalance: Money) {
        withContext(ioDispatcher) {
            db.withTransaction {
                val account = resolveAccount(accountAlias)
                if (account != null) {
                    val currentBalance = financeDao.getAccountBalance(account.id) ?: Money(0L)
                    val diff = targetBalance - currentBalance
                    val newOpening = account.openingBalance + diff
                    val lower = account.name.lowercase().trim()
                    val targetType = if (liquidWalletNames.contains(lower) && account.type != AccountType.CASH) {
                        AccountType.CASH
                    } else account.type
                    financeDao.updateAccount(account.copy(openingBalance = newOpening, type = targetType))
                    financeDao.recalculateAccountBalance(account.id)
                } else {
                    val newAccount = com.projectkaka.inventory.data.local.entity.AccountEntity(
                        name = accountAlias.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() },
                        type = AccountType.CASH,
                        openingBalance = targetBalance
                    )
                    val id = financeDao.insertAccount(newAccount)
                    financeDao.recalculateAccountBalance(id.toInt())
                }
                syncCapitalOpeningBalance()
            }
        }
    }

    override suspend fun reconcileBalances(): Int =
        withContext(ioDispatcher) {
            db.withTransaction {
                autoRepairLiquidWalletTypes()
                var repairedCount = 0
                val accounts = financeDao.getAllAccountsSnapshot()
                for (acc in accounts) {
                    val oldBalance = financeDao.getAccountBalance(acc.id)
                    financeDao.recalculateAccountBalance(acc.id)
                    val newBalance = financeDao.getAccountBalance(acc.id)
                    if (oldBalance != newBalance) {
                        repairedCount++
                    }
                }
                syncCapitalOpeningBalance()
                repairedCount
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
                    val journalEntryId = entry.linkedTransactionId
                    val postings = journalDao.getPostingsForJournalEntry(journalEntryId)
                    if (postings.isNotEmpty()) {
                        val accountIds = postings.map { it.accountId }
                        journalDao.deleteJournalEntryById(journalEntryId)
                        accountIds.forEach { financeDao.recalculateAccountBalance(it) }

                        val isPayable = entry.type == com.projectkaka.inventory.data.local.entity.LedgerType.PAYABLE
                        val counterAccountId = if (isPayable) {
                            financeDao.getAccountByName("Liabilities")?.id ?: throw IllegalStateException("Liabilities missing")
                        } else {
                            financeDao.getAccountByName("Assets")?.id ?: throw IllegalStateException("Assets missing")
                        }

                        val newJournalEntryId = journalDao.insertJournalEntry(
                            com.projectkaka.inventory.data.local.entity.JournalEntryEntity(
                                timestamp = System.currentTimeMillis(),
                                description = entry.note,
                                status = com.projectkaka.inventory.data.local.entity.JournalStatus.DRAFT,
                                approvalStatus = com.projectkaka.inventory.data.local.entity.ApprovalStatus.APPROVED
                            )
                        ).toInt()

                        val accountId = entry.accountId ?: accountIds.first { it != counterAccountId }
                        
                        journalDao.insertPosting(
                            com.projectkaka.inventory.data.local.entity.PostingEntity(
                                journalEntryId = newJournalEntryId,
                                accountId = accountId,
                                amount = entry.amount,
                                isCredit = !isPayable, 
                                note = entry.note
                            )
                        )

                        journalDao.insertPosting(
                            com.projectkaka.inventory.data.local.entity.PostingEntity(
                                journalEntryId = newJournalEntryId,
                                accountId = counterAccountId,
                                amount = entry.amount,
                                isCredit = isPayable,
                                note = entry.note
                            )
                        )

                        journalDao.commitJournalEntry(newJournalEntryId)
                        financeDao.recalculateAccountBalance(accountId)
                        financeDao.recalculateAccountBalance(counterAccountId)
                        
                        financeDao.updateLedgerEntry(entry.copy(linkedTransactionId = newJournalEntryId))
                    }
                }
            }
        }

    override suspend fun deleteLedgerEntry(entry: LedgerEntryEntity) =
        withContext(ioDispatcher) {
            db.withTransaction {
                financeDao.deleteLedgerEntry(entry)
                if (entry.linkedTransactionId != null) {
                    val postings = journalDao.getPostingsForJournalEntry(entry.linkedTransactionId)
                    val accountIds = postings.map { it.accountId }
                    journalDao.deleteJournalEntryById(entry.linkedTransactionId)
                    accountIds.forEach { financeDao.recalculateAccountBalance(it) }
                }
            }
        }

    override fun observeTotalReceivable(): Flow<Money> =
        financeDao.observeTotalReceivable()

    override fun observeTotalPayable(): Flow<Money> =
        financeDao.observeTotalPayable()

    override fun observeContactSummaries(): Flow<List<ContactSummaryRow>> =
        financeDao.observeContactSummaries()

    override fun searchLedgerEntries(
        contactQuery: String?,
        isSettled: Boolean?,
        minAmount: Long?,
        maxAmount: Long?,
        minDate: Long?,
        maxDate: Long?
    ): kotlinx.coroutines.flow.Flow<List<LedgerEntryEntity>> {
        val query = contactQuery?.let { "%${com.projectkaka.inventory.util.SearchHelper.escapeLike(it)}%" }
        return financeDao.searchLedgerEntries(query, isSettled, minAmount, maxAmount, minDate, maxDate)
    }

    override suspend fun issueDebt(
        entry: LedgerEntryEntity,
        accountId: Int,
        note: String
    ): Long = withContext(ioDispatcher) {
        db.withTransaction {
            val isPayable = entry.type == com.projectkaka.inventory.data.local.entity.LedgerType.PAYABLE

            val entryId = journalDao.insertJournalEntry(
                com.projectkaka.inventory.data.local.entity.JournalEntryEntity(
                    timestamp = System.currentTimeMillis(),
                    description = note,
                    status = com.projectkaka.inventory.data.local.entity.JournalStatus.DRAFT,
                    approvalStatus = com.projectkaka.inventory.data.local.entity.ApprovalStatus.APPROVED
                )
            ).toInt()

            val counterAccountId = if (isPayable) {
                financeDao.getAccountByName("Liabilities")?.id ?: throw IllegalStateException("Liabilities missing")
            } else {
                financeDao.getAccountByName("Assets")?.id ?: throw IllegalStateException("Assets missing")
            }

            journalDao.insertPosting(
                com.projectkaka.inventory.data.local.entity.PostingEntity(
                    journalEntryId = entryId,
                    accountId = accountId,
                    amount = entry.amount,
                    isCredit = !isPayable, 
                    note = note
                )
            )

            journalDao.insertPosting(
                com.projectkaka.inventory.data.local.entity.PostingEntity(
                    journalEntryId = entryId,
                    accountId = counterAccountId,
                    amount = entry.amount,
                    isCredit = isPayable,
                    note = note
                )
            )

            journalDao.commitJournalEntry(entryId)
            financeDao.recalculateAccountBalance(accountId)
            financeDao.recalculateAccountBalance(counterAccountId)

            val newEntry = entry.copy(
                accountId = accountId,
                linkedTransactionId = entryId
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

            val isPayable = entry.type == com.projectkaka.inventory.data.local.entity.LedgerType.PAYABLE

            val journalEntryId = journalDao.insertJournalEntry(
                com.projectkaka.inventory.data.local.entity.JournalEntryEntity(
                    timestamp = System.currentTimeMillis(),
                    description = note,
                    status = com.projectkaka.inventory.data.local.entity.JournalStatus.DRAFT,
                    approvalStatus = com.projectkaka.inventory.data.local.entity.ApprovalStatus.APPROVED
                )
            ).toInt()

            val counterAccountId = if (isPayable) {
                financeDao.getAccountByName("Liabilities")?.id ?: throw IllegalStateException("Liabilities missing")
            } else {
                financeDao.getAccountByName("Assets")?.id ?: throw IllegalStateException("Assets missing")
            }

            journalDao.insertPosting(
                com.projectkaka.inventory.data.local.entity.PostingEntity(
                    journalEntryId = journalEntryId,
                    accountId = accountId,
                    amount = entry.amount,
                    isCredit = isPayable, 
                    note = note
                )
            )

            journalDao.insertPosting(
                com.projectkaka.inventory.data.local.entity.PostingEntity(
                    journalEntryId = journalEntryId,
                    accountId = counterAccountId,
                    amount = entry.amount,
                    isCredit = !isPayable,
                    note = note
                )
            )

            journalDao.commitJournalEntry(journalEntryId)
            financeDao.recalculateAccountBalance(accountId)
            financeDao.recalculateAccountBalance(counterAccountId)

            financeDao.updateLedgerEntry(entry.copy(isSettled = true))
        }
    }

    override suspend fun settleDebtPartial(
        entryId: Int,
        accountId: Int,
        paidAmount: Money,
        note: String
    ): LedgerEntryEntity? = withContext(ioDispatcher) {
        db.withTransaction {
            val entry = financeDao.getLedgerEntryById(entryId) ?: return@withTransaction null
            if (entry.isSettled) return@withTransaction null

            if (paidAmount >= entry.amount) {
                settleDebt(entryId, accountId, note)
                return@withTransaction null
            }

            val isPayable = entry.type == com.projectkaka.inventory.data.local.entity.LedgerType.PAYABLE

            val journalEntryId = journalDao.insertJournalEntry(
                com.projectkaka.inventory.data.local.entity.JournalEntryEntity(
                    timestamp = System.currentTimeMillis(),
                    description = note,
                    status = com.projectkaka.inventory.data.local.entity.JournalStatus.DRAFT,
                    approvalStatus = com.projectkaka.inventory.data.local.entity.ApprovalStatus.APPROVED
                )
            ).toInt()

            val counterAccountId = if (isPayable) {
                financeDao.getAccountByName("Liabilities")?.id ?: throw IllegalStateException("Liabilities missing")
            } else {
                financeDao.getAccountByName("Assets")?.id ?: throw IllegalStateException("Assets missing")
            }

            journalDao.insertPosting(
                com.projectkaka.inventory.data.local.entity.PostingEntity(
                    journalEntryId = journalEntryId,
                    accountId = accountId,
                    amount = paidAmount,
                    isCredit = isPayable,
                    note = note
                )
            )

            journalDao.insertPosting(
                com.projectkaka.inventory.data.local.entity.PostingEntity(
                    journalEntryId = journalEntryId,
                    accountId = counterAccountId,
                    amount = paidAmount,
                    isCredit = !isPayable,
                    note = note
                )
            )

            journalDao.commitJournalEntry(journalEntryId)
            financeDao.recalculateAccountBalance(accountId)
            financeDao.recalculateAccountBalance(counterAccountId)

            // Update original entry to settled for the paid amount
            val settledEntry = entry.copy(
                amount = paidAmount,
                isSettled = true,
                linkedTransactionId = journalEntryId
            )
            financeDao.updateLedgerEntry(settledEntry)

            // Insert new open remainder entry for the unpaid balance
            val remainderAmount = entry.amount - paidAmount
            val remainderEntry = entry.copy(
                id = 0,
                amount = remainderAmount,
                isSettled = false,
                linkedTransactionId = null,
                note = "${entry.note} (Remainder balance)".trim(),
                createdAt = System.currentTimeMillis()
            )
            val newId = financeDao.insertLedgerEntry(remainderEntry)
            remainderEntry.copy(id = newId.toInt())
        }
    }

    override suspend fun getDistinctContactNames(): List<String> = withContext(ioDispatcher) {
        financeDao.getDistinctContactNames()
    }

    override suspend fun getUnsettledEntriesForContact(contactQuery: String): List<LedgerEntryEntity> = withContext(ioDispatcher) {
        val exact = financeDao.getUnsettledEntriesForExactContact(contactQuery.trim())
        if (exact.isNotEmpty()) {
            exact
        } else {
            financeDao.getUnsettledEntriesForContact("%" + com.projectkaka.inventory.util.SearchHelper.escapeLike(contactQuery) + "%")
        }
    }

    override suspend fun getUnsettledEntriesForExactContact(contact: String): List<LedgerEntryEntity> = withContext(ioDispatcher) {
        financeDao.getUnsettledEntriesForExactContact(contact.trim())
    }

    override suspend fun getTransactionsInRangeSnapshot(startMs: Long, endMs: Long): List<TransactionEntity> = withContext(ioDispatcher) {
        financeDao.getTransactionsInRangeSnapshot(startMs, endMs)
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
