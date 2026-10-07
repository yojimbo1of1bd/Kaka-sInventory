package com.projectkaka.inventory.search

object TerminalParser {

    fun parse(input: String): TerminalCommand {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return TerminalCommand.Empty

        // Action commands
        if (trimmed.equals("kaka show graph", ignoreCase = true)) return TerminalCommand.KakaAction("graph")
        if (trimmed.equals("kaka show alias", ignoreCase = true) || trimmed.equals("kaka alias", ignoreCase = true)) return TerminalCommand.KakaAction("alias")
        if (trimmed.equals("kaka ledger", ignoreCase = true)) return TerminalCommand.KakaAction("ledger")
        if (trimmed.equals("kaka accounts", ignoreCase = true)) return TerminalCommand.KakaAction("accounts")
        if (trimmed.equals("kaka export", ignoreCase = true)) return TerminalCommand.KakaAction("export")
        if (trimmed.equals("kaka settings", ignoreCase = true)) return TerminalCommand.KakaAction("settings")
        if (trimmed.startsWith("kaka search ", ignoreCase = true)) return TerminalCommand.KakaAction("search", trimmed.substring(12).trim())
        
        if (trimmed.equals("/help", ignoreCase = true) || trimmed.equals("help", ignoreCase = true)) return TerminalCommand.Help

        // Slash normalization: if a slash directly touches the next token without space (e.g. settle/"babul mama" or f/-120), insert space
        val slashNormalized = Regex("^([a-zA-Z0-9]+)/([^\\s].*)$").replace(trimmed) { match ->
            "${match.groupValues[1]}/ ${match.groupValues[2]}"
        }

        if (slashNormalized.startsWith("man ", ignoreCase = true)) {
            val topic = slashNormalized.substring(4).trim()
            return TerminalCommand.Man(topic.ifBlank { "?" })
        }
        if (slashNormalized.equals("help/ ?", ignoreCase = true) || slashNormalized.equals("help/?", ignoreCase = true) || slashNormalized.equals("man ?", ignoreCase = true)) {
            return TerminalCommand.Man("?")
        }

        // Basic tokenization
        val tokens = tokenize(slashNormalized)
        if (tokens.isEmpty()) return TerminalCommand.Empty
        
        val cmd = tokens.first().lowercase()
        val args = tokens.drop(1)

        return when (cmd) {
            "f/" -> parseFinancial(args)
            "man/" -> TerminalCommand.Man(args.joinToString(" ").ifBlank { "?" })
            "help/" -> TerminalCommand.Man(args.joinToString(" ").ifBlank { "?" })
            "log/" -> TerminalCommand.Log(args.joinToString(" ").trim())
            "snapshot/" -> parseSnapshot(args)
            "init/" -> parseInit(args)
            "xfer/", "accrue/", "realize/" -> parseTransfer(args)
            "cashout/" -> parseCashout(args)
            "charge/" -> parseCharge(args)
            "due/" -> parseDue(args)
            "settle/" -> parseSettle(args)
            "verify/" -> parseVerify(args)
            "history/" -> parseHistory(args)
            "account/" -> parseAccount(args)
            "delete/" -> parseDelete(args)
            "bal/" -> parseBalanceCheck(args)
            "report/" -> parseReport(args)
            "alter/" -> parseAlter(args)
            "reset/" -> TerminalCommand.Reset
            else -> TerminalCommand.Error("Unknown terminal command: $cmd")
        }
    }

