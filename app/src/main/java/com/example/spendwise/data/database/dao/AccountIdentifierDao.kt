package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spendwise.data.database.entity.AccountIdentifierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountIdentifierDao {

    @Query("SELECT * FROM account_identifiers WHERE account_id = :accountId ORDER BY id ASC")
    fun getByAccount(accountId: Long): Flow<List<AccountIdentifierEntity>>

    @Query("SELECT * FROM account_identifiers WHERE account_id = :accountId ORDER BY id ASC")
    suspend fun getByAccountList(accountId: Long): List<AccountIdentifierEntity>

    /** The lookup hot path for SMS matching (see IngestionService.findAccount). */
    @Query("SELECT * FROM account_identifiers WHERE value = :last4 AND is_active = 1")
    suspend fun getActiveByValue(last4: String): List<AccountIdentifierEntity>

    @Query("SELECT * FROM account_identifiers WHERE is_active = 1")
    suspend fun getAllActive(): List<AccountIdentifierEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(identifier: AccountIdentifierEntity): Long

    @Query("UPDATE account_identifiers SET is_active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Query("DELETE FROM account_identifiers WHERE id = :id")
    suspend fun delete(id: Long)
}