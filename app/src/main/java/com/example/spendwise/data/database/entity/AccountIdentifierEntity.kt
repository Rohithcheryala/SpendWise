package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * How a last-4 attaches to an account. `account` = bank account number
 * (savings UPI/NEFT SMS), `card` = card number (credit-card SMS, POS/ATM).
 * One account owns several (its account number + each debit card).
 *
 * Port of the old server's account_identifiers table — storing these per-kind
 * is what keeps SMS from silently orphaning.
 */
@Entity(
    tableName = "account_identifiers",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("account_id"),
        Index(value = ["value", "is_active"])
    ]
)
data class AccountIdentifierEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "account_id")
    val accountId: Long,

    /** The last-4 digits of the account or card number. */
    val value: String,

    /** "account" | "card" */
    val kind: String,

    val label: String? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)