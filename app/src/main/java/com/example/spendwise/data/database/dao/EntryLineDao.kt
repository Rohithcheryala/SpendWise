package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.EntryLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryLineDao {

    @Query("""
        SELECT * FROM entry_lines
        ORDER BY id ASC
    """)
    fun getAll(): Flow<List<EntryLineEntity>>

    @Query("""
        SELECT * FROM entry_lines
        WHERE id = :id
    """)
    suspend fun getById(id: Long): EntryLineEntity?

    @Query("""
        SELECT * FROM entry_lines
        WHERE entry_id = :entryId
        ORDER BY id ASC
    """)
    fun getByEntry(entryId: Long): Flow<List<EntryLineEntity>>

    @Query("""
        SELECT * FROM entry_lines
        WHERE account_id = :accountId
        ORDER BY id DESC
    """)
    fun getByAccount(accountId: Long): Flow<List<EntryLineEntity>>

    @Query("""
        SELECT * FROM entry_lines
        WHERE category_id = :categoryId
        ORDER BY id DESC
    """)
    fun getByCategory(categoryId: Long): Flow<List<EntryLineEntity>>

    @Query("""
        SELECT * FROM entry_lines
        WHERE bucket_id = :bucketId
        ORDER BY id DESC
    """)
    fun getByBucket(bucketId: Long): Flow<List<EntryLineEntity>>

    @Query("""
        SELECT * FROM entry_lines
        WHERE counterparty_id = :counterpartyId
        ORDER BY id DESC
    """)
    fun getByCounterparty(counterpartyId: Long): Flow<List<EntryLineEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entryLine: EntryLineEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entryLines: List<EntryLineEntity>): List<Long>

    @Update
    suspend fun update(entryLine: EntryLineEntity)

    @Delete
    suspend fun delete(entryLine: EntryLineEntity)

    @Query("""
        DELETE FROM entry_lines
        WHERE entry_id = :entryId
    """)
    suspend fun deleteByEntry(entryId: Long)
}