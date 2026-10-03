package com.projectkaka.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.projectkaka.inventory.data.local.entity.JournalEntryEntity
import com.projectkaka.inventory.data.local.entity.PostingEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface JournalDao {
    @Insert
    suspend fun insertJournalEntry(entry: JournalEntryEntity): Long

    @Insert
    suspend fun insertPosting(posting: PostingEntity): Long

    @Insert
    suspend fun insertPostings(postings: List<PostingEntity>)

    @Update
    suspend fun updateJournalEntry(entry: JournalEntryEntity)

    /** Commit a pending journal entry (triggers fire on commit). */
    @Query("UPDATE journal_entries SET status = 'POSTED' WHERE id = :id")
    suspend fun commitJournalEntry(id: Int)

    /** Account balance = SUM(all postings for this account). */
    @Query(
        """
        SELECT COALESCE(
            SUM(
                CASE 
                    WHEN a.type IN ('CASH', 'ASSET', 'EXPENSE') THEN 
                        CASE WHEN p.is_credit = 0 THEN p.amount ELSE -p.amount END
                    ELSE 
                        CASE WHEN p.is_credit = 1 THEN p.amount ELSE -p.amount END
                END
            ), 0
        )
        FROM postings p
        INNER JOIN journal_entries j ON j.id = p.journal_entry_id
        INNER JOIN accounts a ON a.id = p.account_id
        WHERE p.account_id = :accountId AND j.status = 'POSTED'
    """
    )
    suspend fun getAccountBalance(accountId: Int): Long

    /** 
     * Account statement (list of postings).
     * Running balance will be computed in the repository or UI layer to avoid Room SQLite parser issues with OVER().
     */
    @Query(
        """
        SELECT p.*
        FROM postings p
        INNER JOIN journal_entries j ON j.id = p.journal_entry_id
        WHERE p.account_id = :accountId AND j.status = 'POSTED'
        ORDER BY j.timestamp DESC, j.id DESC
    """
    )
    fun getAccountStatement(accountId: Int): Flow<List<PostingEntity>>

    @Query("SELECT * FROM postings WHERE id = :postingId LIMIT 1")
    suspend fun getPostingById(postingId: Int): PostingEntity?

    @Query("SELECT * FROM postings WHERE journal_entry_id = :entryId")
    suspend fun getPostingsForJournalEntry(entryId: Int): List<PostingEntity>

    @Query("DELETE FROM journal_entries WHERE id = :entryId")
    suspend fun deleteJournalEntryById(entryId: Int)
}
