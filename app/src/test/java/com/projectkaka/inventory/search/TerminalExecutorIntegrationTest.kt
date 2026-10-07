package com.projectkaka.inventory.search

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projectkaka.inventory.data.local.AppDatabase
import com.projectkaka.inventory.data.local.DatabaseSeeder
import com.projectkaka.inventory.data.repository.FinanceRepositoryImpl
import com.projectkaka.inventory.data.settings.UserPreferences
import com.projectkaka.inventory.model.Money
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalExecutorIntegrationTest {

    private var _db: AppDatabase? = null
    private val db get() = _db!!
    private lateinit var financeRepo: FinanceRepositoryImpl
    private lateinit var preferences: UserPreferences
    private lateinit var executor: TerminalExecutor
    private lateinit var context: android.content.Context

    @Before
    fun setup() {
        _db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        DatabaseSeeder.seedV6(db.openHelper.writableDatabase)
        financeRepo = FinanceRepositoryImpl(db, db.financeDao(), db.journalDao())
        context = ApplicationProvider.getApplicationContext<android.content.Context>()
        preferences = UserPreferences(context)
        executor = TerminalExecutor(financeRepo, context, preferences)
    }

    @After
    fun teardown() {
        _db?.close()
    }

    @Test
    fun testInitChargeAndCashoutFlow() = runBlocking {
        // 1. Initialize bKash with 5,000 Taka
        val initResult = executor.execute("init/ 5000 bkash")
        assertTrue(initResult is TerminalResult.Success)

        val bkash = financeRepo.resolveAccountsExact("bkash").firstOrNull()
        org.junit.Assert.assertNotNull(bkash)
        assertEquals(Money(500000L), bkash!!.balance)

        // 2. Set cashout charge for bKash to 1.85%
        val chargeResult = executor.execute("charge/ bkash *1.85%")
        assertTrue(chargeResult is TerminalResult.Success)

        // Verify charge rate is saved in UserPreferences
        val savedRate = preferences.getAccountChargeRate("bkash")
        org.junit.Assert.assertNotNull(savedRate)
        assertEquals(0.0185, savedRate!!, 0.0001)

        // 3. Perform cashout: cashout/ 1000 bkash
        // Handing cash with 1,000 Taka, fee = 1000 * 1.85% = 18.50 Taka = 1850 minor units
        // Total deducted from bKash = 1018.50 Taka = 101850 minor units
        // Cash credited = 1000.00 Taka = 100000 minor units
        val cashoutResult = executor.execute("cashout/ 1000 bkash")
        assertTrue(cashoutResult is TerminalResult.Success)
        val msg = (cashoutResult as TerminalResult.Success).message
        assertTrue(msg.contains("Cashed out") || msg.contains("Cashout"))

        // Check bKash balance: 5000 - 1018.50 = 3981.50 Taka = 398150 minor units
        val finalBkash = financeRepo.resolveAccountsExact("bkash").first()
        assertEquals(398150L, finalBkash.balance.minorUnits)

        // Check Cash balance: 0 + 1000 = 1000.00 Taka = 100000 minor units
        val finalCash = financeRepo.resolveAccountsExact("Cash").first()
        assertEquals(100000L, finalCash.balance.minorUnits)

        // 4. Verify Income Statement (P&L report):
        // Cashout fee is charged to Capital equity, NOT operating expenses!
        val pnlResult = executor.execute("report/ pnl")
        assertTrue(pnlResult is TerminalResult.Success)
        val pnlMsg = (pnlResult as TerminalResult.Success).message
        assertTrue("Net Income should be 0.00, got: $pnlMsg", pnlMsg.contains("0.00"))

        // 5. Verify Balance Sheet:
        // Assets = bKash (3981.50) + Cash (1000.00) = 4981.50
        // Capital = 5000 - 18.50 fee = 4981.50
        // Assets == Liabilities (0) + Equity (4981.50)
        val bsResult = executor.execute("report/ bs")
        assertTrue(bsResult is TerminalResult.Success)
        val bsMsg = (bsResult as TerminalResult.Success).message
        assertTrue("Balance sheet should balance at 4,981.50, got: $bsMsg", bsMsg.contains("4,981.50") || bsMsg.contains("4981.50"))
    }

    @Test
    fun testDoubleEntryFinancialCommandExpenseAndIncome() = runBlocking {
        executor.execute("init/ 1000 bkash")
        
        // 1. Expense: Debit Operating Expenses (+120), Credit bKash (-120)
        val expRes = executor.execute("f/ -120 exp bkash \"For lunch\"")
        assertTrue(expRes is TerminalResult.Success)
        val expMsg = (expRes as TerminalResult.Success).message
        assertTrue(expMsg.contains("DR: Operating Expenses") || expMsg.contains("Expenses"))
        assertTrue(expMsg.contains("CR: bKash"))

        val bkashAfterExp = financeRepo.resolveAccountsExact("bkash").first()
        assertEquals(88000L, bkashAfterExp.balance.minorUnits)

        // 2. Income: Debit bKash (+5050), Credit Salary (+5050)
        val incRes = executor.execute("f/ +5050 bkash salary \"Father sent money\"")
        assertTrue(incRes is TerminalResult.Success)
        val incMsg = (incRes as TerminalResult.Success).message
        assertTrue(incMsg.contains("DR: bKash"))
        assertTrue(incMsg.contains("CR: Salary") || incMsg.contains("CR: Sales Revenue"))

        val bkashAfterInc = financeRepo.resolveAccountsExact("bkash").first()
        assertEquals(593000L, bkashAfterInc.balance.minorUnits)
    }

    @Test
    fun testManAndHandbookCommands() = runBlocking {
        val helpRes = executor.execute("help/ ?")
        assertTrue(helpRes is TerminalResult.Success)
        val helpMsg = (helpRes as TerminalResult.Success).message
        assertTrue(helpMsg.contains("Handbook of Debits and Credits") || helpMsg.contains("DEBITS & CREDITS"))
        assertTrue(helpMsg.contains("Assets + Expenses = Liabilities + Equity + Revenue"))

        val manRes = executor.execute("man f/")
        assertTrue(manRes is TerminalResult.Success)
        val manMsg = (manRes as TerminalResult.Success).message
        assertTrue(manMsg.contains("f/ - Record a rigorous double-entry financial transaction"))
        assertTrue(manMsg.contains("THE DEBIT ACCOUNT"))
    }

    @Test
    fun testFifoDebtSettlementMultiLiability() = runBlocking {
        executor.execute("init/ 5000 Cash")
        
        // Log two liabilities for Babul Mama
        val due1 = executor.execute("due/ out 50 \"babul mama\" \"First debt\"")
        assertTrue(due1 is TerminalResult.Success)
        val due2 = executor.execute("due/ out 70 \"babul mama\" \"Second debt\"")
        assertTrue(due2 is TerminalResult.Success)

        // Partial settle: pay 80 Taka (clears debt 1 of 50, and 30 from debt 2, leaving 40 open)
        val settleRes = executor.execute("settle/ \"babul mama\" 80 Cash")
        assertTrue(settleRes is TerminalResult.Success)
        val settleMsg = (settleRes as TerminalResult.Success).message
        assertTrue(settleMsg.contains("Settled 2 liability entries") || settleMsg.contains("80.00"))

        val remainingEntries = financeRepo.getUnsettledEntriesForExactContact("babul mama")
        assertEquals(1, remainingEntries.size)
        assertEquals(4000L, remainingEntries[0].amount.minorUnits)
    }

    @Test
    fun testTradeoffLogCommand() = runBlocking {
        executor.execute("init/ 1000 bkash")
        executor.execute("f/ -120 exp bkash \"For lunch\"")

        val logRes = executor.execute("log/ all")
        assertTrue(logRes is TerminalResult.Success)
        val logMsg = (logRes as TerminalResult.Success).message
        assertTrue(logMsg.contains("FINANCIAL TRADEOFF AUDIT LOG"))
        assertTrue(logMsg.contains("For lunch") || logMsg.contains("120.00"))
    }

    @Test
    fun testSnapshotCreateAndVerifyCycle() = runBlocking {
        // Setup a dummy image in the test context image dir
        val imgDir = com.projectkaka.inventory.data.local.LocalImageStore.imageDir(context)
        val dummyFile = java.io.File(imgDir, "test_camera_photo.webp")
        dummyFile.writeBytes(ByteArray(1024) { it.toByte() })

        // 1. Snapshot create
        val createRes = executor.execute("snapshot/ create")
        assertTrue(createRes is TerminalResult.Success)
        val createMsg = (createRes as TerminalResult.Success).message
        assertTrue(createMsg.contains("Cryptographic Baseline Snapshot Created"))
        assertTrue(createMsg.contains("test_camera_photo.webp"))
        assertTrue(createMsg.contains("sha256:"))

        // 2. Snapshot verify (should be intact)
        val verifyRes = executor.execute("snapshot/ verify")
        assertTrue(verifyRes is TerminalResult.Success)
        val verifyMsg = (verifyRes as TerminalResult.Success).message
        assertTrue(verifyMsg.contains("Cryptographic Audit Passed"))
        assertTrue(verifyMsg.contains("[INTACT]"))

        // 3. Verify single item or file
        val fileVerifyRes = executor.execute("verify/ test_camera_photo")
        assertTrue(fileVerifyRes is TerminalResult.Success)
        val fileMsg = (fileVerifyRes as TerminalResult.Success).message
        assertTrue(fileMsg.contains("Verified File 'test_camera_photo.webp'"))
        assertTrue(fileMsg.contains("INTACT"))

        // 4. Simulate tampering / file alteration
        dummyFile.writeBytes(ByteArray(1024) { (it + 1).toByte() })
        val tamperedRes = executor.execute("snapshot/ verify")
        assertTrue(tamperedRes is TerminalResult.Failure)
        val tamperMsg = (tamperedRes as TerminalResult.Failure).reason
        assertTrue(tamperMsg.contains("Integrity Alert Detected"))
        assertTrue(tamperMsg.contains("HASH MISMATCH"))
    }
}
