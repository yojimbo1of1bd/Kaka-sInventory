package com.projectkaka.inventory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single financial event (journal entry).
 * A journal entry must contain at least two postings to maintain the
 * fundamental accounting equation (Assets = Liabilities + Equity).
 */
@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["status"]),
        Index(value = ["approval_status"])
    ]
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "description")
    val description: String = "",
    
    @ColumnInfo(name = "status")
    val status: JournalStatus = JournalStatus.POSTED,
    
    @ColumnInfo(name = "approval_status")
    val approvalStatus: ApprovalStatus = ApprovalStatus.APPROVED,
    
    @ColumnInfo(name = "created_by")
    val createdBy: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
