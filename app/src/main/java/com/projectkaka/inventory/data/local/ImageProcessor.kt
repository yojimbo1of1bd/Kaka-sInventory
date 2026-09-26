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

/**
 * Phase 2 storage pipeline.
 *
 * Flow: CameraX JPEG (in-memory) -> decode -> honour EXIF rotation -> downscale
 *       -> WebP @ 75% quality -> app-private file, then delete the temp JPEG.
 *
 * Everything runs on Dispatchers.IO. Nothing here touches the network.
 */
object ImageProcessor {

    private const val WEBP_QUALITY = 75          // project spec: 75%
    private const val MAX_DIMENSION = 1600       // keeps WebP files small for burst mode

    data class Result(val file: File, val sizeBytes: Long)

    /**
     * Compresses the JPEG produced by CameraX into a WebP file inside the kaka_webp_store dir.
     * @param sourceJpeg the temp file CameraX wrote
     * @return the resulting WebP file plus its byte size
     */
    suspend fun compressToWebp(
        context: Context,
        sourceJpeg: File
    ): Result = withContext(Dispatchers.IO) {

        val target = LocalImageStore.newImageFile(context, System.currentTimeMillis())

        // 1. Decode bounds first so we never blow memory on huge sensors.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceJpeg.absolutePath, bounds)

        val sampleOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(sourceJpeg.absolutePath, sampleOptions)
            ?: throw IllegalStateException("Could not decode ${sourceJpeg.name}")

        // 2. Apply EXIF orientation so portrait shots aren't sideways.
        val rotated = applyExifRotation(sourceJpeg, decoded)

        // 3. Write WebP at 75% quality.
        FileOutputStream(target).use { out ->
            rotated.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, out)
            out.flush()
        }

        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()

        // 4. The source JPEG was only scratch space — remove it immediately.
        if (sourceJpeg.exists()) sourceJpeg.delete()

        Result(file = target, sizeBytes = target.length())
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
