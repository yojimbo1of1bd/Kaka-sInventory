package com.projectkaka.inventory.ui.settings

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projectkaka.inventory.data.local.AppDatabase
import com.projectkaka.inventory.data.local.DatabaseSeeder
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.data.repository.FinanceRepositoryImpl
import com.projectkaka.inventory.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountsManagerTest {
    private var _db: AppDatabase? = null
    private val db get() = _db!!
    private lateinit var financeRepo: FinanceRepositoryImpl

    @Before
    fun setup() {
        _db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        DatabaseSeeder.seedV6(db.openHelper.writableDatabase)
        financeRepo = FinanceRepositoryImpl(db, db.financeDao(), db.journalDao())
    }

    @After
    fun teardown() {
        _db?.close()
    }

    @Test
    fun testInsertAccountWithInitialBalance() = runBlocking {
        val acc = AccountEntity(
            name = "Babul Mama",
            type = AccountType.LIABILITY,
            openingBalance = Money(2300L) // 23 Taka
        )
        val id = financeRepo.insertAccount(acc)
        assertTrue(id > 0)

        val retrieved = db.financeDao().getAccountById(id.toInt())
        assertNotNull(retrieved)
        assertEquals("Babul Mama", retrieved!!.name)
        assertEquals(AccountType.LIABILITY, retrieved.type)
        assertEquals(Money(2300L), retrieved.openingBalance)
        assertEquals(Money(2300L), retrieved.balance)

        val list = financeRepo.observeAccountsWithCounts().first()
        assertTrue(list.any { it.account.name == "Babul Mama" })
    }
}
