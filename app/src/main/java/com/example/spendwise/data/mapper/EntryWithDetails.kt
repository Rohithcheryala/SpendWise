package com.example.spendwise.data.mapper

import androidx.room.Embedded
import androidx.room.Relation
import com.example.spendwise.data.database.entity.EntryEntity
import com.example.spendwise.data.database.entity.EntryLineEntity
import com.example.spendwise.data.database.entity.EntryProvenanceEntity

data class EntryWithDetails(

    @Embedded
    val entry: EntryEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "entry_id"
    )
    val lines: List<EntryLineEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "entry_id"
    )
    val provenance: List<EntryProvenanceEntity>
)


enum class EntryStatus {
    BUFFER,
    CONFIRMED
}

enum class EntrySource {
    SMS,
    QR_SCAN,
    MANUAL,
    SPLIT,
    NOTIFICATION,
    STATEMENT
}