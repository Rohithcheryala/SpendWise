package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.CounterpartyAliasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterpartyAliasDao {

    @Query(
        """
        SELECT * FROM counterparty_aliases
        ORDER BY alias_display ASC
    """
    )
    fun getAll(): Flow<List<CounterpartyAliasEntity>>

    @Query(
        """
        SELECT * FROM counterparty_aliases
        WHERE id = :id
    """
    )
    suspend fun getById(id: Long): CounterpartyAliasEntity?

    @Query(
        """
        SELECT * FROM counterparty_aliases
        WHERE counterparty_id = :counterpartyId
        ORDER BY alias_display ASC
    """
    )
    fun getByCounterparty(counterpartyId: Long): Flow<List<CounterpartyAliasEntity>>

    @Query(
        """
        SELECT * FROM counterparty_aliases
        WHERE alias_norm = :aliasNorm
        LIMIT 1
    """
    )
    suspend fun findByNormalizedAlias(aliasNorm: String): CounterpartyAliasEntity?

    @Query(
        """
        SELECT COUNT(*) FROM counterparty_aliases
        WHERE counterparty_id = :counterpartyId AND alias_norm = :aliasNorm
    """
    )
    suspend fun countAlias(counterpartyId: Long, aliasNorm: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(alias: CounterpartyAliasEntity): Long

    @Update
    suspend fun update(alias: CounterpartyAliasEntity)

    @Delete
    suspend fun delete(alias: CounterpartyAliasEntity)
}