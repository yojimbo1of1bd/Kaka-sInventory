package com.projectkaka.inventory.util

import android.content.Context
import com.projectkaka.inventory.data.local.DocumentImageStore
import com.projectkaka.inventory.data.local.LocalImageStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enterprise-grade cryptographic integrity manager for Project Kaka.
 * Computes hardware-accelerated SHA-256 streaming hashes for image files of arbitrary size
 * (including 100MB+ files) with constant O(1) ~64KB memory footprint.
 *
 * Maintains an authoritative cryptographic snapshot manifest (`snapshot_baseline.json`)
 * and redundant file sidecars to detect alterations, bit-rot, tampering, deletions, or untracked additions.
 */
object ImageIntegrityManager {

    private const val MANIFEST_NAME = "snapshot_baseline.json"
    private const val BUFFER_SIZE = 65536 // 64 KB buffer

    data class VerifiedFileInfo(
        val fileName: String,
        val sizeBytes: Long,
        val sha256: String
    ) {
        fun formatSize(): String = formatBytes(sizeBytes)
    }

    data class AlteredFileInfo(
        val fileName: String,
        val expectedHash: String,
        val actualHash: String
    )

    enum class FileIntegrityStatus {
        INTACT, ALTERED, UNTRACKED
    }

    data class SingleFileAuditResult(
        val fileName: String,
        val sizeBytes: Long,
        val actualHash: String,
        val expectedHash: String,
        val status: FileIntegrityStatus
    ) {
        fun formatSize(): String = formatBytes(sizeBytes)
    }

    data class ImageAuditResult(
        val totalScanned: Int,
        val intactCount: Int,
        val alteredFiles: List<AlteredFileInfo>,
        val baselineExists: Boolean = true,
        val baselineDate: String? = null,
        val totalSizeBytes: Long = 0L,
        val intactFiles: List<VerifiedFileInfo> = emptyList(),
        val missingFiles: List<String> = emptyList(),
        val untrackedFiles: List<String> = emptyList()
    ) {
        fun formatTotalSize(): String = formatBytes(totalSizeBytes)
    }

    data class BaselineEntry(
        val relativePath: String,
        val fileName: String,
        val sizeBytes: Long,
        val sha256: String,
        val lastModified: Long
    ) {
        fun formatSize(): String = formatBytes(sizeBytes)
    }

