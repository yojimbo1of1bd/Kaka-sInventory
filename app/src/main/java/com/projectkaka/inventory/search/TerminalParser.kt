package com.projectkaka.inventory.search

object TerminalParser {

    fun parse(input: String): TerminalCommand {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return TerminalCommand.Empty

        // Action commands
        if (trimmed.equals("kaka show graph", ignoreCase = true)) return TerminalCommand.KakaAction("graph")
        if (trimmed.equals("kaka show alias", ignoreCase = true)) return TerminalCommand.Help
        if (trimmed.equals("kaka ledger", ignoreCase = true)) return TerminalCommand.KakaAction("ledger")
        if (trimmed.equals("kaka accounts", ignoreCase = true)) return TerminalCommand.KakaAction("accounts")
        if (trimmed.equals("kaka export", ignoreCase = true)) return TerminalCommand.KakaAction("export")
        if (trimmed.equals("kaka settings", ignoreCase = true)) return TerminalCommand.KakaAction("settings")
        if (trimmed.startsWith("kaka search ", ignoreCase = true)) return TerminalCommand.KakaAction("search", trimmed.substring(12).trim())
        
        if (trimmed.equals("/help", ignoreCase = true) || trimmed.equals("help", ignoreCase = true)) return TerminalCommand.Help

        // Basic tokenization
        val tokens = tokenize(trimmed)
        if (tokens.isEmpty()) return TerminalCommand.Empty
        
        val cmd = tokens.first().lowercase()
        val args = tokens.drop(1)

        return when (cmd) {
            "f/" -> parseFinancial(args, null)
            "credit/" -> parseFinancial(args, true)
            "debit/" -> parseFinancial(args, false)
            "init/" -> parseInit(args)
            "xfer/" -> parseTransfer(args)
            "due/" -> parseDue(args)
            "settle/" -> parseSettle(args)
            "account/" -> parseAccount(args)
            "delete/" -> parseDelete(args)
            else -> TerminalCommand.Error("Unknown terminal command: $cmd")
        }
    }

    private fun parseFinancial(args: List<String>, forceCredit: Boolean?): TerminalCommand {
        if (args.isEmpty()) return TerminalCommand.Error("Usage: f/ ±amount <account> <category> [note] [@date]")

        var amount: Double? = null
        var isCredit = false
        var amountTokenIndex = -1
        var dateToken: String? = null
        var dateTokenIndex = -1

        for ((index, token) in args.withIndex()) {
            if (token.startsWith("@")) {
                dateToken = token.substring(1)
                dateTokenIndex = index
                continue
            }
            if (amount == null) {
                val rawNumber = token.removePrefix("+").removePrefix("-")
                val parsed = rawNumber.toDoubleOrNull()
                if (parsed != null && parsed > 0.0 && !parsed.isNaN() && !parsed.isInfinite()) {
                    amount = parsed
                    isCredit = forceCredit ?: token.startsWith("+")
                    amountTokenIndex = index
                }
            }
        }

        if (amount == null) return TerminalCommand.Error("Amount missing or invalid.")

        val remainingTokens = args.filterIndexed { index, _ -> index != amountTokenIndex && index != dateTokenIndex }
        return TerminalCommand.Financial(amount, isCredit, remainingTokens, dateToken)
    }

    private fun parseInit(args: List<String>): TerminalCommand {
        if (args.size < 2) return TerminalCommand.Error("Usage: init/ <account> <amount>")
        val t1 = args[0]
        val t2 = args[1]
        
        val amt1 = t1.toDoubleOrNull()
        val amt2 = t2.toDoubleOrNull()
        
        return if (amt1 != null && amt2 == null) {
            TerminalCommand.Init(t2, amt1)
        } else if (amt2 != null && amt1 == null) {
            TerminalCommand.Init(t1, amt2)
        } else {
            TerminalCommand.Error("Could not determine amount for init command.")
        }
    }

