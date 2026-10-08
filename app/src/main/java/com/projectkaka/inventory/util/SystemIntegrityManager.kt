package com.projectkaka.inventory.util

import android.content.Context
import com.projectkaka.inventory.data.local.AppDatabase
import com.projectkaka.inventory.data.local.LocalImageStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * SystemIntegrityManager: Whole-System Hierarchical Merkle Tree Cryptographic Engine.
 *
 * Computes deterministic Merkle branch digests for:
 * 1. Inventory Branch (items table: id, name, quantity, value, serial, status, timestamps)
 * 2. Finance Branch (accounts, transactions, journal_entries, postings, ledger_entries)
 * 3. Documents Branch (documents, document_pages)
 * 4. Cartons/Boxes Branch (baskets, basket_item_cross_ref)
 * 5. Media Files Branch (streaming 64KB O(1) RAM SHA-256 across disk media files)
 *
 * Salting with arbitrary user passphrases:
 * RootHash = SHA-256(BranchHashes + ":" + SHA-256(Passphrase))
 */
object SystemIntegrityManager {

    private const val HISTORY_FILE_NAME = "snapshots_history.json"
    private const val ACTIVE_BASELINE_NAME = "snapshot_baseline.json"

    data class BranchAudit(
        val name: String,
        val hash: String,
        val countDescription: String,
        val isIntact: Boolean = true,
        val diffNote: String? = null
    )

    data class SnapshotManifest(
        val id: String,
        val timestampEpoch: Long,
        val formattedDate: String,
        val hasPassphrase: Boolean,
        val itemCount: Int,
        val accountCount: Int,
        val transactionCount: Int,
        val documentCount: Int,
        val basketCount: Int,
        val fileCount: Int,
        val totalMediaBytes: Long,
        val branches: Map<String, String>, // branch name -> SHA-256
        val secretRootHash: String,        // Never printed to user
        val saltVerificationHash: String   // sha256(passphrase) or ""
    ) {
        fun formatMediaSize(): String {
            val mb = totalMediaBytes.toDouble() / (1024 * 1024)
            return if (mb >= 1.0) "%.2f MB".format(mb) else "%.1f KB".format(totalMediaBytes.toDouble() / 1024)
        }
    }

    data class AuditResult(
        val isPassed: Boolean,
        val baselineId: String,
        val baselineDate: String,
        val branchAudits: List<BranchAudit>,
        val requiresPassphrase: Boolean,
        val passphraseMismatch: Boolean = false,
        val errorReason: String? = null
    )

    // ── Helper: Stream Hash A File ──
    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(file.inputStream(), 65536).use { bis ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (bis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256String(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── Branch 1: Inventory Branch ──
    private fun computeInventoryBranch(db: AppDatabase): Pair<String, Int> {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0
        db.openHelper.readableDatabase.query(
            "SELECT id, name, category, location_tag, estimated_value, image_path, is_draft, status, date_added, linked_journal_id FROM items ORDER BY id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                count++
                val id = cursor.getInt(0)
                val name = cursor.getString(1) ?: ""
                val cat = cursor.getString(2) ?: ""
                val loc = cursor.getString(3) ?: ""
                val estVal = cursor.getLong(4)
                val img = cursor.getString(5) ?: ""
                val isDraft = cursor.getInt(6)
                val status = cursor.getString(7) ?: ""
                val dateAdded = cursor.getLong(8)
                val journalId = if (cursor.isNull(9)) -1 else cursor.getInt(9)

                val rowRepr = "$id|$name|$cat|$loc|$estVal|$img|$isDraft|$status|$dateAdded|$journalId\n"
                digest.update(rowRepr.toByteArray(Charsets.UTF_8))
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return Pair(hash, count)
    }

    // ── Branch 2: Finance Branch ──
    private fun computeFinanceBranch(db: AppDatabase): Triple<String, Int, Int> {
        val digest = MessageDigest.getInstance("SHA-256")
        var accCount = 0
        var txCount = 0

        // Accounts
        db.openHelper.readableDatabase.query("SELECT id, name, type, opening_balance, is_active FROM accounts ORDER BY id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                accCount++
                val id = cursor.getInt(0)
                val name = cursor.getString(1) ?: ""
                val type = cursor.getString(2) ?: ""
                val opening = cursor.getLong(3)
                val isActive = cursor.getInt(4)
                digest.update("ACC:$id|$name|$type|$opening|$isActive\n".toByteArray(Charsets.UTF_8))
            }
        }

        // Journal Entries & Postings
        db.openHelper.readableDatabase.query("SELECT id, timestamp, description, status FROM journal_entries ORDER BY id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                txCount++
                val id = cursor.getInt(0)
                val ts = cursor.getLong(1)
                val desc = cursor.getString(2) ?: ""
                val status = cursor.getString(3) ?: ""
                digest.update("JOURNAL:$id|$ts|$desc|$status\n".toByteArray(Charsets.UTF_8))
            }
        }

        db.openHelper.readableDatabase.query("SELECT id, journal_entry_id, account_id, amount, is_credit, note FROM postings ORDER BY id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getInt(0)
                val jId = cursor.getInt(1)
                val accId = cursor.getInt(2)
                val amt = cursor.getLong(3)
                val isCredit = cursor.getInt(4)
                val note = cursor.getString(5) ?: ""
                digest.update("POSTING:$id|$jId|$accId|$amt|$isCredit|$note\n".toByteArray(Charsets.UTF_8))
            }
        }

        // Ledger entries
        db.openHelper.readableDatabase.query("SELECT id, contact_name, amount, type, is_settled, created_at FROM ledger_entries ORDER BY id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getInt(0)
                val contact = cursor.getString(1) ?: ""
                val amt = cursor.getLong(2)
                val type = cursor.getString(3) ?: ""
                val isSettled = cursor.getInt(4)
                val ts = cursor.getLong(5)
                digest.update("LEDGER:$id|$contact|$amt|$type|$isSettled|$ts\n".toByteArray(Charsets.UTF_8))
            }
        }

        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return Triple(hash, accCount, txCount)
    }

