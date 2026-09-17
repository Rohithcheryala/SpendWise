package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"]
        ),
    ],
    indices = [
        Index(value = ["account_id", "period"], unique = true)
    ]
)
/**
 * An envelope: a monthly spend cap on one expense-node account (Rust
 * migration 006). Keyed by (account_id, [period]) with period 'YYYY-MM' —
 * "left to spend" = amount - SUM(confirmed lines to the node within the
 * month). No allocation transactions exist; value-holding goal pots are
 * child accounts (parent_id), not budgets.
 */
data class BudgetEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "account_id")
    val accountId: Long,

    /** Month key, 'YYYY-MM'. */
    val period: String,

    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)