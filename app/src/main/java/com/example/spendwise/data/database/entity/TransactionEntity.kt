package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
//  Identity fields
    @PrimaryKey val id: String,

//  Transaction fields
    @ColumnInfo(name = "status") // BUFFER | CONFIRMED
    val status: String = "BUFFER",
    @ColumnInfo(name = "source") // SMS | manual | <others>
    val source: String? = null,

//  Transaction account fields
    @ColumnInfo(name = "account_last4")
    val accountLast4: String? = null,

//  Transaction details fields
    val amount: Double, // TODO: amount as float or int
    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

//  Transaction's other end details fields
    @ColumnInfo(name = "merchant_name")
    val merchantName: String,

//  Transaction's grouping fields
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "tags")
    val tags: String, // Tags is supposed to be comma separated tags concatenated string

//  Transaction recognition fields
    @ColumnInfo(name = "transaction_hash")
    val transactionHash: String? = null,

//  Transaction Metadata fields
    @ColumnInfo(name = "occurred_at_epoch_millis")
    val occurredAtEpochMillis: Long,
    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false,

//  Generic Metadata
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long = occurredAtEpochMillis,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = occurredAtEpochMillis,

    val sourceSmsBody: String,
)