    private fun parseTransfer(args: List<String>): TerminalCommand {
        if (args.size < 3) return TerminalCommand.Error("Usage: xfer/ <amount> <from> <to> [note]")
        val amount = args[0].toDoubleOrNull() ?: return TerminalCommand.Error("Invalid amount for transfer: ${args[0]}")
        return TerminalCommand.Transfer(amount, args[1], args[2], args.drop(3))
    }

    private fun parseDue(args: List<String>): TerminalCommand {
        if (args.size < 3) return TerminalCommand.Error("Usage: due/ <in|out> <amount> <contact> [@date]")
        val direction = args[0].lowercase()
        val isOut = when (direction) {
            "in" -> false
            "out" -> true
            else -> return TerminalCommand.Error("First argument of due/ must be 'in' or 'out'")
        }
        val amount = args[1].toDoubleOrNull() ?: return TerminalCommand.Error("Invalid amount for due: ${args[1]}")
        
        var dateToken: String? = null
        var contactParts = mutableListOf<String>()
        for (i in 2 until args.size) {
            if (args[i].startsWith("@")) {
                dateToken = args[i].substring(1)
            } else {
                contactParts.add(args[i])
            }
        }
        val contactToken = contactParts.joinToString(" ")
        if (contactToken.isEmpty()) return TerminalCommand.Error("Contact name missing for due.")
        
        return TerminalCommand.LedgerDue(isOut, amount, contactToken, dateToken)
    }

    private fun parseSettle(args: List<String>): TerminalCommand {
        if (args.isEmpty()) return TerminalCommand.Error("Usage: settle/ <contact> [amount]")
        val amountToken = args.lastOrNull()?.toDoubleOrNull()
        return if (amountToken != null) {
            TerminalCommand.LedgerSettle(args.dropLast(1).joinToString(" "), amountToken)
        } else {
            TerminalCommand.LedgerSettle(args.joinToString(" "), null)
        }
    }

    private fun parseAccount(args: List<String>): TerminalCommand {
        if (args.isEmpty()) return TerminalCommand.Error("Usage: account/ <add|hide|show|archive|restore> ...")
        val action = args[0].lowercase()
        return when (action) {
            "add" -> {
                if (args.size < 3) return TerminalCommand.Error("Usage: account/ add <name> <type> [balance]")
                val balance = if (args.size > 3) args.last().toDoubleOrNull() else null
                val typeToken = if (balance != null) args[args.size - 2] else args.last()
                val nameEnd = if (balance != null) args.size - 2 else args.size - 1
                val name = args.subList(1, nameEnd).joinToString(" ")
                TerminalCommand.AccountAdd(name, typeToken, balance)
            }
            "hide", "show", "archive", "restore" -> {
                if (args.size < 2) return TerminalCommand.Error("Usage: account/ $action <name>")
                TerminalCommand.AccountAction(action, args.subList(1, args.size).joinToString(" "))
            }
            else -> TerminalCommand.Error("Unknown account action: $action")
        }
    }

    private fun parseDelete(args: List<String>): TerminalCommand {
        if (args.size < 2) return TerminalCommand.Error("Usage: delete/ <tx|account|entry> <selector>")
        return TerminalCommand.Delete(args[0].lowercase(), args.subList(1, args.size).joinToString(" "))
    }

    /** Shell-like tokenizer: whitespace separates tokens outside quotes. */
    private fun tokenize(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false

        for (char in input) {
            if (escaped) {
                current.append(char)
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = true
                continue
            }
            if (quote != null) {
                if (char == quote) quote = null else current.append(char)
            } else if (char == '\'' || char == '"') {
                quote = char
                current.append(char)
            } else if (char.isWhitespace()) {
                if (current.isNotEmpty()) {
                    result += current.toString()
                    current.clear()
                }
            } else {
                current.append(char)
            }
        }
        if (escaped) current.append('\\')
        if (quote != null) return listOf(input)
        if (current.isNotEmpty()) result += current.toString()
        return result
    }
}