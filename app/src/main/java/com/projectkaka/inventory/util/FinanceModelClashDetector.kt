package com.projectkaka.inventory.util

import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.TransactionEntity

/**
 * Pre-export auditor to safeguard the financial model.
 *
 * Verifies that informal debt ledger commitments (ledger_entries) do not have
 * broken foreign keys, orphaned references, or corrupted balances that would
 * clash with double-entry journal accounts or balance sheet calculations.
 *
 * If a clash is detected, the liability ledger is automatically skipped during backup
 * so that core items, documents, and formal accounts are safely preserved without corruption.
 */
object FinanceModelClashDetector {

    data class ClashCheckResult(
        val hasClash: Boolean,
        val clashReasons: List<String>,
        val exportableLedgerEntries: List<LedgerEntryEntity>?
    )

    fun evaluate(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        ledgerEntries: List<LedgerEntryEntity>
    ): ClashCheckResult {
        val reasons = mutableListOf<String>()
        val accountIds = accounts.map { it.id }.toSet()
        val txIds = transactions.map { it.id }.toSet()

        // 1. Check for orphaned account references
        val orphanAccounts = ledgerEntries.filter { it.accountId != null && it.accountId !in accountIds }
        if (orphanAccounts.isNotEmpty()) {
            reasons.add("${orphanAccounts.size} ledger entry(ies) reference non-existent account ID(s)")
        }

        // 2. Check for orphaned transaction references
        val orphanTransactions = ledgerEntries.filter { it.linkedTransactionId != null && it.linkedTransactionId !in txIds }
        if (orphanTransactions.isNotEmpty()) {
            reasons.add("${orphanTransactions.size} ledger entry(ies) reference non-existent transaction ID(s)")
        }

        // 3. Check for invalid negative currency amounts
        val invalidAmounts = ledgerEntries.filter { it.amount.minorUnits < 0 }
        if (invalidAmounts.isNotEmpty()) {
            reasons.add("${invalidAmounts.size} ledger entry(ies) contain corrupted negative balance(s)")
        }

        val hasClash = reasons.isNotEmpty()
        return ClashCheckResult(
            hasClash = hasClash,
            clashReasons = reasons,
            exportableLedgerEntries = if (hasClash) null else ledgerEntries
        )
    }
}
