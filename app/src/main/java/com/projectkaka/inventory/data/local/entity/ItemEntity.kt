package com.projectkaka.inventory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    indices = [
        Index(value = ["status"]),
        Index(value = ["category"]),
        Index(value = ["is_draft"])
    ]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String = "Draft - ${System.currentTimeMillis()}",

    @ColumnInfo(name = "category")
    val category: String = "Uncategorized",

    @ColumnInfo(name = "location_tag")
    val locationTag: String = "",

    @ColumnInfo(name = "estimated_value")
    val estimatedValue: Double = 0.0,

    @ColumnInfo(name = "image_path")
    val imagePath: String,

    @ColumnInfo(name = "is_draft")
    val isDraft: Boolean = true,

    @ColumnInfo(name = "status")
    val status: ItemStatus = ItemStatus.ACTIVE,

    @ColumnInfo(name = "date_added")
    val dateAdded: Long = System.currentTimeMillis()
)
