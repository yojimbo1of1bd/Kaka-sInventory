package com.projectkaka.inventory.data.local

import android.content.Context
import android.net.Uri
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.local.entity.CategoryType
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.data.local.entity.ItemStatus
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.LedgerType
import com.projectkaka.inventory.data.local.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

import androidx.room.withTransaction

data class RestoreResult(
    val itemsRestored: Int,
    val accountsRestored: Int,
    val categoriesRestored: Int,
    val transactionsRestored: Int,
    val ledgerEntriesRestored: Int
)

object ImportReader {

    /**
     * Reads a `.kaka` ZIP file from the given [uri].
     * Extracts images to the app's image folder.
     * Parses the `data.json` and inserts/merges all entities into the database.
     */
    suspend fun restoreKakaZip(context: Context, uri: Uri, db: AppDatabase): RestoreResult =
        withContext(Dispatchers.IO) {
            val imageDir = File(context.filesDir, "images")
            if (!imageDir.exists()) imageDir.mkdirs()

            var jsonData: String? = null

            // 1. Unzip the file
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "data.json") {
                            jsonData = zis.reader().readText()
                        } else if (entry.name.startsWith("images/") && !entry.isDirectory) {
                            val fileName = File(entry.name).name
                            val destFile = File(imageDir, fileName)
                            // Overwrite or create image
                            FileOutputStream(destFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (jsonData == null) {
                throw IllegalArgumentException("Invalid backup: data.json missing")
            }

            // 2. Parse JSON and insert into DB
            val root = JSONObject(jsonData!!)
            val itemsArr = root.optJSONArray("items")
            
            var itemsRestored = 0
            var accountsRestored = 0
            var categoriesRestored = 0
            var transactionsRestored = 0
            var ledgerEntriesRestored = 0

            db.withTransaction {
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        val itemObj = itemsArr.getJSONObject(i)
                        val id = itemObj.optInt("id", 0)
                        
                        val itemEntity = ItemEntity(
                            id = if (id > 0) id else 0,
                            name = itemObj.getString("name"),
                            category = itemObj.getString("category"),
                            locationTag = itemObj.getString("locationTag"),
                            estimatedValue = itemObj.getDouble("estimatedValue"),
                            status = ItemStatus.valueOf(itemObj.optString("status", "ACTIVE")),
                            isDraft = itemObj.getBoolean("isDraft"),
                            dateAdded = itemObj.getLong("dateAdded"),
                            imagePath = itemObj.optString("imageName").let { name ->
                                if (name.isNotBlank()) File(imageDir, name).absolutePath else ""
                            }
                        )
                        val newId = db.itemDao().insertItem(itemEntity)

                        val tasksArr = itemObj.optJSONArray("careTasks")
                        if (tasksArr != null) {
                            for (j in 0 until tasksArr.length()) {
                                val tObj = tasksArr.getJSONObject(j)
                                val task = CareTaskEntity(
                                    itemId = newId.toInt(),
                                    taskName = tObj.getString("taskName"),
                                    frequencyDays = tObj.getInt("frequencyDays"),
                                    lastCompletedDate = tObj.getLong("lastCompletedDate")
                                )
                                db.careTaskDao().insertTask(task)
                            }
                        }
                        itemsRestored++
                    }
                }

                val finObj = root.optJSONObject("finance")
                if (finObj != null) {
                    val accsArr = finObj.optJSONArray("accounts")
                    if (accsArr != null) {
                        for (i in 0 until accsArr.length()) {
                            val accObj = accsArr.getJSONObject(i)
                            val id = accObj.optInt("id", 0)
                            val acc = AccountEntity(
                                id = if (id > 0) id else 0,
                                name = accObj.getString("name"),
                                type = AccountType.valueOf(accObj.optString("type", "CASH")),
                                aliases = accObj.optString("aliases", ""),
                                openingBalance = accObj.optDouble("openingBalance", 0.0),
                                isActive = accObj.optBoolean("isActive", true),
                                createdAt = accObj.optLong("createdAt", System.currentTimeMillis())
                            )
                            db.financeDao().insertAccount(acc)
                            accountsRestored++
                        }
                    }

                    val catsArr = finObj.optJSONArray("categories")
                    if (catsArr != null) {
                        for (i in 0 until catsArr.length()) {
                            val cObj = catsArr.getJSONObject(i)
                            val id = cObj.optInt("id", 0)
                            val cat = FinancialCategoryEntity(
                                id = if (id > 0) id else 0,
                                name = cObj.getString("name"),
                                type = CategoryType.valueOf(cObj.optString("type", "EXPENSE")),
                                aliases = cObj.optString("aliases", ""),
                                createdAt = cObj.optLong("createdAt", System.currentTimeMillis())
                            )
                            db.financeDao().insertCategory(cat)
                            categoriesRestored++
                        }
                    }

                    val txsArr = finObj.optJSONArray("transactions")
                    if (txsArr != null) {
                        for (i in 0 until txsArr.length()) {
                            val tObj = txsArr.getJSONObject(i)
                            val id = tObj.optInt("id", 0)
                            val tx = TransactionEntity(
                                id = if (id > 0) id else 0,
                                amount = tObj.getDouble("amount"),
                                accountId = tObj.getInt("accountId"),
                                categoryId = tObj.getInt("categoryId"),
                                isCredit = tObj.getBoolean("isCredit"),
                                note = tObj.optString("note", ""),
                                timestamp = tObj.optLong("timestamp", System.currentTimeMillis())
                            )
                            db.financeDao().insertTransaction(tx)
                            transactionsRestored++
                        }
                    }

                    val ledArr = finObj.optJSONArray("ledgerEntries")
                    if (ledArr != null) {
                        for (i in 0 until ledArr.length()) {
                            val lObj = ledArr.getJSONObject(i)
                            val id = lObj.optInt("id", 0)
                            val le = LedgerEntryEntity(
                                id = if (id > 0) id else 0,
                                contactName = lObj.getString("contactName"),
                                contactPhone = lObj.optString("contactPhone", ""),
                                amount = lObj.getDouble("amount"),
                                type = LedgerType.valueOf(lObj.optString("type", "PAYABLE")),
                                isSettled = lObj.getBoolean("isSettled"),
                                note = lObj.optString("note", ""),
                                dueDate = if (lObj.isNull("dueDate")) null else lObj.getLong("dueDate"),
                                createdAt = lObj.optLong("createdAt", System.currentTimeMillis())
                            )
                            db.financeDao().insertLedgerEntry(le)
                            ledgerEntriesRestored++
                        }
                    }
                }
            }

            RestoreResult(
                itemsRestored = itemsRestored,
                accountsRestored = accountsRestored,
                categoriesRestored = categoriesRestored,
                transactionsRestored = transactionsRestored,
                ledgerEntriesRestored = ledgerEntriesRestored
            )
        }
}
