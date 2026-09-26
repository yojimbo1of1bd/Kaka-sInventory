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
}
