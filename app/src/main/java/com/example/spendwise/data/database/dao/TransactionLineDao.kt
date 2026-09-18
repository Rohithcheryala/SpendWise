package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.TransactionLineEntity
import kotlinx.coroutines.flow.Flow

/** Projection for per-person receivable nets (see CounterpartyDao.receivableNets). */
data class CounterpartyNetRow(
    val cpId: Long,
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
     * Signed raw sum of this account's lines on money-real transactions —
     * confirmed or pending-review (buffer), never voided — optionally bounded
     * by occurred_on <= :throughMillis. Same inclusion rule as
     * v_account_balances (a view can't take a date parameter); used for the
     * reconciliation continuity check and tests. Month-scoped spend analytics
     * (sumConfirmedForAccountInMonth) deliberately stays confirmed-only.
     */
    @Query(
        """
        SELECT COALESCE(SUM(l.amount_paise), 0)
        FROM transaction_lines l
        JOIN transactions e ON e.id = l.transaction_id
        WHERE l.account_id = :accountId
          AND e.status IN ('confirmed', 'buffer')
          AND e.voided_at IS NULL
          AND (:throughMillis IS NULL OR e.occurred_on <= :throughMillis)
    """
    )
    suspend fun sumPostedForAccount(accountId: Long, throughMillis: Long?): Long

    /** Transaction ids having a line on the given account. */
    @Query("SELECT DISTINCT transaction_id FROM transaction_lines WHERE account_id = :accountId")
    suspend fun transactionIdsForAccount(accountId: Long): List<Long>

    @Query(
        """
        SELECT * FROM transaction_lines
        WHERE account_id = :accountId
        ORDER BY id DESC
    """
    )
    fun getByAccount(accountId: Long): Flow<List<TransactionLineEntity>>

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
     * Signed sum of confirmed, non-voided lines posted to [accountId] (a
     * category account under the unified model) whose transaction occurred
     * within [monthStart, monthEnd]. Used by the budget screen to derive real
     * spend instead of placeholder figures.
     */
    @Query(
        """
        SELECT COALESCE(SUM(l.amount_paise), 0)
        FROM transaction_lines l
        JOIN transactions e ON e.id = l.transaction_id
        WHERE l.account_id = :accountId
          AND e.status = 'confirmed'
          AND e.voided_at IS NULL
          AND (:monthStart IS NULL OR e.occurred_on >= :monthStart)
          AND (:monthEnd IS NULL OR e.occurred_on <= :monthEnd)
        """
    )
    suspend fun sumConfirmedForAccountInMonth(
        accountId: Long,
        monthStart: Long?,
        monthEnd: Long?,
    ): Long
}