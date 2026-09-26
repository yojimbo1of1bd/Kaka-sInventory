package com.projectkaka.inventory.search

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.projectkaka.inventory.data.local.entity.ItemStatus

/** A parsed, validated search expression ready for Room's @RawQuery. */
data class ParsedInventoryQuery(
    val source: String,
    val query: SupportSQLiteQuery,
    val filters: List<String>
)

/**
 * Raw parsed `f/` command.  Alias resolution (token → DB entity) happens
 * in the ViewModel / Repository — the parser only tokenises.
 *
 * [isCredit] is `true` when the sign is `+` (income), `false` for `-` or
 * no sign (expense — the common case for a student).
 */
data class FinancialCommand(
    val amount: Double,
    val isCredit: Boolean,
    val accountToken: String?,
    val categoryToken: String?,
    val note: String
)

sealed interface ParseResult {
    data class Success(val value: ParsedInventoryQuery) : ParseResult
    data class Financial(val command: FinancialCommand) : ParseResult
    data class Action(val actionType: String) : ParseResult
    data class InitAccount(val accountAlias: String, val amount: Double) : ParseResult
    data class AlterAccount(val accountAlias: String, val newName: String) : ParseResult
    data class DeleteAccount(val accountAlias: String) : ParseResult
    data class Error(val message: String) : ParseResult
}

/**
 * Tokenizer/parser for the Magic Input Bar.
 *
 * Tokens stack with AND:
 *   i/laptop       name contains "laptop"
 *   c/electronics  category contains "electronics"
 *   l/garage       location_tag contains "garage"
 *   v/>500         estimated_value > 500   (also v/<, v/=, v/>=, v/<=)
 *   m/due          has at least one overdue care task
 *   m/none         has no care tasks at all
 *   s/sold         item status is SOLD      (also s/donated, s/trashed, s/active)
 *
 * Terminal commands:
 *   f/ [amount] [account] [category] [note]   — financial entry
 *   init/ [account] [amount]                  — initialize account balance
 *   alter/ [account] [newname]                — rename an account
 *   delete/ [account]                         — delete an account
 *   credit/ [amount] [account] [category]     — shorthand for f/ +amount
 *   debit/ [amount] [account] [category]      — shorthand for f/ -amount
 *   kaka show graph                           — open analytics
 *   kaka show alias                           — open terminal manual
 *   kaka ledger                               — open ledger
 *   /help                                     — show available commands
 *
 * SECURITY: no token value is ever concatenated into the SQL string. Values travel
 * as bound arguments; the only interpolated fragments are column names and enum
 * names taken from fixed internal maps.
 */
object MagicInputParser {

    private val valuePattern = Regex("^v/([<>]=?|=)(.+)$", RegexOption.IGNORE_CASE)
    private val fieldPattern = Regex("^([icl])/([^\\s].*)$", RegexOption.IGNORE_CASE)
    private val maintenancePattern = Regex("^m/(due|none)$", RegexOption.IGNORE_CASE)
    private val statusPattern = Regex("^s/(active|sold|donated|trashed)$", RegexOption.IGNORE_CASE)

    fun parse(
        input: String,
        knownAccounts: Set<String> = emptySet(),
        knownCategories: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis()
    ): ParseResult {
        val trimmed = input.trim()

        // ── Financial command: f/ -120 bkash lunch [note] ───────────────
        if (trimmed.startsWith("f/", ignoreCase = true)) {
            return parseFinancial(trimmed.substring(2).trim(), knownAccounts, knownCategories, null)
        }
        
        // ── Credit shorthand: credit/ 500 bkash salary ───────────────
        if (trimmed.startsWith("credit/", ignoreCase = true)) {
            val body = trimmed.substring(7).trim()
            if (body.isBlank()) return ParseResult.Error("Usage: credit/ [amount] [account] [category]")
            return parseFinancial(body, knownAccounts, knownCategories, true)
        }
        
        // ── Debit shorthand: debit/ 500 cash food ───────────────
        if (trimmed.startsWith("debit/", ignoreCase = true)) {
            val body = trimmed.substring(6).trim()
            if (body.isBlank()) return ParseResult.Error("Usage: debit/ [amount] [account] [category]")
            return parseFinancial(body, knownAccounts, knownCategories, false)
        }
        
        // ── Action commands ───────────────
        if (trimmed.equals("kaka show graph", ignoreCase = true)) {
            return ParseResult.Action("show_graph")
        }
        if (trimmed.equals("kaka show alias", ignoreCase = true)) {
            return ParseResult.Action("show_alias")
        }
        if (trimmed.equals("kaka ledger", ignoreCase = true)) {
            return ParseResult.Action("ledger")
        }
        if (trimmed.equals("kaka export", ignoreCase = true)) {
            return ParseResult.Action("export")
        }
        
        // ── Help command ───────────────
        if (trimmed.equals("/help", ignoreCase = true) || trimmed.equals("help", ignoreCase = true)) {
            return ParseResult.Action("show_alias")
        }
        
        // ── Init command ───────────────
        if (trimmed.startsWith("init/", ignoreCase = true)) {
            val parts = trimmed.substring(5).trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                val accountAlias = parts[0]
                val amount = parts[1].toDoubleOrNull()
                if (amount != null) {
                    return ParseResult.InitAccount(accountAlias, amount)
                }
            }
            return ParseResult.Error("Usage: init/ [account] [amount]")
        }
        
