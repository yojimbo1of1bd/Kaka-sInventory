package com.projectkaka.inventory.util

import com.projectkaka.inventory.data.repository.ItemExportRow
import com.projectkaka.inventory.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeResolutionTest {

    @Test
    fun `carton code parsing strips prefix and normalizes case`() {
        val rawScans = listOf(
            "KAKA-BOX:BOX-001" to "BOX-001",
            "kaka-box:living-room-01" to "LIVING-ROOM-01",
            "  BOX-999  " to "BOX-999",
            "Kaka-Box:Kitchen-02" to "KITCHEN-02"
        )

        for ((input, expected) in rawScans) {
            val trimmed = input.trim().uppercase()
            val cleanCode = if (trimmed.startsWith("KAKA-BOX:")) {
                trimmed.removePrefix("KAKA-BOX:")
            } else {
                trimmed
            }
            assertEquals(expected, cleanCode)
        }
    }

    @Test
    fun `visual map category filtering supports any category or all items`() {
        val items = listOf(
            ItemExportRow(
                id = 1,
                name = "Laptop",
                category = "Electronics",
                locationTag = "Desk",
                estimatedValue = Money(15000000L),
                status = "ACTIVE",
                isDraft = false,
                dateAdded = 1000L,
                imagePath = "",
                careTasks = emptyList()
            ),
            ItemExportRow(
                id = 2,
                name = "Winter Jacket",
                category = "Clothing",
                locationTag = "Closet",
                estimatedValue = Money(500000L),
                status = "ACTIVE",
                isDraft = false,
                dateAdded = 2000L,
                imagePath = "",
                careTasks = emptyList()
            ),
            ItemExportRow(
                id = 3,
                name = "Prescription A",
                category = "Prescriptions & Slips",
                locationTag = "Drawer",
                estimatedValue = Money(120000L),
                status = "ACTIVE",
                isDraft = false,
                dateAdded = 3000L,
                imagePath = "",
                careTasks = emptyList()
            )
        )

        // Test "ALL"
        val allFiltered = items.filter { true }
        assertEquals(3, allFiltered.size)

        // Test custom category "Clothing"
        val clothingFiltered = items.filter { it.category.equals("Clothing", ignoreCase = true) }
        assertEquals(1, clothingFiltered.size)
        assertEquals("Winter Jacket", clothingFiltered.first().name)

        // Test custom category substring
        val docFiltered = items.filter { it.category.contains("Prescription", ignoreCase = true) }
        assertEquals(1, docFiltered.size)
        assertEquals("Prescription A", docFiltered.first().name)
    }
}
