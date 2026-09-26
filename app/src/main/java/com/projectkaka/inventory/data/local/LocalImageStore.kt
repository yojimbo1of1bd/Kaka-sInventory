package com.projectkaka.inventory.data.local

import android.content.Context
import java.io.File

/**
 * Owns the on-device WebP directory. Everything stays under the app sandbox,
 * so the app state is a single sovereign unit: one .db file + one image folder.
 */
object LocalImageStore {

    private const val DIR_NAME = "kaka_webp_store"

    fun imageDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun newImageFile(context: Context, timestamp: Long = System.currentTimeMillis()): File =
        File(imageDir(context), "kaka_$timestamp.webp")
}
