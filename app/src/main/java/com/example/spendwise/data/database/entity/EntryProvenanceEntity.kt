package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "entry_provenance",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["entry_id"]),
        Index(value = ["dedupe_hash"], unique = true)
    ]
)
data class EntryProvenanceEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "entry_id")
    val entryId: Long,

    @ColumnInfo(name = "raw_text")
    val rawText: String? = null,

    @ColumnInfo(name = "dedupe_hash")
    val dedupeHash: String? = null,

    @ColumnInfo(name = "bank_ref")
    val bankRef: String? = null
)