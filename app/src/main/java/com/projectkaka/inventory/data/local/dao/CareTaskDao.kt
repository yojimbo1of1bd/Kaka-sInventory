package com.projectkaka.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import kotlinx.coroutines.flow.Flow

/** Flattened care-task row including the parent item's display fields. */
data class DueTaskRowRaw(
    val id: Int,
    val itemId: Int,
    val taskName: String,
    val frequencyDays: Int,
    val lastCompletedDate: Long,
    val itemName: String,
    val itemImagePath: String
)

@Dao
interface CareTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: CareTaskEntity): Long

    @Update
    suspend fun updateTask(task: CareTaskEntity)

    @Delete
    suspend fun deleteTask(task: CareTaskEntity)

    @Query("SELECT * FROM care_tasks WHERE item_id = :itemId")
    fun getTasksForItem(itemId: Int): Flow<List<CareTaskEntity>>

    /**
     * Triage math: due when (last_completed_date + frequency_days * 86_400_000) <= now.
     * Days are whole units; the multiplication is done in SQLite to keep it exact.
     */
    @Query(
        """
        SELECT * FROM care_tasks
        WHERE (last_completed_date + (frequency_days * 86400000)) <= :currentTimeMs
        ORDER BY (last_completed_date + (frequency_days * 86400000)) ASC
        """
    )
    fun getOverdueTasks(currentTimeMs: Long): Flow<List<CareTaskEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM care_tasks
        WHERE (last_completed_date + (frequency_days * 86400000)) <= :currentTimeMs
        """
    )
    fun getOverdueTasksCount(currentTimeMs: Long): Flow<Int>

    /**
     * The Red Ring bottom sheet needs task + item context in one query, so the
     * item's name and WebP path are joined in rather than fetched per row.
     */
    @Query(
        """
        SELECT
            ct.id                AS id,
            ct.item_id           AS itemId,
            ct.task_name         AS taskName,
            ct.frequency_days    AS frequencyDays,
            ct.last_completed_date AS lastCompletedDate,
            i.name               AS itemName,
            i.image_path         AS itemImagePath
        FROM care_tasks ct
        INNER JOIN items i ON i.id = ct.item_id
        WHERE (ct.last_completed_date + (ct.frequency_days * 86400000)) <= :currentTimeMs
          AND i.status = 'ACTIVE'
        ORDER BY (ct.last_completed_date + (ct.frequency_days * 86400000)) ASC
        """
    )
    fun getDueTaskDetail(currentTimeMs: Long): Flow<List<DueTaskRowRaw>>
}
