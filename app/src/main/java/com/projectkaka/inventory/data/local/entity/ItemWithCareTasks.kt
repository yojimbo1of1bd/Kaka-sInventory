package com.projectkaka.inventory.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ItemWithCareTasks(
    @Embedded
    val item: ItemEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "item_id"
    )
    val careTasks: List<CareTaskEntity>
)
