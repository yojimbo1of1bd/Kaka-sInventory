package com.projectkaka.inventory.data.local.entity

import androidx.room.ColumnInfo

import com.projectkaka.inventory.model.Money

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
enum class TransactionType {
    EXPENSE, INCOME, TRANSFER, DEBT_ISSUE, DEBT_SETTLE, ADJUSTMENT
}

data class TransactionEntity(
    val id: Int = 0,

    @ColumnInfo(name = "amount")
    val amount: Money,

    @ColumnInfo(name = "account_id")
    val accountId: Int,

    @ColumnInfo(name = "category_id")
    val categoryId: Int?,

    @ColumnInfo(name = "transfer_id")
    val transferId: String? = null,

    @ColumnInfo(name = "counter_account_id")
    val counterAccountId: Int? = null,

    @ColumnInfo(name = "type")
    val type: TransactionType = TransactionType.EXPENSE,

    @ColumnInfo(name = "is_credit")
    val isCredit: Boolean,

    @ColumnInfo(name = "note")
    val note: String = "",

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
