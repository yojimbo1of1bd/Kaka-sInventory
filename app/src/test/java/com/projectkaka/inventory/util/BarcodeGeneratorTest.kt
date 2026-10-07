package com.projectkaka.inventory.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BarcodeGeneratorTest {

    @Test
    fun generateQrCode_createsValidBitmap() {
        val bitmap = BarcodeGenerator.generateQrCode("KAKA-BOX:BOX-001", size = 256)
        assertNotNull(bitmap)
        assertEquals(256, bitmap.width)
        assertEquals(256, bitmap.height)
    }

    @Test
    fun generate1DBarcode_createsValidBitmap() {
        val bitmap = BarcodeGenerator.generate1DBarcode("BOX-001", width = 400, height = 120)
        assertNotNull(bitmap)
        assertEquals(400, bitmap.width)
        assertEquals(120, bitmap.height)
    }

    @Test
    fun generatePrintableBoxLabel_createsCompleteBadge() {
        val labelBitmap = BarcodeGenerator.generatePrintableBoxLabel(
            boxName = "Kitchen Glassware",
            boxCode = "BOX-001",
            description = "Fragile dishes",
            itemCount = 12,
            themeColorHex = "#3B82F6",
            isBlackAndWhite = false
        )
        assertNotNull(labelBitmap)
        assertEquals(800, labelBitmap.width)
        assertEquals(1050, labelBitmap.height)

        val bwLabelBitmap = BarcodeGenerator.generatePrintableBoxLabel(
            boxName = "Books & Documents",
            boxCode = "BOX-002",
            description = "Heavy box",
            itemCount = 25,
            themeColorHex = "#10B981",
            isBlackAndWhite = true
        )
        assertNotNull(bwLabelBitmap)
        assertEquals(800, bwLabelBitmap.width)
        assertEquals(1050, bwLabelBitmap.height)
    }
}
