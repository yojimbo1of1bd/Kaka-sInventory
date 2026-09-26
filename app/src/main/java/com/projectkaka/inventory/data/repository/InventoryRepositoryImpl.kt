package com.projectkaka.inventory.data.repository

import androidx.sqlite.db.SupportSQLiteQuery
import com.projectkaka.inventory.data.local.dao.CareTaskDao
import com.projectkaka.inventory.data.local.dao.ItemDao
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.data.local.entity.ItemStatus
import com.projectkaka.inventory.data.local.entity.ItemWithCareTasks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class InventoryRepositoryImpl(
    private val itemDao: ItemDao,
    private val careTaskDao: CareTaskDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : InventoryRepository {

    override fun getActiveItems(): Flow<List<ItemEntity>> = itemDao.getActiveItems()

    override fun getDraftItems(): Flow<List<ItemEntity>> = itemDao.getDraftItems()

    override fun searchItems(query: SupportSQLiteQuery): Flow<List<ItemEntity>> =
        itemDao.searchItemsRaw(query)

    override fun observeItem(id: Int): Flow<ItemEntity?> = itemDao.observeItemById(id)

    override suspend fun getItemById(id: Int): ItemEntity? =
        withContext(ioDispatcher) { itemDao.getItemById(id) }

    override fun getItemWithCareTasks(id: Int): Flow<ItemWithCareTasks?> =
        itemDao.getItemWithCareTasks(id)

    override suspend fun saveItem(item: ItemEntity): Long =
        withContext(ioDispatcher) { itemDao.insertItem(item) }

    override suspend fun updateItem(item: ItemEntity) =
        withContext(ioDispatcher) { itemDao.updateItem(item) }

    override suspend fun liquidateItem(id: Int, status: ItemStatus, recoveredValue: Double) =
        withContext(ioDispatcher) { itemDao.updateItemStatus(id, status, recoveredValue) }

    override suspend fun deleteItem(item: ItemEntity) =
        withContext(ioDispatcher) { itemDao.deleteItem(item) }

    override fun getOverdueTasks(currentTimeMs: Long): Flow<List<CareTaskEntity>> =
        careTaskDao.getOverdueTasks(currentTimeMs)

    override fun getOverdueCount(currentTimeMs: Long): Flow<Int> =
        careTaskDao.getOverdueTasksCount(currentTimeMs)

    override fun getDueTaskDetail(currentTimeMs: Long): Flow<List<DueTaskRow>> =
        careTaskDao.getDueTaskDetail(currentTimeMs).map { rows ->
            rows.map { row ->
                DueTaskRow(
                    id = row.id,
                    itemId = row.itemId,
                    taskName = row.taskName,
                    frequencyDays = row.frequencyDays,
                    lastCompletedDate = row.lastCompletedDate,
                    itemName = row.itemName,
                    itemImagePath = row.itemImagePath
                )
            }
        }

    override fun getTasksForItem(itemId: Int): Flow<List<CareTaskEntity>> =
        careTaskDao.getTasksForItem(itemId)

    override suspend fun saveCareTask(task: CareTaskEntity): Long =
        withContext(ioDispatcher) { careTaskDao.insertTask(task) }

    override suspend fun completeCareTask(task: CareTaskEntity) =
        withContext(ioDispatcher) {
            careTaskDao.updateTask(task.copy(lastCompletedDate = System.currentTimeMillis()))
        }

    override suspend fun deleteCareTask(task: CareTaskEntity) =
        withContext(ioDispatcher) { careTaskDao.deleteTask(task) }

    override fun getClearedItemsCount(): Flow<Int> = itemDao.getClearedItemsCount()

    override fun getTotalCashRecovered(): Flow<Double> = itemDao.getTotalCashRecovered()

    override suspend fun exportSnapshot(): List<ItemExportRow> = withContext(ioDispatcher) {
        val items = itemDao.getAllItemsSnapshot()
        val tasksByItem = itemDao.getAllCareTasksSnapshot().groupBy { it.itemId }
        items.map { item ->
            ItemExportRow(
                id = item.id,
                name = item.name,
                category = item.category,
                locationTag = item.locationTag,
                estimatedValue = item.estimatedValue,
                status = item.status.name,
                isDraft = item.isDraft,
                dateAdded = item.dateAdded,
                imagePath = item.imagePath,
                careTasks = tasksByItem[item.id].orEmpty()
            )
        }
    }
}
