package com.projectkaka.inventory.search

sealed interface TerminalCommand {
    data class Financial(
        val amount: com.projectkaka.inventory.model.Money,
        val isCredit: Boolean,
        val tokens: List<String>,
        val dateToken: String? = null
    ) : TerminalCommand

    data class Init(val accountToken: String, val amount: com.projectkaka.inventory.model.Money) : TerminalCommand
    
    data class Transfer(
        val amount: com.projectkaka.inventory.model.Money, 
        val fromToken: String, 
        val toToken: String, 
        val noteTokens: List<String>
    ) : TerminalCommand

    data class LedgerDue(
        val isOut: Boolean, 
        val amount: com.projectkaka.inventory.model.Money, 
        val contactToken: String, 
        val dateToken: String? = null
    ) : TerminalCommand

    data class LedgerSettle(
        val contactToken: String, 
        val amount: com.projectkaka.inventory.model.Money?
    ) : TerminalCommand

    data class AccountAdd(
        val name: String, 
        val typeToken: String, 
        val balance: com.projectkaka.inventory.model.Money?
    ) : TerminalCommand

    data class AccountAction(
        val action: String, // hide, show, archive, restore
        val accountToken: String
    ) : TerminalCommand

    data class Delete(
        val targetType: String, // tx, account, entry
        val selector: String
    ) : TerminalCommand

    data class BalanceCheck(val accountToken: String) : TerminalCommand

    data class Report(val reportType: String, val arg: String? = null) : TerminalCommand

    data class AccountAlter(
        val accountToken: String,
        val action: String,
        val newName: String
    ) : TerminalCommand

    data object Reset : TerminalCommand

    data class KakaAction(
        val action: String, // graph, ledger, accounts, export, settings, search
        val arg: String? = null
    ) : TerminalCommand

    data object Help : TerminalCommand
    
    data class Error(val message: String) : TerminalCommand
    data object Empty : TerminalCommand
}

sealed interface TerminalResult {
    data class Success(val message: String, val undoHint: String? = null) : TerminalResult
    data class Failure(val reason: String, val hint: String? = null) : TerminalResult
    data class NeedsInput(val question: String, val options: List<String>) : TerminalResult
    data object Pending : TerminalResult
    data class PendingAction(val action: String, val arg: String? = null) : TerminalResult
}