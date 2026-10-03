package com.projectkaka.inventory.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * Utility for generating high quality QR codes, 1D Code-128 barcodes,
 * and printable packaging labels for moving boxes / baskets.
 * 100% offline, zero-network.
 */
object BarcodeGenerator {

    /**
     * Generates a 2D QR Code Bitmap.
     * @param content The string payload encoded in the QR code.
     * @param size Width and height in pixels.
     * @param foregroundColor ARGB color for the dark modules (black or custom theme color).
     * @param backgroundColor ARGB color for the light modules (default white).
     */
    fun generateQrCode(
        content: String,
        size: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1) // 1 module quiet zone
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
        }

        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints
        )

        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) foregroundColor else backgroundColor
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Generates a 1D Code-128 Barcode Bitmap.
     * @param content Alphanumeric text to encode.
     * @param width Width in pixels.
     * @param height Height in pixels.
     * @param foregroundColor Bar color.
     * @param backgroundColor Background color.
     */
    fun generate1DBarcode(
        content: String,
        width: Int = 600,
        height: Int = 180,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        // Code-128 requires ASCII alphanumeric characters. Fallback to sanitised string.
        val safeContent = content.filter { it.code in 32..126 }.ifEmpty { "BOX-001" }

        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 10)
        }

        val bitMatrix = Code128Writer().encode(
            safeContent,
            BarcodeFormat.CODE_128,
            width,
            height,
            hints
        )

        val matrixWidth = bitMatrix.width
        val matrixHeight = bitMatrix.height
        val pixels = IntArray(matrixWidth * matrixHeight)

        for (y in 0 until matrixHeight) {
            val offset = y * matrixWidth
            for (x in 0 until matrixWidth) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) foregroundColor else backgroundColor
            }
        }

        return Bitmap.createBitmap(pixels, matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
    }

    /**
     * Generates a complete, ready-to-print carton label badge bitmap (e.g. 800 x 1050 px)
     * containing Box Name, Box Code, Category/Room, QR Code, 1D Barcode, and item count.
     */
    fun generatePrintableBoxLabel(
        boxName: String,
        boxCode: String,
        description: String,
        itemCount: Int,
        themeColorHex: String,
        isBlackAndWhite: Boolean = false
    ): Bitmap {
        val width = 800
        val height = 1050
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val accentColor = if (isBlackAndWhite) {
            Color.BLACK
        } else {
            try {
                Color.parseColor(themeColorHex)
            } catch (_: Exception) {
                Color.parseColor("#3B82F6")
            }
        }

        // Fill background white
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint().apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 14f
            isAntiAlias = true
        }

        // Outer border with rounded corners
        val borderMargin = 20f
        canvas.drawRoundRect(
            RectF(borderMargin, borderMargin, width - borderMargin, height - borderMargin),
            24f, 24f,
            borderPaint
        )

        // Top Header Banner
        val headerPaint = Paint().apply {
            color = accentColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(
            borderMargin,
            borderMargin,
            width - borderMargin,
            borderMargin + 130f,
            headerPaint
        )

        // Header Title: App / Moving Label
        val headerTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("PROJECT KAKA • MOVING BOX", width / 2f, borderMargin + 50f, headerTitlePaint)

        // Header Box Code
        val headerCodePaint = Paint().apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(boxCode, width / 2f, borderMargin + 105f, headerCodePaint)

        // Box Name (Big Bold)
        val namePaint = Paint().apply {
            color = Color.rgb(20, 24, 30)
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val safeName = if (boxName.length > 25) boxName.take(23) + "..." else boxName
        canvas.drawText(safeName, width / 2f, 215f, namePaint)

        // Description / Notes
        val descPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val safeDesc = if (description.isNotBlank()) {
            if (description.length > 38) description.take(35) + "..." else description
        } else {
            "Contains $itemCount packed items"
        }
        canvas.drawText(safeDesc, width / 2f, 260f, descPaint)

        // QR Code Payload: e.g. "KAKA-BOX:BOX-001"
        val qrPayload = "KAKA-BOX:$boxCode"
        val qrSize = 360
        val qrBitmap = generateQrCode(
            content = qrPayload,
            size = qrSize,
            foregroundColor = if (isBlackAndWhite) Color.BLACK else accentColor,
            backgroundColor = Color.WHITE
        )

        val qrLeft = (width - qrSize) / 2f
        val qrTop = 295f
        canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)

        // Instruction under QR
        val qrHintPaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SCAN WITH PHONE CAMERA TO VIEW CONTENTS", width / 2f, qrTop + qrSize + 30f, qrHintPaint)

        // Divider
        val dividerPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 3f
        }
        val dividerY = 720f
        canvas.drawLine(50f, dividerY, width - 50f, dividerY, dividerPaint)

        // 1D Barcode
        val barcodeBitmap = generate1DBarcode(
            content = boxCode,
            width = 660,
            height = 140,
            foregroundColor = Color.BLACK,
            backgroundColor = Color.WHITE
        )
        val barcodeLeft = (width - 660) / 2f
        val barcodeTop = 750f
        canvas.drawBitmap(barcodeBitmap, barcodeLeft, barcodeTop, null)

        // Barcode Text Below
        val barcodeTextPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("* $boxCode *", width / 2f, barcodeTop + 140f + 32f, barcodeTextPaint)

        // Footer details
        val footerPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("PACKED ITEMS: $itemCount  |  OFFLINE VERIFIED", width / 2f, 985f, footerPaint)

        return bitmap
    }
}
