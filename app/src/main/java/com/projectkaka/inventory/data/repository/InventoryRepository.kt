package com.projectkaka.inventory.data.repository

import androidx.sqlite.db.SupportSQLiteQuery
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.data.local.entity.ItemStatus
import com.projectkaka.inventory.data.local.entity.ItemWithCareTasks
import kotlinx.coroutines.flow.Flow

/** Single source of truth. Every ViewModel talks only to this. */
interface InventoryRepository {
    fun getActiveItems(): Flow<List<ItemEntity>>
    fun getDraftItems(): Flow<List<ItemEntity>>
    fun searchItems(query: SupportSQLiteQuery): Flow<List<ItemEntity>>
    fun observeItem(id: Int): Flow<ItemEntity?>
    suspend fun getItemById(id: Int): ItemEntity?
    fun getItemWithCareTasks(id: Int): Flow<ItemWithCareTasks?>
    suspend fun saveItem(item: ItemEntity): Long
    suspend fun updateItem(item: ItemEntity)
    suspend fun liquidateItem(id: Int, status: ItemStatus, recoveredValue: com.projectkaka.inventory.model.Money)
    suspend fun deleteItem(item: ItemEntity)

    // Triage
    fun getOverdueTasks(currentTimeMs: Long): Flow<List<CareTaskEntity>>
    fun getOverdueCount(currentTimeMs: Long): Flow<Int>
    fun getDueTaskDetail(currentTimeMs: Long): Flow<List<DueTaskRow>>
    fun getTasksForItem(itemId: Int): Flow<List<CareTaskEntity>>
    suspend fun saveCareTask(task: CareTaskEntity): Long
    suspend fun completeCareTask(task: CareTaskEntity)
    suspend fun deleteCareTask(task: CareTaskEntity)

    // Stats
    fun getClearedItemsCount(): Flow<Int>
    fun getTotalCashRecovered(): Flow<com.projectkaka.inventory.model.Money>

    // Export (read-only snapshots of the sovereign local store)
    suspend fun exportSnapshot(): List<ItemExportRow>
}

/** Care task joined with its item's display fields, straight from SQL. */
data class DueTaskRow(
    val id: Int,
    val itemId: Int,
    val taskName: String,
    val frequencyDays: Int,
    val lastCompletedDate: Long,
    val itemName: String,
    val itemImagePath: String
)

/** Fully denormalised item row for CSV/JSON export. */
data class ItemExportRow(
    val id: Int,
    val name: String,
    val category: String,
    val locationTag: String,
    val estimatedValue: com.projectkaka.inventory.model.Money,
    val status: String,
    val isDraft: Boolean,
    val dateAdded: Long,
    val imagePath: String,
    val careTasks: List<CareTaskEntity>
)
