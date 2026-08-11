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