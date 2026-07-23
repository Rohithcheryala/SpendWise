package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "buckets",
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
data class BucketEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "account_id")
    val accountId: Long,

    val name: String,

    @ColumnInfo(name = "target_paise")
    val targetPaise: Long? = null,

    val icon: String? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime,

    @ColumnInfo(name = "manual_allocation_paise")
    val manualAllocationPaise: Long = 0
)