        // ── Alter command: rename an account ───────────────
        if (trimmed.startsWith("alter/", ignoreCase = true)) {
            val parts = trimmed.substring(6).trim().split("\\s+".toRegex(), limit = 2)
            if (parts.size >= 2 && parts[1].isNotBlank()) {
                return ParseResult.AlterAccount(parts[0], parts[1])
            }
            return ParseResult.Error("Usage: alter/ [account] [new_name]")
        }
        
        // ── Delete command: delete an account ───────────────
        if (trimmed.startsWith("delete/", ignoreCase = true)) {
            val alias = trimmed.substring(7).trim()
            if (alias.isNotBlank()) {
                return ParseResult.DeleteAccount(alias)
            }
            return ParseResult.Error("Usage: delete/ [account]")
        }

        val tokens = tokenize(trimmed)
        if (tokens.isEmpty()) return ParseResult.Success(build(input, emptyList(), emptyList(), null))

        val clauses = mutableListOf<Clause>()
        val labels = mutableListOf<String>()
        var statusOverride: ItemStatus? = null

        for (token in tokens) {
            // ---- v/ numeric comparison ----
            val valueMatch = valuePattern.matchEntire(token)
            if (valueMatch != null) {
                val operator = valueMatch.groupValues[1]
                val rawNumber = valueMatch.groupValues[2]
                val number = rawNumber.toDoubleOrNull()
                    ?: return ParseResult.Error("Invalid value: $rawNumber")
                if (number.isNaN() || number.isInfinite()) {
                    return ParseResult.Error("Invalid value: $rawNumber")
                }
                clauses += Clause("estimated_value ${operatorToSql(operator)} ?", number)
                labels += token
                continue
            }
            if (token.startsWith("v/", ignoreCase = true)) {
                return ParseResult.Error("Use v/>500, v/<500, v/=500")
            }

            // ---- i/ c/ l/ text filters ----
            val fieldMatch = fieldPattern.matchEntire(token)
            if (fieldMatch != null) {
                val prefix = fieldMatch.groupValues[1].lowercase()
                val rawValue = unquote(fieldMatch.groupValues[2]).trim()
                if (rawValue.isBlank()) return ParseResult.Error("Empty filter: $token")
                val column = when (prefix) {
                    "i" -> "name"
                    "c" -> "category"
                    "l" -> "location_tag"
                    else -> return ParseResult.Error("Unknown filter: $token")
                }
                clauses += Clause(
                    "LOWER(items.$column) LIKE LOWER(?) ESCAPE '\\'",
                    "%${escapeLike(rawValue)}%"
                )
                labels += token
                continue
            }

            // ---- m/ maintenance filters ----
            val maintenanceMatch = maintenancePattern.matchEntire(token)
            if (maintenanceMatch != null) {
                when (maintenanceMatch.groupValues[1].lowercase()) {
                    "due" -> clauses += Clause(
                        "EXISTS (SELECT 1 FROM care_tasks ct WHERE ct.item_id = items.id " +
                            "AND (ct.last_completed_date + (ct.frequency_days * 86400000)) <= ?)",
                        now
                    )
                    "none" -> clauses += Clause(
                        "NOT EXISTS (SELECT 1 FROM care_tasks ct WHERE ct.item_id = items.id)",
                        null
                    )
                }
                labels += token
                continue
            }
            if (token.startsWith("m/", ignoreCase = true)) {
                return ParseResult.Error("Use m/due or m/none")
            }

            // ---- s/ status filters ----
            val statusMatch = statusPattern.matchEntire(token)
            if (statusMatch != null) {
                statusOverride = when (statusMatch.groupValues[1].lowercase()) {
                    "active" -> ItemStatus.ACTIVE
                    "sold" -> ItemStatus.SOLD
                    "donated" -> ItemStatus.DONATED
                    "trashed" -> ItemStatus.TRASHED
                    else -> return ParseResult.Error("Unknown status: $token")
                }
                labels += token
                continue
            }
            if (token.startsWith("s/", ignoreCase = true)) {
                return ParseResult.Error("Use s/active, s/sold, s/donated or s/trashed")
            }

            // ---- bare word = quick name search ----
            if (!token.contains('/')) {
                val rawValue = unquote(token).trim()
                if (rawValue.isNotBlank()) {
                    clauses += Clause(
                        "LOWER(items.name) LIKE LOWER(?) ESCAPE '\\'",
                        "%${escapeLike(rawValue)}%"
                    )
                    labels += "i/$rawValue"
                }
                continue
            }

            return ParseResult.Error("Unknown filter: $token")
        }

