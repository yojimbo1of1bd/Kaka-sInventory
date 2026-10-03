package com.projectkaka.inventory.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.projectkaka.inventory.data.repository.ItemExportRow
import com.projectkaka.inventory.model.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Generates an infographic diagram / visual belongings map as a high-resolution PNG bitmap.
 * 100% offline, zero-network.
 */
object VisualMapGenerator {

    fun generateVisualMap(
        categoryTitle: String,
        items: List<ItemExportRow>
    ): Bitmap {
        val width = 1080
        val padding = 40f
        val contentWidth = width - (padding * 2)

        // Calculate dynamic height based on item count
        // Header + Metrics + Location stats + Item cards + Footer
        val cardHeight = 120f
        val cardSpacing = 16f
        val itemsCount = items.size
        val itemsSectionHeight = if (itemsCount == 0) 150f else (itemsCount * (cardHeight + cardSpacing))
        val totalHeight = max(1350f, 620f + itemsSectionHeight + 100f).toInt()

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background (Sleek Dark Modern #0F172A)
        canvas.drawColor(Color.rgb(15, 23, 42))

        var currentY = padding

        // 2. Top Banner Header
        val brandPaint = Paint().apply {
            color = Color.rgb(56, 189, 248) // Sky blue
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("PROJECT KAKA • VISUAL BELONGINGS MAP", padding, currentY + 30f, brandPaint)

        currentY += 45f

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val displayCategory = if (categoryTitle.isBlank() || categoryTitle.equals("all", ignoreCase = true)) {
            "Complete Inventory Overview"
        } else {
            "Category: ${categoryTitle.replaceFirstChar { it.uppercase() }}"
        }
        canvas.drawText(displayCategory, padding, currentY + 50f, titlePaint)

        currentY += 65f

        val dateStr = SimpleDateFormat("MMMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date())
        val datePaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("Generated on $dateStr • Sovereign Local Store", padding, currentY + 25f, datePaint)

        currentY += 55f

        // 3. Metrics Overview Cards (4 cards in a row)
        val totalValuationMinorUnits = items.sumOf { it.estimatedValue.minorUnits }
        val distinctLocations = items.map { it.locationTag.ifBlank { "Unassigned" } }.distinct().size
        val totalCareTasks = items.sumOf { it.careTasks.size }

        val metricCardWidth = (contentWidth - (3 * 16f)) / 4f
        val metricCardHeight = 120f

        val metrics = listOf(
            Pair("TOTAL ITEMS", "${items.size}"),
            Pair("VALUATION", Money(totalValuationMinorUnits).format()),
            Pair("LOCATIONS", "$distinctLocations Rooms"),
            Pair("CARE TASKS", "$totalCareTasks Active")
        )

        val cardBgPaint = Paint().apply {
            color = Color.rgb(30, 41, 59) // Slate 800
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cardBorderPaint = Paint().apply {
            color = Color.rgb(51, 65, 85) // Slate 700
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        val metricLabelPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val metricValuePaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        for (i in metrics.indices) {
            val cardLeft = padding + i * (metricCardWidth + 16f)
            val rect = RectF(cardLeft, currentY, cardLeft + metricCardWidth, currentY + metricCardHeight)
            canvas.drawRoundRect(rect, 16f, 16f, cardBgPaint)
            canvas.drawRoundRect(rect, 16f, 16f, cardBorderPaint)

            canvas.drawText(metrics[i].first, cardLeft + 16f, currentY + 38f, metricLabelPaint)
            
            // Value text with fallback size for long values
            val valText = metrics[i].second
            if (valText.length > 10) {
                metricValuePaint.textSize = 20f
            } else {
                metricValuePaint.textSize = 26f
            }
            canvas.drawText(valText, cardLeft + 16f, currentY + 84f, metricValuePaint)
        }

        currentY += metricCardHeight + 35f

        // 4. Location Breakdown Pills
        val locSectionPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Location Breakdown", padding, currentY + 24f, locSectionPaint)
        currentY += 40f

        val locationCounts = items.groupBy { it.locationTag.ifBlank { "Unassigned" } }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        val pillBgPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val pillTextPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isAntiAlias = true
        }

        var pillX = padding
        val pillHeight = 44f
        for ((locName, count) in locationCounts.take(6)) {
            val pillText = "$locName ($count)"
            val textWidth = pillTextPaint.measureText(pillText)
            val pillWidth = textWidth + 32f

            if (pillX + pillWidth > width - padding) {
                break // Don't overflow row
            }

            val pillRect = RectF(pillX, currentY, pillX + pillWidth, currentY + pillHeight)
            canvas.drawRoundRect(pillRect, 22f, 22f, pillBgPaint)
            canvas.drawText(pillText, pillX + 16f, currentY + 30f, pillTextPaint)

            pillX += pillWidth + 12f
        }

        currentY += pillHeight + 35f

        // 5. Items List Cards
        val itemsHeaderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Cataloged Belongings (${items.size})", padding, currentY + 24f, itemsHeaderPaint)
        currentY += 42f

        val itemNamePaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val itemSubPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val itemValuePaint = Paint().apply {
            color = Color.rgb(52, 211, 153) // Emerald green
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val taskCountPaint = Paint().apply {
            color = Color.rgb(251, 191, 36) // Amber
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        if (items.isEmpty()) {
            val emptyPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            canvas.drawText("No items found under this category.", padding + 20f, currentY + 60f, emptyPaint)
            currentY += 100f
        } else {
            for (item in items) {
                val cardRect = RectF(padding, currentY, width - padding, currentY + cardHeight)
                canvas.drawRoundRect(cardRect, 14f, 14f, cardBgPaint)
                canvas.drawRoundRect(cardRect, 14f, 14f, cardBorderPaint)

                // Item Name (Left)
                val safeName = if (item.name.length > 34) item.name.take(31) + "..." else item.name
                canvas.drawText(safeName, padding + 24f, currentY + 45f, itemNamePaint)

                // Category & Location (Left Sub)
                val subText = "${item.category.ifBlank { "Uncategorized" }}  •  Location: ${item.locationTag.ifBlank { "Unassigned" }}"
                canvas.drawText(subText, padding + 24f, currentY + 88f, itemSubPaint)

                // Estimated Value (Right)
                val valueText = item.estimatedValue.format()
                canvas.drawText(valueText, width - padding - 24f, currentY + 48f, itemValuePaint)

                // Care tasks / Status (Right Sub)
                val statusText = if (item.careTasks.isNotEmpty()) "${item.careTasks.size} task(s) active" else item.status.uppercase()
                canvas.drawText(statusText, width - padding - 24f, currentY + 88f, taskCountPaint)

                currentY += cardHeight + cardSpacing
            }
        }

        // 6. Footer
        currentY += 20f
        val footerPaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SOVEREIGN LOCAL INVENTORY  •  100% OFFLINE ENCRYPTED & VERIFIED", width / 2f, currentY + 30f, footerPaint)

        return bitmap
    }
}
