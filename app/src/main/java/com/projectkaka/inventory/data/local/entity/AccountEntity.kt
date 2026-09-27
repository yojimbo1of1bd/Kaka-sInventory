package com.projectkaka.inventory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

import com.projectkaka.inventory.model.Money

/**
 * A monetary account the user controls.
 *
 * Balances are **computed** — never stored as a mutable field:
 *   balance = opening_balance + SUM(credits) − SUM(debits)
 *
 * This guarantees double-entry integrity: the DB never drifts because there
 * is no second source of truth to get out of sync.
 *
 * [aliases] is a comma-separated list of shorthand names used by the `f/`
 * parser for fuzzy matching.  Example: `"bkash,bikash,bk"`.
 */
@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["type"]),
        Index(value = ["is_active"])
    ]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: AccountType = AccountType.CASH,

    @ColumnInfo(name = "aliases")
    val aliases: String = "",

    @ColumnInfo(name = "opening_balance")
    val openingBalance: Money = Money(0L),

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "balance_minor")
    val balance: Money = Money(0L),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Returns true if [input] (lowercased) matches the name or any alias. */
    fun matchesInput(input: String): Boolean {
        return com.projectkaka.inventory.util.SearchHelper.matchesAlias(input, name, aliases)
    }
}
