package com.projectkaka.inventory.model

import java.text.NumberFormat
import java.util.Locale

@JvmInline
value class Money(val minorUnits: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(this.minorUnits + other.minorUnits)
    operator fun minus(other: Money) = Money(this.minorUnits - other.minorUnits)
    override fun compareTo(other: Money): Int = this.minorUnits.compareTo(other.minorUnits)

    fun format(locale: Locale = Locale.getDefault()): String {
        val major = minorUnits / 100.0
        return NumberFormat.getCurrencyInstance(locale).format(major)
    }

    companion object {
        /**
         * Converts a user-input decimal string into Money (integer minor units).
         * Uses Math.round to ensure explicit half-up rounding, avoiding truncation
         * issues where 10.01 * 100.0 becomes 1000.9999999 -> 1000 instead of 1001.
         */
        fun fromDecimalString(value: String): Money {
            val d = value.toDoubleOrNull() ?: 0.0
            return fromDouble(d)
        }

        /**
         * Converts a Double into Money (integer minor units).
         */
        fun fromDouble(value: Double): Money {
            return Money(Math.round(value * 100))
        }
    }
}
