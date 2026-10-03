package com.projectkaka.inventory.data.repository

import androidx.room.withTransaction
import com.projectkaka.inventory.data.local.AppDatabase
import com.projectkaka.inventory.data.local.dao.DocumentDao
import com.projectkaka.inventory.data.local.entity.DocumentEntity
import com.projectkaka.inventory.data.local.entity.DocumentPageEntity
import com.projectkaka.inventory.data.local.relation.DocumentWithPages
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class DocumentRepositoryImpl(
    private val db: AppDatabase,
    private val documentDao: DocumentDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DocumentRepository {

    override fun observeActiveDocuments(): Flow<List<DocumentEntity>> =
        documentDao.getActiveDocuments()

    override fun observeArchivedDocuments(): Flow<List<DocumentEntity>> =
        documentDao.getArchivedDocuments()

    override fun observeByType(docType: String): Flow<List<DocumentEntity>> =
        documentDao.getByType(docType)

    override fun observeForItem(itemId: Int): Flow<List<DocumentEntity>> =
        documentDao.getForItem(itemId)

    override fun observeDocumentWithPages(documentId: Int): Flow<DocumentWithPages?> =
        documentDao.observeDocumentWithPages(documentId)

    override fun observeDocumentCount(): Flow<Int> =
        documentDao.observeDocumentCount()

    override fun observeDocTypes(): Flow<List<String>> =
        documentDao.getAllDocTypes()

    override fun searchDocuments(query: String): Flow<List<DocumentEntity>> =
        documentDao.searchDocuments(query)

    override suspend fun getDocumentById(id: Int): DocumentEntity? =
        withContext(ioDispatcher) { documentDao.getDocumentById(id) }

    override suspend fun getDocumentWithPages(id: Int): DocumentWithPages? =
        withContext(ioDispatcher) { documentDao.getDocumentWithPages(id) }

    override suspend fun getAllDocumentsWithPagesSnapshot(): List<DocumentWithPages> =
        withContext(ioDispatcher) { documentDao.getAllDocumentsWithPagesSnapshot() }

    override suspend fun saveDocumentWithPages(
        document: DocumentEntity,
        pageImagePaths: List<String>
    ): Long = withContext(ioDispatcher) {
        db.withTransaction {
            val coverPath = pageImagePaths.firstOrNull() ?: document.coverImagePath
            val docToInsert = document.copy(
                coverImagePath = coverPath,
                pageCount = maxOf(1, pageImagePaths.size)
            )
            val docId = documentDao.insertDocument(docToInsert).toInt()

            pageImagePaths.forEachIndexed { index, path ->
                val pageEntity = DocumentPageEntity(
                    documentId = docId,
                    pageNumber = index + 1,
                    imagePath = path
                )
                documentDao.insertPage(pageEntity)
            }
            docId.toLong()
        }
    }

    override suspend fun addPageToDocument(
        documentId: Int,
        imagePath: String,
        pageNote: String
    ): Long = withContext(ioDispatcher) {
        db.withTransaction {
            val doc = documentDao.getDocumentById(documentId)
                ?: throw IllegalArgumentException("Document $documentId not found")
            val newPageNum = doc.pageCount + 1
            val page = DocumentPageEntity(
                documentId = documentId,
                pageNumber = newPageNum,
                imagePath = imagePath,
                pageNote = pageNote
            )
            val pageId = documentDao.insertPage(page)
            documentDao.updateDocument(doc.copy(pageCount = newPageNum))
            pageId
        }
    }

    override suspend fun updateDocument(document: DocumentEntity): Unit =
        withContext(ioDispatcher) { documentDao.updateDocument(document) }

    override suspend fun deleteDocument(documentId: Int): Unit = withContext(ioDispatcher) {
        db.withTransaction {
            val withPages = documentDao.getDocumentWithPages(documentId)
            // Delete image files from disk
            withPages?.pages?.forEach { page ->
                if (page.imagePath.isNotBlank()) {
                    runCatching { File(page.imagePath).delete() }
                }
            }
            documentDao.deleteDocumentById(documentId)
        }
    }

    override suspend fun archiveDocument(documentId: Int): Unit =
        withContext(ioDispatcher) { documentDao.archiveDocument(documentId) }

    override suspend fun unarchiveDocument(documentId: Int): Unit =
        withContext(ioDispatcher) { documentDao.unarchiveDocument(documentId) }

    override suspend fun deletePage(pageId: Int, documentId: Int): Unit = withContext(ioDispatcher) {
        db.withTransaction {
            val doc = documentDao.getDocumentById(documentId) ?: return@withTransaction
            val withPages = documentDao.getDocumentWithPages(documentId) ?: return@withTransaction
            val pageToDelete = withPages.pages.find { it.id == pageId } ?: return@withTransaction

            // Delete file
            if (pageToDelete.imagePath.isNotBlank()) {
                runCatching { File(pageToDelete.imagePath).delete() }
            }
            documentDao.deletePage(pageToDelete)

            // Re-index remaining pages
            val remaining = withPages.pages.filter { it.id != pageId }.sortedBy { it.pageNumber }
            remaining.forEachIndexed { index, p ->
                val newNum = index + 1
                if (p.pageNumber != newNum) {
                    documentDao.insertPage(p.copy(pageNumber = newNum))
                }
            }
            val newCover = remaining.firstOrNull()?.imagePath ?: ""
            documentDao.updateDocument(doc.copy(pageCount = remaining.size, coverImagePath = newCover))
        }
    }
}
