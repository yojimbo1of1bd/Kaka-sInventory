package com.projectkaka.inventory.data.local

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class SerializationMigrationTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testV2ToV3Migration() = runBlocking {
        // Create a fake V2 backup
        val v2Json = """
        {
            "version": 2,
            "exportedAt": 1690000000000,
            "itemCount": 1,
            "items": [
                {
                    "id": 1,
                    "name": "Test Item",
                    "category": "Test",
                    "locationTag": "Home",
                    "estimatedValue": 150.50,
                    "status": "ACTIVE",
                    "isDraft": false,
                    "dateAdded": 1690000000000,
                    "imageName": ""
                }
            ],
            "finance": {
                "accounts": [
                    {
                        "id": 1,
                        "name": "Cash",
                        "type": "CASH",
                        "aliases": "",
                        "openingBalance": 100.25,
                        "isActive": true,
                        "createdAt": 1690000000000
                    }
                ],
                "categories": [
                    {
                        "id": 1,
                        "name": "Food",
                        "type": "EXPENSE",
                        "aliases": "",
                        "createdAt": 1690000000000
                    }
                ],
                "transactions": [
                    {
                        "id": 1,
                        "amount": 50.75,
                        "accountId": 1,
                        "categoryId": 1,
                        "isCredit": true,
                        "note": "",
                        "timestamp": 1690000000000
                    }
                ],
                "ledgerEntries": [
                    {
                        "id": 1,
                        "contactName": "Alice",
                        "contactPhone": "",
                        "amount": 25.10,
                        "type": "RECEIVABLE",
                        "isSettled": false,
                        "note": "",
                        "dueDate": null,
                        "createdAt": 1690000000000
                    }
                ]
            }
        }
        """.trimIndent()

        val tempZip = File(context.cacheDir, "backup.kaka")
        ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(v2Json.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val uri = Uri.fromFile(tempZip)
        val result = ImportReader.restoreKakaZip(context, uri, db)

        assertEquals(1, result.itemsRestored)
        assertEquals(1, result.accountsRestored)
        assertEquals(1, result.transactionsRestored)
        assertEquals(1, result.ledgerEntriesRestored)

        // Verify Money is correctly parsed (minor units = value * 100)
        val item = db.itemDao().getAllItemsSnapshot().first()
        assertEquals(15050L, item.estimatedValue.minorUnits)

        val account = db.financeDao().getActiveAccountsSnapshot().first()
        assertEquals(10025L, account.openingBalance.minorUnits)

        val transaction = db.financeDao().getAllTransactionsExport().first()
        assertEquals(5075L, transaction.amount.minorUnits)

        val ledger = db.financeDao().getAllLedgerEntriesExport().first()
        assertEquals(2510L, ledger.amount.minorUnits)
    }

    @Test
    fun testV3Restore() = runBlocking {
        // Create a fake V3 backup
        val v3Json = """
        {
            "version": 3,
            "exportedAt": 1690000000000,
            "itemCount": 1,
            "items": [
                {
                    "id": 1,
                    "name": "Test Item V3",
                    "category": "Test",
                    "locationTag": "Home",
                    "estimatedValue": 15050,
                    "status": "ACTIVE",
                    "isDraft": false,
                    "dateAdded": 1690000000000,
                    "imageName": ""
                }
            ],
            "finance": {
                "accounts": [
                    {
                        "id": 1,
                        "name": "Cash",
                        "type": "CASH",
                        "aliases": "",
                        "openingBalance": 10025,
                        "isActive": true,
                        "createdAt": 1690000000000
                    },
                    {
                        "id": 2,
                        "name": "Bank",
                        "type": "CASH",
                        "aliases": "",
                        "openingBalance": 50000,
                        "isActive": true,
                        "createdAt": 1690000000000
                    }
                ],
                "categories": [
                    {
                        "id": 1,
                        "name": "Food",
                        "type": "EXPENSE",
                        "aliases": "",
                        "createdAt": 1690000000000
                    }
                ],
                "transactions": [
                    {
                        "id": 1,
                        "amount": 5075,
                        "accountId": 1,
                        "categoryId": 1,
                        "transferId": "abc",
                        "counterAccountId": 2,
                        "type": "TRANSFER",
                        "isCredit": true,
                        "note": "",
                        "timestamp": 1690000000000
                    }
                ],
                "ledgerEntries": [
                    {
                        "id": 1,
                        "contactName": "Alice",
                        "contactPhone": "",
                        "amount": 2510,
                        "type": "RECEIVABLE",
                        "isSettled": false,
                        "note": "",
                        "dueDate": null,
                        "accountId": 1,
                        "linkedTransactionId": 1,
                        "createdAt": 1690000000000
                    }
                ]
            }
        }
        """.trimIndent()

        val tempZip = File(context.cacheDir, "backup_v3.kaka")
        ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(v3Json.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val uri = Uri.fromFile(tempZip)
        val result = ImportReader.restoreKakaZip(context, uri, db)

        assertEquals(1, result.itemsRestored)
        assertEquals(2, result.accountsRestored)
        assertEquals(1, result.transactionsRestored)
        assertEquals(1, result.ledgerEntriesRestored)

        // Verify Money is correctly parsed (minor units = value directly)
        val item = db.itemDao().getAllItemsSnapshot().first()
        assertEquals(15050L, item.estimatedValue.minorUnits)

        val account = db.financeDao().getActiveAccountsSnapshot().first()
        assertEquals(10025L, account.openingBalance.minorUnits)

        val transaction = db.financeDao().getAllTransactionsExport().first()
        assertEquals(5075L, transaction.amount.minorUnits)
        assertEquals("TRANSFER", transaction.type.name)
        assertEquals("abc", transaction.transferId)
        assertEquals(2, transaction.counterAccountId)

        val ledger = db.financeDao().getAllLedgerEntriesExport().first()
        assertEquals(2510L, ledger.amount.minorUnits)
        assertEquals(1, ledger.accountId)
        assertEquals(1, ledger.linkedTransactionId)
    }
}
