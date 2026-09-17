package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets ORDER BY period DESC, account_id ASC")
    fun getAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): BudgetEntity?

    /** All envelopes for one month key ('YYYY-MM'). */
    @Query("SELECT * FROM budgets WHERE period = :period ORDER BY account_id ASC")
    fun getByPeriod(period: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE account_id = :accountId ORDER BY period DESC")
    fun getByAccount(accountId: Long): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE account_id = :accountId AND period = :period LIMIT 1")
    suspend fun getFor(accountId: Long, period: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)
}