package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_provenance",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transaction_id"]),
        Index(value = ["dedupe_hash"], unique = true)
    ]
)
data class TransactionProvenanceEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,

    @ColumnInfo(name = "raw_text")
    val rawText: String? = null,

    @ColumnInfo(name = "dedupe_hash")
    val dedupeHash: String? = null,

    @ColumnInfo(name = "bank_ref")
    val bankRef: String? = null,

    /**
     * Parse facts frozen at ingest time ("bank|last4|accountKind"), so orphan
     * reclaim can re-run account matching without re-parsing the raw SMS.
     */
    @ColumnInfo(name = "parsed_facts")
    val parsedFacts: String? = null,

    /**
     * The running balance the SMS stated AFTER this movement. Kept here —
     * NOT on lines (Rust Q12 deletes balance_after_paise from lines: it was
     * written from SMS claims and never maintained on edits) — so the
     * reconciliation continuity check still has its anchor while balances
     * stay derived from lines only.
     */
    @ColumnInfo(name = "stated_balance_paise")
    val statedBalancePaise: Long? = null
)