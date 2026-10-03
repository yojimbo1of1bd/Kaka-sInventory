package com.projectkaka.inventory.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

@JvmInline
value class Money(val minorUnits: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(this.minorUnits + other.minorUnits)
    operator fun minus(other: Money) = Money(this.minorUnits - other.minorUnits)
    override fun compareTo(other: Money): Int = this.minorUnits.compareTo(other.minorUnits)

    fun format(locale: Locale = Locale.getDefault()): String {
        val major = BigDecimal(minorUnits).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        return NumberFormat.getCurrencyInstance(locale).format(major)
    }

    companion object {
        val ZERO = Money(0L)

        /**
         * Converts a user-input decimal string into Money (integer minor units).
         * Throws IllegalArgumentException on malformed, non-finite, or out-of-range input.
         */
        fun fromDecimalString(value: String): Money {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return ZERO
            return try {
                val bd = BigDecimal(trimmed)
                Money(bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact())
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid monetary value: $value")
            }
        }
    }
}