    private fun parseFinancial(args: List<String>): TerminalCommand {
        if (args.size < 3) return TerminalCommand.Error("Usage: f/ <amount> <debitAccount> <creditAccount> [\"comments\"] [@date]\nAlways DEBIT first, then CREDIT. See 'man f/' for details.")

        var amount: com.projectkaka.inventory.model.Money? = null
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
                val parsed = try { com.projectkaka.inventory.model.Money.fromDecimalString(rawNumber) } catch (e: Exception) { null }
                if (parsed != null && parsed.minorUnits > 0L) {
                    amount = parsed
                    amountTokenIndex = index
                }
            }
        }

        if (amount == null) return TerminalCommand.Error("Amount missing or invalid.")

        val remainingTokens = args.filterIndexed { index, _ -> index != amountTokenIndex && index != dateTokenIndex }
        if (remainingTokens.size < 2) {
            return TerminalCommand.Error("Both Debit and Credit accounts required: f/ <amount> <debitAccount> <creditAccount> [\"comments\"]. See 'man f/'.")
        }

        val debitAccount = remainingTokens[0]
        val creditAccount = remainingTokens[1]
        val note = remainingTokens.drop(2).joinToString(" ").trim()

        return TerminalCommand.Financial(amount, debitAccount, creditAccount, note, dateToken)
    }

    private fun parseSnapshot(args: List<String>): TerminalCommand {
        val action = args.firstOrNull()?.lowercase() ?: "create"
        return TerminalCommand.Snapshot(action)
    }

    private fun parseInit(args: List<String>): TerminalCommand {
        if (args.size < 2) return TerminalCommand.Error("Usage: init/ <account> <amount>")
        val t1 = args[0]
        val t2 = args[1]
        
        val amt1 = try { com.projectkaka.inventory.model.Money.fromDecimalString(t1) } catch (e: Exception) { null }
        val amt2 = try { com.projectkaka.inventory.model.Money.fromDecimalString(t2) } catch (e: Exception) { null }
        
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
        val amount = try { com.projectkaka.inventory.model.Money.fromDecimalString(args[0]) } catch (e: Exception) { return TerminalCommand.Error("Invalid amount for transfer: ${args[0]}") }
        return TerminalCommand.Transfer(amount, args[1], args[2], args.drop(3))
    }

    private fun parseCashout(args: List<String>): TerminalCommand {
        if (args.size < 2) return TerminalCommand.Error("Usage: cashout/ <amount> <sourceAccount> [targetCashAccount]")
        val amount = try { com.projectkaka.inventory.model.Money.fromDecimalString(args[0]) } catch (e: Exception) {
            return TerminalCommand.Error("Invalid amount for cashout: ${args[0]}")
        }
        val source = args[1]
        val target = if (args.size > 2) args[2] else "Cash"
        return TerminalCommand.Cashout(amount, source, target)
    }

    private fun parseCharge(args: List<String>): TerminalCommand {
        if (args.isEmpty() || args[0].equals("all", ignoreCase = true) || args[0].equals("show", ignoreCase = true)) {
            return TerminalCommand.Charge(null, null, "all")
        }
        if (args.size == 1) {
            return TerminalCommand.Charge(args[0], null, "get")
        }
        val action = if (args[1].equals("clear", ignoreCase = true) || args[1].equals("delete", ignoreCase = true)) "clear" else "set"
        return TerminalCommand.Charge(args[0], args[1], action)
    }

    private fun parseVerify(args: List<String>): TerminalCommand {
        val target = args.firstOrNull()?.lowercase() ?: "images"
        return TerminalCommand.Verify(target)
    }

    private fun parseHistory(args: List<String>): TerminalCommand {
        val count = args.firstOrNull()?.toIntOrNull() ?: 20
        return TerminalCommand.History(count)
    }

    private fun parseDue(args: List<String>): TerminalCommand {
        if (args.size < 3) return TerminalCommand.Error("Usage: due/ <in|out> <amount> <contact> [note] [@date]")
        val direction = args[0].lowercase()
        val isOut = when (direction) {
            "in" -> false
            "out" -> true
            else -> return TerminalCommand.Error("First argument of due/ must be 'in' or 'out'")
        }
        val amount = try { com.projectkaka.inventory.model.Money.fromDecimalString(args[1]) } catch (e: Exception) { return TerminalCommand.Error("Invalid amount for due: ${args[1]}") }
        
        var dateToken: String? = null
        val remaining = mutableListOf<String>()
        for (i in 2 until args.size) {
            if (args[i].startsWith("@")) {
                dateToken = args[i].substring(1)
            } else {
                remaining.add(args[i])
            }
        }
        if (remaining.isEmpty()) return TerminalCommand.Error("Contact name missing for due.")
        
        val contactToken = remaining.joinToString(" ")
        return TerminalCommand.LedgerDue(isOut, amount, contactToken, "", dateToken)
    }

    private fun parseSettle(args: List<String>): TerminalCommand {
        if (args.isEmpty()) return TerminalCommand.Error("Usage: settle/ <contact> [amount] [account]")
        var amount: com.projectkaka.inventory.model.Money? = null
        var amountIndex = -1
        for ((idx, arg) in args.withIndex()) {
            val parsed = try { com.projectkaka.inventory.model.Money.fromDecimalString(arg) } catch (e: Exception) { null }
            if (parsed != null && parsed.minorUnits > 0L) {
                amount = parsed
                amountIndex = idx
                break
            }
        }
        val contactTokens = if (amountIndex != -1) {
            args.filterIndexed { idx, _ -> idx < amountIndex }
        } else {
            args
        }
        val accountToken = if (amountIndex != -1 && amountIndex + 1 < args.size) {
            args[amountIndex + 1]
        } else "Cash"
        
        val contact = contactTokens.joinToString(" ").trim()
        if (contact.isEmpty()) return TerminalCommand.Error("Contact name missing for settle.")
        return TerminalCommand.LedgerSettle(contact, amount, accountToken)
    }

    private fun parseAccount(args: List<String>): TerminalCommand {
        if (args.isEmpty()) return TerminalCommand.Error("Usage: account/ <add|hide|show|archive|restore> ...")
        val action = args[0].lowercase()
        return when (action) {
            "add" -> {
                if (args.size < 2) return TerminalCommand.Error("Usage: account/ add <name> [type] [balance]")
                val remaining = args.drop(1).toMutableList()
                
                // 1. Check if the last token is a balance
                val balance = if (remaining.size > 1) {
                    try {
                        val parsed = com.projectkaka.inventory.model.Money.fromDecimalString(remaining.last())
                        remaining.removeAt(remaining.size - 1)
                        parsed
                    } catch (e: Exception) { null }
                } else null
                
                // 2. Check if the last token is an account type
                val knownTypes = setOf("cash", "asset", "liability", "capital", "revenue", "expense", "debt", "loan", "due", "iou", "income", "cost")
                val typeToken = if (remaining.size > 1 && remaining.last().lowercase() in knownTypes) {
                    remaining.removeAt(remaining.size - 1)
                } else {
                    "CASH"
                }
                
                val name = remaining.joinToString(" ").trim()
                if (name.isEmpty()) return TerminalCommand.Error("Account name cannot be empty.")
                TerminalCommand.AccountAdd(name, typeToken, balance)
            }
            "type" -> {
                if (args.size < 3) return TerminalCommand.Error("Usage: account/ type <name> <type>")
                TerminalCommand.AccountAlter(args[1], "type", args.drop(2).joinToString(" "))
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

    private fun parseBalanceCheck(args: List<String>): TerminalCommand {
        if (args.isEmpty()) return TerminalCommand.Error("Usage: bal/ <account|all>")
        return TerminalCommand.BalanceCheck(args.joinToString(" ").lowercase())
    }

    private fun parseReport(args: List<String>): TerminalCommand {
        if (args.isEmpty()) return TerminalCommand.Error("Usage: report/ <bs|pnl|trend> [arg]")
        val type = args[0].lowercase()
        if (type !in listOf("bs", "pnl", "trend")) {
            return TerminalCommand.Error("Unknown report type: $type. Use bs, pnl, or trend.")
        }
        val arg = if (args.size > 1) args[1] else null
        return TerminalCommand.Report(type, arg)
    }

    private fun parseAlter(args: List<String>): TerminalCommand {
        if (args.size < 3) {
            return TerminalCommand.Error("Usage: alter/ <account> <rename|type> <value>")
        }
        val accountToken = args[0]
        val action = args[1].lowercase()
        if (action !in listOf("rename", "type")) {
            return TerminalCommand.Error("Usage: alter/ <account> <rename|type> <value>")
        }
        val value = args.drop(2).joinToString(" ")
        return TerminalCommand.AccountAlter(accountToken, action, value)
    }

    /** Shell-like tokenizer: whitespace separates tokens outside quotes. */
    fun tokenize(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val normalized = input
            .replace('“', '"')
            .replace('”', '"')
            .replace('„', '"')
            .replace('‘', '\'')
            .replace('’', '\'')
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false

        for (char in normalized) {
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
                if (char == quote) {
                    quote = null
                } else {
                    current.append(char)
                }
            } else if (char == '\'' || char == '"') {
                quote = char
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
        if (current.isNotEmpty()) result += current.toString()
        return result
    }
}