    // ── Branch 3: Documents Branch ──
    private fun computeDocumentsBranch(db: AppDatabase): Pair<String, Int> {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0
        db.openHelper.readableDatabase.query("SELECT id, title, doc_type, page_count, cover_image_path FROM documents ORDER BY id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                count++
                val id = cursor.getInt(0)
                val title = cursor.getString(1) ?: ""
                val docType = cursor.getString(2) ?: ""
                val pageCount = cursor.getInt(3)
                val cover = cursor.getString(4) ?: ""
                digest.update("DOC:$id|$title|$docType|$pageCount|$cover\n".toByteArray(Charsets.UTF_8))
            }
        }
        db.openHelper.readableDatabase.query("SELECT id, document_id, page_number, image_path FROM document_pages ORDER BY id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getInt(0)
                val docId = cursor.getInt(1)
                val page = cursor.getInt(2)
                val img = cursor.getString(3) ?: ""
                digest.update("PAGE:$id|$docId|$page|$img\n".toByteArray(Charsets.UTF_8))
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return Pair(hash, count)
    }

    // ── Branch 4: Cartons/Boxes Branch ──
    private fun computeCartonsBranch(db: AppDatabase): Pair<String, Int> {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0
        db.openHelper.readableDatabase.query("SELECT id, name, code, description, color_hex, is_packed FROM baskets ORDER BY id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                count++
                val id = cursor.getInt(0)
                val name = cursor.getString(1) ?: ""
                val code = cursor.getString(2) ?: ""
                val desc = cursor.getString(3) ?: ""
                val color = cursor.getString(4) ?: ""
                val packed = cursor.getInt(5)
                digest.update("BASKET:$id|$name|$code|$desc|$color|$packed\n".toByteArray(Charsets.UTF_8))
            }
        }
        db.openHelper.readableDatabase.query("SELECT basket_id, item_id, added_at FROM basket_items ORDER BY basket_id, item_id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val bId = cursor.getInt(0)
                val iId = cursor.getInt(1)
                val addedAt = cursor.getLong(2)
                digest.update("REF:$bId|$iId|$addedAt\n".toByteArray(Charsets.UTF_8))
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return Pair(hash, count)
    }

    // ── Branch 5: Media Files Branch ──
    private fun computeMediaBranch(context: Context): Triple<String, Int, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val dir = LocalImageStore.imageDir(context)
        val files = dir.listFiles { f ->
            f.isFile && (f.extension.equals("webp", ignoreCase = true) ||
                         f.extension.equals("jpg", ignoreCase = true) ||
                         f.extension.equals("png", ignoreCase = true))
        }?.sortedBy { it.name } ?: emptyList()

