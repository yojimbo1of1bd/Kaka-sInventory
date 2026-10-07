package com.projectkaka.inventory.search

import com.projectkaka.inventory.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalParserTest {

    @Test
    fun testTransferCommandWithSmartQuotes() {
        val input = "xfer/ 200 bkash cash \u201cCash out from agent\u201d"
        val cmd = TerminalParser.parse(input)
        assertTrue("Expected TerminalCommand.Transfer, got $cmd", cmd is TerminalCommand.Transfer)
        val transfer = cmd as TerminalCommand.Transfer
        assertEquals(Money(20000L), transfer.amount)
        assertEquals("bkash", transfer.fromToken)
        assertEquals("cash", transfer.toToken)
        assertEquals("Cash out from agent", transfer.noteTokens.joinToString(" "))
    }

    @Test
    fun testTransferCommandWithStandardQuotes() {
        val input = "xfer/ 200 bkash cash \"Cash out from agent\""
        val cmd = TerminalParser.parse(input)
        assertTrue(cmd is TerminalCommand.Transfer)
        val transfer = cmd as TerminalCommand.Transfer
        assertEquals(Money(20000L), transfer.amount)
        assertEquals("bkash", transfer.fromToken)
        assertEquals("cash", transfer.toToken)
        assertEquals("Cash out from agent", transfer.noteTokens.joinToString(" "))
    }

    @Test
    fun testTransferCommandWithoutQuotes() {
        val input = "xfer/ 200 bkash cash Cash out from agent"
        val cmd = TerminalParser.parse(input)
        assertTrue(cmd is TerminalCommand.Transfer)
        val transfer = cmd as TerminalCommand.Transfer
        assertEquals(Money(20000L), transfer.amount)
        assertEquals("bkash", transfer.fromToken)
        assertEquals("cash", transfer.toToken)
        assertEquals("Cash out from agent", transfer.noteTokens.joinToString(" "))
    }

    @Test
    fun testFinancialCommandWithFee() {
        val input = "f/ -3.70 bkash Bills \"bKash cash out charge\""
        val cmd = TerminalParser.parse(input)
        assertTrue(cmd is TerminalCommand.Financial)
        val financial = cmd as TerminalCommand.Financial
        assertEquals(Money(370L), financial.amount)
        assertEquals("bkash", financial.debitToken)
        assertEquals("Bills", financial.creditToken)
        assertEquals("bKash cash out charge", financial.note)
    }

    @Test
    fun testFinancialDoubleEntryExpenseAndIncome() {
        val expenseCmd = TerminalParser.parse("f/ -120 exp bkash \"For lunch\"")
        assertTrue(expenseCmd is TerminalCommand.Financial)
        val exp = expenseCmd as TerminalCommand.Financial
        assertEquals(Money(12000L), exp.amount)
        assertEquals("exp", exp.debitToken)
        assertEquals("bkash", exp.creditToken)
        assertEquals("For lunch", exp.note)

        val incomeCmd = TerminalParser.parse("f/ +5050 bkash salary \"Father sent me 5050 taka for month August\"")
        assertTrue(incomeCmd is TerminalCommand.Financial)
        val inc = incomeCmd as TerminalCommand.Financial
        assertEquals(Money(505000L), inc.amount)
        assertEquals("bkash", inc.debitToken)
        assertEquals("salary", inc.creditToken)
        assertEquals("Father sent me 5050 taka for month August", inc.note)
    }

    @Test
    fun testManAndHelpCommands() {
        val help1 = TerminalParser.parse("help/ ?")
        assertTrue(help1 is TerminalCommand.Man)
        assertEquals("?", (help1 as TerminalCommand.Man).topic)

        val manCmd = TerminalParser.parse("man f/")
        assertTrue(manCmd is TerminalCommand.Man)
        assertEquals("f/", (manCmd as TerminalCommand.Man).topic)
    }

    @Test
    fun testLogAndSnapshotCommands() {
        val logCmd = TerminalParser.parse("log/ august")
        assertTrue(logCmd is TerminalCommand.Log)
        assertEquals("august", (logCmd as TerminalCommand.Log).rawFilter)

        val snapCmd = TerminalParser.parse("snapshot/ create")
        assertTrue(snapCmd is TerminalCommand.Snapshot)
        assertEquals("create", (snapCmd as TerminalCommand.Snapshot).action)
    }

    @Test
    fun testSlashPreNormalizationWithQuotes() {
        val cmd = TerminalParser.parse("settle/\"babul mama\"")
        assertTrue(cmd is TerminalCommand.LedgerSettle)
        assertEquals("babul mama", (cmd as TerminalCommand.LedgerSettle).contactToken)
    }

    @Test
    fun testUnclosedQuotesDoesNotReturnEntireLineAsSingleToken() {
        val tokens = TerminalParser.tokenize("xfer/ 200 bkash cash \"unclosed quote")
        assertEquals(listOf("xfer/", "200", "bkash", "cash", "unclosed quote"), tokens)
    }

    @Test
    fun testChargeCommand() {
        val cmd1 = TerminalParser.parse("charge/ bkash *1.85%")
        assertTrue(cmd1 is TerminalCommand.Charge)
        val c1 = cmd1 as TerminalCommand.Charge
        assertEquals("bkash", c1.accountToken)
        assertEquals("*1.85%", c1.rateToken)
        assertEquals("set", c1.action)

        val cmd2 = TerminalParser.parse("charge/ nagad 1.5%")
        assertTrue(cmd2 is TerminalCommand.Charge)
        val c2 = cmd2 as TerminalCommand.Charge
        assertEquals("nagad", c2.accountToken)
        assertEquals("1.5%", c2.rateToken)
        assertEquals("set", c2.action)

        val cmdAll = TerminalParser.parse("charge/ all")
        assertTrue(cmdAll is TerminalCommand.Charge)
        assertEquals("all", (cmdAll as TerminalCommand.Charge).action)

        val cmdGet = TerminalParser.parse("charge/ bkash")
        assertTrue(cmdGet is TerminalCommand.Charge)
        assertEquals("get", (cmdGet as TerminalCommand.Charge).action)
    }

    @Test
    fun testCashoutCommand() {
        val cmd1 = TerminalParser.parse("cashout/ 1000 bkash")
        assertTrue(cmd1 is TerminalCommand.Cashout)
        val co1 = cmd1 as TerminalCommand.Cashout
        assertEquals(Money(100000L), co1.amount)
        assertEquals("bkash", co1.sourceToken)
        assertEquals("Cash", co1.targetToken)

        val cmd2 = TerminalParser.parse("cashout/ 500 nagad Cash")
        assertTrue(cmd2 is TerminalCommand.Cashout)
        val co2 = cmd2 as TerminalCommand.Cashout
        assertEquals(Money(50000L), co2.amount)
        assertEquals("nagad", co2.sourceToken)
        assertEquals("Cash", co2.targetToken)
    }

    @Test
    fun testVerifyAndHistoryCommands() {
        val vCmd = TerminalParser.parse("verify/")
        assertTrue(vCmd is TerminalCommand.Verify)
        assertEquals("images", (vCmd as TerminalCommand.Verify).target)

        val hCmd = TerminalParser.parse("history/")
        assertTrue(hCmd is TerminalCommand.History)
        assertEquals(20, (hCmd as TerminalCommand.History).count)
    }

    @Test
    fun testDueCommandMultiWord() {
        val cmd = TerminalParser.parse("due/ out 500 babul mama grocery credit")
        assertTrue(cmd is TerminalCommand.LedgerDue)
        val due = cmd as TerminalCommand.LedgerDue
        assertEquals(Money(50000L), due.amount)
        assertEquals(true, due.isOut)
        assertEquals("babul mama grocery credit", due.contactToken)
    }
}
