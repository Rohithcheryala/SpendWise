package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    foreignKeys = [
    ],
    indices = [
        Index(value = ["slug"], unique = true),
        Index(value = ["last4"])
    ]
)
data class AccountEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val slug: String,

    val name: String,

    val bank: String? = null,

    val kind: String,

    val last4: String? = null,

    val platform: String? = null,

    @ColumnInfo(name = "opening_balance_paise")
    val openingBalancePaise: Long,

    @ColumnInfo(name = "credit_limit_paise")
    val creditLimitPaise: Long? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "closed_at")
    val closedAt: Long? = null,

    @ColumnInfo(name = "reconciled_through")
    val reconciledThrough: Long? = null
)