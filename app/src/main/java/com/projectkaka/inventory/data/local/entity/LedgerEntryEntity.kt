package com.projectkaka.inventory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

import com.projectkaka.inventory.model.Money

/**
 * A personal ledger entry tracking money owed to or from a contact.
 *
 * This is separate from the transaction table because dues/liabilities
 * are commitments, not completed money movements.  When a due is settled,
 * the user records a transaction (via `f/`) and flips [isSettled].
 */
@Entity(
    tableName = "ledger_entries",
    indices = [
        Index(value = ["is_settled"]),
        Index(value = ["type"]),
        Index(value = ["due_date"]),
        Index(value = ["account_id"]),
        Index(value = ["linked_transaction_id"])
    ]
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "contact_name")
    val contactName: String,

    @ColumnInfo(name = "contact_phone")
    val contactPhone: String = "",

    @ColumnInfo(name = "amount")
    val amount: Money,

    @ColumnInfo(name = "type")
    val type: LedgerType,

    @ColumnInfo(name = "is_settled")
    val isSettled: Boolean = false,

    @ColumnInfo(name = "note")
    val note: String = "",

    @ColumnInfo(name = "aliases")
    val aliases: String = "",

    @ColumnInfo(name = "due_date")
    val dueDate: Long? = null,

    @ColumnInfo(name = "account_id")
    val accountId: Int? = null,

    @ColumnInfo(name = "linked_transaction_id")
    val linkedTransactionId: Int? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Returns true if [input] (lowercased) matches the contactName or any alias. */
    fun matchesInput(input: String): Boolean {
        return com.projectkaka.inventory.util.SearchHelper.matchesAlias(input, contactName, aliases)
    }
}
