package com.projectkaka.inventory.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "basket_items",
    primaryKeys = ["basket_id", "item_id"],
    foreignKeys = [
        ForeignKey(
            entity = BasketEntity::class,
            parentColumns = ["id"],
            childColumns = ["basket_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["item_id"])
    ]
)
data class BasketItemCrossRef(
    @ColumnInfo(name = "basket_id")
    val basketId: Int,

    @ColumnInfo(name = "item_id")
    val itemId: Int,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
