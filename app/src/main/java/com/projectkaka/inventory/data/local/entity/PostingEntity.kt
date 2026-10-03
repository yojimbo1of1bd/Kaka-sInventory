package com.projectkaka.inventory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.projectkaka.inventory.model.Money

/**
 * Represents a single line item (posting) within a journal entry.
 * Belongs to one journal entry and posts to one account.
 * [isCredit] determines whether the posting is a debit (false) or credit (true).
 */
@Entity(
    tableName = "postings",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journal_entry_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["journal_entry_id"]),
        Index(value = ["account_id"])
    ]
)
data class PostingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "journal_entry_id")
    val journalEntryId: Int,
    
    @ColumnInfo(name = "account_id")
    val accountId: Int,
    
    @ColumnInfo(name = "amount")
    val amount: Money,
    
    @ColumnInfo(name = "is_credit")
    val isCredit: Boolean,
    
    @ColumnInfo(name = "note")
    val note: String = ""
)
