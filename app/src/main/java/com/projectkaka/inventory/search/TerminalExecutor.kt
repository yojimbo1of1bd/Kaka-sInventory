package com.projectkaka.inventory.search

import com.projectkaka.inventory.data.local.entity.TransactionEntity
import com.projectkaka.inventory.data.repository.FinanceRepository
import com.projectkaka.inventory.model.Money

class TerminalExecutor(
    private val financeRepo: FinanceRepository,
    private val context: android.content.Context? = null,
    private val preferences: com.projectkaka.inventory.data.settings.UserPreferences? = null
) {
    private var pendingDestructiveCommand: TerminalCommand.Delete? = null
    private var pendingSnapshotAction: String? = null

    suspend fun execute(input: String): TerminalResult {
        val trimmed = input.trim()
        if (trimmed.isNotBlank()) {
            preferences?.addCommandToHistory(trimmed)
        }
        
        // Handle pending confirmation
        val pending = pendingDestructiveCommand
        if (pending != null) {
            if (trimmed.equals("yes", ignoreCase = true) || trimmed.equals("y", ignoreCase = true)) {
                pendingDestructiveCommand = null
                return executeConfirmedDelete(pending)
            } else {
                pendingDestructiveCommand = null
                return TerminalResult.Failure("Command cancelled.")
            }
        }

        // Handle pending snapshot passphrase
        val pendingSnap = pendingSnapshotAction
        if (pendingSnap != null) {
            if (trimmed.equals("cancel", ignoreCase = true) || trimmed.equals("exit", ignoreCase = true)) {
                pendingSnapshotAction = null
                return TerminalResult.Failure("Snapshot operation cancelled.")
            }
            if (trimmed.startsWith("snapshot/") || trimmed.startsWith("f/") || trimmed.startsWith("clear") || trimmed == "pop" || trimmed == "pop/") {
                pendingSnapshotAction = null
            } else {
                pendingSnapshotAction = null
                val ctx = context ?: return TerminalResult.Failure("Context unavailable for snapshot.")
                return if (pendingSnap == "take") {
                    val pass = if (trimmed.equals("NONE", ignoreCase = true)) "" else trimmed
                    takeSnapshotWithPassphrase(ctx, pass)
                } else if (pendingSnap == "check") {
                    val pass = if (trimmed.equals("NONE", ignoreCase = true)) "" else trimmed
                    checkSnapshotWithPassphrase(ctx, pass)
                } else {
                    TerminalResult.Failure("Unknown pending snapshot action.")
                }
            }
        }
        
        val command = TerminalParser.parse(trimmed)
        return try {
            executeCommand(command)
        } catch (e: Exception) {
            TerminalResult.Failure(e.message ?: "Command execution failed.")
        }
    }

    suspend fun executeCommand(command: TerminalCommand): TerminalResult {
        return when (command) {
            is TerminalCommand.Empty -> TerminalResult.Pending
            is TerminalCommand.Error -> TerminalResult.Failure(command.message)
            is TerminalCommand.Help -> TerminalResult.Success(
                "Available commands (Quick Reference):\n" +
                "  f/ [±]amount <debit_acc> <credit_acc> [\"notes\"] [@date]\n" +
                "  init/ <account> <amount>\n" +
                "  xfer/ <amount> <from> <to> [note]\n" +
                "  cashout/ <amount> <source> [target]\n" +
                "  charge/ <account> <rate> (e.g. *1.85%)\n" +
                "  due/ <in|out> <amount> <contact> [note]\n" +
                "  settle/ <contact> [amount] [account]\n" +
                "  log/ <month|today|YYYY-MM-DD [window]|all>\n" +
                "  snapshot/ <create|verify>\n" +
                "  verify/ images\n" +
                "  history/ [count]\n" +
                "  account/ <add|hide|show|archive|restore>\n" +
                "  alter/ <account> <rename|type> <value>\n" +
                "  bal/ <account|all>\n" +
                "  report/ <bs|pnl|trend>\n" +
                "  help/ ?  or  man ? (Debits & Credits Accounting Handbook)\n" +
                "  man <cmd> (e.g. man f/, man log/, man settle/, man snapshot/)\n" +
                "  kaka show alias|graph|ledger|export\n\n" +
                "💡 Tip: Tap '📖 Terminal Manual' button above or type 'kaka show alias' to view the full interactive reference and debits/credits guide."
            )
            is TerminalCommand.KakaAction -> TerminalResult.PendingAction(command.action, command.arg)
            is TerminalCommand.Charge -> executeCharge(command)
            is TerminalCommand.Cashout -> executeCashout(command)
            is TerminalCommand.Verify -> executeVerify(command)
            is TerminalCommand.Snapshot -> executeSnapshot(command)
            is TerminalCommand.Clear -> TerminalResult.PendingAction("clear")
            is TerminalCommand.CMatrix -> TerminalResult.PendingAction("cmatrix")
            is TerminalCommand.Pop -> TerminalResult.PendingAction(command.action)
            is TerminalCommand.Man -> executeMan(command)
            is TerminalCommand.Log -> executeLog(command)
            is TerminalCommand.History -> executeHistory(command)
            is TerminalCommand.Financial -> executeFinancial(command)
            is TerminalCommand.Init -> {
                val accounts = financeRepo.resolveAccountsExact(command.accountToken)
                if (accounts.size > 1) return TerminalResult.NeedsInput("Ambiguous account '${command.accountToken}'. Options:", accounts.map { it.name })
                
                if (accounts.isEmpty()) {
                    financeRepo.initializeAccountBalance(command.accountToken, command.amount)
                    val formatted = command.accountToken.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    TerminalResult.Success("Created account '$formatted' and initialized balance to ${command.amount.format()}")
                } else {
                    financeRepo.initializeAccountBalance(accounts.first().name, command.amount)
                    TerminalResult.Success("Initialized ${accounts.first().name} to ${command.amount.format()}")
                }
            }
            is TerminalCommand.Delete -> {
                pendingDestructiveCommand = command
                TerminalResult.NeedsInput("Are you sure you want to delete ${command.targetType} '${command.selector}'? Type YES to confirm.", emptyList())
            }
            is TerminalCommand.Transfer -> executeTransfer(command)
            is TerminalCommand.LedgerDue -> executeLedgerDue(command)
            is TerminalCommand.LedgerSettle -> executeLedgerSettle(command)
            is TerminalCommand.AccountAdd -> executeAccountAdd(command)
            is TerminalCommand.AccountAction -> executeAccountAction(command)
            is TerminalCommand.BalanceCheck -> executeBalanceCheck(command)
            is TerminalCommand.Report -> executeReport(command)
            is TerminalCommand.AccountAlter -> executeAccountAlter(command)
            is TerminalCommand.Reset -> TerminalResult.Failure("Reset command is blocked to prevent accidental data loss. App maintains structural integrity.")
        }
    }

    private suspend fun executeConfirmedDelete(command: TerminalCommand.Delete): TerminalResult {
        return if (command.targetType == "account") {
            val accounts = financeRepo.resolveAccountsExact(command.selector)
            if (accounts.isEmpty()) return TerminalResult.Failure("Account not found: ${command.selector}")
            if (accounts.size > 1) return TerminalResult.Failure("Ambiguous account: ${command.selector}")
            
            try {
                financeRepo.deleteAccount(accounts.first())
                TerminalResult.Success("Deleted account: ${accounts.first().name}")
            } catch (e: Exception) {
                TerminalResult.Failure(e.message ?: "Failed to delete account.")
            }
        } else {
            TerminalResult.Failure("Unsupported delete target: ${command.targetType}")
        }
    }

    private suspend fun executeTransfer(command: TerminalCommand.Transfer): TerminalResult {
        val fromAccounts = financeRepo.resolveAccountsExact(command.fromToken)
        if (fromAccounts.isEmpty()) return TerminalResult.Failure("Unknown from account: ${command.fromToken}")
        if (fromAccounts.size > 1) return TerminalResult.NeedsInput("Ambiguous from account: ${command.fromToken}", fromAccounts.map { it.name })
        
        // Multi-word greedy match for to-account
        var toAccounts = financeRepo.resolveAccountsExact(command.toToken)
        var note = command.noteTokens.joinToString(" ")
        if (toAccounts.isEmpty() && command.noteTokens.isNotEmpty()) {
            val candidateTo = "${command.toToken} ${command.noteTokens.first()}"
            val candidateAccounts = financeRepo.resolveAccountsExact(candidateTo)
            if (candidateAccounts.isNotEmpty()) {
                toAccounts = candidateAccounts
                note = command.noteTokens.drop(1).joinToString(" ")
            }
        }
        if (toAccounts.isEmpty()) return TerminalResult.Failure("Unknown to account: ${command.toToken}")
        if (toAccounts.size > 1) return TerminalResult.NeedsInput("Ambiguous to account: ${command.toToken}", toAccounts.map { it.name })
        
        val fromAcc = fromAccounts.first()
        val toAcc = toAccounts.first()
        
        financeRepo.transfer(fromAcc.id, toAcc.id, command.amount, note, System.currentTimeMillis())
        return TerminalResult.Success("Transferred ${command.amount.format()} from ${fromAcc.name} to ${toAcc.name}")
    }

    private suspend fun executeCharge(command: TerminalCommand.Charge): TerminalResult {
        return when (command.action) {
            "all" -> {
                val all = preferences?.getAllAccountChargeRates() ?: emptyMap()
                if (all.isEmpty()) return TerminalResult.Success("No account cashout charges configured.\nSet one with: charge/ <account> <rate> (e.g. charge/ bkash *1.85%)")
                val lines = all.entries.joinToString("\n") { (acc, rate) ->
                    "  • ${acc.replaceFirstChar { it.uppercase() }}: ${"%.2f".format(rate * 100)}%"
                }
                TerminalResult.Success("Configured Cashout Charges:\n$lines")
            }
            "get" -> {
                val acc = command.accountToken ?: return TerminalResult.Failure("Account name required.")
                val rate = preferences?.getAccountChargeRate(acc)
                if (rate == null) {
                    TerminalResult.Failure("No cashout charge configured for '$acc'.", hint = "Set rate with: charge/ $acc *1.85%")
                } else {
                    TerminalResult.Success("$acc cashout charge: ${"%.2f".format(rate * 100)}%")
                }
            }
            "clear" -> {
                val acc = command.accountToken ?: return TerminalResult.Failure("Account name required.")
                preferences?.removeAccountChargeRate(acc)
                TerminalResult.Success("Cleared cashout charge for '$acc'.")
            }
            "set" -> {
                val acc = command.accountToken ?: return TerminalResult.Failure("Account name required.")
                val rateStr = command.rateToken ?: return TerminalResult.Failure("Charge rate required (e.g. *1.85% or 1.85%).")
                val cleaned = rateStr.removePrefix("*").removeSuffix("%").trim()
                val parsed = cleaned.toDoubleOrNull() ?: return TerminalResult.Failure("Invalid rate '$rateStr'. Use format like: *1.85% or 1.5%")
                val decimalRate = if (parsed >= 0.20) parsed / 100.0 else parsed
                preferences?.setAccountChargeRate(acc, decimalRate)
                TerminalResult.Success("Set cashout charge for '$acc' to ${"%.2f".format(decimalRate * 100)}%")
            }
            else -> TerminalResult.Failure("Unknown charge action: ${command.action}")
        }
    }

    private suspend fun executeCashout(command: TerminalCommand.Cashout): TerminalResult {
        val sourceAccounts = financeRepo.resolveAccountsExact(command.sourceToken)
        if (sourceAccounts.isEmpty()) return TerminalResult.Failure("Unknown source account: '${command.sourceToken}'")
        if (sourceAccounts.size > 1) return TerminalResult.NeedsInput("Ambiguous source account: '${command.sourceToken}'", sourceAccounts.map { it.name })
        val sourceAcc = sourceAccounts.first()

        val targetAccounts = financeRepo.resolveAccountsExact(command.targetToken)
        if (targetAccounts.isEmpty()) return TerminalResult.Failure("Unknown target account: '${command.targetToken}'")
        if (targetAccounts.size > 1) return TerminalResult.NeedsInput("Ambiguous target account: '${command.targetToken}'", targetAccounts.map { it.name })
        val targetAcc = targetAccounts.first()

        val rate = preferences?.getAccountChargeRate(sourceAcc.name)
            ?: preferences?.getAccountChargeRate(command.sourceToken)

        if (rate == null) {
            return TerminalResult.Failure(
                "No cashout charge configured for '${sourceAcc.name}'.",
                hint = "Set charge first: charge/ ${sourceAcc.name.lowercase()} *1.85%"
            )
        }

        val feeMinor = Math.round(command.amount.minorUnits * rate)
        val feeMoney = Money(feeMinor)

        // 1. Transfer principal to Cash
        financeRepo.transfer(sourceAcc.id, targetAcc.id, command.amount, "Cash out principal", System.currentTimeMillis())

        // 2. Record fee if > 0 against Capital (so it does not pollute the operating expense Net Income Statement)
        if (feeMinor > 0) {
            val capitalAcc = financeRepo.getAllAccountsSnapshot().find { it.name.equals("Capital", ignoreCase = true) }
                ?: financeRepo.getAllAccountsSnapshot().firstOrNull { it.type == com.projectkaka.inventory.data.local.entity.AccountType.CAPITAL }
                ?: run {
                    val newAcc = com.projectkaka.inventory.data.local.entity.AccountEntity(name = "Capital", type = com.projectkaka.inventory.data.local.entity.AccountType.CAPITAL, openingBalance = Money.ZERO)
                    val id = financeRepo.insertAccount(newAcc)
                    newAcc.copy(id = id.toInt())
                }
            val feeTx = TransactionEntity(
                accountId = sourceAcc.id,
                categoryId = null,
                counterAccountId = capitalAcc.id,
                amount = feeMoney,
                isCredit = false,
                type = com.projectkaka.inventory.data.local.entity.TransactionType.TRANSFER,
                note = "${sourceAcc.name} cash out charge (${"%.2f".format(rate * 100)}%)",
                timestamp = System.currentTimeMillis()
            )
            financeRepo.recordTransaction(feeTx)
        }

        val totalDeducted = Money(command.amount.minorUnits + feeMinor)
        return TerminalResult.Success(
            "Cashed out ${command.amount.format()} from ${sourceAcc.name} to ${targetAcc.name}. Charge: ${feeMoney.format()} (${"%.2f".format(rate * 100)}%). Total deducted from ${sourceAcc.name}: ${totalDeducted.format()}."
        )
    }

    private suspend fun executeLedgerDue(command: TerminalCommand.LedgerDue): TerminalResult {
        val existingContacts = financeRepo.getDistinctContactNames()
        val tokens = command.contactToken.split(" ").filter { it.isNotBlank() }
        
        var matchedContact: String? = null
        var note = command.note
        
        for (len in tokens.size downTo 1) {
            val candidate = tokens.take(len).joinToString(" ")
            val exact = existingContacts.find { it.equals(candidate, ignoreCase = true) }
            if (exact != null) {
                matchedContact = exact
                if (note.isBlank()) {
                    note = tokens.drop(len).joinToString(" ")
                }
                break
            }
        }
        
        val resolvedContact = matchedContact ?: run {
            if (tokens.size <= 2) {
                tokens.joinToString(" ")
            } else {
                if (note.isBlank()) {
                    note = tokens.drop(2).joinToString(" ")
                }
                tokens.take(2).joinToString(" ")
            }
        }
        
        if (resolvedContact.isBlank()) {
            return TerminalResult.Failure("Contact name required for ledger due.")
        }
        
        val defaultAccount = financeRepo.resolveAccount("Cash") 
            ?: financeRepo.getActiveAccountsSnapshot().firstOrNull()
            ?: return TerminalResult.Failure("No active cash account found to anchor liability.")
        
        val type = if (command.isOut) com.projectkaka.inventory.data.local.entity.LedgerType.PAYABLE else com.projectkaka.inventory.data.local.entity.LedgerType.RECEIVABLE
        val entry = com.projectkaka.inventory.data.local.entity.LedgerEntryEntity(
            contactName = resolvedContact,
            amount = command.amount,
            type = type,
            note = note,
            dueDate = null
        )
        financeRepo.issueDebt(entry, defaultAccount.id, note.ifBlank { "Due for $resolvedContact" })
        return TerminalResult.Success("Recorded ${if (command.isOut) "payable (I owe)" else "receivable (owes me)"}: ${command.amount.format()} to '$resolvedContact'${if (note.isNotBlank()) " ($note)" else ""}")
    }
    
    private suspend fun executeFinancial(command: TerminalCommand.Financial): TerminalResult {
        val debitAcc = resolveOrCreateAccount(command.debitToken, isDebitSide = true)
            ?: return TerminalResult.Failure("Could not resolve or create debit account: '${command.debitToken}'")
        val creditAcc = resolveOrCreateAccount(command.creditToken, isDebitSide = false)
            ?: return TerminalResult.Failure("Could not resolve or create credit account: '${command.creditToken}'")

        val txTime = if (command.dateToken != null) {
            parseDateTokenToEpoch(command.dateToken) ?: System.currentTimeMillis()
        } else {
            System.currentTimeMillis()
        }

        val entry = com.projectkaka.inventory.data.local.entity.JournalEntryEntity(
            timestamp = txTime,
            description = command.note.ifBlank { "${debitAcc.name} / ${creditAcc.name}" },
            status = com.projectkaka.inventory.data.local.entity.JournalStatus.POSTED,
            approvalStatus = com.projectkaka.inventory.data.local.entity.ApprovalStatus.APPROVED
        )

        val postings = listOf(
            com.projectkaka.inventory.data.local.entity.PostingEntity(
                journalEntryId = 0,
                accountId = debitAcc.id,
                amount = command.amount,
                isCredit = false, // DEBIT
                note = command.note
            ),
            com.projectkaka.inventory.data.local.entity.PostingEntity(
                journalEntryId = 0,
                accountId = creditAcc.id,
                amount = command.amount,
                isCredit = true, // CREDIT
                note = command.note
            )
        )

        financeRepo.recordSplitTransaction(entry, postings)

        val noteStr = if (command.note.isNotBlank()) " | \"${command.note}\"" else ""
        return TerminalResult.Success(
            "✓ [DR: ${debitAcc.name} ${command.amount.format()}] ➔ [CR: ${creditAcc.name} ${command.amount.format()}]$noteStr"
        )
    }

    private suspend fun resolveOrCreateAccount(token: String, isDebitSide: Boolean): com.projectkaka.inventory.data.local.entity.AccountEntity? {
        val lower = token.lowercase().trim()
        val allAccounts = financeRepo.getAllAccountsSnapshot()

        // Generic expenses
        if (lower in setOf("exp", "expense", "expenses", "cost", "khoroch")) {
            return allAccounts.find { it.name.equals("Expenses", ignoreCase = true) }
                ?: allAccounts.find { it.name.equals("Operating Expenses", ignoreCase = true) }
                ?: run {
                    val newAcc = com.projectkaka.inventory.data.local.entity.AccountEntity(
                        name = "Expenses",
                        type = com.projectkaka.inventory.data.local.entity.AccountType.EXPENSE,
                        openingBalance = Money.ZERO
                    )
                    val id = financeRepo.insertAccount(newAcc)
                    newAcc.copy(id = id.toInt())
                }
        }

        // Generic income
        if (lower in setOf("inc", "income", "revenue", "aay")) {
            return allAccounts.find { it.name.equals("Income", ignoreCase = true) }
                ?: allAccounts.firstOrNull { it.type == com.projectkaka.inventory.data.local.entity.AccountType.REVENUE }
                ?: run {
                    val newAcc = com.projectkaka.inventory.data.local.entity.AccountEntity(
                        name = "Income",
                        type = com.projectkaka.inventory.data.local.entity.AccountType.REVENUE,
                        openingBalance = Money.ZERO
                    )
                    val id = financeRepo.insertAccount(newAcc)
                    newAcc.copy(id = id.toInt())
                }
        }

        // Match existing accounts by exact name or alias
        val matched = financeRepo.resolveAccountsExact(token)
        if (matched.isNotEmpty()) return matched.first()

        val found = allAccounts.find { it.name.equals(token, ignoreCase = true) }
        if (found != null) return found

        // Create new account with sensible type default
        val type = when {
            lower in setOf("salary", "gift", "allowance", "bonus", "dividend", "interest") -> com.projectkaka.inventory.data.local.entity.AccountType.REVENUE
            lower in setOf("lunch", "food", "dinner", "breakfast", "grocery", "bill", "rent", "utility", "fare", "transport") -> com.projectkaka.inventory.data.local.entity.AccountType.EXPENSE
            lower in setOf("bkash", "bikash", "nagad", "rocket", "upay", "cellfin", "cash", "bank", "wallet") -> com.projectkaka.inventory.data.local.entity.AccountType.CASH
            !isDebitSide -> com.projectkaka.inventory.data.local.entity.AccountType.REVENUE
            else -> com.projectkaka.inventory.data.local.entity.AccountType.EXPENSE
        }

        val formattedName = token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        val newAcc = com.projectkaka.inventory.data.local.entity.AccountEntity(
            name = formattedName,
            type = type,
            openingBalance = Money.ZERO
        )
        val id = financeRepo.insertAccount(newAcc)
        return newAcc.copy(id = id.toInt())
    }

    private fun parseDateTokenToEpoch(dateToken: String): Long? {
        return try {
            val date = java.time.LocalDate.parse(dateToken.trim())
            date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    private fun executeMan(command: TerminalCommand.Man): TerminalResult {
        val content = TerminalManPages.getManPage(command.topic)
        return TerminalResult.Success(content)
    }

    private suspend fun executeLog(command: TerminalCommand.Log): TerminalResult {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDate.now()
        val filter = command.rawFilter.trim().lowercase()

        val (startMs, endMs, label) = when {
            filter.isEmpty() -> {
                val start = now.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = now.withDayOfMonth(now.lengthOfMonth()).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                Triple(start, end, "Current Month (${now.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))})")
            }
            filter == "today" -> {
                val start = now.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                Triple(start, end, "Today (${now.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"))})")
            }
            filter == "all" -> {
                Triple(0L, Long.MAX_VALUE, "All Time")
            }
            java.time.Month.entries.any { it.name.lowercase().startsWith(filter) || filter.startsWith(it.name.lowercase().take(3)) } -> {
                val month = java.time.Month.entries.first { it.name.lowercase().startsWith(filter) || filter.startsWith(it.name.lowercase().take(3)) }
                val year = now.year
                val start = java.time.LocalDate.of(year, month, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = java.time.LocalDate.of(year, month, month.length(java.time.Year.isLeap(year.toLong()))).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                Triple(start, end, "${month.name.lowercase().replaceFirstChar { it.uppercase() }} $year")
            }
            filter.matches(Regex("""^\d{4}-\d{2}-\d{2}(?:\s+\d{1,2}:\d{2}-\d{1,2}:\d{2})?$""")) -> {
                val parts = filter.split(" ")
                val datePart = java.time.LocalDate.parse(parts[0])
                if (parts.size > 1) {
                    val timeParts = parts[1].split("-")
                    val t1 = java.time.LocalTime.parse(timeParts[0])
                    val t2 = java.time.LocalTime.parse(timeParts[1])
                    val start = datePart.atTime(t1).atZone(zone).toInstant().toEpochMilli()
                    val end = datePart.atTime(t2).atZone(zone).toInstant().toEpochMilli()
                    Triple(start, end, filter)
                } else {
                    val start = datePart.atStartOfDay(zone).toInstant().toEpochMilli()
                    val end = datePart.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                    Triple(start, end, "$datePart")
                }
            }
            filter.matches(Regex("""^\d{1,2}:\d{2}-\d{1,2}:\d{2}$""")) -> {
                val timeParts = filter.split("-")
                val t1 = java.time.LocalTime.parse(timeParts[0])
                val t2 = java.time.LocalTime.parse(timeParts[1])
                val start = now.atTime(t1).atZone(zone).toInstant().toEpochMilli()
                val end = now.atTime(t2).atZone(zone).toInstant().toEpochMilli()
                Triple(start, end, "Today ($filter)")
            }
            else -> {
                return TerminalResult.Failure("Invalid log filter: '${command.rawFilter}'. Use a month (e.g. august), date (e.g. 2026-10-07), time range (e.g. 10:00-18:00), 'today', or 'all'.")
            }
        }

        val allTxs = financeRepo.getTransactionsInRangeSnapshot(startMs, endMs)
        val allAccounts = financeRepo.getAllAccountsSnapshot().associateBy { it.id }

        if (allTxs.isEmpty()) {
            return TerminalResult.Success("No financial trade-offs recorded for: $label.")
        }

        val grouped = allTxs.groupBy { it.transferId ?: "single_${it.id}" }
        val dateFmt = java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm")

        var totalVolumeMinor = 0L
        val lines = mutableListOf<String>()
        lines.add("=== FINANCIAL TRADEOFF AUDIT LOG: $label ===")

        for ((_, postings) in grouped) {
            val primary = postings.first()
            val timeStr = java.time.Instant.ofEpochMilli(primary.timestamp).atZone(zone).format(dateFmt)

            val debitPosting = postings.find { !it.isCredit }
            val creditPosting = postings.find { it.isCredit }

            val debitAccName = debitPosting?.let { allAccounts[it.accountId]?.name } ?: "Assets"
            val creditAccName = creditPosting?.let { allAccounts[it.accountId]?.name } ?: "Expenses"
            val amt = primary.amount

            totalVolumeMinor += amt.minorUnits
            val note = primary.note.ifBlank { "Trade-off" }

            lines.add("[$timeStr] ${primary.type}: ${amt.format()}")
            lines.add("  Tradeoff: \"$note\"")
            lines.add("  Flow: [DR: $debitAccName] ➔ [CR: $creditAccName]")
        }

        lines.add("--------------------------------------------------")
        lines.add("Total Entries: ${grouped.size} | Volume: ${Money(totalVolumeMinor).format()}")

        return TerminalResult.Success(lines.joinToString("\n"))
    }

    private suspend fun executeSnapshot(command: TerminalCommand.Snapshot): TerminalResult {
        val ctx = context ?: return TerminalResult.Failure("Context unavailable for snapshot.")
        return when (command.action.lowercase()) {
            "take", "create", "make", "new" -> {
                if (command.param.isNotBlank()) {
                    val pass = if (command.param.equals("NONE", ignoreCase = true)) "" else command.param
                    takeSnapshotWithPassphrase(ctx, pass)
                } else {
                    pendingSnapshotAction = "take"
                    TerminalResult.NeedsInput("Enter passphrase to seal snapshot with cryptographic salt (or type 'NONE' for unencrypted):", listOf("NONE"))
                }
            }
            "check", "verify", "audit", "test" -> {
                val latest = com.projectkaka.inventory.util.SystemIntegrityManager.getLatestSnapshot(ctx)
                if (latest == null) {
                    return TerminalResult.Failure("No baseline snapshot found. Run 'snapshot/ take [passphrase]' first to create a baseline.")
                }
                if (latest.hasPassphrase) {
                    if (command.param.isNotBlank()) {
                        checkSnapshotWithPassphrase(ctx, command.param)
                    } else {
                        pendingSnapshotAction = "check"
                        TerminalResult.NeedsInput("Snapshot ${latest.id} is sealed with a passphrase. Enter passphrase to verify:", emptyList())
                    }
                } else {
                    checkSnapshotWithPassphrase(ctx, command.param)
                }
            }
            "list", "ls", "history", "all" -> {
                val history = com.projectkaka.inventory.util.SystemIntegrityManager.getSnapshotHistory(ctx)
                if (history.isEmpty()) {
                    return TerminalResult.Success("No snapshots found in ledger. Create one with 'snapshot/ take [passphrase]'.")
                }
                val sb = StringBuilder("Whole-System Snapshot History (${history.size} recorded in secret ledger):\n")
                history.forEachIndexed { idx, snap ->
                    val sealBadge = if (snap.hasPassphrase) "[SEALED WITH PASSPHRASE]" else "[UNENCRYPTED]"
                    sb.append("\n  ${idx + 1}. ${snap.id} • ${snap.formattedDate} $sealBadge\n")
                    sb.append("     Counts: ${snap.itemCount} items, ${snap.accountCount} accs, ${snap.transactionCount} txs, ${snap.documentCount} docs, ${snap.basketCount} boxes, ${snap.fileCount} files (${snap.formatMediaSize()})\n")
                    sb.append("     Root Hash: [PROTECTED / HIDDEN]\n")
                }
                sb.append("\n💡 Check current system state against baseline: 'snapshot/ check [passphrase]'\n")
                sb.append("💡 Delete snapshot: 'snapshot/ delete <id>' or 'snapshot/ delete all'")
                TerminalResult.Success(sb.toString())
            }
            "delete", "rm", "del", "remove" -> {
                if (command.param.isBlank()) {
                    return TerminalResult.Failure("Specify snapshot ID to delete or 'all': snapshot/ delete <id|all>")
                }
                val deleted = com.projectkaka.inventory.util.SystemIntegrityManager.deleteSnapshot(ctx, command.param)
                if (deleted) {
                    TerminalResult.Success("Deleted snapshot '${command.param}' from secret ledger.")
                } else {
                    TerminalResult.Failure("Snapshot '${command.param}' not found in ledger.")
                }
            }
            else -> TerminalResult.Failure("Unknown snapshot action: '${command.action}'. Usage: snapshot/ <take|check|list|delete> [param]")
        }
    }

    private fun takeSnapshotWithPassphrase(ctx: android.content.Context, passphrase: String): TerminalResult {
        val manifest = com.projectkaka.inventory.util.SystemIntegrityManager.saveSnapshot(ctx, passphrase)
        val sb = StringBuilder("Whole-System Cryptographic Snapshot Created\n")
        sb.append("• ID: ${manifest.id}\n")
        sb.append("• Timestamp: ${manifest.formattedDate}\n")
        sb.append("• Protection: ${if (manifest.hasPassphrase) "Sealed with user passphrase" else "Unsalted / Open"}\n")
        sb.append("• Merkle State Tree Branches Hashed:\n")
        sb.append("   - Inventory: ${manifest.itemCount} items\n")
        sb.append("   - Finance: ${manifest.accountCount} accounts, ${manifest.transactionCount} transactions\n")
        sb.append("   - Documents: ${manifest.documentCount} documents\n")
        sb.append("   - Boxes/Cartons: ${manifest.basketCount} baskets\n")
        sb.append("   - Media Files: ${manifest.fileCount} files (${manifest.formatMediaSize()})\n")
        sb.append("• Root Hash: [PROTECTED / HIDDEN]\n")
        sb.append("• Storage: snapshots_history.json & active baseline")
        return TerminalResult.Success(sb.toString())
    }

    private fun checkSnapshotWithPassphrase(ctx: android.content.Context, passphrase: String): TerminalResult {
        val res = com.projectkaka.inventory.util.SystemIntegrityManager.auditAgainstLatest(ctx, passphrase)
        if (res.passphraseMismatch) {
            return TerminalResult.Failure("Passphrase mismatch! Cryptographic seal verification failed. Passphrase does not match snapshot baseline.")
        }
        if (res.errorReason != null) {
            return TerminalResult.Failure(res.errorReason)
        }

        val sb = StringBuilder()
        if (res.isPassed) {
            sb.append("✓ Whole-System Cryptographic Audit Passed!\n")
            sb.append("• Baseline: ${res.baselineId} (${res.baselineDate})\n")
            sb.append("• Merkle Branches Verified:\n")
            res.branchAudits.forEach { branch ->
                sb.append("   - ${branch.name}: [INTACT] (${branch.countDescription})\n")
            }
            sb.append("• Root Hash: [PROTECTED / HIDDEN]\n")
            sb.append("Zero alterations detected across all database records and media files.")
            return TerminalResult.Success(sb.toString())
        } else {
            sb.append("✗ Whole-System Integrity Alert Detected!\n")
            sb.append("• Baseline: ${res.baselineId} (${res.baselineDate})\n")
            sb.append("• Merkle Branches Audit:\n")
            res.branchAudits.forEach { branch ->
                val status = if (branch.isIntact) "[INTACT]" else "[ALTERED]"
                val note = if (branch.diffNote != null) " - ${branch.diffNote}" else " (${branch.countDescription})"
                sb.append("   - ${branch.name}: $status$note\n")
            }
            sb.append("• Root Hash: [PROTECTED / HIDDEN]\n")
            sb.append("Discrepancies found! System state has been modified since snapshot baseline.")
            return TerminalResult.Failure(sb.toString())
        }
    }
    
    private suspend fun executeLedgerSettle(command: TerminalCommand.LedgerSettle): TerminalResult {
        val existingContacts = financeRepo.getDistinctContactNames()
        val exactMatch = existingContacts.find { it.equals(command.contactToken, ignoreCase = true) }
        val resolvedContact = exactMatch ?: existingContacts.find { it.contains(command.contactToken, ignoreCase = true) } ?: command.contactToken.trim()
        
        val openEntries = financeRepo.getUnsettledEntriesForContact(resolvedContact)
        if (openEntries.isEmpty()) {
            return TerminalResult.Failure("No open ledger entries found for '$resolvedContact'.")
        }
        
        val accounts = financeRepo.resolveAccountsExact(command.accountToken)
        val settleAcc = accounts.firstOrNull() 
            ?: financeRepo.resolveAccount("Cash") 
            ?: financeRepo.getActiveAccountsSnapshot().firstOrNull()
            ?: return TerminalResult.Failure("No account available for settlement.")

        val totalOpenMinor = openEntries.sumOf { it.amount.minorUnits }
        val totalOpenMoney = Money(totalOpenMinor)

        // Case 1: No amount specified -> Settle ALL open entries for contact in FIFO order
        if (command.amount == null) {
            for (entry in openEntries) {
                financeRepo.settleDebt(entry.id, settleAcc.id, "Settled all with $resolvedContact")
            }
            return TerminalResult.Success(
                "✓ Fully settled all ${openEntries.size} open liabilities totaling ${totalOpenMoney.format()} for '$resolvedContact' using ${settleAcc.name}."
            )
        }

        // Case 2: Amount specified -> Apply FIFO across open entries
        var remainingPayment = command.amount.minorUnits
        var settledCount = 0
        var totalSettledMinor = 0L
        var partialSettledInfo: String? = null

        for (entry in openEntries) {
            if (remainingPayment <= 0L) break

            if (remainingPayment >= entry.amount.minorUnits) {
                financeRepo.settleDebt(entry.id, settleAcc.id, "Settled debt with $resolvedContact")
                remainingPayment -= entry.amount.minorUnits
                totalSettledMinor += entry.amount.minorUnits
                settledCount++
            } else {
                // Partial settlement of this entry
                val paidPart = Money(remainingPayment)
                financeRepo.settleDebtPartial(entry.id, settleAcc.id, paidPart, "Partially settled debt with $resolvedContact")
                totalSettledMinor += remainingPayment
                partialSettledInfo = "Partially settled ${paidPart.format()} on entry of ${entry.amount.format()} (Remainder: ${Money(entry.amount.minorUnits - remainingPayment).format()})"
                remainingPayment = 0L
                settledCount++
                break
            }
        }

        val totalSettledMoney = Money(totalSettledMinor)
        val remainingDebt = totalOpenMoney - totalSettledMoney

        val changeMsg = if (remainingPayment > 0L) {
            val changeMoney = Money(remainingPayment)
            " All debts cleared! Change returned: ${changeMoney.format()}."
        } else ""

        val remainingMsg = if (remainingDebt.minorUnits > 0L) {
            " Remaining open balance: ${remainingDebt.format()}."
        } else ""

        val partialMsg = if (partialSettledInfo != null) "\n  • $partialSettledInfo" else ""

        return TerminalResult.Success(
            "✓ Settled ${totalSettledMoney.format()} across $settledCount entry/entries for '$resolvedContact' using ${settleAcc.name}.$changeMsg$remainingMsg$partialMsg"
        )
    }

    private suspend fun executeVerify(command: TerminalCommand.Verify): TerminalResult {
        val ctx = context ?: return TerminalResult.Failure("Context unavailable for image verification.")
        val target = command.target.trim()

        // 1. Check if target is a specific Item ID (e.g. "3" or "item 3" or "#3")
        val cleanNumberStr = target.removePrefix("item").removePrefix("#").trim()
        val itemId = cleanNumberStr.toIntOrNull()
        if (itemId != null) {
            val db = com.projectkaka.inventory.data.local.AppDatabase.getInstance(ctx)
            val item = db.itemDao().getItemById(itemId)
            if (item == null) {
                return TerminalResult.Failure("Item #$itemId not found in database.")
            }
            if (item.imagePath.isBlank()) {
                return TerminalResult.Failure("Item #$itemId ('${item.name}') does not have an attached image.")
            }
            val imgFile = java.io.File(item.imagePath)
            if (!imgFile.exists()) {
                return TerminalResult.Failure("Image file for Item #$itemId is missing on disk: ${imgFile.name}")
            }
            val fileRes = com.projectkaka.inventory.util.ImageIntegrityManager.verifySingleFile(ctx, imgFile)
            val statusStr = when (fileRes.status) {
                com.projectkaka.inventory.util.ImageIntegrityManager.FileIntegrityStatus.INTACT -> "INTACT (Matches cryptographic baseline)"
                com.projectkaka.inventory.util.ImageIntegrityManager.FileIntegrityStatus.ALTERED -> "ALTERED / CORRUPTED! Expected ${fileRes.expectedHash.take(10)}..., Found ${fileRes.actualHash.take(10)}..."
                com.projectkaka.inventory.util.ImageIntegrityManager.FileIntegrityStatus.UNTRACKED -> "UNTRACKED (No baseline hash recorded)"
            }
            return if (fileRes.status == com.projectkaka.inventory.util.ImageIntegrityManager.FileIntegrityStatus.INTACT) {
                TerminalResult.Success(
                    "Verified Item #$itemId ('${item.name}'):\n" +
                    "• File: ${imgFile.name} (${fileRes.formatSize()})\n" +
                    "• SHA-256: ${fileRes.actualHash}\n" +
                    "• Status: $statusStr"
                )
            } else {
                TerminalResult.Failure(
                    "Integrity Alert for Item #$itemId ('${item.name}'):\n" +
                    "• File: ${imgFile.name} (${fileRes.formatSize()})\n" +
                    "• SHA-256: ${fileRes.actualHash}\n" +
                    "• Status: $statusStr"
                )
            }
        }

        // 2. Check if target is a specific file name or pattern (not generic "images" or "all")
        if (target != "images" && target != "all" && target.isNotBlank()) {
            val matchingFile = com.projectkaka.inventory.util.ImageIntegrityManager.findFileByNameOrPrefix(ctx, target)
            if (matchingFile != null) {
                val fileRes = com.projectkaka.inventory.util.ImageIntegrityManager.verifySingleFile(ctx, matchingFile)
                val statusStr = when (fileRes.status) {
                    com.projectkaka.inventory.util.ImageIntegrityManager.FileIntegrityStatus.INTACT -> "INTACT (Matches baseline)"
                    com.projectkaka.inventory.util.ImageIntegrityManager.FileIntegrityStatus.ALTERED -> "ALTERED! Expected ${fileRes.expectedHash.take(10)}..., Found ${fileRes.actualHash.take(10)}..."
                    com.projectkaka.inventory.util.ImageIntegrityManager.FileIntegrityStatus.UNTRACKED -> "UNTRACKED (No baseline hash recorded)"
                }
                return TerminalResult.Success(
                    "Verified File '${matchingFile.name}':\n" +
                    "• Size: ${fileRes.formatSize()}\n" +
                    "• SHA-256: ${fileRes.actualHash}\n" +
                    "• Status: $statusStr"
                )
            } else {
                return TerminalResult.Failure("No item or image file found matching '$target'.")
            }
        }

        // 3. Global verification (verify/ or verify/ images or verify/ all)
        val res = com.projectkaka.inventory.util.ImageIntegrityManager.verifyAllImages(ctx)
        return if (res.alteredFiles.isEmpty() && res.missingFiles.isEmpty()) {
            val lines = mutableListOf<String>()
            lines.add("Verified ${res.intactCount} saved image(s) (${res.formatTotalSize()}):")
            lines.add("All cryptographic checksums intact. Zero alterations detected.")
            if (res.intactFiles.isNotEmpty()) {
                lines.add("\nVerified Files:")
                res.intactFiles.take(8).forEach { f ->
                    lines.add("  • ${f.fileName} (${f.formatSize()}) | sha256: ${f.sha256.take(12)}... [INTACT]")
                }
                if (res.intactFiles.size > 8) {
                    lines.add("  ... and ${res.intactFiles.size - 8} more file(s).")
                }
            }
            TerminalResult.Success(lines.joinToString("\n"))
        } else {
            val details = res.alteredFiles.joinToString("\n") { "  • ${it.fileName} (Expected ${it.expectedHash.take(8)}..., Found ${it.actualHash.take(8)}...)" }
            TerminalResult.Failure("Integrity alert! ${res.alteredFiles.size} altered file(s), ${res.missingFiles.size} missing file(s) detected:\n$details")
        }
    }

    private fun executeHistory(command: TerminalCommand.History): TerminalResult {
        val history = preferences?.getCommandHistory() ?: emptyList()
        if (history.isEmpty()) return TerminalResult.Success("Command history is empty.")
        val count = minOf(command.count, history.size)
        val recent = history.takeLast(count)
        val formatted = recent.mapIndexed { idx, cmd -> " ${idx + 1}. $cmd" }.joinToString("\n")
        return TerminalResult.Success("Recent commands:\n$formatted")
    }

    private suspend fun executeAccountAdd(command: TerminalCommand.AccountAdd): TerminalResult {
        val type = try {
            when (command.typeToken.lowercase()) {
                "debt", "loan", "due", "iou", "dhar" -> com.projectkaka.inventory.data.local.entity.AccountType.LIABILITY
                "wallet", "haat", "pocket" -> com.projectkaka.inventory.data.local.entity.AccountType.CASH
                "cost", "khoroch" -> com.projectkaka.inventory.data.local.entity.AccountType.EXPENSE
                "income", "aay" -> com.projectkaka.inventory.data.local.entity.AccountType.REVENUE
                else -> com.projectkaka.inventory.data.local.entity.AccountType.valueOf(command.typeToken.uppercase())
            }
        } catch (e: Exception) {
            return TerminalResult.Failure("Invalid account type: '${command.typeToken}'. Use CASH, LIABILITY, ASSET, EXPENSE, or REVENUE.")
        }
        val existing = financeRepo.resolveAccountsExact(command.name)
        if (existing.isNotEmpty()) {
            return TerminalResult.Failure("Account '${existing.first().name}' already exists.")
        }
        val acc = com.projectkaka.inventory.data.local.entity.AccountEntity(
            name = command.name,
            type = type,
            openingBalance = command.balance ?: Money(0L)
        )
        financeRepo.insertAccount(acc)
        return TerminalResult.Success("Created account: ${command.name} (${type.name})")
    }

    private suspend fun executeAccountAction(command: TerminalCommand.AccountAction): TerminalResult {
        val accounts = financeRepo.resolveAccountsExact(command.accountToken)
        if (accounts.isEmpty()) return TerminalResult.Failure("Unknown account: ${command.accountToken}")
        if (accounts.size > 1) return TerminalResult.NeedsInput("Ambiguous account: ${command.accountToken}", accounts.map { it.name })
        
        val account = accounts.first()
        return when (command.action.lowercase()) {
            "archive" -> {
                financeRepo.updateAccount(account.copy(isActive = false))
                TerminalResult.Success("Archived account: ${account.name}")
            }
            "restore" -> {
                financeRepo.updateAccount(account.copy(isActive = true))
                TerminalResult.Success("Restored account: ${account.name}")
            }
            else -> TerminalResult.Failure("Account action ${command.action} not fully implemented yet.")
        }
    }

    private suspend fun executeBalanceCheck(command: TerminalCommand.BalanceCheck): TerminalResult {
        if (command.accountToken == "all") {
            val accounts = financeRepo.getActiveAccountsSnapshot()
            val totalCash = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.CASH }.sumOf { it.balance.minorUnits }
            val totalAssets = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.ASSET }.sumOf { it.balance.minorUnits }
            val lines = accounts.joinToString("\n") { "${it.name}: ${it.balance.format()}" }
            return TerminalResult.Success("$lines\n---\nTotal Cash: ${Money(totalCash).format()}\nTotal Assets: ${Money(totalAssets).format()}")
        }
        
        val accounts = financeRepo.resolveAccountsExact(command.accountToken)
        if (accounts.isEmpty()) return TerminalResult.Failure("Unknown account: ${command.accountToken}")
        if (accounts.size > 1) return TerminalResult.NeedsInput("Ambiguous account: ${command.accountToken}", accounts.map { it.name })
        
        val account = accounts.first()
        return TerminalResult.Success("${account.name} Balance: ${account.balance.format()}")
    }

    private suspend fun executeReport(command: TerminalCommand.Report): TerminalResult {
        return when (command.reportType) {
            "bs" -> {
                financeRepo.runInvariantCheck()
                val accounts = financeRepo.getAllAccountsSnapshot().filter { it.isActive }
                
                val isZeroPlaceholder = { name: String, bal: Long ->
                    (name.equals("Assets", ignoreCase = true) || name.equals("Liabilities", ignoreCase = true)) && bal == 0L
                }

                val cashEquivalents = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.CASH }
                val otherAssets = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.ASSET && !isZeroPlaceholder(it.name, it.balance.minorUnits) }
                val liabilities = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.LIABILITY && !isZeroPlaceholder(it.name, it.balance.minorUnits) }
                val equity = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.CAPITAL }
                
                val totalCash = cashEquivalents.sumOf { it.balance.minorUnits }
                val totalOtherAssets = otherAssets.sumOf { it.balance.minorUnits }
                val totalAssets = totalCash + totalOtherAssets
                val totalLiabilities = liabilities.sumOf { it.balance.minorUnits }
                val totalEquity = equity.sumOf { it.balance.minorUnits }
                
                val revenues = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.REVENUE }
                val expenses = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.EXPENSE }
                val netIncome = revenues.sumOf { it.balance.minorUnits } - expenses.sumOf { it.balance.minorUnits }
                
                val finalEquity = totalEquity + netIncome
                
                val sb = StringBuilder("BALANCE SHEET\n")
                sb.append("Assets:\n")
                if (cashEquivalents.isNotEmpty()) {
                    sb.append("  Cash & Cash Equivalents:\n")
                    cashEquivalents.forEach { sb.append("    • ${it.name}: ${it.balance.format()}\n") }
                    sb.append("    Subtotal: ${Money(totalCash).format()}\n")
                }
                if (otherAssets.isNotEmpty()) {
                    sb.append("  Other Assets:\n")
                    otherAssets.forEach { sb.append("    • ${it.name}: ${it.balance.format()}\n") }
                    sb.append("    Subtotal: ${Money(totalOtherAssets).format()}\n")
                }
                sb.append("Total Assets: ${Money(totalAssets).format()}\n\n")
                
                sb.append("Liabilities:\n")
                if (liabilities.isEmpty()) {
                    sb.append("  (None)\n")
                } else {
                    liabilities.forEach { sb.append("  • ${it.name}: ${it.balance.format()}\n") }
                }
                sb.append("Total Liabilities: ${Money(totalLiabilities).format()}\n\n")
                
                sb.append("Equity:\n")
                equity.forEach { sb.append("  • ${it.name}: ${it.balance.format()}\n") }
                sb.append("Net Income: ${Money(netIncome).format()}\n")
                sb.append("Total Equity (incl. NI): ${Money(finalEquity).format()}\n\n")
                sb.append("Equation: A (${Money(totalAssets).format()}) = L+E (${Money(totalLiabilities + finalEquity).format()})")
                
                TerminalResult.Success(sb.toString().trimEnd())
            }
            "pnl" -> {
                val accounts = financeRepo.getAllAccountsSnapshot().filter { it.isActive }
                val revenues = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.REVENUE }
                val expenses = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.EXPENSE }
                
                val totalRevenue = revenues.sumOf { it.balance.minorUnits }
                val totalExpense = expenses.sumOf { it.balance.minorUnits }
                val netIncome = totalRevenue - totalExpense
                
                TerminalResult.Success(
                    "INCOME STATEMENT\n" +
                    "Revenue:\n" + revenues.joinToString("\n") { "  ${it.name}: ${it.balance.format()}" } + "\n" +
                    "Total Revenue: ${Money(totalRevenue).format()}\n\n" +
                    "Expenses:\n" + expenses.joinToString("\n") { "  ${it.name}: ${it.balance.format()}" } + "\n" +
                    "Total Expenses: ${Money(totalExpense).format()}\n\n" +
                    "Net Income: ${Money(netIncome).format()}"
                )
            }
            "trend" -> TerminalResult.PendingAction("graph", "trend")
            else -> TerminalResult.Failure("Unknown report type: ${command.reportType}")
        }
    }

    private suspend fun executeAccountAlter(command: TerminalCommand.AccountAlter): TerminalResult {
        val accounts = financeRepo.resolveAccountsExact(command.accountToken)
        if (accounts.isEmpty()) return TerminalResult.Failure("Unknown account: ${command.accountToken}")
        if (accounts.size > 1) return TerminalResult.NeedsInput("Ambiguous account: ${command.accountToken}", accounts.map { it.name })
        
        val account = accounts.first()
        return when (command.action) {
            "rename" -> {
                financeRepo.updateAccount(account.copy(name = command.newName))
                TerminalResult.Success("Renamed account '${account.name}' to '${command.newName}'")
            }
            "type" -> {
                val newType = try {
                    when (command.newName.lowercase()) {
                        "debt", "loan", "due", "iou", "dhar" -> com.projectkaka.inventory.data.local.entity.AccountType.LIABILITY
                        "wallet", "haat", "pocket" -> com.projectkaka.inventory.data.local.entity.AccountType.CASH
                        "cost", "khoroch" -> com.projectkaka.inventory.data.local.entity.AccountType.EXPENSE
                        "income", "aay" -> com.projectkaka.inventory.data.local.entity.AccountType.REVENUE
                        else -> com.projectkaka.inventory.data.local.entity.AccountType.valueOf(command.newName.uppercase())
                    }
                } catch (e: Exception) {
                    return TerminalResult.Failure("Invalid account type: '${command.newName}'. Use CASH, LIABILITY, ASSET, EXPENSE, CAPITAL, or REVENUE.")
                }
                financeRepo.updateAccount(account.copy(type = newType))
                val recalculated = financeRepo.getAccountBalance(account.id) ?: com.projectkaka.inventory.model.Money(0L)
                TerminalResult.Success("✓ Changed '${account.name}' type to ${newType.name}. Recalculated balance: ${recalculated.format()}")
            }
            else -> TerminalResult.Failure("Account alter action ${command.action} not fully implemented yet.")
        }
    }
}
