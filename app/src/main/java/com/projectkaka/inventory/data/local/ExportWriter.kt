package com.projectkaka.inventory.data.local

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.projectkaka.inventory.data.repository.FinancialExportData
import com.projectkaka.inventory.data.repository.ItemExportRow
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
 * Writes a read-only snapshot of the inventory to a file inside the device's Downloads directory.
 * 
 * On Android 10+ (API 29+), uses MediaStore API for proper scoped storage support.
 * On older versions, falls back to direct file I/O.
 */
object ExportWriter {

    private const val DIR_NAME = "ProjectKaka"

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    // ── MediaStore-based writing for Android 10+ ──

    /**
     * Creates or opens a file in Downloads/ProjectKaka using MediaStore (API 29+)
     * or direct file I/O (API < 29). Returns the output stream and the display name.
     */
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
            // Mark as not pending after write completes — caller must close the stream
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

    /**
     * Legacy export dir for operations that still need a File reference
     * (e.g. ZIP backup which needs random access).
     */
    private fun legacyExportDir(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use app-specific cache, then copy via MediaStore
            File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        } else {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
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
                        row.estimatedValue.toString(),
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
        financeData: FinancialExportData? = null
    ): String =
        withContext(Dispatchers.IO) {
            val fileName = "kaka_inventory_${timestamp()}.json"
            val root = generateJsonObject(rows, financeData)
            val (outputStream, name) = createExportFile(context, fileName, "application/json")
            outputStream.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
            name
        }

    suspend fun writeKakaZip(
        context: Context,
        rows: List<ItemExportRow>,
        financeData: FinancialExportData? = null
    ): String =
        withContext(Dispatchers.IO) {
            val fileName = "kaka_backup_${timestamp()}.kaka"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Write to a temp file first, then copy into MediaStore
                val tempFile = File(context.cacheDir, fileName)
                writeZipToFile(context, tempFile, rows, financeData)
                
                // Now copy into Downloads via MediaStore
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
                writeZipToFile(context, file, rows, financeData)
                fileName
            }
        }

    private fun writeZipToFile(
        context: Context,
        file: File,
        rows: List<ItemExportRow>,
        financeData: FinancialExportData?
    ) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            // 1. Write the JSON data
            val root = generateJsonObject(rows, financeData)
            val jsonBytes = root.toString(2).toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(jsonBytes)
            zos.closeEntry()

            // 2. Add all the images
            val imageDir = File(context.filesDir, "images")
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
        }
    }

    private fun generateJsonObject(
        rows: List<ItemExportRow>,
        financeData: FinancialExportData?
    ): JSONObject {
        val root = JSONObject()
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("itemCount", rows.size)

        val items = JSONArray()
        rows.forEach { row ->
            val obj = JSONObject()
            obj.put("id", row.id)
            obj.put("name", row.name)
            obj.put("category", row.category)
            obj.put("locationTag", row.locationTag)
            obj.put("estimatedValue", row.estimatedValue)
            obj.put("status", row.status)
            obj.put("isDraft", row.isDraft)
            obj.put("dateAdded", row.dateAdded)
            // Use just the filename for paths inside the zip so they restore cleanly
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

        if (financeData != null) {
            val finObj = JSONObject()
            
            val accs = JSONArray()
            financeData.accounts.forEach { acct ->
                accs.put(JSONObject().apply {
                    put("id", acct.id)
                    put("name", acct.name)
                    put("type", acct.type.name)
                    put("aliases", acct.aliases)
                    put("openingBalance", acct.openingBalance)
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
                    put("amount", tx.amount)
                    put("accountId", tx.accountId)
                    put("categoryId", tx.categoryId)
                    put("isCredit", tx.isCredit)
                    put("note", tx.note)
                    put("timestamp", tx.timestamp)
                })
            }
            finObj.put("transactions", txs)

            val ledgers = JSONArray()
            financeData.ledgerEntries.forEach { le ->
                ledgers.put(JSONObject().apply {
                    put("id", le.id)
                    put("contactName", le.contactName)
                    put("contactPhone", le.contactPhone)
                    put("amount", le.amount)
                    put("type", le.type.name)
                    put("isSettled", le.isSettled)
                    put("note", le.note)
                    put("dueDate", le.dueDate)
                    put("createdAt", le.createdAt)
                })
            }
            finObj.put("ledgerEntries", ledgers)

            root.put("finance", finObj)
        }

        return root
    }

    /** RFC 4180 style quoting: wrap in quotes and double any embedded quotes. */
    private fun csv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
