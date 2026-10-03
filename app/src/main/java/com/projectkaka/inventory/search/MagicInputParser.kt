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



sealed interface ParseResult {
    data class Success(val value: ParsedInventoryQuery) : ParseResult
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
 *
 * The search bar is read-only. Terminal commands (f/, init/, delete/, etc.) are not supported.
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
        now: Long = System.currentTimeMillis()
    ): ParseResult {
        val trimmed = input.trim()

        if (trimmed.startsWith("f/", ignoreCase = true) ||
            trimmed.startsWith("credit/", ignoreCase = true) ||
            trimmed.startsWith("debit/", ignoreCase = true) ||
            trimmed.startsWith("init/", ignoreCase = true) ||
            trimmed.startsWith("alter/", ignoreCase = true) ||
            trimmed.startsWith("delete/", ignoreCase = true) ||
            trimmed.startsWith("account/", ignoreCase = true) ||
            trimmed.startsWith("xfer/", ignoreCase = true) ||
            trimmed.startsWith("due/", ignoreCase = true) ||
            trimmed.startsWith("settle/", ignoreCase = true) ||
            trimmed.startsWith("kaka ", ignoreCase = true) ||
            trimmed.equals("/help", ignoreCase = true) || 
            trimmed.equals("help", ignoreCase = true)) {
            return ParseResult.Error("The search bar is read-only. Use the Terminal for commands.")
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
                val minorUnits = try {
                    com.projectkaka.inventory.model.Money.fromDecimalString(rawNumber).minorUnits
                } catch (e: Exception) {
                    return ParseResult.Error("Invalid value: $rawNumber")
                }
                clauses += Clause("estimated_value ${operatorToSql(operator)} ?", minorUnits)
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
    private fun escapeLike(value: String): String = com.projectkaka.inventory.util.SearchHelper.escapeLike(value)

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


}
