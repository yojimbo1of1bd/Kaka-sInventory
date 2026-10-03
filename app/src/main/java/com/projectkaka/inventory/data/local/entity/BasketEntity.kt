package com.projectkaka.inventory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "baskets",
    indices = [
        Index(value = ["code"], unique = true)
    ]
)
data class BasketEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#3F51B5",

    @ColumnInfo(name = "is_packed")
    val isPacked: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