    data class BaselineManifest(
        val timestamp: Long,
        val formattedDate: String,
        val totalFiles: Int,
        val totalSizeBytes: Long,
        val entries: List<BaselineEntry>
    ) {
        fun formatTotalSize(): String = formatBytes(totalSizeBytes)
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Computes the SHA-256 checksum for a file on disk using buffered streaming.
     * Peak memory consumption is strictly ~64KB regardless of whether the file is 1MB or 100MB+.
     */
    fun computeSha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(file.inputStream(), BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes and saves a sidecar `.sha256` file next to [imageFile] to anchor its integrity.
     */
    fun registerImageHash(imageFile: File): String {
        if (!imageFile.exists()) return ""
        val hash = computeSha256(imageFile)
        if (hash.isNotBlank()) {
            val sidecar = File(imageFile.parentFile, "${imageFile.name}.sha256")
            try {
                sidecar.writeText(hash)
            } catch (_: Exception) {}
        }
        return hash
    }

    fun hasBaseline(context: Context): Boolean {
        return File(context.filesDir, MANIFEST_NAME).exists()
    }

    fun getBaseline(context: Context): BaselineManifest? {
        val file = File(context.filesDir, MANIFEST_NAME)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val timestamp = json.optLong("timestamp")
            val formattedDate = json.optString("formattedDate")
            val totalFiles = json.optInt("totalFiles")
            val totalSizeBytes = json.optLong("totalSizeBytes")
            val arr = json.optJSONArray("entries") ?: JSONArray()
            val entries = mutableListOf<BaselineEntry>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                entries.add(
                    BaselineEntry(
                        relativePath = item.optString("relativePath"),
                        fileName = item.optString("fileName"),
                        sizeBytes = item.optLong("sizeBytes"),
                        sha256 = item.optString("sha256"),
                        lastModified = item.optLong("lastModified")
                    )
                )
            }
            BaselineManifest(timestamp, formattedDate, totalFiles, totalSizeBytes, entries)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveManifest(context: Context, manifest: BaselineManifest) {
        val file = File(context.filesDir, MANIFEST_NAME)
        try {
            val json = JSONObject().apply {
                put("timestamp", manifest.timestamp)
                put("formattedDate", manifest.formattedDate)
                put("totalFiles", manifest.totalFiles)
                put("totalSizeBytes", manifest.totalSizeBytes)
                val arr = JSONArray()
                manifest.entries.forEach { e ->
                    val item = JSONObject().apply {
                        put("relativePath", e.relativePath)
                        put("fileName", e.fileName)
                        put("sizeBytes", e.sizeBytes)
                        put("sha256", e.sha256)
                        put("lastModified", e.lastModified)
                    }
                    arr.put(item)
                }
                put("entries", arr)
            }
            file.writeText(json.toString(2))
        } catch (_: Exception) {}
    }

    /**
     * Creates or overwrites a cryptographic baseline snapshot for all item photos and document scans.
     * Generates both individual `.sha256` sidecars and an authoritative `snapshot_baseline.json`.
     */
    fun createBaselineSnapshot(context: Context): BaselineManifest {
        val dirs = listOf(
            DocumentImageStore.docDir(context),
            LocalImageStore.imageDir(context)
        )
        val entries = mutableListOf<BaselineEntry>()
        var totalBytes = 0L

        for (dir in dirs) {
            if (!dir.exists()) continue
            val imageFiles = dir.listFiles { file ->
                file.isFile && (file.extension.equals("webp", ignoreCase = true) || 
                               file.extension.equals("jpg", ignoreCase = true) || 
                               file.extension.equals("png", ignoreCase = true))
            }?.sortedBy { it.name } ?: emptyList()

            for (img in imageFiles) {
                val hash = computeSha256(img)
                val len = img.length()
                totalBytes += len

                // Anchor sidecar
                val sidecar = File(dir, "${img.name}.sha256")
                try { sidecar.writeText(hash) } catch (_: Exception) {}

                entries.add(
                    BaselineEntry(
                        relativePath = "${dir.name}/${img.name}",
                        fileName = img.name,
                        sizeBytes = len,
                        sha256 = hash,
                        lastModified = img.lastModified()
                    )
                )
            }
        }

        val now = System.currentTimeMillis()
        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now))
        val manifest = BaselineManifest(
            timestamp = now,
            formattedDate = formattedDate,
            totalFiles = entries.size,
            totalSizeBytes = totalBytes,
            entries = entries
        )
        saveManifest(context, manifest)
        return manifest
    }

