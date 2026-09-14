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
        Index("account_id")
    ]
)
/**
 * A spend ceiling on one category account. [effectiveFrom/To] are
 * transitional — 3b-2 re-keys budgets to monthly `period` ('YYYY-MM').
 */
data class BudgetEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "account_id")
    val accountId: Long,

    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,

    @ColumnInfo(name = "effective_from")
    val effectiveFrom: Long,

    @ColumnInfo(name = "effective_to")
    val effectiveTo: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)