package com.projectkaka.inventory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Parent container representing a sovereign document (e.g. prescription, warranty, receipt, ID).
 * Real-world documents can have 1 to N pages, which are stored as [DocumentPageEntity].
 */
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["doc_type"]),
        Index(value = ["issue_date"]),
        Index(value = ["expiry_date"]),
        Index(value = ["linked_item_id"]),
        Index(value = ["is_archived"])
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "doc_type")
    val docType: String, // "PRESCRIPTION", "WARRANTY", "RECEIPT", "ID_COPY", "CONTRACT", "OTHER"

    @ColumnInfo(name = "page_count")
    val pageCount: Int = 1,

    @ColumnInfo(name = "cover_image_path")
    val coverImagePath: String, // Cached path of Page 1 for rapid feed/list rendering

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "issue_date")
    val issueDate: Long, // Epoch millis when issued

    @ColumnInfo(name = "expiry_date")
    val expiryDate: Long? = null, // Nullable epoch millis

    @ColumnInfo(name = "linked_item_id")
    val linkedItemId: Int? = null, // Optional FK to items.id

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false
)