        return ParseResult.Success(build(input, clauses, labels, statusOverride))
    }

    private fun build(
        source: String,
        clauses: List<Clause>,
        labels: List<String>,
        statusOverride: ItemStatus?
    ): ParsedInventoryQuery {
        // Only enum names from ItemStatus ever reach the SQL text.
        val status = (statusOverride ?: ItemStatus.ACTIVE).name

        val where = buildList {
            add("items.status = '$status'")
            add("items.is_draft = 0")
            addAll(clauses.map { it.sql })
        }.joinToString(" AND ")

        // Arguments are bound in clause order; clauses without a value are skipped.
        val args = clauses.mapNotNull { it.argument }.toTypedArray()

        val sql = "SELECT * FROM items WHERE $where ORDER BY items.date_added DESC"
        return ParsedInventoryQuery(
            source = source,
            query = SimpleSQLiteQuery(sql, args),
            filters = labels
        )
    }

    private fun operatorToSql(operator: String): String = when (operator) {
        ">" -> ">"
        "<" -> "<"
        "=" -> "="
        ">=" -> ">="
        "<=" -> "<="
        else -> "=" // unreachable: regex only matches the set above
    }

    /** Escapes SQLite LIKE metacharacters so user text cannot act as a wildcard. */
    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private fun unquote(value: String): String =
        if (value.length >= 2 &&
            ((value.first() == '"' && value.last() == '"') ||
                (value.first() == '\'' && value.last() == '\''))
        ) value.substring(1, value.length - 1) else value

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

    private data class Clause(val sql: String, val argument: Any?)

    // ════════════════════════════════════════════════════════════════════
    //  f/ — Financial command parser
    // ════════════════════════════════════════════════════════════════════

    private fun parseFinancial(
        input: String,
        knownAccounts: Set<String>,
        knownCategories: Set<String>,
        forceCredit: Boolean?
    ): ParseResult {
        if (input.isBlank()) {
            return ParseResult.Error(
                "Usage: f/ 120 bkash lunch [note]\n" +
                    "  −amount = expense  •  +amount = income"
            )
        }

        val tokens = tokenize(input)
        if (tokens.isEmpty()) return ParseResult.Error("Usage: f/ [amount] [account] [category]")

        var amount: Double? = null
        var isCredit = false
        var amountTokenIndex = -1

        for ((index, token) in tokens.withIndex()) {
            val rawNumber = token.removePrefix("+").removePrefix("-")
            val parsed = rawNumber.toDoubleOrNull()
            if (parsed != null && parsed > 0.0 && !parsed.isNaN() && !parsed.isInfinite()) {
                amount = parsed
                isCredit = forceCredit ?: token.startsWith("+")
                amountTokenIndex = index
                break
            }
        }

        if (amount == null) {
            return ParseResult.Error("Amount missing or invalid.")
        }

        val remainingTokens = tokens.filterIndexed { index, _ -> index != amountTokenIndex }
        
        val matchedAccounts = mutableListOf<String>()
        val matchedCategories = mutableListOf<String>()
        val unassignedTokens = mutableListOf<String>()
        
        for (token in remainingTokens) {
            val lowerToken = token.lowercase()
            val isAcc = knownAccounts.contains(lowerToken)
            val isCat = knownCategories.contains(lowerToken)
            
            if (isAcc && isCat) {
                return ParseResult.Error("Ambiguous alias: '$token' is both an account and category.")
            }
            if (isAcc) {
                matchedAccounts.add(token)
            } else if (isCat) {
                matchedCategories.add(token)
            } else {
                unassignedTokens.add(token)
            }
        }
        
        val isInit = remainingTokens.any { it.equals("init", ignoreCase = true) }
        if (isInit && matchedAccounts.size == 1) {
            return ParseResult.InitAccount(matchedAccounts[0], amount)
        }

        if (matchedAccounts.size > 1) {
            return ParseResult.Error("Ambiguous accounts: ${matchedAccounts.joinToString(", ")}. Only one account allowed.")
        }
        if (matchedCategories.size > 1) {
            return ParseResult.Error("Ambiguous categories: ${matchedCategories.joinToString(", ")}. Only one category allowed.")
        }
        
        val accountToken = matchedAccounts.firstOrNull()
        val categoryToken = matchedCategories.firstOrNull()
        
        if (accountToken == null && categoryToken == null) {
            return ParseResult.Error("Could not recognize any account or category. Please specify at least one.")
        }

        val note = unassignedTokens.filter { !it.equals("init", ignoreCase = true) }.joinToString(" ").trim()

        return ParseResult.Financial(
            FinancialCommand(
                amount = amount,
                isCredit = isCredit,
                accountToken = accountToken,
                categoryToken = categoryToken,
                note = note
            )
        )
    }
}
