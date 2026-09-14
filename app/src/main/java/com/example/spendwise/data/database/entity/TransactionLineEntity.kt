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
        ),
        ForeignKey(
            entity = CounterpartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterparty_id"]
        )
    ],
    indices = [
        Index("transaction_id"),
        Index("account_id"),
        Index("counterparty_id")
    ]
)
/**
 * One posting, onto exactly one account node — which may be a bank account,
 * a category account (class income/expense) or a system pot. Pure lines
 * (transaction_id, account_id, amount_paise) plus the two transitional
 * enrichments [counterpartyId] (who owes, on receivable legs) and
 * [balanceAfterPaise] (SMS-stated running balance), both slated to move in
 * 3b-2.
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

    @ColumnInfo(name = "counterparty_id")
    val counterpartyId: Long? = null,

    @ColumnInfo(name = "balance_after_paise")
    val balanceAfterPaise: Long? = null
)