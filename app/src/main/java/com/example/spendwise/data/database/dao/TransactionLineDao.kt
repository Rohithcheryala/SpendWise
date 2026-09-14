package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.TransactionLineEntity
import kotlinx.coroutines.flow.Flow

/** Projection for per-counterparty loan nets (see netByCounterpartyOnAccount). */
data class CounterpartyNetRow(
    val cpId: Long?,
    val net: Long,
)

@Dao
interface TransactionLineDao {

    @Query(
        """
        SELECT * FROM transaction_lines
        ORDER BY id ASC
    """
    )
    fun getAll(): Flow<List<TransactionLineEntity>>

    @Query(
        """
        SELECT * FROM transaction_lines
        WHERE id = :id
    """
    )
    suspend fun getById(id: Long): TransactionLineEntity?

    @Query(
        """
        SELECT * FROM transaction_lines
        WHERE transaction_id = :transactionId
        ORDER BY id ASC
    """
    )
    fun getByTransaction(transactionId: Long): Flow<List<TransactionLineEntity>>

    @Query(
        """
        SELECT * FROM transaction_lines
        WHERE transaction_id = :transactionId
        ORDER BY id ASC
    """
    )
    suspend fun getByTransactionList(transactionId: Long): List<TransactionLineEntity>

    /**
     * Signed sum of this account's lines on confirmed, non-voided transactions,
     * optionally bounded by occurred_on <= :throughMillis. The service layer
     * inverts liabilities on top of this raw sum.
     */
    @Query(
        """
        SELECT COALESCE(SUM(l.amount_paise), 0)
        FROM transaction_lines l
        JOIN transactions e ON e.id = l.transaction_id
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
        FROM transaction_lines l
        JOIN transactions e ON e.id = l.transaction_id
        WHERE l.bucket_id = :bucketId
          AND e.status = 'confirmed'
          AND e.voided_at IS NULL
          AND (:throughMillis IS NULL OR e.occurred_on <= :throughMillis)
    """
    )
    suspend fun sumConfirmedForBucket(bucketId: Long, throughMillis: Long?): Long

    /** Detach a bucket tag from every line (bucket deletion cleanup). */
    @Query("UPDATE transaction_lines SET bucket_id = NULL WHERE bucket_id = :bucketId")
    suspend fun detachBucket(bucketId: Long)

    /** Entry ids having a line on the given account. */
    @Query("SELECT DISTINCT transaction_id FROM transaction_lines WHERE account_id = :accountId")
    suspend fun transactionIdsForAccount(accountId: Long): List<Long>

    /** Net receivable per counterparty from confirmed transactions on one pot account. */
    @Query(
        """
        SELECT COALESCE(l.counterparty_id, e.counterparty_id) AS cpId,
               SUM(l.amount_paise) AS net
        FROM transaction_lines l
        JOIN transactions e ON e.id = l.transaction_id
        WHERE l.account_id = :accountId
          AND e.status = 'confirmed'
          AND e.voided_at IS NULL
        GROUP BY cpId
    """
    )
    suspend fun netByCounterpartyOnAccount(accountId: Long): List<CounterpartyNetRow>

    @Query(
        """
        SELECT * FROM transaction_lines
        WHERE account_id = :accountId
        ORDER BY id DESC
    """
    )
    fun getByAccount(accountId: Long): Flow<List<TransactionLineEntity>>

    @Query(
        """
        SELECT * FROM transaction_lines
        WHERE category_id = :categoryId
        ORDER BY id DESC
    """
    )
    fun getByCategory(categoryId: Long): Flow<List<TransactionLineEntity>>

    @Query(
        """
        SELECT * FROM transaction_lines
        WHERE bucket_id = :bucketId
        ORDER BY id DESC
    """
    )
    fun getByBucket(bucketId: Long): Flow<List<TransactionLineEntity>>

    @Query(
        """
        SELECT * FROM transaction_lines
        WHERE counterparty_id = :counterpartyId
        ORDER BY id DESC
    """
    )
    fun getByCounterparty(counterpartyId: Long): Flow<List<TransactionLineEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entryLine: TransactionLineEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entryLines: List<TransactionLineEntity>): List<Long>

    @Update
    suspend fun update(entryLine: TransactionLineEntity)

    @Delete
    suspend fun delete(entryLine: TransactionLineEntity)

    @Query(
        """
        DELETE FROM transaction_lines
        WHERE transaction_id = :transactionId
    """
    )
        suspend fun deleteByTransaction(transactionId: Long)

    /**
     * Signed sum of confirmed, non-voided lines posted to [categoryId] whose
     * entry occurred within [monthStart, monthEnd]. Used by the budget screen
     * to derive real spend instead of placeholder figures.
     */
    @Query(
        """
        SELECT COALESCE(SUM(l.amount_paise), 0)
        FROM transaction_lines l
        JOIN transactions e ON e.id = l.transaction_id
        WHERE l.category_id = :categoryId
          AND e.status = 'confirmed'
          AND e.voided_at IS NULL
          AND (:monthStart IS NULL OR e.occurred_on >= :monthStart)
          AND (:monthEnd IS NULL OR e.occurred_on <= :monthEnd)
        """
    )
    suspend fun sumConfirmedForCategoryInMonth(
        categoryId: Long,
        monthStart: Long?,
        monthEnd: Long?,
    ): Long
}