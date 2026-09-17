package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spendwise.data.database.entity.TagEntity
import com.example.spendwise.data.database.entity.TransactionTagEntity
import kotlinx.coroutines.flow.Flow

/**
 * Tags + transaction_tags (Rust migration 006). Tags are real rows joined
 * many-to-many to transactions — no more JSON strings on the transaction.
 */
@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT id FROM tags WHERE name = :name")
    suspend fun idByName(name: String): Long?

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAll(): Flow<List<TagEntity>>

    @Query("SELECT COUNT(*) FROM transaction_tags WHERE tag_id = :tagId")
    suspend fun usageCount(tagId: Long): Int

    /** Labels on one transaction, alphabetical. */
    @Query(
        """
        SELECT t.name FROM tags t
        JOIN transaction_tags tt ON tt.tag_id = t.id
        WHERE tt.transaction_id = :transactionId
        ORDER BY t.name ASC
    """
    )
    suspend fun namesFor(transactionId: Long): List<String>

    /** Most-used labels across non-voided transactions — the picker's pool. */
    @Query(
        """
        SELECT t.name FROM tags t
        JOIN transaction_tags tt ON tt.tag_id = t.id
        JOIN transactions e ON e.id = tt.transaction_id
        WHERE e.voided_at IS NULL
        GROUP BY t.name
        ORDER BY COUNT(*) DESC, t.name ASC
        LIMIT :limit
    """
    )
    suspend fun popularLabels(limit: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attachAll(rows: List<TransactionTagEntity>)

    @Query("DELETE FROM transaction_tags WHERE transaction_id = :transactionId")
    suspend fun detachAll(transactionId: Long)

    /** Tags left with no transaction after a detach/replace are swept. */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tag_id FROM transaction_tags)")
    suspend fun deleteOrphanTags()
}
