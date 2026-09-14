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
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["default_account_id"]
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["receivable_account_id"]
        ),
    ],
    indices = [
        Index("default_account_id"),
        Index("receivable_account_id")
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

    /** Default posting account (a category account under the unified model). */
    @ColumnInfo(name = "default_account_id")
    val defaultAccountId: Long? = null,

    /**
     * The per-person child account under the loans-receivable pot — the one
     * counterparty→ledger bridge (Rust migration 003). Wired into the loan
     * posting paths in 3b-2 when lines go pure.
     */
    @ColumnInfo(name = "receivable_account_id")
    val receivableAccountId: Long? = null
)