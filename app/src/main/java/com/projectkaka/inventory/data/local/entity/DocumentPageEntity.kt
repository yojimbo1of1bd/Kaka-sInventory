package com.projectkaka.inventory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Child page belonging to a [DocumentEntity].
 * Supports multi-page documents (1..N pages) with high-resolution image paths.
 */
@Entity(
    tableName = "document_pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["document_id"]),
        Index(value = ["document_id", "page_number"], unique = true)
    ]
)
data class DocumentPageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "document_id")
    val documentId: Int,

    @ColumnInfo(name = "page_number")
    val pageNumber: Int, // 1-indexed (Page 1, 2, 3...)

    @ColumnInfo(name = "image_path")
    val imagePath: String, // High-res 95% WebP in filesDir/kaka_doc_store

    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String = "",

    @ColumnInfo(name = "page_note")
    val pageNote: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
