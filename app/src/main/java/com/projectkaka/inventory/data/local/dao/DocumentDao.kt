package com.projectkaka.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.projectkaka.inventory.data.local.entity.DocumentEntity
import com.projectkaka.inventory.data.local.entity.DocumentPageEntity
import com.projectkaka.inventory.data.local.relation.DocumentWithPages
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents WHERE is_archived = 0 ORDER BY created_at DESC")
    fun getActiveDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE is_archived = 1 ORDER BY created_at DESC")
    fun getArchivedDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE doc_type = :docType AND is_archived = 0 ORDER BY issue_date DESC")
    fun getByType(docType: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE linked_item_id = :itemId AND is_archived = 0 ORDER BY issue_date DESC")
    fun getForItem(itemId: Int): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Int): DocumentEntity?

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    fun observeDocumentWithPages(documentId: Int): Flow<DocumentWithPages?>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocumentWithPages(documentId: Int): DocumentWithPages?

    @Transaction
    @Query("SELECT * FROM documents ORDER BY created_at DESC")
    suspend fun getAllDocumentsWithPagesSnapshot(): List<DocumentWithPages>

    @Query("SELECT * FROM documents ORDER BY created_at DESC")
    suspend fun getAllDocumentsSnapshot(): List<DocumentEntity>

    @Query("SELECT * FROM document_pages WHERE document_id = :documentId ORDER BY page_number ASC")
    fun getPagesForDocument(documentId: Int): Flow<List<DocumentPageEntity>>

    @Query("SELECT * FROM document_pages ORDER BY document_id ASC, page_number ASC")
    suspend fun getAllPagesSnapshot(): List<DocumentPageEntity>

    @Query("SELECT COUNT(*) FROM documents WHERE is_archived = 0")
    fun observeDocumentCount(): Flow<Int>

    @Query("SELECT DISTINCT doc_type FROM documents WHERE is_archived = 0")
    fun getAllDocTypes(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Int)

    @Query("UPDATE documents SET is_archived = 1 WHERE id = :id")
    suspend fun archiveDocument(id: Int)

    @Query("UPDATE documents SET is_archived = 0 WHERE id = :id")
    suspend fun unarchiveDocument(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: DocumentPageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<DocumentPageEntity>): List<Long>

    @Delete
    suspend fun deletePage(page: DocumentPageEntity)

    @Query("DELETE FROM document_pages WHERE document_id = :documentId")
    suspend fun deletePagesForDocument(documentId: Int)

    @Query("""
        SELECT * FROM documents
        WHERE is_archived = 0
          AND (title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' OR doc_type LIKE '%' || :query || '%')
        ORDER BY created_at DESC
    """)
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>
}
