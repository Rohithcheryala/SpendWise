package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.AccountBalanceRow
import com.example.spendwise.data.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY sort_order ASC, name ASC")
    fun getAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun listAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY sort_order ASC, name ASC")
    fun getActive(): Flow<List<AccountEntity>>

    /** User-pickable money accounts — asset/liability classes only, so the
     *  income/expense rows that double as categories never leak into
     *  account pickers. */
    @Query(
        """
        SELECT * FROM accounts
        WHERE account_class IN ('asset', 'liability') AND is_system = 0 AND is_archived = 0
        ORDER BY sort_order ASC, name ASC
    """
    )
    fun getUserAccounts(): Flow<List<AccountEntity>>

    /**
     * The ONE balance read path: v_account_balances (signed sum of confirmed,
     * non-voided lines, liabilities already inverted). Returns null when the
     * account doesn't exist — callers decide whether that's an error.
     */
    @Query("SELECT * FROM v_account_balances WHERE account_id = :accountId")
    suspend fun balanceOf(accountId: Long): AccountBalanceRow?

    /** User-visible accounts of one class (categories live here too). */
    @Query(
        """
        SELECT * FROM accounts
        WHERE account_class = :cls AND is_system = 0 AND is_archived = 0
        ORDER BY sort_order ASC, name ASC
    """
    )
    fun getByClass(cls: String): Flow<List<AccountEntity>>

    /** All user category accounts (class income/expense) — the old categories table. */
    @Query(
        """
        SELECT * FROM accounts
        WHERE account_class IN ('income', 'expense') AND is_system = 0 AND is_archived = 0
        ORDER BY COALESCE(parent_id, id) ASC, parent_id IS NOT NULL ASC, sort_order ASC, name ASC
    """
    )
    fun getCategoryAccounts(): Flow<List<AccountEntity>>

    @Query(
        """
        SELECT * FROM accounts
        WHERE account_class IN ('income', 'expense') AND is_system = 0 AND is_archived = 0
          AND parent_id IS NULL
        ORDER BY sort_order ASC, name ASC
    """
    )
    fun getRootCategoryAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE parent_id = :parentId ORDER BY sort_order ASC, name ASC")
    fun getChildren(parentId: Long): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE parent_id = :parentId AND is_archived = 0 ORDER BY sort_order ASC, name ASC")
    suspend fun listChildren(parentId: Long): List<AccountEntity>

    /** System category accounts ("Unclassified" etc.) — get-or-create target. */
    @Query(
        """
        SELECT * FROM accounts
        WHERE account_class = :cls AND name = :name AND is_system = 1
        LIMIT 1
    """
    )
    suspend fun findSystemByName(cls: String, name: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE is_system = 1 AND subtype = :subtype LIMIT 1")
    suspend fun findSystemBySubtype(subtype: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)
}