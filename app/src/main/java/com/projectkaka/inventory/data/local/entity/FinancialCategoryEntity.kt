package com.projectkaka.inventory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An expense or income category for classifying transactions.
 *
 * [aliases] is a comma-separated list used by the `f/` parser so the user
 * can type Bengali slang or abbreviations instead of full category names.
 * Example: `"khabar,lunch,dinner,breakfast,meal,eat,snack,tiffin,cha,tea,bhat"`.
 */
@Entity(
    tableName = "financial_categories",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["type"])
    ]
)
data class FinancialCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: CategoryType = CategoryType.EXPENSE,

    @ColumnInfo(name = "aliases")
    val aliases: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Returns true if [input] (lowercased) matches the name or any alias. */
    fun matchesInput(input: String): Boolean {
        return com.projectkaka.inventory.util.SearchHelper.matchesAlias(input, name, aliases)
    }

    /** Strict match against the full name or an alias. */
    fun matchesExactInput(input: String): Boolean {
        return com.projectkaka.inventory.util.SearchHelper.matchesExact(input, name, aliases)
    }
}
