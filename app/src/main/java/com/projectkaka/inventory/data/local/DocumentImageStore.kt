package com.projectkaka.inventory.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * High-quality storage engine specifically for document scans and pages.
 * Stored in app-private sandbox: filesDir/kaka_doc_store.
 *
 * Uses 95% WebP quality and 2400px max dimension to ensure fine print, doctor signatures,
 * serial numbers, and medication dosages remain crystal-clear.
 */
object DocumentImageStore {

    private const val DIR_NAME = "kaka_doc_store"
    private const val WEBP_QUALITY = 95
    private const val MAX_DIMENSION = 2400

    data class PageImageResult(val file: File, val sizeBytes: Long)

    fun docDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun newDocFile(context: Context, prefix: String = "doc_page"): File {
        val unique = UUID.randomUUID().toString().take(8)
        val timestamp = System.currentTimeMillis()
        return File(docDir(context), "${prefix}_${timestamp}_$unique.webp")
    }

    /**
     * Compresses a captured JPEG file into a high-res 95% WebP document page.
     * Deletes [sourceJpeg] scratch file after compression.
     */
    suspend fun compressPageToWebp(
        context: Context,
        sourceJpeg: File
    ): PageImageResult = withContext(Dispatchers.IO) {
        val target = newDocFile(context)

        // 1. Decode bounds
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceJpeg.absolutePath, bounds)

        val sampleOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(sourceJpeg.absolutePath, sampleOptions)
            ?: throw IllegalStateException("Could not decode document page: ${sourceJpeg.name}")

        // 2. Apply EXIF orientation
        val rotated = applyExifRotation(sourceJpeg, decoded)

        // 3. Save as WebP with 95% quality
        FileOutputStream(target).use { out ->
            rotated.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, out)
            out.flush()
        }

        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()

        // 4. Delete temp JPEG scratch file
        if (sourceJpeg.exists()) sourceJpeg.delete()

        // 5. Compute and register SHA-256 integrity hash
        com.projectkaka.inventory.util.ImageIntegrityManager.registerImageHash(target)

        PageImageResult(file = target, sizeBytes = target.length())
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxDimension && h / 2 >= maxDimension) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun applyExifRotation(source: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }
}
