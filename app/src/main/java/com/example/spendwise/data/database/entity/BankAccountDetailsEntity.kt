package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-bank-account facts that only some accounts have (Rust-shaped side
 * table). Kept off [AccountEntity] so category/bucket rows stay lean.
 */
@Entity(
    tableName = "bank_account_details",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [
        Index(value = ["account_id"], unique = true)
    ]
)
data class BankAccountDetailsEntity(

    /** 1:1 with accounts — the FK is the PK. */
    @ColumnInfo(name = "account_id")
    @PrimaryKey
    val accountId: Long,

    @ColumnInfo(name = "credit_limit_paise")
    val creditLimitPaise: Long? = null,

    @ColumnInfo(name = "statement_day")
    val statementDay: Int? = null,

    @ColumnInfo(name = "due_day")
    val dueDay: Int? = null,

    /** Last date reconciled against a real statement. */
    @ColumnInfo(name = "reconciled_through")
    val reconciledThrough: Long? = null
)