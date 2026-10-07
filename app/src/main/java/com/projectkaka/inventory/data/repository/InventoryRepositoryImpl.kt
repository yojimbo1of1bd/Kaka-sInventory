package com.projectkaka.inventory.data.repository

import androidx.sqlite.db.SupportSQLiteQuery
import com.projectkaka.inventory.data.local.dao.CareTaskDao
import com.projectkaka.inventory.data.local.dao.ItemDao
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.data.local.entity.ItemStatus
import com.projectkaka.inventory.data.local.entity.ItemWithCareTasks
import com.projectkaka.inventory.data.repository.FinanceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

import com.projectkaka.inventory.data.local.dao.BasketDao
import com.projectkaka.inventory.data.local.entity.BasketEntity
import com.projectkaka.inventory.data.local.entity.BasketItemCrossRef
import com.projectkaka.inventory.data.settings.UserPreferences

class InventoryRepositoryImpl(
    private val itemDao: ItemDao,
    private val careTaskDao: CareTaskDao,
    private val financeRepository: FinanceRepository,
    private val basketDao: BasketDao,
    private val preferences: UserPreferences? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : InventoryRepository {

    private val isBusinessMode: Boolean
        get() = preferences?.businessMode?.value ?: false

    override fun getActiveItems(): Flow<List<ItemEntity>> = itemDao.getActiveItems()
    override fun getAllInventoryItems(): Flow<List<ItemEntity>> = itemDao.getAllInventoryItems()

    override fun getDraftItems(): Flow<List<ItemEntity>> = itemDao.getDraftItems()

    override fun searchItems(query: SupportSQLiteQuery): Flow<List<ItemEntity>> =
        itemDao.searchItemsRaw(query)

    override fun observeItem(id: Int): Flow<ItemEntity?> = itemDao.observeItemById(id)

    override suspend fun getItemById(id: Int): ItemEntity? =
        withContext(ioDispatcher) { itemDao.getItemById(id) }

    override fun getItemWithCareTasks(id: Int): Flow<ItemWithCareTasks?> =
        itemDao.getItemWithCareTasks(id)

    override suspend fun saveItem(item: ItemEntity): Long =
        withContext(ioDispatcher) { 
            val id = itemDao.insertItem(item)
            if (isBusinessMode && item.estimatedValue.minorUnits > 0) {
                val invAccId = financeRepository.resolveAccountsExact("Inventory").firstOrNull()?.id ?: 1
                val capitalAccId = financeRepository.resolveAccountsExact("Capital").firstOrNull()?.id ?: 1
                val postings = listOf(
                    com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, invAccId, item.estimatedValue, isCredit = false, note = "Acquired item: ${item.name}"),
                    com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, capitalAccId, item.estimatedValue, isCredit = true, note = "Acquired item: ${item.name}")
                )
                val entry = com.projectkaka.inventory.data.local.entity.JournalEntryEntity(
                    timestamp = System.currentTimeMillis(),
                    description = "Acquired item: ${item.name}",
                    status = com.projectkaka.inventory.data.local.entity.JournalStatus.DRAFT,
                    approvalStatus = com.projectkaka.inventory.data.local.entity.ApprovalStatus.APPROVED
                )
                val journalId = financeRepository.recordSplitTransaction(entry, postings)
                itemDao.updateItemStatus(id.toInt(), item.status, item.estimatedValue, journalId.toInt())
            }
            id
        }

    override suspend fun updateItem(item: ItemEntity) =
        withContext(ioDispatcher) { 
            val oldItem = itemDao.getItemById(item.id)
            itemDao.updateItem(item)
            if (isBusinessMode && oldItem != null && item.estimatedValue.minorUnits != oldItem.estimatedValue.minorUnits) {
                val diff = item.estimatedValue.minorUnits - oldItem.estimatedValue.minorUnits
                val invAccId = financeRepository.resolveAccountsExact("Inventory").firstOrNull()?.id ?: 1
                val capitalAccId = financeRepository.resolveAccountsExact("Capital").firstOrNull()?.id ?: 1
                val absDiff = com.projectkaka.inventory.model.Money(kotlin.math.abs(diff))
                val isIncrease = diff > 0
                val postings = listOf(
                    com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, invAccId, absDiff, isCredit = !isIncrease, note = "Value adjustment: ${item.name}"),
                    com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, capitalAccId, absDiff, isCredit = isIncrease, note = "Value adjustment: ${item.name}")
                )
                val entry = com.projectkaka.inventory.data.local.entity.JournalEntryEntity(
                    timestamp = System.currentTimeMillis(),
                    description = "Value adjustment: ${item.name}",
                    status = com.projectkaka.inventory.data.local.entity.JournalStatus.DRAFT,
                    approvalStatus = com.projectkaka.inventory.data.local.entity.ApprovalStatus.APPROVED
                )
                financeRepository.recordSplitTransaction(entry, postings)
            }
        }

    override suspend fun liquidateItem(id: Int, status: ItemStatus, recoveredValue: com.projectkaka.inventory.model.Money) =
        withContext(ioDispatcher) { 
            val item = itemDao.getItemById(id) ?: return@withContext
            var journalId: Long? = null
            
            if (status == ItemStatus.SOLD || status == ItemStatus.DONATED || status == ItemStatus.TRASHED) {
                // Find needed accounts (defaults to 1 if not found, but real implementation would seed them)
                val cashAccId = financeRepository.resolveAccountsExact("Cash").firstOrNull()?.id ?: 1
                val salesAccId = financeRepository.resolveAccountsExact("Sales Revenue").firstOrNull()?.id ?: 1
                val cogsAccId = financeRepository.resolveAccountsExact("COGS").firstOrNull()?.id ?: 1
                val invAccId = financeRepository.resolveAccountsExact("Inventory").firstOrNull()?.id ?: 1
                val charityAccId = financeRepository.resolveAccountsExact("Charity Expense").firstOrNull()?.id ?: 1
                val lossAccId = financeRepository.resolveAccountsExact("Loss").firstOrNull()?.id ?: 1
                
                val postings = mutableListOf<com.projectkaka.inventory.data.local.entity.PostingEntity>()
                val journalNote = "${status.name.lowercase().replaceFirstChar { it.uppercase() }} item: ${item.name}"
                
                if (status == ItemStatus.SOLD) {
                    if (recoveredValue.minorUnits > 0L) {
                        postings.add(com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, cashAccId, recoveredValue, isCredit = false, note = journalNote))
                        postings.add(com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, salesAccId, recoveredValue, isCredit = true, note = journalNote))
                    }
                    if (isBusinessMode && item.estimatedValue.minorUnits > 0L) {
                        postings.add(com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, cogsAccId, item.estimatedValue, isCredit = false, note = journalNote))
                        postings.add(com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, invAccId, item.estimatedValue, isCredit = true, note = journalNote))
                    }
                } else if (isBusinessMode && status == ItemStatus.DONATED && item.estimatedValue.minorUnits > 0L) {
                    postings.add(com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, charityAccId, item.estimatedValue, isCredit = false, note = journalNote))
                    postings.add(com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, invAccId, item.estimatedValue, isCredit = true, note = journalNote))
                } else if (isBusinessMode && status == ItemStatus.TRASHED && item.estimatedValue.minorUnits > 0L) {
                    postings.add(com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, lossAccId, item.estimatedValue, isCredit = false, note = journalNote))
                    postings.add(com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, invAccId, item.estimatedValue, isCredit = true, note = journalNote))
                }
                
                if (postings.isNotEmpty()) {
                    val entry = com.projectkaka.inventory.data.local.entity.JournalEntryEntity(
                        timestamp = System.currentTimeMillis(),
                        description = journalNote,
                        status = com.projectkaka.inventory.data.local.entity.JournalStatus.DRAFT,
                        approvalStatus = com.projectkaka.inventory.data.local.entity.ApprovalStatus.APPROVED
                    )
                    journalId = financeRepository.recordSplitTransaction(entry, postings)
                }
            }
            itemDao.updateItemStatus(id, status, recoveredValue, journalId?.toInt()) 
        }

    override suspend fun deleteItem(item: ItemEntity) =
        withContext(ioDispatcher) { 
            itemDao.deleteItem(item) 
            if (isBusinessMode && item.estimatedValue.minorUnits > 0) {
                val invAccId = financeRepository.resolveAccountsExact("Inventory").firstOrNull()?.id ?: 1
                val capitalAccId = financeRepository.resolveAccountsExact("Capital").firstOrNull()?.id ?: 1
                val postings = listOf(
                    com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, invAccId, item.estimatedValue, isCredit = true, note = "Deleted item: ${item.name}"),
                    com.projectkaka.inventory.data.local.entity.PostingEntity(0, 0, capitalAccId, item.estimatedValue, isCredit = false, note = "Deleted item: ${item.name}")
                )
                val entry = com.projectkaka.inventory.data.local.entity.JournalEntryEntity(
                    timestamp = System.currentTimeMillis(),
                    description = "Deleted item: ${item.name}",
                    status = com.projectkaka.inventory.data.local.entity.JournalStatus.DRAFT,
                    approvalStatus = com.projectkaka.inventory.data.local.entity.ApprovalStatus.APPROVED
                )
                financeRepository.recordSplitTransaction(entry, postings)
            }
        }

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

    override fun getTotalCashRecovered(): Flow<com.projectkaka.inventory.model.Money> = itemDao.getTotalCashRecovered()

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

    override fun observeAllBaskets(): Flow<List<BasketEntity>> = basketDao.observeAllBaskets()

    override fun observeBasketById(id: Int): Flow<BasketEntity?> = basketDao.observeBasketById(id)

    override suspend fun getBasketById(id: Int): BasketEntity? =
        withContext(ioDispatcher) { basketDao.getBasketById(id) }

    override suspend fun getBasketByCode(code: String): BasketEntity? =
        withContext(ioDispatcher) { basketDao.getBasketByCode(code) }

    override suspend fun saveBasket(basket: BasketEntity): Long =
        withContext(ioDispatcher) { basketDao.insertBasket(basket) }

    override suspend fun updateBasket(basket: BasketEntity) =
        withContext(ioDispatcher) { basketDao.updateBasket(basket) }

    override suspend fun deleteBasket(basket: BasketEntity) =
        withContext(ioDispatcher) { basketDao.deleteBasket(basket) }

    override fun observeItemsForBasket(basketId: Int): Flow<List<ItemEntity>> =
        basketDao.observeItemsForBasket(basketId)

    override fun observeBasketForItem(itemId: Int): Flow<BasketEntity?> =
        basketDao.observeBasketForItem(itemId)

    override fun observeBasketItemCount(basketId: Int): Flow<Int> =
        basketDao.observeBasketItemCount(basketId)

    override suspend fun addItemToBasket(basketId: Int, itemId: Int) =
        withContext(ioDispatcher) {
            basketDao.addItemToBasket(BasketItemCrossRef(basketId = basketId, itemId = itemId))
        }

    override suspend fun removeItemFromBasket(basketId: Int, itemId: Int) =
        withContext(ioDispatcher) {
            basketDao.removeItemFromBasket(basketId, itemId)
        }

    override suspend fun moveItemToBasket(itemId: Int, targetBasketId: Int) =
        withContext(ioDispatcher) {
            basketDao.moveItemToBasket(itemId, targetBasketId)
        }
}
