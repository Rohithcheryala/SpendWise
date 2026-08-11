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

    @Query(
        """
        SELECT * FROM budgets
        ORDER BY effective_from DESC
    """
    )
    fun getAll(): Flow<List<BudgetEntity>>

    @Query(
        """
        SELECT * FROM budgets
        WHERE id = :id
    """
    )
    suspend fun getById(id: Long): BudgetEntity?

    @Query(
        """
        SELECT * FROM budgets
        WHERE category_id = :categoryId
        ORDER BY effective_from DESC
    """
    )
    fun getByCategory(categoryId: Long): Flow<List<BudgetEntity>>

    @Query(
        """
        SELECT * FROM budgets
        WHERE effective_from <= :date
          AND (effective_to IS NULL OR effective_to >= :date)
    """
    )
    fun getActiveBudgets(date: Long): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)
}