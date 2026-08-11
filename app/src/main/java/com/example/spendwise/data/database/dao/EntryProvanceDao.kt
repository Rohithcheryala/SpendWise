package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.EntryProvenanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryProvenanceDao {

    @Query(
        """
        SELECT * FROM entry_provenance
        ORDER BY id DESC
    """
    )
    fun getAll(): Flow<List<EntryProvenanceEntity>>

    @Query(
        """
        SELECT * FROM entry_provenance
        WHERE id = :id
    """
    )
    suspend fun getById(id: Long): EntryProvenanceEntity?

    @Query(
        """
        SELECT * FROM entry_provenance
        WHERE entry_id = :entryId
        LIMIT 1
    """
    )
    suspend fun getByEntry(entryId: Long): EntryProvenanceEntity?

    @Query(
        """
        SELECT * FROM entry_provenance
        WHERE dedupe_hash = :dedupeHash
        LIMIT 1
    """
    )
    suspend fun getByDedupeHash(dedupeHash: String): EntryProvenanceEntity?

    @Query(
        """
        SELECT * FROM entry_provenance
        WHERE bank_ref = :bankRef
    """
    )
    fun getByBankRef(bankRef: String): Flow<List<EntryProvenanceEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entryProvenance: EntryProvenanceEntity): Long

    @Update
    suspend fun update(entryProvenance: EntryProvenanceEntity)

    @Delete
    suspend fun delete(entryProvenance: EntryProvenanceEntity)
}