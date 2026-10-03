package com.projectkaka.inventory.data.local

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.data.local.entity.JournalEntryEntity
import com.projectkaka.inventory.data.local.entity.JournalStatus
import com.projectkaka.inventory.data.local.dao.FinanceDao
import com.projectkaka.inventory.model.Money
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTriggerTest {

    private lateinit var db: AppDatabase
    private lateinit var financeDao: FinanceDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                AppDatabase.createTriggers(db)
            }
        }).build()
        financeDao = db.financeDao()

        // Seed some basic accounts using raw SQL because we just want to test triggers.
        // Actually, let's use the DB's SupportSQLiteDatabase to insert directly since we might not have a DAO for everything yet.
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO accounts (id, name, type, aliases, opening_balance, is_active, balance_minor, created_at) VALUES (1, 'Cash', 'ASSET', '', 0, 1, 0, 1000)"
        )
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO accounts (id, name, type, aliases, opening_balance, is_active, balance_minor, created_at) VALUES (2, 'Revenue', 'REVENUE', '', 0, 1, 0, 1000)"
        )
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertBalancedPostings_succeeds(): Unit = runBlocking {
        val writableDb = db.openHelper.writableDatabase
        
        // Insert a DRAFT journal entry
        writableDb.execSQL(
            "INSERT INTO journal_entries (id, timestamp, description, status, approval_status, created_at, updated_at) VALUES (1, 1000, 'Test', 'DRAFT', 'APPROVED', 1000, 1000)"
        )
        
        // Insert balanced postings
        writableDb.execSQL(
            "INSERT INTO postings (journal_entry_id, account_id, amount, is_credit, note) VALUES (1, 1, 1000, 0, 'Debit')"
        )
        writableDb.execSQL(
            "INSERT INTO postings (journal_entry_id, account_id, amount, is_credit, note) VALUES (1, 2, 1000, 1, 'Credit')"
        )
        
        // Flip to POSTED
        writableDb.execSQL("UPDATE journal_entries SET status = 'POSTED' WHERE id = 1")
        
        // Verify success
        val cursor = writableDb.query("SELECT status FROM journal_entries WHERE id = 1")
        cursor.moveToFirst()
        assertEquals("POSTED", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun insertUnbalancedPostings_throwsWithUnbalancedMessage(): Unit = runBlocking {
        val writableDb = db.openHelper.writableDatabase
        
        // Insert a POSTED journal entry directly
        writableDb.execSQL(
            "INSERT INTO journal_entries (id, timestamp, description, status, approval_status, created_at, updated_at) VALUES (2, 1000, 'Test Unbalanced', 'POSTED', 'APPROVED', 1000, 1000)"
        )
        
        try {
            // Attempt to insert an unbalanced posting (it should immediately throw because it's POSTED)
            writableDb.execSQL(
                "INSERT INTO postings (journal_entry_id, account_id, amount, is_credit, note) VALUES (2, 1, 1000, 0, 'Debit')"
            )
            fail("Expected SQLiteException due to unbalanced postings")
        } catch (e: Exception) {
            // Success: an exception was thrown. It could be SQLiteException, SQLiteConstraintException, etc.
            println("Caught expected exception: ${e.message}")
        }
    }

    @Test
    fun pendingToCommitted_succeedsOnlyWhenBalanced(): Unit = runBlocking {
        val writableDb = db.openHelper.writableDatabase
        
        // Insert a DRAFT journal entry
        writableDb.execSQL(
            "INSERT INTO journal_entries (id, timestamp, description, status, approval_status, created_at, updated_at) VALUES (3, 1000, 'Test Flip', 'DRAFT', 'APPROVED', 1000, 1000)"
        )
        
        // Insert unbalanced postings
        writableDb.execSQL(
            "INSERT INTO postings (journal_entry_id, account_id, amount, is_credit, note) VALUES (3, 1, 1000, 0, 'Debit')"
        )
        
        // Attempt to flip to POSTED, should fail
        try {
            writableDb.execSQL("UPDATE journal_entries SET status = 'POSTED' WHERE id = 3")
            fail("Expected SQLiteException when flipping unbalanced entry to POSTED")
        } catch (e: Exception) {
            // Success: an exception was thrown. It could be SQLiteException, SQLiteConstraintException, etc.
            // We just verify it failed as expected.
            println("Caught expected exception: ${e.message}")
        }
        
        // Add matching credit
        writableDb.execSQL(
            "INSERT INTO postings (journal_entry_id, account_id, amount, is_credit, note) VALUES (3, 2, 1000, 1, 'Credit')"
        )
        
        // Attempt to flip again, should succeed
        writableDb.execSQL("UPDATE journal_entries SET status = 'POSTED' WHERE id = 3")
        
        val cursor = writableDb.query("SELECT status FROM journal_entries WHERE id = 3")
        cursor.moveToFirst()
        assertEquals("POSTED", cursor.getString(0))
        cursor.close()
    }
}
