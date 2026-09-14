package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.BankAccountDetailsEntity

@Dao
interface BankAccountDetailsDao {

    @Query("SELECT * FROM bank_account_details WHERE account_id = :accountId")
    suspend fun getForAccount(accountId: Long): BankAccountDetailsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(details: BankAccountDetailsEntity)

    @Query("DELETE FROM bank_account_details WHERE account_id = :accountId")
    suspend fun deleteForAccount(accountId: Long)

    @Update
    suspend fun update(details: BankAccountDetailsEntity)
}