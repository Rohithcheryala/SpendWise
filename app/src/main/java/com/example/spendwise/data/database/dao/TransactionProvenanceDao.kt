package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.TransactionProvenanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionProvenanceDao {

    @Query(
        """
        SELECT * FROM transaction_provenance
        ORDER BY id DESC
    """
    )
    fun getAll(): Flow<List<TransactionProvenanceEntity>>

    @Query(
        """
        SELECT * FROM transaction_provenance
        WHERE id = :id
    """
    )
    suspend fun getById(id: Long): TransactionProvenanceEntity?

    @Query(
        """
        SELECT * FROM transaction_provenance
        WHERE transaction_id = :transactionId
        LIMIT 1
    """
    )
    suspend fun getByTransaction(transactionId: Long): TransactionProvenanceEntity?

    @Query(
        """
        SELECT * FROM transaction_provenance
        WHERE dedupe_hash = :dedupeHash
        LIMIT 1
    """
    )
    suspend fun getByDedupeHash(dedupeHash: String): TransactionProvenanceEntity?

    @Query(
        """
        SELECT * FROM transaction_provenance
        WHERE bank_ref = :bankRef
    """
    )
    fun getByBankRef(bankRef: String): Flow<List<TransactionProvenanceEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entryProvenance: TransactionProvenanceEntity): Long

    @Update
    suspend fun update(entryProvenance: TransactionProvenanceEntity)

    @Delete
    suspend fun delete(entryProvenance: TransactionProvenanceEntity)
}