package com.projectkaka.inventory.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun testFromDecimalString_precision() {
        assertEquals(115L, Money.fromDecimalString("1.15").minorUnits)
        assertEquals(1005L, Money.fromDecimalString("10.05").minorUnits)
        assertEquals(1L, Money.fromDecimalString("0.01").minorUnits)
        assertEquals(-115L, Money.fromDecimalString("-1.15").minorUnits)

        // 1.15 String -> BigDecimal -> 115 minor units
        // fromDecimalString("1.15") should be 115.
    }
}
