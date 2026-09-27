package com.projectkaka.inventory.search

import com.projectkaka.inventory.data.local.entity.TransactionEntity
import com.projectkaka.inventory.data.repository.FinanceRepository
import com.projectkaka.inventory.model.Money

class TerminalExecutor(
    private val financeRepo: FinanceRepository
) {
    suspend fun execute(input: String): TerminalResult {
        val command = TerminalParser.parse(input)
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
            is TerminalCommand.KakaAction -> TerminalResult.Pending // UI handles navigation
            is TerminalCommand.Financial -> {
                val accountName = command.tokens.getOrNull(0) ?: "Cash"
                val categoryName = command.tokens.getOrNull(1) ?: if (command.isCredit) "Uncategorized Income" else "Uncategorized Expense"
                val note = command.tokens.drop(2).joinToString(" ")
                
                val account = financeRepo.resolveAccount(accountName)
                val category = financeRepo.resolveCategory(categoryName)
                
                if (account != null && category != null) {
                    val transaction = TransactionEntity(
                        accountId = account.id,
                        categoryId = category.id,
                        amount = Money.fromDouble(command.amount),
                        isCredit = command.isCredit,
                        note = note,
                        timestamp = System.currentTimeMillis() // Assuming no date token parsing for now
                    )
                    financeRepo.recordTransaction(transaction)
                    TerminalResult.Success("Success: ${if (command.isCredit) "+" else "-"}৳${command.amount} ${account.name} -> ${category.name}", null)
                } else {
                    TerminalResult.Failure("Unknown account '$accountName' or category '$categoryName'")
                }
            }
            is TerminalCommand.Init -> {
                financeRepo.initializeAccountBalance(command.accountToken, Money.fromDouble(command.amount))
                TerminalResult.Success("Initialized ${command.accountToken} to ৳${command.amount}")
            }
            is TerminalCommand.Delete -> {
                if (command.targetType == "account") {
                    val acc = financeRepo.resolveAccount(command.selector)
                    if (acc != null) {
                        financeRepo.deleteAccount(acc)
                        TerminalResult.Success("Deleted account: ${command.selector}")
                    } else {
                        TerminalResult.Failure("Unknown account: ${command.selector}")
                    }
                } else {
                    TerminalResult.Failure("Unsupported delete target: ${command.targetType}")
                }
            }
            is TerminalCommand.AccountAction -> {
                TerminalResult.Failure("Account action ${command.action} not fully implemented in executor.")
            }
            else -> TerminalResult.Failure("Command not implemented yet.")
        }
    }
}
