package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"]
        ),
    ],
    indices = [
        Index("category_id")
    ]
)
data class BudgetEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,

    @ColumnInfo(name = "effective_from")
    val effectiveFrom: LocalDate,

    @ColumnInfo(name = "effective_to")
    val effectiveTo: LocalDate? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime
)