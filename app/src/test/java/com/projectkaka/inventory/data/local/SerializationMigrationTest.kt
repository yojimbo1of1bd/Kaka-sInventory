package com.projectkaka.inventory.data.local

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

        val transactionCursor = db.query(androidx.sqlite.db.SimpleSQLiteQuery("SELECT amount FROM postings LIMIT 1"), null)
        transactionCursor.moveToFirst()
        assertEquals(5075L, transactionCursor.getLong(0))
        transactionCursor.close()

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

        val transactionCursor = db.query(androidx.sqlite.db.SimpleSQLiteQuery("SELECT amount FROM postings LIMIT 1"), null)
        transactionCursor.moveToFirst()
        assertEquals(5075L, transactionCursor.getLong(0))
        transactionCursor.close()

        val ledger = db.financeDao().getAllLedgerEntriesExport().first()
        assertEquals(2510L, ledger.amount.minorUnits)
        assertEquals(1, ledger.accountId)
        assertEquals(1, ledger.linkedTransactionId)
    }

    @Test
    fun testImageRoundTrip() = runBlocking {
        val v3Json = """
        {
            "version": 3,
            "exportedAt": 1690000000000,
            "itemCount": 1,
            "items": [
                {
                    "id": 1,
                    "name": "Item with Image",
                    "category": "Test",
                    "locationTag": "Home",
                    "estimatedValue": 1000,
                    "status": "ACTIVE",
                    "isDraft": false,
                    "dateAdded": 1690000000000,
                    "imageName": "test_image.webp"
                }
            ]
        }
        """.trimIndent()

        val tempZip = File(context.cacheDir, "backup_with_image.kaka")
        ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(v3Json.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            
            zos.putNextEntry(ZipEntry("images/test_image.webp"))
            zos.write("fake image content".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val uri = Uri.fromFile(tempZip)
        val result = ImportReader.restoreKakaZip(context, uri, db)

        assertEquals(1, result.itemsRestored)

        val item = db.itemDao().getAllItemsSnapshot().first()
        val imageDir = LocalImageStore.imageDir(context)
        val expectedPath = File(imageDir, "test_image.webp").absolutePath

        assertEquals(expectedPath, item.imagePath)

        val restoredFile = File(expectedPath)
        org.junit.Assert.assertTrue("Restored image file should exist", restoredFile.exists())
        assertEquals("fake image content", restoredFile.readText())
    }

    @Test
    fun testV4DocumentVaultRoundTrip() = runBlocking {
        val v4Json = """
        {
            "version": 4,
            "exportedAt": 1690000000000,
            "itemCount": 0,
            "documentCount": 1,
            "items": [],
            "documents": [
                {
                    "id": 1,
                    "title": "Prescription - Dr Smith",
                    "docType": "PRESCRIPTION",
                    "pageCount": 2,
                    "coverImageName": "presc_p1.webp",
                    "notes": "Refill in 30 days",
                    "issueDate": 1690000000000,
                    "expiryDate": 1692592000000,
                    "createdAt": 1690000000000,
                    "isArchived": false,
                    "pages": [
                        {
                            "id": 10,
                            "pageNumber": 1,
                            "imageName": "presc_p1.webp",
                            "pageNote": "Front page with dosage",
                            "createdAt": 1690000000000
                        },
                        {
                            "id": 11,
                            "pageNumber": 2,
                            "imageName": "presc_p2.webp",
                            "pageNote": "Back page with doctor stamp",
                            "createdAt": 1690000000000
                        }
                    ]
                }
            ]
        }
        """.trimIndent()

        val tempZip = File(context.cacheDir, "backup_v4_docs.kaka")
        ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(v4Json.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            
            zos.putNextEntry(ZipEntry("doc_images/presc_p1.webp"))
            zos.write("page 1 crisp doc image content".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("doc_images/presc_p2.webp"))
            zos.write("page 2 crisp doc image content".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val uri = Uri.fromFile(tempZip)
        val result = ImportReader.restoreKakaZip(context, uri, db)

        assertEquals(0, result.itemsRestored)
        assertEquals(1, result.documentsRestored)

        val docs = db.documentDao().getAllDocumentsWithPagesSnapshot()
        assertEquals(1, docs.size)

        val docWithPages = docs.first()
        assertEquals("Prescription - Dr Smith", docWithPages.document.title)
        assertEquals("PRESCRIPTION", docWithPages.document.docType)
        assertEquals(2, docWithPages.document.pageCount)
        assertEquals(2, docWithPages.pages.size)

        val docDir = DocumentImageStore.docDir(context)
        val expectedP1 = File(docDir, "presc_p1.webp").absolutePath
        val expectedP2 = File(docDir, "presc_p2.webp").absolutePath

        val p1 = docWithPages.sortedPages[0]
        val p2 = docWithPages.sortedPages[1]

        assertEquals(1, p1.pageNumber)
        assertEquals(expectedP1, p1.imagePath)
        assertEquals(2, p2.pageNumber)
        assertEquals(expectedP2, p2.imagePath)

        val f1 = File(expectedP1)
        val f2 = File(expectedP2)
        org.junit.Assert.assertTrue("Page 1 file should exist", f1.exists())
        org.junit.Assert.assertTrue("Page 2 file should exist", f2.exists())
        assertEquals("page 1 crisp doc image content", f1.readText())
        assertEquals("page 2 crisp doc image content", f2.readText())
    }

    @Test
    fun testBalanceMismatchInspectionAndSkipFinance() = runBlocking {
        val corruptedJson = """
        {
            "version": 4,
            "exportedAt": 1690000000000,
            "items": [
                {
                    "id": 1,
                    "name": "Vintage Camera",
                    "category": "Electronics",
                    "locationTag": "Desk",
                    "estimatedValue": 35000,
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
                        "name": "Bkash",
                        "type": "CASH",
                        "openingBalance": 10000,
                        "balance": 20000,
                        "isActive": true
                    }
                ],
                "transactions": [
                    {
                        "id": 1,
                        "amount": -500,
                        "accountId": 1,
                        "isCredit": false,
                        "timestamp": 1690000000000
                    }
                ]
            }
        }
        """.trimIndent()

        val tempZip = File(context.cacheDir, "mismatched_backup.kaka")
        java.util.zip.ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("data.json"))
            zos.write(corruptedJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val uri = Uri.fromFile(tempZip)
        val inspection = ImportReader.inspectKakaZip(context, uri)
        assertTrue(inspection.hasBalanceMismatch)
        assertEquals(1, inspection.itemCount)

        // Attempting normal restore without skipFinance throws BalanceMismatchException
        var threwMismatch = false
        try {
            ImportReader.restoreKakaZip(context, uri, db, skipFinance = false)
        } catch (e: BalanceMismatchException) {
            threwMismatch = true
        }
        assertTrue("Expected BalanceMismatchException", threwMismatch)

        // Restoring with skipFinance = true updates inventory while declining finance
        val result = ImportReader.restoreKakaZip(context, uri, db, skipFinance = true)
        assertEquals(1, result.itemsRestored)
        assertEquals(0, result.accountsRestored)
        assertEquals(0, result.transactionsRestored)

        val items = db.itemDao().getAllItemsSnapshot()
        assertEquals(1, items.size)
        assertEquals("Vintage Camera", items.first().name)
    }
}
