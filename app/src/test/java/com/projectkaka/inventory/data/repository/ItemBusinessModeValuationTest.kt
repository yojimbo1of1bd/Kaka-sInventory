package com.projectkaka.inventory.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projectkaka.inventory.data.local.AppDatabase
import com.projectkaka.inventory.data.local.DatabaseSeeder
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.data.local.entity.ItemStatus
import com.projectkaka.inventory.data.settings.UserPreferences
import com.projectkaka.inventory.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemBusinessModeValuationTest {
    private var _db: AppDatabase? = null
    private val db get() = _db!!
    private lateinit var financeRepo: FinanceRepositoryImpl
    private lateinit var preferences: UserPreferences

    @Before
    fun setup() {
        _db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        preferences = UserPreferences(ApplicationProvider.getApplicationContext())

        runBlocking {
            DatabaseSeeder.seedV6(db.openHelper.writableDatabase)
            // Seed business/liquidation accounts
            val now = System.currentTimeMillis()
            db.financeDao().insertAccount(AccountEntity(name = "Inventory", type = AccountType.ASSET, createdAt = now))
            db.financeDao().insertAccount(AccountEntity(name = "Capital", type = AccountType.CAPITAL, createdAt = now))
            db.financeDao().insertAccount(AccountEntity(name = "Sales Revenue", type = AccountType.REVENUE, createdAt = now))
            db.financeDao().insertAccount(AccountEntity(name = "COGS", type = AccountType.EXPENSE, createdAt = now))
            db.financeDao().insertAccount(AccountEntity(name = "Charity Expense", type = AccountType.EXPENSE, createdAt = now))
            db.financeDao().insertAccount(AccountEntity(name = "Loss", type = AccountType.EXPENSE, createdAt = now))
        }

        financeRepo = FinanceRepositoryImpl(db, db.financeDao(), db.journalDao())
    }

    @After
    fun teardown() {
        _db?.close()
    }

    @Test
    fun `personal mode (businessMode = false) does not create Inventory or Capital postings on save`() = runBlocking {
        preferences.setBusinessMode(false)
        val invRepo = InventoryRepositoryImpl(
            itemDao = db.itemDao(),
            careTaskDao = db.careTaskDao(),
            financeRepository = financeRepo,
            basketDao = db.basketDao(),
            preferences = preferences
        )

        val item = ItemEntity(
            name = "Personal Backpack",
            category = "Gear",
            locationTag = "Room",
            estimatedValue = Money(200000), // ৳2,000.00
            imagePath = "",
            isDraft = false,
            status = ItemStatus.ACTIVE,
            dateAdded = System.currentTimeMillis()
        )

        val savedId = invRepo.saveItem(item)
        assertTrue(savedId > 0)

        // Verify zero postings were written to Inventory or Capital
        val invAcc = db.financeDao().getAccountByName("Inventory")!!
        val invPostings = db.journalDao().getAccountStatement(invAcc.id).first()
        assertEquals(0, invPostings.size)

        val capitalAcc = db.financeDao().getAccountByName("Capital")!!
        val capitalPostings = db.journalDao().getAccountStatement(capitalAcc.id).first()
        assertEquals(0, capitalPostings.size)
    }

    @Test
    fun `business mode (businessMode = true) creates Inventory and Capital postings on save`() = runBlocking {
        preferences.setBusinessMode(true)
        val invRepo = InventoryRepositoryImpl(
            itemDao = db.itemDao(),
            careTaskDao = db.careTaskDao(),
            financeRepository = financeRepo,
            basketDao = db.basketDao(),
            preferences = preferences
        )

        val item = ItemEntity(
            name = "Stock Backpack",
            category = "Merchandise",
            locationTag = "Warehouse",
            estimatedValue = Money(200000), // ৳2,000.00
            imagePath = "",
            isDraft = false,
            status = ItemStatus.ACTIVE,
            dateAdded = System.currentTimeMillis()
        )

        val savedId = invRepo.saveItem(item)
        assertTrue(savedId > 0)

        val invAcc = db.financeDao().getAccountByName("Inventory")!!
        val invPostings = db.journalDao().getAccountStatement(invAcc.id).first()
        assertEquals(1, invPostings.size)
        assertEquals(200000L, invPostings[0].amount.minorUnits)
    }

    @Test
    fun `personal mode selling item only logs cash and sales without COGS or Inventory deduction`() = runBlocking {
        preferences.setBusinessMode(false)
        val invRepo = InventoryRepositoryImpl(
            itemDao = db.itemDao(),
            careTaskDao = db.careTaskDao(),
            financeRepository = financeRepo,
            basketDao = db.basketDao(),
            preferences = preferences
        )

        val item = ItemEntity(
            name = "Old Monitor",
            category = "Electronics",
            locationTag = "Desk",
            estimatedValue = Money(500000),
            imagePath = "",
            isDraft = false,
            status = ItemStatus.ACTIVE,
            dateAdded = System.currentTimeMillis()
        )
        val itemId = invRepo.saveItem(item).toInt()

        // Liquidate as SOLD for ৳3,000 cash
        invRepo.liquidateItem(itemId, ItemStatus.SOLD, Money(300000))

        // Cash account should receive posting
        val cashAcc = db.financeDao().getAccountByName("Cash")!!
        val cashPostings = db.journalDao().getAccountStatement(cashAcc.id).first()
        assertEquals(1, cashPostings.size)
        assertEquals(300000L, cashPostings[0].amount.minorUnits)

        // COGS and Inventory should have NO postings
        val cogsAcc = db.financeDao().getAccountByName("COGS")!!
        val cogsPostings = db.journalDao().getAccountStatement(cogsAcc.id).first()
        assertEquals(0, cogsPostings.size)

        val invAcc = db.financeDao().getAccountByName("Inventory")!!
        val invPostings = db.journalDao().getAccountStatement(invAcc.id).first()
        assertEquals(0, invPostings.size)
    }

    @Test
    fun `personal mode donating or trashing item creates no journal entries`() = runBlocking {
        preferences.setBusinessMode(false)
        val invRepo = InventoryRepositoryImpl(
            itemDao = db.itemDao(),
            careTaskDao = db.careTaskDao(),
            financeRepository = financeRepo,
            basketDao = db.basketDao(),
            preferences = preferences
        )

        val item = ItemEntity(
            name = "Broken Mug",
            category = "Kitchen",
            locationTag = "Shelf",
            estimatedValue = Money(20000),
            imagePath = "",
            isDraft = false,
            status = ItemStatus.ACTIVE,
            dateAdded = System.currentTimeMillis()
        )
        val itemId = invRepo.saveItem(item).toInt()

        invRepo.liquidateItem(itemId, ItemStatus.TRASHED, Money(0))

        val lossAcc = db.financeDao().getAccountByName("Loss")!!
        val lossPostings = db.journalDao().getAccountStatement(lossAcc.id).first()
        assertEquals(0, lossPostings.size)

        val updated = invRepo.getItemById(itemId)!!
        assertEquals(ItemStatus.TRASHED, updated.status)
    }
}
