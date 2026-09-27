package com.projectkaka.inventory.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun testFromDouble_truncationPolicy() {
        // Policy: round to nearest minor unit (e.g., 100 * value)
        assertEquals(115L, Money.fromDouble(1.15).minorUnits)
        assertEquals(1005L, Money.fromDouble(10.05).minorUnits)
        assertEquals(1L, Money.fromDouble(0.01).minorUnits)
        assertEquals(-115L, Money.fromDouble(-1.15).minorUnits)

        // Floating point precision cases that used to fail with simple toLong()
        // 1.15 * 100 = 114.99999999999999 -> toLong() = 114
        // fromDouble(1.15) should be 115.
    }
}
