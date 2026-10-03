package com.projectkaka.inventory.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projectkaka.inventory.data.repository.ItemExportRow
import com.projectkaka.inventory.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualMapGeneratorTest {

    @Test
    fun generateVisualMap_emptyItems_generatesValidInfographic() {
        val bitmap = VisualMapGenerator.generateVisualMap("Electronics", emptyList())
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertTrue(bitmap.height >= 1350)
    }

    @Test
    fun generateVisualMap_withItems_generatesPopulatedInfographic() {
        val sampleItems = listOf(
            ItemExportRow(
                id = 1,
                name = "MacBook Pro 16",
                category = "Electronics",
                locationTag = "Home Office",
                estimatedValue = Money(25000000L), // 250,000.00
                status = "ACTIVE",
                isDraft = false,
                dateAdded = System.currentTimeMillis(),
                imagePath = "",
                careTasks = emptyList()
            ),
            ItemExportRow(
                id = 2,
                name = "Sony WH-1000XM5",
                category = "Electronics",
                locationTag = "Bedroom",
                estimatedValue = Money(3800000L), // 38,000.00
                status = "ACTIVE",
                isDraft = false,
                dateAdded = System.currentTimeMillis(),
                imagePath = "",
                careTasks = emptyList()
            )
        )

        val bitmap = VisualMapGenerator.generateVisualMap("Electronics", sampleItems)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertTrue(bitmap.height >= 1350)
    }
}
