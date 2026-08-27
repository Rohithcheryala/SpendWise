package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.EntryLineEntity
import kotlinx.coroutines.flow.Flow

/** Projection for per-counterparty loan nets (see netByCounterpartyOnAccount). */
data class CounterpartyNetRow(
    val cpId: Long?,
    val net: Long,
)

@Dao
interface EntryLineDao {

    @Query(
        """
        SELECT * FROM entry_lines
        ORDER BY id ASC
    """
    )
    fun getAll(): Flow<List<EntryLineEntity>>

    @Query(
        """
        SELECT * FROM entry_lines
        WHERE id = :id
    """
    )
    suspend fun getById(id: Long): EntryLineEntity?

    @Query(
        """
        SELECT * FROM entry_lines
        WHERE entry_id = :entryId
        ORDER BY id ASC
    """
    )
    fun getByEntry(entryId: Long): Flow<List<EntryLineEntity>>

    @Query(
        """
        SELECT * FROM entry_lines
        WHERE entry_id = :entryId
        ORDER BY id ASC
    """
    )
    suspend fun getByEntryList(entryId: Long): List<EntryLineEntity>

    /**
     * Signed sum of this account's lines on confirmed, non-voided entries,
     * optionally bounded by occurred_on <= :throughMillis. The service layer
     * inverts liabilities on top of this raw sum.
     */
    @Query(
        """
        SELECT COALESCE(SUM(l.amount_paise), 0)
        FROM entry_lines l
        JOIN entries e ON e.id = l.entry_id
        WHERE l.account_id = :accountId
          AND e.status = 'confirmed'
          AND e.voided_at IS NULL
          AND (:throughMillis IS NULL OR e.occurred_on <= :throughMillis)
    """
    )
    suspend fun sumConfirmedForAccount(accountId: Long, throughMillis: Long?): Long

    /** Same as [sumConfirmedForAccount] but for bucket-tagged lines. */
    @Query(
        """
        SELECT COALESCE(SUM(l.amount_paise), 0)
        FROM entry_lines l
        JOIN entries e ON e.id = l.entry_id
        WHERE l.bucket_id = :bucketId
          AND e.status = 'confirmed'
          AND e.voided_at IS NULL
          AND (:throughMillis IS NULL OR e.occurred_on <= :throughMillis)
    """
    )
    suspend fun sumConfirmedForBucket(bucketId: Long, throughMillis: Long?): Long

    /** Detach a bucket tag from every line (bucket deletion cleanup). */
    @Query("UPDATE entry_lines SET bucket_id = NULL WHERE bucket_id = :bucketId")
    suspend fun detachBucket(bucketId: Long)

    /** Entry ids having a line on the given account. */
    @Query("SELECT DISTINCT entry_id FROM entry_lines WHERE account_id = :accountId")
    suspend fun entryIdsForAccount(accountId: Long): List<Long>

    /** Net receivable per counterparty from confirmed entries on one pot account. */
    @Query(
        """
        SELECT COALESCE(l.counterparty_id, e.counterparty_id) AS cpId,
               SUM(l.amount_paise) AS net
        FROM entry_lines l
        JOIN entries e ON e.id = l.entry_id
        WHERE l.account_id = :accountId
          AND e.status = 'confirmed'
          AND e.voided_at IS NULL
        GROUP BY cpId
    """
    )
    suspend fun netByCounterpartyOnAccount(accountId: Long): List<CounterpartyNetRow>

    @Query(
        """
        SELECT * FROM entry_lines
        WHERE account_id = :accountId
        ORDER BY id DESC
    """
    )
    fun getByAccount(accountId: Long): Flow<List<EntryLineEntity>>

    @Query(
        """
        SELECT * FROM entry_lines
        WHERE category_id = :categoryId
        ORDER BY id DESC
    """
    )
    fun getByCategory(categoryId: Long): Flow<List<EntryLineEntity>>

    @Query(
        """
        SELECT * FROM entry_lines
        WHERE bucket_id = :bucketId
        ORDER BY id DESC
    """
    )
    fun getByBucket(bucketId: Long): Flow<List<EntryLineEntity>>

    @Query(
        """
        SELECT * FROM entry_lines
        WHERE counterparty_id = :counterpartyId
        ORDER BY id DESC
    """
    )
    fun getByCounterparty(counterpartyId: Long): Flow<List<EntryLineEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entryLine: EntryLineEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entryLines: List<EntryLineEntity>): List<Long>

    @Update
    suspend fun update(entryLine: EntryLineEntity)

    @Delete
    suspend fun delete(entryLine: EntryLineEntity)

    @Query(
        """
        DELETE FROM entry_lines
        WHERE entry_id = :entryId
    """
    )
    suspend fun deleteByEntry(entryId: Long)
}