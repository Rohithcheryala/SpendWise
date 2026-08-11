package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "counterparties",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["default_category_id"]
        ),
    ],
    indices = [
        Index("default_category_id")
    ]
)
data class CounterpartyEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "party_type")
    val partyType: String,

    @ColumnInfo(name = "default_tags")
    val defaultTags: String? = null,

    @ColumnInfo(name = "default_intent")
    val defaultIntent: String? = null,

    val notes: String? = null,

    @ColumnInfo(name = "first_seen")
    val firstSeen: Long? = null,

    @ColumnInfo(name = "last_seen")
    val lastSeen: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "default_category_id")
    val defaultCategoryId: Long? = null
)