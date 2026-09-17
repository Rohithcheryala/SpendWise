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
        WHERE default_account_id = :accountId
        ORDER BY display_name ASC
    """
    )
    fun getByDefaultAccount(accountId: Long): Flow<List<CounterpartyEntity>>

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

    /**
     * Net outstanding per PERSON counterparty, from the per-person receivable
     * child accounts under [potId] (Rust Q4/Q14: how much Rahul owes me is the
     * signed balance of his child pot). Uses the single balance read path
     * (v_account_balances) — positive = they owe you.
     */
    @Query(
        """
        SELECT c.id AS cpId, COALESCE(v.balance_paise, 0) AS net
        FROM counterparties c
        JOIN accounts a ON a.id = c.receivable_account_id AND a.parent_id = :potId
        JOIN v_account_balances v ON v.account_id = a.id
        WHERE c.party_type = 'person'
    """
    )
    suspend fun receivableNets(potId: Long): List<CounterpartyNetRow>

    /** Per-person receivable child accounts minted so far (children of [potId]). */
    @Query(
        """
        SELECT c.* FROM counterparties c
        JOIN accounts a ON a.id = c.receivable_account_id AND a.parent_id = :potId
    """
    )
    suspend fun withReceivableUnder(potId: Long): List<CounterpartyEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(counterparty: CounterpartyEntity): Long

    @Update
    suspend fun update(counterparty: CounterpartyEntity)

    @Delete
    suspend fun delete(counterparty: CounterpartyEntity)
}