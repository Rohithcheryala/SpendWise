package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "entries",
    foreignKeys = [

        ForeignKey(
            entity = CounterpartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterparty_id"]
        ),
//        ForeignKey(
//            entity = GroupEntity::class,
//            parentColumns = ["id"],
//            childColumns = ["group_id"]
//        ),
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_entry_id"]
        )
    ],
    indices = [
        Index("counterparty_id"),
        Index("group_id"),
        Index("linked_entry_id")
    ]
)
data class EntryEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,


    @ColumnInfo(name = "occurred_on")
    val occurredOn: Long,

    @ColumnInfo(name = "happened_at")
    val happenedAt: Long? = null,

    @ColumnInfo(name = "counterparty_id")
    val counterpartyId: Long? = null,

    @ColumnInfo(name = "group_id")
    val groupId: Long? = null,

    val note: String? = null,

    val tags: String? = null,

    val status: String,

    val source: String,

    @ColumnInfo(name = "linked_entry_id")
    val linkedEntryId: Long? = null,

    @ColumnInfo(name = "voided_at")
    val voidedAt: Long? = null,

    @ColumnInfo(name = "voided_reason")
    val voidedReason: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
