package com.projectkaka.inventory.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.projectkaka.inventory.data.local.relation.DocumentWithPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sovereign PDF generation engine using Android's native android.graphics.pdf.PdfDocument.
 * Zero external libraries, 100% offline.
 */
object DocumentPdfGenerator {

    private const val DIRECTORY_NAME = "ProjectKaka/Documents"
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val MARGIN = 30f

    /**
     * Renders a multi-page [DocumentWithPages] to a PDF and saves it to the device's
     * Downloads/ProjectKaka/Documents folder.
     */
    suspend fun savePdfToDownloads(
        context: Context,
        docWithPages: DocumentWithPages
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitizedTitle = docWithPages.document.title.ifBlank { "document" }
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${sanitizedTitle}_$timestamp.pdf"

            val pdfDocument = createPdfDocument(docWithPages)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DIRECTORY_NAME")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                    ?: error("Failed to create MediaStore entry for $fileName")

                resolver.openOutputStream(uri)?.use { out ->
                    pdfDocument.writeTo(out)
                } ?: error("Failed to open output stream for $uri")

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIRECTORY_NAME).apply {
                    if (!exists()) mkdirs()
                }
                val target = File(dir, fileName)
                FileOutputStream(target).use { out ->
                    pdfDocument.writeTo(out)
                }
            }

            pdfDocument.close()
            fileName
        }
    }

    /**
     * Renders a multi-page [DocumentWithPages] to a temporary cache file and returns a
     * shareable content URI via FileProvider for instant sharing (e.g. WhatsApp, Email).
     */
    suspend fun createShareablePdf(
        context: Context,
        docWithPages: DocumentWithPages
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitizedTitle = docWithPages.document.title.ifBlank { "document" }
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val cacheDir = File(context.cacheDir, "shared_documents").apply { if (!exists()) mkdirs() }
            val cacheFile = File(cacheDir, "${sanitizedTitle}_${System.currentTimeMillis()}.pdf")

            val pdfDocument = createPdfDocument(docWithPages)
            FileOutputStream(cacheFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )
        }
    }

    /**
     * Returns a list of FileProvider content URIs for each page image file in this document.
     */
    fun getShareableImageUris(
        context: Context,
        docWithPages: DocumentWithPages
    ): List<Uri> {
        val uris = mutableListOf<Uri>()
        docWithPages.sortedPages.forEach { page ->
            if (page.imagePath.isNotBlank()) {
                val file = File(page.imagePath)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    uris.add(uri)
                }
            }
        }
        return uris
    }

    private fun createPdfDocument(docWithPages: DocumentWithPages): PdfDocument {
        val pdfDocument = PdfDocument()
        val pages = docWithPages.sortedPages
        val totalPages = maxOf(1, pages.size)

        val headerPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
            isAntiAlias = true
        }

        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            isAntiAlias = true
        }

        pages.forEachIndexed { index, pageEntity ->
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
            val pdfPage = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = pdfPage.canvas

            // Header text
            val titleText = docWithPages.document.title.take(40)
            canvas.drawText(titleText, MARGIN, MARGIN, headerPaint)

            // Image area
            val imgFile = File(pageEntity.imagePath)
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                if (bitmap != null) {
                    val maxW = A4_WIDTH - (MARGIN * 2)
                    val maxH = A4_HEIGHT - (MARGIN * 2) - 40f // room for header/footer

                    val scale = minOf(maxW / bitmap.width.toFloat(), maxH / bitmap.height.toFloat())
                    val drawW = bitmap.width * scale
                    val drawH = bitmap.height * scale

                    val left = MARGIN + (maxW - drawW) / 2f
                    val top = MARGIN + 20f + (maxH - drawH) / 2f
                    val destRect = RectF(left, top, left + drawW, top + drawH)

                    canvas.drawBitmap(bitmap, null, destRect, null)
                    bitmap.recycle()
                }
            }

            // Footer text
            val footerText = "Page ${index + 1} of $totalPages • ProjectKaka Document Vault"
            canvas.drawText(footerText, MARGIN, A4_HEIGHT - 15f, footerPaint)

            pdfDocument.finishPage(pdfPage)
        }

        return pdfDocument
    }
}
