package com.projectkaka.inventory.data.local

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.projectkaka.inventory.data.local.relation.DocumentWithPages
import com.projectkaka.inventory.data.repository.FinancialExportData
import com.projectkaka.inventory.data.repository.ItemExportRow
import com.projectkaka.inventory.util.FinanceModelClashDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a read-only snapshot of the inventory, documents, and finance to a file inside
 * the device's Downloads directory.
 *
 * Scoped storage compliant (MediaStore on Android 10+).
 */
object ExportWriter {

    private const val DIR_NAME = "ProjectKaka"

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    // ── MediaStore-based writing for Android 10+ ──

    private fun createExportFile(
        context: Context,
        fileName: String,
        mimeType: String
    ): Pair<OutputStream, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DIR_NAME")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: throw Exception("Failed to create MediaStore entry for $fileName")
            val os = resolver.openOutputStream(uri)
                ?: throw Exception("Failed to open output stream for $fileName")
            return object : OutputStream() {
                override fun write(b: Int) = os.write(b)
                override fun write(b: ByteArray) = os.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = os.write(b, off, len)
                override fun flush() = os.flush()
                override fun close() {
                    os.close()
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } to fileName
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val file = File(dir, fileName)
            return FileOutputStream(file) to fileName
        }
    }

    suspend fun writeCsv(context: Context, rows: List<ItemExportRow>): String =
        withContext(Dispatchers.IO) {
            val fileName = "kaka_inventory_${timestamp()}.csv"
            val sb = StringBuilder()
            sb.append(
                "id,name,category,location_tag,estimated_value,status,is_draft," +
                    "date_added_iso,image_path,care_tasks\n"
            )
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            rows.forEach { row ->
                val tasks = row.careTasks.joinToString(";") { task ->
                    "${task.taskName} every ${task.frequencyDays}d"
                }
                sb.append(
                    listOf(
                        row.id.toString(),
                        csv(row.name),
                        csv(row.category),
                        csv(row.locationTag),
                        row.estimatedValue.minorUnits.toString(),
                        row.status,
                        row.isDraft.toString(),
                        iso.format(Date(row.dateAdded)),
                        csv(row.imagePath),
                        csv(tasks)
                    ).joinToString(",")
                ).append('\n')
            }
            val (outputStream, name) = createExportFile(context, fileName, "text/csv")
            outputStream.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
            name
        }

    suspend fun writeJson(
        context: Context,
        rows: List<ItemExportRow>,
        documents: List<DocumentWithPages> = emptyList(),
        financeData: FinancialExportData? = null
    ): String =
        withContext(Dispatchers.IO) {
            val fileName = "kaka_inventory_${timestamp()}.json"
            val root = generateJsonObject(rows, documents, financeData)
            val (outputStream, name) = createExportFile(context, fileName, "application/json")
            outputStream.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
            name
        }

    suspend fun writeKakaZip(
        context: Context,
        rows: List<ItemExportRow>,
        documents: List<DocumentWithPages> = emptyList(),
        financeData: FinancialExportData? = null
    ): String =
        withContext(Dispatchers.IO) {
            val fileName = "kaka_backup_${timestamp()}.kaka"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val tempFile = File(context.cacheDir, fileName)
                writeZipToFile(context, tempFile, rows, documents, financeData)
                
                val (outputStream, name) = createExportFile(context, fileName, "application/zip")
                outputStream.use { os ->
                    FileInputStream(tempFile).use { fis ->
                        fis.copyTo(os)
                    }
                }
                tempFile.delete()
                name
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME).apply {
                    if (!exists()) mkdirs()
                }
                val file = File(dir, fileName)
                writeZipToFile(context, file, rows, documents, financeData)
                fileName
            }
        }

    private fun writeZipToFile(
        context: Context,
        file: File,
        rows: List<ItemExportRow>,
        documents: List<DocumentWithPages>,
        financeData: FinancialExportData?
    ) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            // 1. Write the JSON data
            val root = generateJsonObject(rows, documents, financeData)
            val jsonBytes = root.toString(2).toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(jsonBytes)
            zos.closeEntry()

            // 2. Add inventory item images
            val imageDir = LocalImageStore.imageDir(context)
            if (imageDir.exists()) {
                rows.forEach { row ->
                    if (row.imagePath.isNotBlank()) {
                        val imgFile = File(row.imagePath)
                        if (imgFile.exists() && imgFile.isFile) {
                            zos.putNextEntry(ZipEntry("images/${imgFile.name}"))
                            FileInputStream(imgFile).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }

            // 3. Add document vault images (crisp multi-page doc images)
            val docDir = DocumentImageStore.docDir(context)
            if (docDir.exists()) {
                documents.forEach { docWithPages ->
                    docWithPages.sortedPages.forEach { page ->
                        if (page.imagePath.isNotBlank()) {
                            val docImg = File(page.imagePath)
                            if (docImg.exists() && docImg.isFile) {
                                zos.putNextEntry(ZipEntry("doc_images/${docImg.name}"))
                                FileInputStream(docImg).use { fis ->
                                    fis.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun generateJsonObject(
        rows: List<ItemExportRow>,
        documents: List<DocumentWithPages>,
        financeData: FinancialExportData?
    ): JSONObject {
        val root = JSONObject()
        root.put("version", 4)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("itemCount", rows.size)
        root.put("documentCount", documents.size)

        // ── 1. Items ──
        val items = JSONArray()
        rows.forEach { row ->
            val obj = JSONObject()
            obj.put("id", row.id)
            obj.put("name", row.name)
            obj.put("category", row.category)
            obj.put("locationTag", row.locationTag)
            obj.put("estimatedValue", row.estimatedValue.minorUnits)
            obj.put("status", row.status)
            obj.put("isDraft", row.isDraft)
            obj.put("dateAdded", row.dateAdded)
            obj.put("imageName", File(row.imagePath).name)
            obj.put("imagePath", row.imagePath)

            val tasks = JSONArray()
            row.careTasks.forEach { task ->
                tasks.put(
                    JSONObject()
                        .put("taskName", task.taskName)
                        .put("frequencyDays", task.frequencyDays)
                        .put("lastCompletedDate", task.lastCompletedDate)
                )
            }
            obj.put("careTasks", tasks)
            items.put(obj)
        }
        root.put("items", items)

        // ── 2. Document Vault (Multi-page) ──
        val docsArr = JSONArray()
        documents.forEach { docWithPages ->
            val doc = docWithPages.document
            val docObj = JSONObject().apply {
                put("id", doc.id)
                put("title", doc.title)
                put("docType", doc.docType)
                put("pageCount", doc.pageCount)
                put("coverImageName", File(doc.coverImagePath).name)
                put("notes", doc.notes)
                put("issueDate", doc.issueDate)
                if (doc.expiryDate != null) put("expiryDate", doc.expiryDate)
                if (doc.linkedItemId != null) put("linkedItemId", doc.linkedItemId)
                put("createdAt", doc.createdAt)
                put("isArchived", doc.isArchived)

                val pagesArr = JSONArray()
                docWithPages.sortedPages.forEach { page ->
                    pagesArr.put(JSONObject().apply {
                        put("id", page.id)
                        put("pageNumber", page.pageNumber)
                        put("imageName", File(page.imagePath).name)
                        put("pageNote", page.pageNote)
                        put("createdAt", page.createdAt)
                    })
                }
                put("pages", pagesArr)
            }
            docsArr.put(docObj)
        }
        root.put("documents", docsArr)

        // ── 3. Finance & Liability Ledger (with Clash Safeguard) ──
        if (financeData != null) {
            val finObj = JSONObject()
            
            val accs = JSONArray()
            financeData.accounts.forEach { acct ->
                accs.put(JSONObject().apply {
                    put("id", acct.id)
                    put("name", acct.name)
                    put("type", acct.type.name)
                    put("aliases", acct.aliases)
                    put("openingBalance", acct.openingBalance.minorUnits)
                    put("isActive", acct.isActive)
                    put("createdAt", acct.createdAt)
                })
            }
            finObj.put("accounts", accs)

            val cats = JSONArray()
            financeData.categories.forEach { cat ->
                cats.put(JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("type", cat.type.name)
                    put("aliases", cat.aliases)
                    put("createdAt", cat.createdAt)
                })
            }
            finObj.put("categories", cats)

            val txs = JSONArray()
            financeData.transactions.forEach { tx ->
                txs.put(JSONObject().apply {
                    put("id", tx.id)
                    put("amount", tx.amount.minorUnits)
                    put("accountId", tx.accountId)
                    if (tx.categoryId != null) put("categoryId", tx.categoryId)
                    if (tx.transferId != null) put("transferId", tx.transferId)
                    if (tx.counterAccountId != null) put("counterAccountId", tx.counterAccountId)
                    put("type", tx.type.name)
                    put("isCredit", tx.isCredit)
                    put("note", tx.note)
                    put("timestamp", tx.timestamp)
                })
            }
            finObj.put("transactions", txs)

            // Audit liability ledger for potential conflicts with the double-entry finance model
            val clashCheck = FinanceModelClashDetector.evaluate(
                accounts = financeData.accounts,
                transactions = financeData.transactions,
                ledgerEntries = financeData.ledgerEntries
            )

            if (clashCheck.hasClash) {
                // Safely skip exporting the liability ledger to prevent financial model corruption
                finObj.put("liabilityLedgerSkippedDueToClash", true)
                val reasons = JSONArray()
                clashCheck.clashReasons.forEach { reasons.put(it) }
                finObj.put("liabilityLedgerClashReasons", reasons)
            } else {
                val ledgers = JSONArray()
                financeData.ledgerEntries.forEach { le ->
                    ledgers.put(JSONObject().apply {
                        put("id", le.id)
                        put("contactName", le.contactName)
                        put("contactPhone", le.contactPhone)
                        put("amount", le.amount.minorUnits)
                        put("type", le.type.name)
                        put("isSettled", le.isSettled)
                        put("note", le.note)
                        put("dueDate", le.dueDate)
                        if (le.accountId != null) put("accountId", le.accountId)
                        if (le.linkedTransactionId != null) put("linkedTransactionId", le.linkedTransactionId)
                        put("createdAt", le.createdAt)
                    })
                }
                finObj.put("ledgerEntries", ledgers)
            }

            root.put("finance", finObj)
        }

        return root
    }

    private fun csv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
