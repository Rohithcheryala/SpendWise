package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "entry_lines",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"]
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"]
        ),
        ForeignKey(
            entity = BucketEntity::class,
            parentColumns = ["id"],
            childColumns = ["bucket_id"]
        ),
        ForeignKey(
            entity = CounterpartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterparty_id"]
        )
    ],
    indices = [
        Index("entry_id"),
        Index("account_id"),
        Index("category_id"),
        Index("bucket_id"),
        Index("counterparty_id")
    ]
)
data class EntryLineEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "entry_id")
    val entryId: Long,

    @ColumnInfo(name = "account_id")
    val accountId: Long? = null,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    @ColumnInfo(name = "bucket_id")
    val bucketId: Long? = null,

    @ColumnInfo(name = "counterparty_id")
    val counterpartyId: Long? = null,

    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,

    @ColumnInfo(name = "balance_after_paise")
    val balanceAfterPaise: Long? = null
)