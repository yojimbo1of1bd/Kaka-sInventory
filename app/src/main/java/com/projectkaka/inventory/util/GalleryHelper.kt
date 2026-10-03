package com.projectkaka.inventory.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object GalleryHelper {

    private const val DIRECTORY_NAME = "ProjectKaka"

    /**
     * Copies a local image file (e.g. WebP from app internal storage) into the system's public
     * Gallery under Pictures/ProjectKaka.
     */
    fun saveImageToGallery(context: Context, imagePath: String, title: String): Result<Uri> {
        return runCatching {
            val sourceFile = File(imagePath)
            if (!sourceFile.exists()) {
                error("Source image file does not exist: $imagePath")
            }

            val sanitizedTitle = title.ifBlank { "kaka_item" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val fileName = "${sanitizedTitle}_${System.currentTimeMillis()}.webp"
            val contentResolver = context.contentResolver

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$DIRECTORY_NAME")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = contentResolver.insert(collection, values) ?: error("Failed to create MediaStore entry")

            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                } ?: error("Failed to open output stream for $uri")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                } else {
                    // For legacy Android, notify media scanner
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(sourceFile.absolutePath),
                        arrayOf("image/webp"),
                        null
                    )
                }
                uri
            } catch (t: Throwable) {
                contentResolver.delete(uri, null, null)
                throw t
            }
        }
    }

    /**
     * Saves an in-memory Bitmap (e.g. Visual Belongings Map or QR / Barcode label) into the system's
     * public Gallery under Pictures/ProjectKaka as a PNG or JPEG.
     */
    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        title: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG
    ): Result<Uri> {
        return runCatching {
            val extension = when (format) {
                Bitmap.CompressFormat.JPEG -> "jpg"
                Bitmap.CompressFormat.PNG -> "png"
                else -> "png"
            }
            val mimeType = when (format) {
                Bitmap.CompressFormat.JPEG -> "image/jpeg"
                Bitmap.CompressFormat.PNG -> "image/png"
                else -> "image/png"
            }

            val sanitizedTitle = title.ifBlank { "kaka_export" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val fileName = "${sanitizedTitle}_${System.currentTimeMillis()}.$extension"
            val contentResolver = context.contentResolver

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$DIRECTORY_NAME")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = contentResolver.insert(collection, values) ?: error("Failed to create MediaStore entry")

            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(format, 100, out)) {
                        error("Failed to compress bitmap into output stream")
                    }
                } ?: error("Failed to open output stream for $uri")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                }
                uri
            } catch (t: Throwable) {
                contentResolver.delete(uri, null, null)
                throw t
            }
        }
    }
}
