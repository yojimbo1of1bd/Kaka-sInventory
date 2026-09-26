package com.projectkaka.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.data.local.entity.ItemStatus
import com.projectkaka.inventory.data.local.entity.ItemWithCareTasks
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Delete
    suspend fun deleteItem(item: ItemEntity)

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): ItemEntity?

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    fun observeItemById(id: Int): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE status = 'ACTIVE' AND is_draft = 0 ORDER BY date_added DESC")
    fun getActiveItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE is_draft = 1 ORDER BY date_added DESC")
    fun getDraftItems(): Flow<List<ItemEntity>>

    @Query("UPDATE items SET status = :newStatus, estimated_value = :recoveredValue WHERE id = :itemId")
    suspend fun updateItemStatus(itemId: Int, newStatus: ItemStatus, recoveredValue: Double)

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    fun getItemWithCareTasks(id: Int): Flow<ItemWithCareTasks?>

    /**
     * Dynamic search entry point for the Magic Input Bar.
     *
     * CareTaskEntity is listed in observedEntities because m/due and m/none filters
     * read the care_tasks table via EXISTS — without it, completing a task would not
     * refresh a filtered list until some unrelated items write occurred.
     */
    @RawQuery(observedEntities = [ItemEntity::class, CareTaskEntity::class])
    fun searchItemsRaw(query: SupportSQLiteQuery): Flow<List<ItemEntity>>

    @Query("SELECT COUNT(*) FROM items WHERE status != 'ACTIVE'")
    fun getClearedItemsCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(estimated_value), 0.0) FROM items WHERE status = 'SOLD'")
    fun getTotalCashRecovered(): Flow<Double>

    // ---- Export support (read-only snapshots) ----
    @Query("SELECT * FROM items ORDER BY date_added DESC")
    suspend fun getAllItemsSnapshot(): List<ItemEntity>

    @Query("SELECT * FROM care_tasks")
    suspend fun getAllCareTasksSnapshot(): List<CareTaskEntity>
}
