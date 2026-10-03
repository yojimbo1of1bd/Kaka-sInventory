package com.projectkaka.inventory.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2() {
        // v1 has `items` and `care_tasks`.
        var db = helper.createDatabase(TEST_DB, 1)

        val itemValues = ContentValues().apply {
            put("id", 1)
            put("name", "Test Item")
            put("category", "Test")
            put("location_tag", "Home")
            put("estimated_value", 100)
            put("image_path", "test.jpg")
            put("is_draft", 0)
            put("status", "ACTIVE")
            put("date_added", 1000L)
        }
        db.insert("items", SQLiteDatabase.CONFLICT_REPLACE, itemValues)
        db.close()

        // Migrate to v2 (adds finance tables)
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.Companion.MIGRATION_1_2)

        // Verify accounts table exists and has default trigger (wait, defaults are in v5_6, v1_2 just creates the tables)
        val cursor = db.query("SELECT * FROM financial_categories")
        assertTrue(cursor.count == 0)
        cursor.close()
        db.close()
    }

    @Test
    fun migrate2To3() {
        var db = helper.createDatabase(TEST_DB, 2)
        
        // Insert v2 records. In v2, amount/balances are REAL (Double)
        db.execSQL("INSERT INTO accounts (id, name, type, aliases, opening_balance, is_active, created_at) VALUES (1, 'Cash', 'CASH', '', 15.25, 1, 1000)")
        db.execSQL("INSERT INTO financial_categories (id, name, type, aliases, created_at) VALUES (1, 'Food', 'EXPENSE', '', 1000)")
        db.execSQL("INSERT INTO financial_transactions (id, amount, account_id, category_id, is_credit, note, timestamp) VALUES (1, 10.50, 1, 1, 0, 'Lunch', 1000)")
        db.execSQL("INSERT INTO items (id, name, category, location_tag, estimated_value, image_path, is_draft, status, date_added) VALUES (1, 'Item', 'Cat', 'Loc', 20.99, '', 0, 'ACTIVE', 1000)")
        db.execSQL("INSERT INTO ledger_entries (id, contact_name, contact_phone, amount, type, is_settled, note, due_date, created_at) VALUES (1, 'Alice', '', 5.10, 'PAYABLE', 0, '', NULL, 1000)")

        db.close()

        // Migrate to v3 (converts everything to minor units)
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.Companion.MIGRATION_2_3)

        // Check converted values
        var cursor = db.query("SELECT opening_balance FROM accounts WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(1525, cursor.getLong(0))
        cursor.close()

        cursor = db.query("SELECT amount, type, is_credit FROM financial_transactions WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(1050, cursor.getLong(0))
        assertEquals("EXPENSE", cursor.getString(1))
        assertEquals(0, cursor.getInt(2))
        cursor.close()

        cursor = db.query("SELECT estimated_value FROM items WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(2099, cursor.getLong(0))
        cursor.close()

        cursor = db.query("SELECT amount FROM ledger_entries WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(510, cursor.getLong(0))
        cursor.close()

        db.close()
    }

    @Test
    fun migrate3To4() {
        var db = helper.createDatabase(TEST_DB, 3)
        // v3 ledger_entries does not have account_id or linked_transaction_id
        db.execSQL("INSERT INTO ledger_entries (id, contact_name, contact_phone, amount, type, is_settled, note, due_date, created_at) VALUES (1, 'Alice', '', 500, 'PAYABLE', 0, '', NULL, 1000)")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.Companion.MIGRATION_3_4)
        val cursor = db.query("SELECT account_id, linked_transaction_id FROM ledger_entries WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.isNull(0))
        assertTrue(cursor.isNull(1))
        cursor.close()
        db.close()
    }

    @Test
    fun migrate4To5() {
        var db = helper.createDatabase(TEST_DB, 4)
        db.execSQL("INSERT INTO accounts (id, name, type, aliases, opening_balance, is_active, created_at) VALUES (1, 'Cash', 'CASH', '', 1000, 1, 1000)")
        db.execSQL("INSERT INTO financial_transactions (id, amount, account_id, type, is_credit, note, timestamp) VALUES (1, 500, 1, 'INCOME', 1, '', 1000)")
        db.execSQL("INSERT INTO financial_transactions (id, amount, account_id, type, is_credit, note, timestamp) VALUES (2, 200, 1, 'EXPENSE', 0, '', 1000)")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.Companion.MIGRATION_4_5)
        
        val cursor = db.query("SELECT balance_minor FROM accounts WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        // 1000 + 500 - 200 = 1300
        assertEquals(1300, cursor.getLong(0))
        cursor.close()
        db.close()
    }

    @Test
    fun migrate5To6() {
        var db = helper.createDatabase(TEST_DB, 5)
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.Companion.MIGRATION_5_6)
        
        val cursor = db.query("SELECT id FROM accounts WHERE name = 'Cash'")
        assertTrue(cursor.moveToFirst()) // should be seeded
        cursor.close()
        db.close()
    }

    @Test
    fun migrate6To7() {
        var db = helper.createDatabase(TEST_DB, 6)
        db.execSQL("INSERT INTO ledger_entries (id, contact_name, contact_phone, amount, type, is_settled, note, due_date, account_id, linked_transaction_id, created_at) VALUES (1, '  Alice Smith  ', '', 500, 'PAYABLE', 0, '', NULL, NULL, NULL, 1000)")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.Companion.MIGRATION_6_7)
        
        val cursor = db.query("SELECT aliases FROM ledger_entries WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals("alice smith", cursor.getString(0))
        cursor.close()
        db.close()
    }

    @Test
    fun migrate10To11() {
        var db = helper.createDatabase(TEST_DB, 10)
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 11, true, AppDatabase.Companion.MIGRATION_10_11)

        val cursor = db.query("SELECT count(*) FROM baskets")
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0))
        cursor.close()

        val cursorItems = db.query("SELECT count(*) FROM basket_items")
        assertTrue(cursorItems.moveToFirst())
        assertEquals(0, cursorItems.getInt(0))
        cursorItems.close()

        db.close()
    }
}
