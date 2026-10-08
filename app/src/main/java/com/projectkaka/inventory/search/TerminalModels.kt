package com.projectkaka.inventory.search

sealed interface TerminalCommand {
    data class Financial(
        val amount: com.projectkaka.inventory.model.Money,
        val debitToken: String,
        val creditToken: String,
        val note: String = "",
        val dateToken: String? = null
    ) : TerminalCommand

    data class Man(val topic: String = "?") : TerminalCommand

    data class Log(val rawFilter: String = "") : TerminalCommand

    data class Snapshot(val action: String = "create", val param: String = "") : TerminalCommand

    data object Clear : TerminalCommand

    data object CMatrix : TerminalCommand

    data class Pop(val action: String = "pop") : TerminalCommand

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
        val note: String = "",
        val dateToken: String? = null
    ) : TerminalCommand

    data class LedgerSettle(
        val contactToken: String, 
        val amount: com.projectkaka.inventory.model.Money? = null,
        val accountToken: String = "Cash"
    ) : TerminalCommand

    data class Charge(
        val accountToken: String?, 
        val rateToken: String?, 
        val action: String = "set" // "set", "get", "all", "clear"
    ) : TerminalCommand

    data class Cashout(
        val amount: com.projectkaka.inventory.model.Money,
        val sourceToken: String,
        val targetToken: String = "Cash"
    ) : TerminalCommand

    data class Verify(
        val target: String = "images"
    ) : TerminalCommand

    data class History(
        val count: Int = 20
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