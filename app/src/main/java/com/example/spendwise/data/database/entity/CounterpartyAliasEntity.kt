package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "counterparty_aliases",
    foreignKeys = [
        ForeignKey(
            entity = CounterpartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterparty_id"]
        )
    ],
    indices = [
        Index("counterparty_id"),
        Index(value = ["alias_norm"], unique = true)
    ]
)
data class CounterpartyAliasEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "counterparty_id")
    val counterpartyId: Long,

    @ColumnInfo(name = "alias_norm")
    val aliasNorm: String,

    @ColumnInfo(name = "alias_display")
    val aliasDisplay: String,

    val source: String
)