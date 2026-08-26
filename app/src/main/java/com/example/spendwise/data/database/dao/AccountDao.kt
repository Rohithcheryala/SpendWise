package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE last4 = :last4")
    suspend fun getByLast4(last4: String): List<AccountEntity>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun listAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE is_active = 1 ORDER BY name ASC")
    fun getActive(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)
}