        var totalBytes = 0L
        files.forEach { file ->
            totalBytes += file.length()
            val fileHash = sha256File(file)
            digest.update("${file.name}:${file.length()}:$fileHash\n".toByteArray(Charsets.UTF_8))
        }

        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return Triple(hash, files.size, totalBytes)
    }

    // ── Create Live Merkle State Tree ──
    fun createLiveSnapshot(context: Context, passphrase: String = "", customDb: AppDatabase? = null): SnapshotManifest {
        val db = customDb ?: AppDatabase.getInstance(context)
        val nowEpoch = System.currentTimeMillis()
        val fmtDate = Instant.ofEpochMilli(nowEpoch).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        val (invHash, itemCount) = computeInventoryBranch(db)
        val (finHash, accCount, txCount) = computeFinanceBranch(db)
        val (docHash, docCount) = computeDocumentsBranch(db)
        val (boxHash, boxCount) = computeCartonsBranch(db)
        val (medHash, fileCount, mediaBytes) = computeMediaBranch(context)

        val branches = mapOf(
            "Inventory" to invHash,
            "Finance" to finHash,
            "Documents" to docHash,
            "Boxes/Cartons" to boxHash,
            "Media Files" to medHash
        )

        // Combine Merkle branches deterministically
        val treeRepr = "INVENTORY:$invHash\nFINANCE:$finHash\nDOCS:$docHash\nBOXES:$boxHash\nMEDIA:$medHash"
        val saltHash = if (passphrase.isNotBlank()) sha256String(passphrase.trim()) else ""
        val saltedPayload = if (saltHash.isNotBlank()) "$treeRepr\nSALT:$saltHash" else treeRepr
        val rootHash = sha256String(saltedPayload)

        val snapId = "SNAP-${nowEpoch % 1000000}"

        return SnapshotManifest(
            id = snapId,
            timestampEpoch = nowEpoch,
            formattedDate = fmtDate,
            hasPassphrase = saltHash.isNotBlank(),
            itemCount = itemCount,
            accountCount = accCount,
            transactionCount = txCount,
            documentCount = docCount,
            basketCount = boxCount,
            fileCount = fileCount,
            totalMediaBytes = mediaBytes,
            branches = branches,
            secretRootHash = rootHash,
            saltVerificationHash = saltHash
        )
    }

    // ── Save Snapshot to History Ledger ──
    fun saveSnapshot(context: Context, passphrase: String = "", customDb: AppDatabase? = null): SnapshotManifest {
        val manifest = createLiveSnapshot(context, passphrase, customDb)
        val file = File(context.filesDir, HISTORY_FILE_NAME)

        val array = if (file.exists()) {
            try { JSONArray(file.readText()) } catch (_: Exception) { JSONArray() }
        } else {
            JSONArray()
        }

        val json = JSONObject().apply {
            put("id", manifest.id)
            put("timestampEpoch", manifest.timestampEpoch)
            put("formattedDate", manifest.formattedDate)
            put("hasPassphrase", manifest.hasPassphrase)
            put("itemCount", manifest.itemCount)
            put("accountCount", manifest.accountCount)
            put("transactionCount", manifest.transactionCount)
            put("documentCount", manifest.documentCount)
            put("basketCount", manifest.basketCount)
            put("fileCount", manifest.fileCount)
            put("totalMediaBytes", manifest.totalMediaBytes)
            put("secretRootHash", manifest.secretRootHash)
            put("saltVerificationHash", manifest.saltVerificationHash)

            val branchObj = JSONObject()
            manifest.branches.forEach { (k, v) -> branchObj.put(k, v) }
            put("branches", branchObj)
        }

        array.put(json)
        file.writeText(array.toString(2))

        // Also save as active baseline for backward compatibility
        val activeFile = File(context.filesDir, ACTIVE_BASELINE_NAME)
        activeFile.writeText(json.toString(2))

        // Also ensure legacy ImageIntegrityManager baseline is kept in sync for verify/ file commands
        try {
            ImageIntegrityManager.createBaselineSnapshot(context)
        } catch (_: Throwable) {}

        return manifest
    }

    // ── Get Snapshot History ──
    fun getSnapshotHistory(context: Context): List<SnapshotManifest> {
        val file = File(context.filesDir, HISTORY_FILE_NAME)
        if (!file.exists()) return emptyList()

        return try {
            val array = JSONArray(file.readText())
            val list = mutableListOf<SnapshotManifest>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val branchObj = obj.optJSONObject("branches") ?: JSONObject()
                val branchMap = mutableMapOf<String, String>()
                branchObj.keys().forEach { k -> branchMap[k] = branchObj.getString(k) }

                list.add(
                    SnapshotManifest(
                        id = obj.getString("id"),
                        timestampEpoch = obj.getLong("timestampEpoch"),
                        formattedDate = obj.getString("formattedDate"),
                        hasPassphrase = obj.getBoolean("hasPassphrase"),
                        itemCount = obj.getInt("itemCount"),
                        accountCount = obj.getInt("accountCount"),
                        transactionCount = obj.getInt("transactionCount"),
                        documentCount = obj.getInt("documentCount"),
                        basketCount = obj.getInt("basketCount"),
                        fileCount = obj.getInt("fileCount"),
                        totalMediaBytes = obj.getLong("totalMediaBytes"),
                        branches = branchMap,
                        secretRootHash = obj.getString("secretRootHash"),
                        saltVerificationHash = obj.optString("saltVerificationHash", "")
                    )
                )
            }
            list.sortedByDescending { it.timestampEpoch }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getLatestSnapshot(context: Context): SnapshotManifest? {
        return getSnapshotHistory(context).firstOrNull()
    }

    fun deleteSnapshot(context: Context, id: String): Boolean {
        val file = File(context.filesDir, HISTORY_FILE_NAME)
        if (!file.exists()) return false

        try {
            val array = JSONArray(file.readText())
            val newArray = JSONArray()
            var deleted = false

            if (id.equals("all", ignoreCase = true)) {
                file.delete()
                File(context.filesDir, ACTIVE_BASELINE_NAME).delete()
                return true
            }

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getString("id").equals(id, ignoreCase = true)) {
                    deleted = true
                } else {
                    newArray.put(obj)
                }
            }

            if (deleted) {
                file.writeText(newArray.toString(2))
            }
            return deleted
        } catch (_: Exception) {
            return false
        }
    }

    // ── Audit Whole System Against Latest Snapshot Baseline ──
    fun auditAgainstLatest(context: Context, inputPassphrase: String = "", customDb: AppDatabase? = null): AuditResult {
        val latest = getLatestSnapshot(context)
            ?: return AuditResult(
                isPassed = false,
                baselineId = "None",
                baselineDate = "N/A",
                branchAudits = emptyList(),
                requiresPassphrase = false,
                errorReason = "No baseline snapshot found. Take a snapshot first with 'snapshot/ take [passphrase]'."
            )

        // Passphrase verification
        if (latest.hasPassphrase) {
            val enteredSaltHash = sha256String(inputPassphrase.trim())
            if (enteredSaltHash != latest.saltVerificationHash) {
                return AuditResult(
                    isPassed = false,
                    baselineId = latest.id,
                    baselineDate = latest.formattedDate,
                    branchAudits = emptyList(),
                    requiresPassphrase = true,
                    passphraseMismatch = true,
                    errorReason = "Passphrase mismatch! Provide the correct passphrase used when snapshot was taken."
                )
            }
        }

        // Compute current live snapshot with the verified passphrase
        val current = createLiveSnapshot(context, inputPassphrase, customDb)

        // Compare branches
        val branchAudits = mutableListOf<BranchAudit>()
        var allIntact = true

        val branchNames = listOf("Inventory", "Finance", "Documents", "Boxes/Cartons", "Media Files")
        branchNames.forEach { name ->
            val baselineHash = latest.branches[name] ?: ""
            val currentHash = current.branches[name] ?: ""
            val match = baselineHash.isNotBlank() && baselineHash == currentHash

            val countDesc = when (name) {
                "Inventory" -> "${current.itemCount} items"
                "Finance" -> "${current.accountCount} accs, ${current.transactionCount} txs"
                "Documents" -> "${current.documentCount} docs"
                "Boxes/Cartons" -> "${current.basketCount} boxes"
                "Media Files" -> "${current.fileCount} files (${current.formatMediaSize()})"
                else -> ""
            }

            val diffNote = if (!match) {
                allIntact = false
                when (name) {
                    "Inventory" -> "Records altered or restored (${current.itemCount} current vs ${latest.itemCount} baseline)"
                    "Finance" -> "Accounts/Ledgers altered (${current.transactionCount} txs vs ${latest.transactionCount} baseline)"
                    "Documents" -> "Document records altered (${current.documentCount} docs vs ${latest.documentCount} baseline)"
                    "Boxes/Cartons" -> "Cartons altered (${current.basketCount} boxes vs ${latest.basketCount} baseline)"
                    "Media Files" -> "Media files altered or added/removed (${current.fileCount} vs ${latest.fileCount} baseline)"
                    else -> "Branch state modified"
                }
            } else null

            branchAudits.add(
                BranchAudit(
                    name = name,
                    hash = currentHash,
                    countDescription = countDesc,
                    isIntact = match,
                    diffNote = diffNote
                )
            )
        }

        return AuditResult(
            isPassed = allIntact,
            baselineId = latest.id,
            baselineDate = latest.formattedDate,
            branchAudits = branchAudits,
            requiresPassphrase = latest.hasPassphrase
        )
    }
}
