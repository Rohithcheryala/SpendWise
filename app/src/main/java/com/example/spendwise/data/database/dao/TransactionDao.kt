package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query(
        """
        SELECT * FROM transactions
        ORDER BY occurred_on DESC, happened_at DESC, created_at DESC
    """
    )
    fun getAll(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE id = :id
    """
    )
    suspend fun getById(id: Long): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE counterparty_id = :counterpartyId
        ORDER BY occurred_on DESC, happened_at DESC
    """
    )
    fun getByCounterparty(counterpartyId: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE occurred_on BETWEEN :startDate AND :endDate
        ORDER BY occurred_on DESC, happened_at DESC
    """
    )
    fun getBetweenDates(
        startDate: Long,
        endDate: Long
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE status = :status
        ORDER BY occurred_on DESC, happened_at DESC
    """
    )
    fun getByStatus(status: String): Flow<List<TransactionEntity>>

    /** All non-empty tag strings ever saved, for the tag picker's suggestions. */
    @Query(
        """
        SELECT tags FROM transactions
        WHERE tags IS NOT NULL AND tags != ''
          AND voided_at IS NULL
    """
    )
    suspend fun getAllTagStrings(): List<String>

    // ── Backend service additions (suspend, non-Flow) ───────────────────────

    @Query(
        """
        SELECT * FROM transactions
        WHERE (:status IS NULL OR status = :status)
          AND (:fromMillis IS NULL OR occurred_on >= :fromMillis)
          AND (:toMillis IS NULL OR occurred_on <= :toMillis)
          AND voided_at IS NULL
        ORDER BY occurred_on DESC, happened_at DESC, created_at DESC
    """
    )
    suspend fun listSuspend(status: String?, fromMillis: Long?, toMillis: Long?): List<TransactionEntity>

    /** Break a sibling's link to this transaction before deleting it (purge). */
    @Query("UPDATE transactions SET linked_transaction_id = NULL WHERE linked_transaction_id = :transactionId")
    suspend fun clearLinkedTransaction(transactionId: Long)

    /** Recent QR-scan buffer transactions for the QR<->SMS merge (see IngestionService). */
    @Query(
        """
        SELECT * FROM transactions
        WHERE source = 'qr_scan'
          AND status = 'buffer'
          AND created_at >= :cutoffMillis
          AND voided_at IS NULL
          AND id != :excludeId
        ORDER BY created_at DESC
    """
    )
    suspend fun recentBufferQrScans(cutoffMillis: Long, excludeId: Long): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE source = :source
        ORDER BY occurred_on DESC, happened_at DESC
    """
    )
    fun getBySource(source: String): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: TransactionEntity): Long

    @Update
    suspend fun update(entry: TransactionEntity)

    @Delete
    suspend fun delete(entry: TransactionEntity)
}