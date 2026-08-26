package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query(
        """
        SELECT * FROM entries
        ORDER BY occurred_on DESC, happened_at DESC, created_at DESC
    """
    )
    fun getAll(): Flow<List<EntryEntity>>

    @Query(
        """
        SELECT * FROM entries
        WHERE id = :id
    """
    )
    suspend fun getById(id: Long): EntryEntity?

    @Query(
        """
        SELECT * FROM entries
        WHERE counterparty_id = :counterpartyId
        ORDER BY occurred_on DESC, happened_at DESC
    """
    )
    fun getByCounterparty(counterpartyId: Long): Flow<List<EntryEntity>>

    @Query(
        """
        SELECT * FROM entries
        WHERE occurred_on BETWEEN :startDate AND :endDate
        ORDER BY occurred_on DESC, happened_at DESC
    """
    )
    fun getBetweenDates(
        startDate: Long,
        endDate: Long
    ): Flow<List<EntryEntity>>

    @Query(
        """
        SELECT * FROM entries
        WHERE status = :status
        ORDER BY occurred_on DESC, happened_at DESC
    """
    )
    fun getByStatus(status: String): Flow<List<EntryEntity>>

    // ── Backend service additions (suspend, non-Flow) ───────────────────────

    @Query(
        """
        SELECT * FROM entries
        WHERE (:status IS NULL OR status = :status)
          AND (:fromMillis IS NULL OR occurred_on >= :fromMillis)
          AND (:toMillis IS NULL OR occurred_on <= :toMillis)
          AND voided_at IS NULL
        ORDER BY occurred_on DESC, happened_at DESC, created_at DESC
    """
    )
    suspend fun listSuspend(status: String?, fromMillis: Long?, toMillis: Long?): List<EntryEntity>

    /** Break a sibling's link to this entry before deleting it (purge). */
    @Query("UPDATE entries SET linked_entry_id = NULL WHERE linked_entry_id = :entryId")
    suspend fun clearLinkedEntry(entryId: Long)

    /** Entries carrying a bucket's allocation note on one of their lines. */
    @Query(
        """
        SELECT DISTINCT e.id FROM entries e
        JOIN entry_lines l ON l.entry_id = e.id
        WHERE l.bucket_id = :bucketId AND e.note = :note
    """
    )
    suspend fun bucketAllocationEntryIds(bucketId: Long, note: String): List<Long>

    /** Recent QR-scan buffer entries for the QR<->SMS merge (see IngestionService). */
    @Query(
        """
        SELECT * FROM entries
        WHERE source = 'qr_scan'
          AND status = 'buffer'
          AND created_at >= :cutoffMillis
          AND voided_at IS NULL
          AND id != :excludeId
        ORDER BY created_at DESC
    """
    )
    suspend fun recentBufferQrScans(cutoffMillis: Long, excludeId: Long): List<EntryEntity>

    @Query(
        """
        SELECT * FROM entries
        WHERE source = :source
        ORDER BY occurred_on DESC, happened_at DESC
    """
    )
    fun getBySource(source: String): Flow<List<EntryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Delete
    suspend fun delete(entry: EntryEntity)
}