package com.projectkaka.inventory.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the Magic Input Bar parser. No Android dependencies,
 * so these run on the host with `gradlew test`.
 */
class MagicInputParserTest {

    private fun success(input: String): ParsedInventoryQuery {
        val result = MagicInputParser.parse(input)
        assertTrue("Expected success for: $input", result is ParseResult.Success)
        return (result as ParseResult.Success).value
    }

    private fun sqlOf(input: String): String = success(input).query.sql

    @Test
    fun emptyInput_queriesAllActiveItems() {
        val sql = sqlOf("")
        assertEquals(
            "SELECT * FROM items WHERE items.status = 'ACTIVE' AND items.is_draft = 0 " +
                "ORDER BY items.date_added DESC",
            sql
        )
    }

    @Test
    fun nameFilter_buildsLikeClause() {
        assertTrue(sqlOf("i/laptop").contains("LOWER(items.name) LIKE LOWER(?)"))
    }

    @Test
    fun categoryAndLocationFilters_areSupported() {
        assertTrue(sqlOf("c/electronics").contains("LOWER(items.category) LIKE LOWER(?)"))
        assertTrue(sqlOf("l/garage").contains("LOWER(items.location_tag) LIKE LOWER(?)"))
    }

    @Test
    fun valueComparisons_areSupported() {
        assertTrue(sqlOf("v/>500").contains("estimated_value > ?"))
        assertTrue(sqlOf("v/<100").contains("estimated_value < ?"))
        assertTrue(sqlOf("v/=25").contains("estimated_value = ?"))
        assertTrue(sqlOf("v/>=10").contains("estimated_value >= ?"))
        assertTrue(sqlOf("v/<=10").contains("estimated_value <= ?"))
    }

    @Test
    fun stackedTags_areAndedTogether() {
        val sql = sqlOf("c/electronics v/>500")
        assertTrue(sql.contains("LOWER(items.category) LIKE LOWER(?)"))
        assertTrue(sql.contains("estimated_value > ?"))
        assertTrue(sql.contains(" AND "))
    }

    @Test
    fun bareWord_searchesName() {
        assertTrue(sqlOf("drill").contains("LOWER(items.name) LIKE LOWER(?)"))
    }

    @Test
    fun maintenanceDue_usesExistenceSubquery() {
        val sql = sqlOf("m/due")
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM care_tasks"))
        assertTrue(sql.contains("ct.frequency_days * 86400000"))
    }

    @Test
    fun maintenanceNone_usesNegatedExistenceSubquery() {
        assertTrue(sqlOf("m/none").contains("NOT EXISTS (SELECT 1 FROM care_tasks"))
    }

    @Test
    fun statusFilter_overridesTheDefaultActiveScope() {
        val sql = sqlOf("s/sold")
        assertTrue(sql.contains("items.status = 'SOLD'"))
        assertTrue(!sql.contains("items.status = 'ACTIVE'"))
    }

    @Test
    fun statusAndTextStack_together() {
        val sql = sqlOf("s/donated c/books")
        assertTrue(sql.contains("items.status = 'DONATED'"))
        assertTrue(sql.contains("LOWER(items.category) LIKE LOWER(?)"))
    }

    @Test
    fun invalidValue_reportsError() {
        assertTrue(MagicInputParser.parse("v/>abc") is ParseResult.Error)
    }

    @Test
    fun malformedValueOperator_reportsHelpfulError() {
        val result = MagicInputParser.parse("v/500")
        assertTrue(result is ParseResult.Error)
        assertTrue((result as ParseResult.Error).message.contains("v/>"))
    }

    @Test
    fun unknownPrefix_reportsError() {
        assertTrue(MagicInputParser.parse("z/whatever") is ParseResult.Error)
    }

    @Test
    fun unknownStatus_reportsError() {
        assertTrue(MagicInputParser.parse("s/exploded") is ParseResult.Error)
    }

    @Test
    fun quotedValue_survivesTokenization() {
        val result = MagicInputParser.parse("c/\"home office\"")
        assertTrue(result is ParseResult.Success)
    }

    @Test
    fun injectionAttempt_travelsAsBoundArgument() {
        val query = success("i/x' OR '1'='1").query

        // The payload must never appear in the SQL text itself.
        assertTrue("SQL must not contain the raw payload", !query.sql.contains("OR '1'='1"))
        assertTrue(query.sql.contains("?"))
        assertEquals(1, query.argCount)
    }

    @Test
    fun likeWildcards_areEscapedIntoTheBoundArgument() {
        // The % in user input must be escaped so it cannot widen the match.
        val query = success("i/100%").query
        assertTrue(!query.sql.contains("100%"))
        assertEquals(1, query.argCount)
    }

    @Test
    fun manyTokens_allBecomeBoundArguments() {
        val query = success("c/tools v/>50 l/shed m/due").query
        // Each of the 4 filters contributes one bound argument.
        assertEquals(4, query.argCount)
    }

    // --- Financial Command Tests ---

    private fun parseFin(input: String, accs: Set<String> = setOf("bkash", "cash", "bank"), cats: Set<String> = setOf("lunch", "salary", "uber")): ParseResult {
        return MagicInputParser.parse(input, accs, cats)
    }

    @Test
    fun financial_validFull_parsedCorrectly() {
        val result = parseFin("f/ -120 bkash lunch note here") as ParseResult.Financial
        assertEquals(120.0, result.command.amount, 0.001)
        assertEquals(false, result.command.isCredit)
        assertEquals("bkash", result.command.accountToken)
        assertEquals("lunch", result.command.categoryToken)
        assertEquals("note here", result.command.note)
    }

    @Test
    fun financial_reordered_parsedCorrectly() {
        // "amount required, account or category/contact required, optional and order-independent"
        val result = parseFin("f/ lunch bkash 120 note") as ParseResult.Financial
        assertEquals(120.0, result.command.amount, 0.001)
        assertEquals(false, result.command.isCredit) // No sign, defaults to expense
        assertEquals("bkash", result.command.accountToken)
        assertEquals("lunch", result.command.categoryToken)
        assertEquals("note", result.command.note)
    }

    @Test
    fun financial_missingCategory_parsedCorrectly() {
        val result = parseFin("f/ -120 bkash note") as ParseResult.Financial
        assertEquals(120.0, result.command.amount, 0.001)
        assertEquals(false, result.command.isCredit)
        assertEquals("bkash", result.command.accountToken)
        assertEquals(null, result.command.categoryToken)
        assertEquals("note", result.command.note)
    }

    @Test
    fun financial_ambiguousAlias_returnsError() {
        val result = parseFin("f/ 120 bkash lunch", accs = setOf("bkash"), cats = setOf("lunch", "bkash"))
        assertTrue(result is ParseResult.Error)
        assertTrue((result as ParseResult.Error).message.contains("Ambiguous alias"))
    }

    @Test
    fun financial_multipleAccounts_returnsError() {
        val result = parseFin("f/ 120 bkash bank lunch")
        assertTrue(result is ParseResult.Error)
        assertTrue((result as ParseResult.Error).message.contains("Ambiguous accounts"))
    }

    @Test
    fun financial_noAccountOrCategory_returnsError() {
        val result = parseFin("f/ 120 note")
        assertTrue(result is ParseResult.Error)
        assertTrue((result as ParseResult.Error).message.contains("Could not recognize any account or category"))
    }

    @Test
    fun financial_initCommand_returnsInitAccount() {
        val result = parseFin("init/ bkash 500") as ParseResult.InitAccount
        assertEquals(500.0, result.amount, 0.001)
        assertEquals("bkash", result.accountAlias)
    }
}