    /**
     * Audits all saved document scan pages and item images across app storage.
     * Compares the current disk contents against the recorded cryptographic baseline snapshot.
     */
    fun verifyAllImages(context: Context): ImageAuditResult {
        val dirs = listOf(
            DocumentImageStore.docDir(context),
            LocalImageStore.imageDir(context)
        )

        val manifest = getBaseline(context)
        val baselineExists = manifest != null
        val baselineDate = manifest?.formattedDate
        val baselineMap = manifest?.entries?.associateBy { it.fileName } ?: emptyMap()

        val scannedFiles = mutableListOf<File>()
        for (dir in dirs) {
            if (!dir.exists()) continue
            val imageFiles = dir.listFiles { file ->
                file.isFile && (file.extension.equals("webp", ignoreCase = true) || 
                               file.extension.equals("jpg", ignoreCase = true) || 
                               file.extension.equals("png", ignoreCase = true))
            } ?: emptyArray()
            scannedFiles.addAll(imageFiles)
        }

        var totalSizeBytes = 0L
        val intactList = mutableListOf<VerifiedFileInfo>()
        val alteredList = mutableListOf<AlteredFileInfo>()
        val untrackedList = mutableListOf<String>()
        val diskFileNames = mutableSetOf<String>()

        for (img in scannedFiles) {
            diskFileNames.add(img.name)
            val currentHash = computeSha256(img)
            val len = img.length()
            totalSizeBytes += len

            val expectedEntry = baselineMap[img.name]
            if (expectedEntry != null) {
                if (expectedEntry.sha256.equals(currentHash, ignoreCase = true)) {
                    intactList.add(VerifiedFileInfo(img.name, len, currentHash))
                } else {
                    alteredList.add(
                        AlteredFileInfo(
                            fileName = img.name,
                            expectedHash = expectedEntry.sha256,
                            actualHash = currentHash
                        )
                    )
                }
            } else {
                // Check if sidecar exists
                val sidecar = File(img.parentFile, "${img.name}.sha256")
                if (sidecar.exists()) {
                    val sidecarHash = sidecar.readText().trim()
                    if (sidecarHash.equals(currentHash, ignoreCase = true)) {
                        intactList.add(VerifiedFileInfo(img.name, len, currentHash))
                    } else {
                        alteredList.add(
                            AlteredFileInfo(
                                fileName = img.name,
                                expectedHash = sidecarHash,
                                actualHash = currentHash
                            )
                        )
                    }
                } else {
                    untrackedList.add("${img.name} (${formatBytes(len)})")
                }
            }
        }

        // Check for missing files recorded in baseline but missing from disk
        val missingList = mutableListOf<String>()
        if (manifest != null) {
            for (entry in manifest.entries) {
                if (!diskFileNames.contains(entry.fileName)) {
                    missingList.add("${entry.fileName} (Expected ${entry.formatSize()})")
                }
            }
        }

        return ImageAuditResult(
            totalScanned = scannedFiles.size,
            intactCount = intactList.size,
            alteredFiles = alteredList,
            baselineExists = baselineExists,
            baselineDate = baselineDate,
            totalSizeBytes = totalSizeBytes,
            intactFiles = intactList,
            missingFiles = missingList,
            untrackedFiles = untrackedList
        )
    }

    /**
     * Verifies a single file specifically against its baseline entry or sidecar hash.
     */
    fun verifySingleFile(context: Context, file: File): SingleFileAuditResult {
        if (!file.exists() || !file.isFile) {
            return SingleFileAuditResult(file.name, 0L, "", "", FileIntegrityStatus.UNTRACKED)
        }
        val currentHash = computeSha256(file)
        val len = file.length()
        val manifest = getBaseline(context)
        val baselineEntry = manifest?.entries?.find { it.fileName.equals(file.name, ignoreCase = true) }

        val expectedHash = baselineEntry?.sha256 ?: run {
            val sidecar = File(file.parentFile, "${file.name}.sha256")
            if (sidecar.exists()) sidecar.readText().trim() else ""
        }

        val status = when {
            expectedHash.isBlank() -> FileIntegrityStatus.UNTRACKED
            expectedHash.equals(currentHash, ignoreCase = true) -> FileIntegrityStatus.INTACT
            else -> FileIntegrityStatus.ALTERED
        }

        return SingleFileAuditResult(
            fileName = file.name,
            sizeBytes = len,
            actualHash = currentHash,
            expectedHash = expectedHash,
            status = status
        )
    }

    /**
     * Finds a file in document or webp storage matching a name or query.
     */
    fun findFileByNameOrPrefix(context: Context, query: String): File? {
        val dirs = listOf(
            DocumentImageStore.docDir(context),
            LocalImageStore.imageDir(context)
        )
        val clean = query.trim().lowercase()
        for (dir in dirs) {
            if (!dir.exists()) continue
            val match = dir.listFiles { f ->
                f.isFile && f.name.lowercase().contains(clean) && !f.name.endsWith(".sha256")
            }?.firstOrNull()
            if (match != null) return match
        }
        return null
    }
}
