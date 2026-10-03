package com.projectkaka.inventory.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.projectkaka.inventory.data.local.entity.DocumentEntity
import com.projectkaka.inventory.data.local.entity.DocumentPageEntity

/**
 * Composite relation containing a parent [DocumentEntity] and its sorted child [DocumentPageEntity] list.
 */
data class DocumentWithPages(
    @Embedded val document: DocumentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "document_id"
    )
    val pages: List<DocumentPageEntity>
) {
    val sortedPages: List<DocumentPageEntity>
        get() = pages.sortedBy { it.pageNumber }
}
