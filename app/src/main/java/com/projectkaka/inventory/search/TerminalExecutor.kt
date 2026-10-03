package com.projectkaka.inventory.search

import com.projectkaka.inventory.data.local.entity.TransactionEntity
import com.projectkaka.inventory.data.repository.FinanceRepository
import com.projectkaka.inventory.model.Money

class TerminalExecutor(
    private val financeRepo: FinanceRepository
) {
    private var pendingDestructiveCommand: TerminalCommand.Delete? = null

    suspend fun execute(input: String): TerminalResult {
        val trimmed = input.trim()
        
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
        
        val command = TerminalParser.parse(trimmed)
        return executeCommand(command)
    }

    suspend fun executeCommand(command: TerminalCommand): TerminalResult {
        return when (command) {
            is TerminalCommand.Empty -> TerminalResult.Pending
            is TerminalCommand.Error -> TerminalResult.Failure(command.message)
            is TerminalCommand.Help -> TerminalResult.Success(
                "Available commands:\n" +
                "f/ ±amount <account> <category> [note]\n" +
                "init/ <account> <amount>\n" +
                "delete/ <account|tx> <name|id>\n" +
                "kaka show graph|ledger|alias|export"
            )
            is TerminalCommand.KakaAction -> TerminalResult.PendingAction(command.action, command.arg)
            is TerminalCommand.Financial -> {
                val accountName = command.tokens.getOrNull(0) ?: "Cash"
                val categoryName = command.tokens.getOrNull(1) ?: if (command.isCredit) "Uncategorized Income" else "Uncategorized Expense"
                val note = command.tokens.drop(2).joinToString(" ")
                
                val accounts = financeRepo.resolveAccountsExact(accountName)
                if (accounts.isEmpty()) return TerminalResult.Failure("Unknown account '$accountName'.")
                if (accounts.size > 1) return TerminalResult.NeedsInput("Ambiguous account '$accountName'. Options:", accounts.map { it.name })
                val account = accounts.first()

                val categories = financeRepo.resolveCategoriesExact(categoryName)
                var categoryId: Int? = null
                var counterAccountId: Int? = null
                var targetName = ""
                
                if (categories.isNotEmpty()) {
                    if (categories.size > 1) return TerminalResult.NeedsInput("Ambiguous category '$categoryName'. Options:", categories.map { it.name })
                    val category = categories.first()
                    categoryId = category.id
                    targetName = category.name
                } else {
                    val fallbackAccounts = financeRepo.resolveAccountsExact(categoryName)
                    if (fallbackAccounts.isEmpty()) return TerminalResult.Failure("Unknown category or account '$categoryName'.")
                    if (fallbackAccounts.size > 1) return TerminalResult.NeedsInput("Ambiguous account '$categoryName'. Options:", fallbackAccounts.map { it.name })
                    val counterAccount = fallbackAccounts.first()
                    counterAccountId = counterAccount.id
                    targetName = counterAccount.name
                }

                val transaction = TransactionEntity(
                    accountId = account.id,
                    categoryId = categoryId,
                    counterAccountId = counterAccountId,
                    amount = command.amount,
                    isCredit = command.isCredit,
                    note = note,
                    timestamp = System.currentTimeMillis() // Assuming no date token parsing for now
                )
                financeRepo.recordTransaction(transaction)
                TerminalResult.Success("Success: ${if (command.isCredit) "+" else "-"}৳${command.amount} ${account.name} -> $targetName", null)
            }
            is TerminalCommand.Init -> {
                val accounts = financeRepo.resolveAccountsExact(command.accountToken)
                if (accounts.isEmpty()) return TerminalResult.Failure("Unknown account '${command.accountToken}'.")
                if (accounts.size > 1) return TerminalResult.NeedsInput("Ambiguous account '${command.accountToken}'. Options:", accounts.map { it.name })
                
                financeRepo.initializeAccountBalance(accounts.first().name, command.amount)
                TerminalResult.Success("Initialized ${accounts.first().name} to ${command.amount.format()}")
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
        
        val toAccounts = financeRepo.resolveAccountsExact(command.toToken)
        if (toAccounts.isEmpty()) return TerminalResult.Failure("Unknown to account: ${command.toToken}")
        if (toAccounts.size > 1) return TerminalResult.NeedsInput("Ambiguous to account: ${command.toToken}", toAccounts.map { it.name })
        
        val fromAcc = fromAccounts.first()
        val toAcc = toAccounts.first()
        
        financeRepo.transfer(fromAcc.id, toAcc.id, command.amount, command.noteTokens.joinToString(" "), System.currentTimeMillis())
        return TerminalResult.Success("Transferred ${command.amount.format()} from ${fromAcc.name} to ${toAcc.name}")
    }

    private suspend fun executeLedgerDue(command: TerminalCommand.LedgerDue): TerminalResult {
        return TerminalResult.Failure("Ledger not fully implemented yet.")
    }
    
    private suspend fun executeLedgerSettle(command: TerminalCommand.LedgerSettle): TerminalResult {
        return TerminalResult.Failure("Ledger not fully implemented yet.")
    }

    private suspend fun executeAccountAdd(command: TerminalCommand.AccountAdd): TerminalResult {
        val type = try {
            com.projectkaka.inventory.data.local.entity.AccountType.valueOf(command.typeToken.uppercase())
        } catch (e: Exception) {
            return TerminalResult.Failure("Invalid account type: ${command.typeToken}")
        }
        val acc = com.projectkaka.inventory.data.local.entity.AccountEntity(
            name = command.name,
            type = type,
            openingBalance = command.balance ?: Money(0L)
        )
        financeRepo.insertAccount(acc)
        return TerminalResult.Success("Created account: ${command.name}")
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
                val accounts = financeRepo.getAllAccountsSnapshot().filter { it.isActive }
                val assets = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.ASSET || it.type == com.projectkaka.inventory.data.local.entity.AccountType.CASH }
                val liabilities = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.LIABILITY }
                val equity = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.CAPITAL }
                
                val totalAssets = assets.sumOf { it.balance.minorUnits }
                val totalLiabilities = liabilities.sumOf { it.balance.minorUnits }
                val totalEquity = equity.sumOf { it.balance.minorUnits }
                
                val revenues = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.REVENUE }
                val expenses = accounts.filter { it.type == com.projectkaka.inventory.data.local.entity.AccountType.EXPENSE }
                val netIncome = revenues.sumOf { it.balance.minorUnits } - expenses.sumOf { it.balance.minorUnits }
                
                val finalEquity = totalEquity + netIncome
                
                TerminalResult.Success(
                    "BALANCE SHEET\n" +
                    "Assets:\n" + assets.joinToString("\n") { "  ${it.name}: ${it.balance.format()}" } + "\n" +
                    "Total Assets: ${Money(totalAssets).format()}\n\n" +
                    "Liabilities:\n" + liabilities.joinToString("\n") { "  ${it.name}: ${it.balance.format()}" } + "\n" +
                    "Total Liabilities: ${Money(totalLiabilities).format()}\n\n" +
                    "Equity:\n" + equity.joinToString("\n") { "  ${it.name}: ${it.balance.format()}" } + "\n" +
                    "Net Income: ${Money(netIncome).format()}\n" +
                    "Total Equity (incl. NI): ${Money(finalEquity).format()}\n\n" +
                    "Equation: A (${Money(totalAssets).format()}) = L+E (${Money(totalLiabilities + finalEquity).format()})"
                )
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
            else -> TerminalResult.Failure("Account alter action ${command.action} not fully implemented yet.")
        }
    }
}
