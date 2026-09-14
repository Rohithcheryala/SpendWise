package com.example.spendwise.data.mapper

import androidx.room.Embedded
import androidx.room.Relation
import com.example.spendwise.data.database.entity.TransactionEntity
import com.example.spendwise.data.database.entity.TransactionLineEntity
import com.example.spendwise.data.database.entity.TransactionProvenanceEntity

data class TransactionWithDetails(

    @Embedded
    val entry: TransactionEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "transaction_id"
    )
    val lines: List<TransactionLineEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "transaction_id"
    )
    val provenance: List<TransactionProvenanceEntity>
)
