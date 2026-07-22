package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.AppMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMetadataDao {

    @Query("SELECT * FROM app_metadata")
    fun observeAll(): Flow<List<AppMetadataEntity>>

    @Query("SELECT * FROM app_metadata LIMIT 1")
    suspend fun get(): AppMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(metadata: AppMetadataEntity): Long

    @Update
    suspend fun update(metadata: AppMetadataEntity)

    @Delete
    suspend fun delete(metadata: AppMetadataEntity)

    @Query("SELECT COUNT(*) FROM app_metadata")
    suspend fun count(): Int
}