package com.projectkaka.inventory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One side of a double-entry financial transaction.
 *
 * [isCredit] determines direction:
 *   - `true`  → money flows IN to [accountId] (income, deposit, refund).
 *   - `false` → money flows OUT of [accountId] (expense, withdrawal).
 *
 * [amount] is always positive; direction is encoded by [isCredit].
 *
 * Account balance is never stored — it is computed as:
 *   `opening_balance + SUM(amount WHERE isCredit) − SUM(amount WHERE NOT isCredit)`
 */
@Entity(
    tableName = "financial_transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FinancialCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["category_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["is_credit"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "account_id")
    val accountId: Int,

    @ColumnInfo(name = "category_id")
    val categoryId: Int,

    @ColumnInfo(name = "is_credit")
    val isCredit: Boolean,

    @ColumnInfo(name = "note")
    val note: String = "",

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
