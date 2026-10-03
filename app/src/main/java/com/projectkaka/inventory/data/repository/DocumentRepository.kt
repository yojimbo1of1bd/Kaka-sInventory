package com.projectkaka.inventory.data.repository

import com.projectkaka.inventory.data.local.entity.DocumentEntity
import com.projectkaka.inventory.data.local.entity.DocumentPageEntity
import com.projectkaka.inventory.data.local.relation.DocumentWithPages
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeActiveDocuments(): Flow<List<DocumentEntity>>
    fun observeArchivedDocuments(): Flow<List<DocumentEntity>>
    fun observeByType(docType: String): Flow<List<DocumentEntity>>
    fun observeForItem(itemId: Int): Flow<List<DocumentEntity>>
    fun observeDocumentWithPages(documentId: Int): Flow<DocumentWithPages?>
    fun observeDocumentCount(): Flow<Int>
    fun observeDocTypes(): Flow<List<String>>
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>

    suspend fun getDocumentById(id: Int): DocumentEntity?
    suspend fun getDocumentWithPages(id: Int): DocumentWithPages?
    suspend fun getAllDocumentsWithPagesSnapshot(): List<DocumentWithPages>

    /**
     * Atomically saves a document and creates its child pages in a single transaction.
     * Updates coverImagePath and pageCount on the parent entity.
     */
    suspend fun saveDocumentWithPages(
        document: DocumentEntity,
        pageImagePaths: List<String>
    ): Long

    /**
     * Appends a new page to an existing document and increments pageCount.
     */
    suspend fun addPageToDocument(
        documentId: Int,
        imagePath: String,
        pageNote: String = ""
    ): Long

    suspend fun updateDocument(document: DocumentEntity)
    suspend fun deleteDocument(documentId: Int)
    suspend fun archiveDocument(documentId: Int)
    suspend fun unarchiveDocument(documentId: Int)
    suspend fun deletePage(pageId: Int, documentId: Int)
}
