package com.projectkaka.inventory.search

sealed interface TerminalCommand {
    data class Financial(
        val amount: Double,
        val isCredit: Boolean,
        val tokens: List<String>,
        val dateToken: String? = null
    ) : TerminalCommand

    data class Init(val accountToken: String, val amount: Double) : TerminalCommand
    
    data class Transfer(
        val amount: Double, 
        val fromToken: String, 
        val toToken: String, 
        val noteTokens: List<String>
    ) : TerminalCommand

    data class LedgerDue(
        val isOut: Boolean, 
        val amount: Double, 
        val contactToken: String, 
        val dateToken: String? = null
    ) : TerminalCommand

    data class LedgerSettle(
        val contactToken: String, 
        val amount: Double?
    ) : TerminalCommand

    data class AccountAdd(
        val name: String, 
        val typeToken: String, 
        val balance: Double?
    ) : TerminalCommand

    data class AccountAction(
        val action: String, // hide, show, archive, restore
        val accountToken: String
    ) : TerminalCommand

    data class Delete(
        val targetType: String, // tx, account, entry
        val selector: String
    ) : TerminalCommand

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
}