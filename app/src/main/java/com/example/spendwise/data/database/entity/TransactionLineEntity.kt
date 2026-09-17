package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_lines",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"]
        )
    ],
    indices = [
        Index("transaction_id"),
        Index("account_id")
    ]
)
/**
 * One posting, onto exactly one account node — PURE (Rust migration 005):
 * (transaction_id, account_id, amount_paise). A line may target a bank
 * account, a category account (class income/expense), a system pot or a
 * per-person receivable child pot — accounts are the only thing a line can
 * aim at; who owes whom lives on the account node (per-person pots), not
 * here. SMS-stated balances live on transaction_provenance.
 */
data class TransactionLineEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,

    @ColumnInfo(name = "account_id")
    val accountId: Long,

    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,
)