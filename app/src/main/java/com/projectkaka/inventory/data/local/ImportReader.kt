package com.projectkaka.inventory.data.local

import android.content.Context
import android.net.Uri
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.local.entity.CategoryType
import com.projectkaka.inventory.data.local.entity.DocumentEntity
import com.projectkaka.inventory.data.local.entity.DocumentPageEntity
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
import com.projectkaka.inventory.util.ImageIntegrityManager

class BalanceMismatchException(
    message: String,
    val details: String? = null
) : Exception(message)

data class BackupInspection(
    val isValid: Boolean,
    val hasBalanceMismatch: Boolean,
    val mismatchReason: String? = null,
    val itemCount: Int = 0,
    val documentCount: Int = 0,
    val hasFinance: Boolean = false
)

data class RestoreResult(
    val itemsRestored: Int,
    val accountsRestored: Int,
    val categoriesRestored: Int,
    val transactionsRestored: Int,
    val ledgerEntriesRestored: Int,
    val documentsRestored: Int = 0
)

object ImportReader {

    /**
     * Inspects a `.kaka` backup without altering the database.
     * Identifies whether the backup contains balance discrepancies, clashes, or orphaned records.
     */
    suspend fun inspectKakaZip(context: Context, uri: Uri): BackupInspection =
        withContext(Dispatchers.IO) {
            var jsonData: String? = null
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ZipInputStream(stream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.replace('\\', '/')
                            if (entryName == "data.json" || entryName.endsWith("/data.json")) {
                                jsonData = zis.reader().readText()
                                break
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } catch (e: Exception) {
                return@withContext BackupInspection(
                    isValid = false,
                    hasBalanceMismatch = false,
                    mismatchReason = "Unable to read backup file: ${e.message}"
                )
            }

            if (jsonData == null) {
                return@withContext BackupInspection(
                    isValid = false,
                    hasBalanceMismatch = false,
                    mismatchReason = "Invalid backup: data.json missing"
                )
            }

            var itemCount = 0
            var docCount = 0

            try {
                val root = JSONObject(jsonData!!)
                val version = root.optInt("version", 2)
                val isV3 = version >= 3

                fun parseMoneyVal(obj: JSONObject, key: String): Long {
                    return if (isV3) {
                        obj.optLong(key, 0L)
                    } else {
                        val strValue = obj.optString(key, "0")
                        try {
                            com.projectkaka.inventory.model.Money.fromDecimalString(strValue).minorUnits
                        } catch (e: Exception) {
                            0L
                        }
                    }
                }

                val itemsArr = root.optJSONArray("items")
                val docsArr = root.optJSONArray("documents")
                itemCount = itemsArr?.length() ?: 0
                docCount = docsArr?.length() ?: 0

                val finObj = root.optJSONObject("finance")
                if (finObj == null) {
                    return@withContext BackupInspection(
                        isValid = true,
                        hasBalanceMismatch = false,
                        itemCount = itemCount,
                        documentCount = docCount,
                        hasFinance = false
                    )
                }

                // Check liability ledger clash flags
                if (finObj.optBoolean("liabilityLedgerSkippedDueToClash", false)) {
                    val reasons = finObj.optJSONArray("liabilityLedgerClashReasons")
                    val reasonStr = if (reasons != null && reasons.length() > 0) {
                        (0 until reasons.length()).joinToString("; ") { reasons.getString(it) }
                    } else "Liability ledger clash detected in backup"
                    return@withContext BackupInspection(
                        isValid = true,
                        hasBalanceMismatch = true,
                        mismatchReason = reasonStr,
                        itemCount = itemCount,
                        documentCount = docCount,
                        hasFinance = true
                    )
                }

                val accsArr = finObj.optJSONArray("accounts")
                val accountIds = mutableSetOf<Int>()
                val accountTypes = mutableMapOf<Int, String>()
                val accountOpening = mutableMapOf<Int, Long>()
                val accountStated = mutableMapOf<Int, Long?>()

                if (accsArr != null) {
                    for (i in 0 until accsArr.length()) {
                        val a = accsArr.getJSONObject(i)
                        val id = a.optInt("id", i + 1)
                        accountIds.add(id)
                        accountTypes[id] = a.optString("type", "CASH")
                        val opening = parseMoneyVal(a, "openingBalance")
                        accountOpening[id] = opening
                        val stated = if (a.has("balance")) parseMoneyVal(a, "balance")
                        else if (a.has("balance_minor")) a.optLong("balance_minor")
                        else null
                        accountStated[id] = stated
                    }
                }

                val txsArr = finObj.optJSONArray("transactions")
                val accountDeltas = mutableMapOf<Int, Long>()
                val txIds = mutableSetOf<Int>()

                if (txsArr != null) {
                    for (i in 0 until txsArr.length()) {
                        val t = txsArr.getJSONObject(i)
                        val tId = t.optInt("id", i + 1)
                        txIds.add(tId)
                        val amount = parseMoneyVal(t, "amount")
                        if (amount < 0) {
                            return@withContext BackupInspection(
                                isValid = true,
                                hasBalanceMismatch = true,
                                mismatchReason = "Transaction #$tId contains invalid negative amount: $amount",
                                itemCount = itemCount,
                                documentCount = docCount,
                                hasFinance = true
                            )
                        }

                        val accId = t.optInt("accountId", -1)
                        if (accId !in accountIds && accsArr != null && accsArr.length() > 0) {
                            return@withContext BackupInspection(
                                isValid = true,
                                hasBalanceMismatch = true,
                                mismatchReason = "Transaction #$tId references unknown account ID $accId",
                                itemCount = itemCount,
                                documentCount = docCount,
                                hasFinance = true
                            )
                        }

                        val isCredit = t.optBoolean("isCredit", false)
                        val type = accountTypes[accId] ?: "CASH"
                        val delta = if (type in listOf("CASH", "ASSET", "EXPENSE")) {
                            if (!isCredit) amount else -amount
                        } else {
                            if (isCredit) amount else -amount
                        }
                        accountDeltas[accId] = (accountDeltas[accId] ?: 0L) + delta
                    }
                }

                // Check stated balances against computed if stated was stored
                for ((accId, stated) in accountStated) {
                    if (stated != null) {
                        val opening = accountOpening[accId] ?: 0L
                        val computed = opening + (accountDeltas[accId] ?: 0L)
                        if (stated != computed) {
                            return@withContext BackupInspection(
                                isValid = true,
                                hasBalanceMismatch = true,
                                mismatchReason = "Account #$accId stored balance ($stated) does not match calculated balance ($computed)",
                                itemCount = itemCount,
                                documentCount = docCount,
                                hasFinance = true
                            )
                        }
                    }
                }

                // Check ledger entries
                val ledArr = finObj.optJSONArray("ledgerEntries")
                if (ledArr != null) {
                    for (i in 0 until ledArr.length()) {
                        val l = ledArr.getJSONObject(i)
                        val amt = parseMoneyVal(l, "amount")
                        if (amt < 0) {
                            return@withContext BackupInspection(
                                isValid = true,
                                hasBalanceMismatch = true,
                                mismatchReason = "Liability ledger entry contains invalid negative amount",
                                itemCount = itemCount,
                                documentCount = docCount,
                                hasFinance = true
                            )
                        }
                        val accId = if (l.isNull("accountId")) null else l.getInt("accountId")
                        if (accId != null && accId !in accountIds && accountIds.isNotEmpty()) {
                            return@withContext BackupInspection(
                                isValid = true,
                                hasBalanceMismatch = true,
                                mismatchReason = "Liability ledger references missing account #$accId",
                                itemCount = itemCount,
                                documentCount = docCount,
                                hasFinance = true
                            )
                        }
                        val linkTx = if (l.isNull("linkedTransactionId")) null else l.getInt("linkedTransactionId")
                        if (linkTx != null && linkTx !in txIds && txIds.isNotEmpty()) {
                            return@withContext BackupInspection(
                                isValid = true,
                                hasBalanceMismatch = true,
                                mismatchReason = "Liability ledger references missing transaction #$linkTx",
                                itemCount = itemCount,
                                documentCount = docCount,
                                hasFinance = true
                            )
                        }
                    }
                }

                BackupInspection(
                    isValid = true,
                    hasBalanceMismatch = false,
                    itemCount = itemCount,
                    documentCount = docCount,
                    hasFinance = true
                )
            } catch (e: Exception) {
                if (itemCount > 0 || docCount > 0) {
                    BackupInspection(
                        isValid = true,
                        hasBalanceMismatch = true,
                        mismatchReason = "Financial verification discrepancy: ${e.message}",
                        itemCount = itemCount,
                        documentCount = docCount,
                        hasFinance = true
                    )
                } else {
                    BackupInspection(
                        isValid = false,
                        hasBalanceMismatch = false,
                        mismatchReason = "Backup inspection failed: ${e.message}"
                    )
                }
            }
        }

    /**
     * Reads a `.kaka` ZIP file from the given [uri].
     * Extracts images to the app's image folder and generates SHA256 integrity records.
     * Parses `data.json` and inserts/merges entities into the database.
     *
     * If [skipFinance] is true, financial and liability records are declined (kept as-is in database),
     * and only items, care tasks, baskets, documents, and document pages are restored.
     */
    suspend fun restoreKakaZip(
        context: Context,
        uri: Uri,
        db: AppDatabase,
        skipFinance: Boolean = false
    ): RestoreResult =
        withContext(Dispatchers.IO) {
            val imageDir = LocalImageStore.imageDir(context)
            val docDir = DocumentImageStore.docDir(context)
            if (!imageDir.exists()) imageDir.mkdirs()
            if (!docDir.exists()) docDir.mkdirs()

            if (!skipFinance) {
                val inspection = inspectKakaZip(context, uri)
                if (inspection.hasBalanceMismatch) {
                    throw BalanceMismatchException(
                        "Balance mismatch detected in backup file: ${inspection.mismatchReason}",
                        inspection.mismatchReason
                    )
                }
            }

            var jsonData: String? = null

            // 1. Unzip the file
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.replace('\\', '/')
                        if (entryName == "data.json" || entryName.endsWith("/data.json")) {
                            jsonData = zis.reader().readText()
                        } else if (entryName.contains("doc_images/") && !entry.isDirectory) {
                            val fileName = File(entryName).name
                            val destFile = File(docDir, fileName)
                            FileOutputStream(destFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            ImageIntegrityManager.registerImageHash(destFile)
                        } else if (entryName.contains("images/") && !entry.isDirectory) {
                            val fileName = File(entryName).name
                            val destFile = File(imageDir, fileName)
                            FileOutputStream(destFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            ImageIntegrityManager.registerImageHash(destFile)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (jsonData == null) {
                throw IllegalArgumentException("Invalid backup: data.json missing")
            }

            val root = JSONObject(jsonData!!)
            val version = root.optInt("version", 2)
            val isV3 = version >= 3

            fun parseMoney(obj: JSONObject, key: String): com.projectkaka.inventory.model.Money {
                return if (isV3) {
                    com.projectkaka.inventory.model.Money(obj.optLong(key, 0L))
                } else {
                    val strValue = obj.optString(key, "0")
                    try {
                        com.projectkaka.inventory.model.Money.fromDecimalString(strValue)
                    } catch (e: Exception) {
                        com.projectkaka.inventory.model.Money.ZERO
                    }
                }
            }

            val itemsArr = root.optJSONArray("items")
            
            var itemsRestored = 0
            var accountsRestored = 0
            var categoriesRestored = 0
            var transactionsRestored = 0
            var ledgerEntriesRestored = 0
            var documentsRestored = 0

            db.withTransaction {
                if (skipFinance) {
                    // Only clear inventory and document tables, preserving financial tables
                    db.openHelper.writableDatabase.execSQL("DELETE FROM care_tasks")
                    db.openHelper.writableDatabase.execSQL("DELETE FROM basket_items")
                    db.openHelper.writableDatabase.execSQL("DELETE FROM items")
                    db.openHelper.writableDatabase.execSQL("DELETE FROM document_pages")
                    db.openHelper.writableDatabase.execSQL("DELETE FROM documents")
                } else {
                    db.clearAllTables()
                }
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        val itemObj = itemsArr.getJSONObject(i)
                        val id = itemObj.optInt("id", 0)
                        
                        val itemEntity = ItemEntity(
                            id = if (id > 0) id else 0,
                            name = itemObj.getString("name"),
                            category = itemObj.getString("category"),
                            locationTag = itemObj.getString("locationTag"),
                            estimatedValue = parseMoney(itemObj, "estimatedValue"),
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

                // ── Document Vault Restore ──
                val docsArr = root.optJSONArray("documents")
                if (docsArr != null) {
                    for (i in 0 until docsArr.length()) {
                        val docObj = docsArr.getJSONObject(i)
                        val id = docObj.optInt("id", 0)
                        val coverImgName = docObj.optString("coverImageName", "")
                        var coverPath = if (coverImgName.isNotBlank()) File(docDir, coverImgName).absolutePath else ""

                        val docEntity = DocumentEntity(
                            id = if (id > 0) id else 0,
                            title = docObj.getString("title"),
                            docType = docObj.optString("docType", "OTHER"),
                            pageCount = docObj.optInt("pageCount", 1),
                            coverImagePath = coverPath,
                            notes = docObj.optString("notes", ""),
                            issueDate = docObj.optLong("issueDate", System.currentTimeMillis()),
                            expiryDate = if (docObj.isNull("expiryDate")) null else docObj.getLong("expiryDate"),
                            linkedItemId = if (docObj.isNull("linkedItemId")) null else docObj.getInt("linkedItemId"),
                            createdAt = docObj.optLong("createdAt", System.currentTimeMillis()),
                            isArchived = docObj.optBoolean("isArchived", false)
                        )
                        val newDocId = db.documentDao().insertDocument(docEntity).toInt()

                        val pagesArr = docObj.optJSONArray("pages")
                        if (pagesArr != null) {
                            for (p in 0 until pagesArr.length()) {
                                val pObj = pagesArr.getJSONObject(p)
                                val pId = pObj.optInt("id", 0)
                                val pImgName = pObj.optString("imageName", "")
                                val pImgPath = if (pImgName.isNotBlank()) File(docDir, pImgName).absolutePath else ""

                                if (coverPath.isBlank() && pImgPath.isNotBlank()) {
                                    coverPath = pImgPath
                                    db.documentDao().updateDocument(docEntity.copy(id = newDocId, coverImagePath = coverPath))
                                }

                                val pageEntity = DocumentPageEntity(
                                    id = if (pId > 0) pId else 0,
                                    documentId = newDocId,
                                    pageNumber = pObj.getInt("pageNumber"),
                                    imagePath = pImgPath,
                                    pageNote = pObj.optString("pageNote", ""),
                                    createdAt = pObj.optLong("createdAt", System.currentTimeMillis())
                                )
                                db.documentDao().insertPage(pageEntity)
                            }
                        }
                        documentsRestored++
                    }
                }

                if (!skipFinance) {
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
                                    openingBalance = parseMoney(accObj, "openingBalance"),
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
                                val amount = parseMoney(tObj, "amount")
                                val accountId = tObj.getInt("accountId")
                                val counterAccountIdRaw = if (tObj.isNull("counterAccountId")) null else tObj.getInt("counterAccountId")
                                val isCredit = tObj.getBoolean("isCredit")
                                val note = tObj.optString("note", "")
                                val timestamp = tObj.optLong("timestamp", System.currentTimeMillis())
                                val typeStr = tObj.optString("type", "EXPENSE")
                                
                                val journalEntryId = db.journalDao().insertJournalEntry(
                                    com.projectkaka.inventory.data.local.entity.JournalEntryEntity(
                                        id = if (id > 0) id else 0,
                                        timestamp = timestamp,
                                        description = note,
                                        status = com.projectkaka.inventory.data.local.entity.JournalStatus.POSTED,
                                        approvalStatus = com.projectkaka.inventory.data.local.entity.ApprovalStatus.APPROVED
                                    )
                                ).toInt()

                                db.journalDao().insertPosting(
                                    com.projectkaka.inventory.data.local.entity.PostingEntity(
                                        journalEntryId = journalEntryId,
                                        accountId = accountId,
                                        amount = amount,
                                        isCredit = isCredit,
                                        note = note
                                    )
                                )

                                val targetCounterId = if (counterAccountIdRaw != null) {
                                    counterAccountIdRaw
                                } else {
                                    val accName = when (typeStr) {
                                        "INCOME" -> "Income"
                                        "DEBT_ISSUE", "DEBT_SETTLE" -> if (isCredit) "Liabilities" else "Assets"
                                        else -> "Expenses"
                                    }
                                    val existingAcc = db.financeDao().getAccountByName(accName)
                                    if (existingAcc != null) existingAcc.id
                                    else {
                                        val accType = when (typeStr) {
                                            "INCOME" -> AccountType.REVENUE
                                            "DEBT_ISSUE", "DEBT_SETTLE" -> if (isCredit) AccountType.LIABILITY else AccountType.ASSET
                                            else -> AccountType.EXPENSE
                                        }
                                        db.financeDao().insertAccount(AccountEntity(name = accName, type = accType, openingBalance = com.projectkaka.inventory.model.Money.ZERO)).toInt()
                                    }
                                }
                                
                                db.journalDao().insertPosting(
                                    com.projectkaka.inventory.data.local.entity.PostingEntity(
                                        journalEntryId = journalEntryId,
                                        accountId = targetCounterId,
                                        amount = amount,
                                        isCredit = !isCredit,
                                        note = note
                                    )
                                )
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
                                    amount = parseMoney(lObj, "amount"),
                                    type = LedgerType.valueOf(lObj.optString("type", "PAYABLE")),
                                    isSettled = lObj.getBoolean("isSettled"),
                                    note = lObj.optString("note", ""),
                                    dueDate = if (lObj.isNull("dueDate")) null else lObj.getLong("dueDate"),
                                    accountId = if (lObj.isNull("accountId")) null else lObj.getInt("accountId"),
                                    linkedTransactionId = if (lObj.isNull("linkedTransactionId")) null else lObj.getInt("linkedTransactionId"),
                                    createdAt = lObj.optLong("createdAt", System.currentTimeMillis())
                                )
                                db.financeDao().insertLedgerEntry(le)
                                ledgerEntriesRestored++
                            }
                        }

                        // Recalculate balances for all accounts after bulk import
                        val allAccounts = db.financeDao().getAllAccountsSnapshot()
                        allAccounts.forEach { account ->
                            db.financeDao().recalculateAccountBalance(account.id)
                        }

                        val mismatches = db.financeDao().getAccountsWithMismatchedBalances()
                        if (mismatches.isNotEmpty()) {
                            throw BalanceMismatchException("Balance mismatch detected in accounts: ${mismatches.joinToString { it.name }}")
                        }
                    }
                }
            }

            RestoreResult(
                itemsRestored = itemsRestored,
                accountsRestored = accountsRestored,
                categoriesRestored = categoriesRestored,
                transactionsRestored = transactionsRestored,
                ledgerEntriesRestored = ledgerEntriesRestored,
                documentsRestored = documentsRestored
            )
        }
}
