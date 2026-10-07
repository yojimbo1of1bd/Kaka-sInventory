package com.projectkaka.inventory.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projectkaka.inventory.data.local.dao.JournalDao
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.data.local.entity.JournalEntryEntity
import com.projectkaka.inventory.data.local.entity.JournalStatus
import com.projectkaka.inventory.data.local.entity.PostingEntity
import com.projectkaka.inventory.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalDaoTest {
    private var _db: AppDatabase? = null
    private val db get() = _db!!
    private lateinit var journalDao: JournalDao

    @Before
    fun setup() {
        _db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                AppDatabase.createTriggers(db)
            }
        }).allowMainThreadQueries().build()
        journalDao = db.journalDao()
    }

    @After
    fun teardown() {
        _db?.close()
    }

    @Test
    fun `test complete lifecycle of a journal entry`() = runBlocking {
        // 1. Setup accounts
        val assetAccountId = db.financeDao().insertAccount(
            AccountEntity(name = "Cash", type = AccountType.ASSET)
        ).toInt()
        val expenseAccountId = db.financeDao().insertAccount(
            AccountEntity(name = "Food", type = AccountType.EXPENSE)
        ).toInt()

        // 2. Create entry (PENDING)
        val entryId = journalDao.insertJournalEntry(
            JournalEntryEntity(
                description = "Lunch",
                status = JournalStatus.DRAFT
            )
        ).toInt()

        // 3. Insert unbalanced postings (should succeed because status is PENDING)
        journalDao.insertPosting(
            PostingEntity(
                journalEntryId = entryId,
                accountId = assetAccountId,
                amount = Money(1500), // $15.00
                isCredit = true // credit asset (decrease)
            )
        )
        
        journalDao.insertPosting(
            PostingEntity(
                journalEntryId = entryId,
                accountId = expenseAccountId,
                amount = Money(1500),
                isCredit = false // debit expense (increase)
            )
        )

        // 4. Commit entry
        journalDao.commitJournalEntry(entryId)

        // 5. Verify balances
        val assetBalance = journalDao.getAccountBalance(assetAccountId)
        assertEquals(-1500L, assetBalance) // Credit decreases asset

        val expenseBalance = journalDao.getAccountBalance(expenseAccountId)
        assertEquals(1500L, expenseBalance) // Debit increases expense

        // 6. Verify account statement (list of postings)
        val assetStatement = journalDao.getAccountStatement(assetAccountId).first()
        assertEquals(1, assetStatement.size)
        assertEquals(1500L, assetStatement[0].amount.minorUnits)
        assertEquals(true, assetStatement[0].isCredit)
    }
}
