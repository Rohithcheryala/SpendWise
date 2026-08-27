package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spendwise.data.database.entity.ContactEntity

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts WHERE phone_last10 = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE phone_last10 IN (:phones)")
    suspend fun getByPhones(phones: List<String>): List<ContactEntity>

    @Query("DELETE FROM contacts")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)
}
