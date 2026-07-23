package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.BucketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BucketDao {

    @Query("SELECT * FROM buckets ORDER BY sort_order ASC, name ASC")
    fun getAll(): Flow<List<BucketEntity>>

    @Query("SELECT * FROM buckets WHERE id = :id")
    suspend fun getById(id: Long): BucketEntity?

    @Query("""
        SELECT * FROM buckets
        WHERE account_id = :accountId
        ORDER BY sort_order ASC, name ASC
    """)
    fun getByAccount(accountId: Long): Flow<List<BucketEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bucket: BucketEntity): Long

    @Update
    suspend fun update(bucket: BucketEntity)

    @Delete
    suspend fun delete(bucket: BucketEntity)
}