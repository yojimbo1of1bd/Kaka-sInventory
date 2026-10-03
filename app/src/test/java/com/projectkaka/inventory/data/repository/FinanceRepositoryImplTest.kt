package com.projectkaka.inventory.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projectkaka.inventory.data.local.AppDatabase
import com.projectkaka.inventory.data.local.DatabaseSeeder
import com.projectkaka.inventory.data.local.entity.TransactionEntity
import com.projectkaka.inventory.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceRepositoryImplTest {
    private var _db: AppDatabase? = null
    private val db get() = _db!!
    private lateinit var repository: FinanceRepositoryImpl

    @Before
    fun setup() {
        _db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        runBlocking {
            DatabaseSeeder.seedV6(db.openHelper.writableDatabase)
            db.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO accounts (name, type, aliases, opening_balance, balance_minor, is_active, created_at)
                SELECT 
                    name, 
                    CASE WHEN type = 'EXPENSE' THEN 'EXPENSE' ELSE 'REVENUE' END, 
                    aliases, 
                    0, 0, 1, created_at 
                FROM financial_categories
                WHERE name NOT IN (SELECT name FROM accounts)
                """.trimIndent()
            )
        }
        repository = FinanceRepositoryImpl(db, db.financeDao(), db.journalDao())
    }

    @After
    fun teardown() {
        _db?.close()
    }

    @Test
    fun testRecordAndGetTransaction() = runBlocking {
        val account = db.financeDao().getAccountByName("Cash")!!
        val category = db.financeDao().getAllCategories().first().find { it.name == "Food" }!!
        
        val transaction = TransactionEntity(
            accountId = account.id,
            amount = Money(5000), // 50.00
            note = "Lunch",
            timestamp = System.currentTimeMillis(),
            isCredit = false, // Expense decreases Cash
            categoryId = category.id
        )

        val transactionId = repository.recordTransaction(transaction)
        
        val transactions = repository.getTransactionsForAccount(account.id).first()
        assertEquals(1, transactions.size)
        assertEquals("Lunch", transactions[0].note)
        assertEquals(5000L, transactions[0].amount.minorUnits)
        
        // Ensure double entry actually affected balance
        val cashBalance = db.journalDao().getAccountBalance(account.id)
        assertEquals(-5000L, cashBalance) // Cash decreased by 5000
    }

    @Test
    fun testTransfer() = runBlocking {
        val cashAccount = db.financeDao().getAccountByName("Cash")!!
        val bankAccount = db.financeDao().getAccountByName("Bank")!!
        
        repository.transfer(
            fromAccountId = cashAccount.id,
            toAccountId = bankAccount.id,
            amount = Money(10000),
            note = "ATM Deposit",
            timestamp = System.currentTimeMillis()
        )
        
        val cashBalance = db.journalDao().getAccountBalance(cashAccount.id)
        val bankBalance = db.journalDao().getAccountBalance(bankAccount.id)
        
        assertEquals(-10000L, cashBalance)
        assertEquals(10000L, bankBalance)
    }
}
