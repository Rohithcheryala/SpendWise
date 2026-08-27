package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.CounterpartyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterpartyDao {

    @Query(
        """
        SELECT * FROM counterparties
        ORDER BY display_name ASC
    """
    )
    fun getAll(): Flow<List<CounterpartyEntity>>

    @Query(
        """
        SELECT * FROM counterparties
        WHERE id = :id
    """
    )
    suspend fun getById(id: Long): CounterpartyEntity?

    @Query(
        """
        SELECT * FROM counterparties
        WHERE display_name = :displayName
        LIMIT 1
    """
    )
    suspend fun getByDisplayName(displayName: String): CounterpartyEntity?

    @Query(
        """
        SELECT * FROM counterparties
        WHERE default_category_id = :categoryId
        ORDER BY display_name ASC
    """
    )
    fun getByCategory(categoryId: Long): Flow<List<CounterpartyEntity>>

    @Query(
        """
        SELECT * FROM counterparties
        WHERE party_type = :partyType
        ORDER BY display_name ASC
    """
    )
    fun getByPartyType(partyType: String): Flow<List<CounterpartyEntity>>

    @Query("SELECT * FROM counterparties ORDER BY display_name ASC")
    suspend fun listAll(): List<CounterpartyEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(counterparty: CounterpartyEntity): Long

    @Update
    suspend fun update(counterparty: CounterpartyEntity)

    @Delete
    suspend fun delete(counterparty: CounterpartyEntity)
}