package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("""
        SELECT * FROM categories
        ORDER BY sort_order ASC, name ASC
    """)
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("""
        SELECT * FROM categories
        WHERE id = :id
    """)
    suspend fun getById(id: Long): CategoryEntity?

    @Query("""
        SELECT * FROM categories
        WHERE parent_id IS NULL
        ORDER BY sort_order ASC, name ASC
    """)
    fun getRootCategories(): Flow<List<CategoryEntity>>

    @Query("""
        SELECT * FROM categories
        WHERE parent_id = :parentId
        ORDER BY sort_order ASC, name ASC
    """)
    fun getChildren(parentId: Long): Flow<List<CategoryEntity>>

    @Query("""
        SELECT * FROM categories
        WHERE is_excluded = 0
        ORDER BY sort_order ASC, name ASC
    """)
    fun getIncludedCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}