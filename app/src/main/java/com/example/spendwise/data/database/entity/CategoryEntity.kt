package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    foreignKeys = [
        // ForeignKey(
        //     entity = CategoryEntity::class,
        //     parentColumns = ["id"],
        //     childColumns = ["parent_id"]
        // )
    ],
    indices = [
        Index("parent_id"),
        Index(value = ["parent_id", "name"], unique = true)
    ]
)
data class CategoryEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,

    @ColumnInfo(name = "is_excluded")
    val isExcluded: Boolean = false,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    val icon: String? = null,

    /**
     * "income" | "expense" — which side of the ledger this category posts to.
     * Mirrors the old server's Category.kind (needed by describe-entry and the
     * ingestion contra-line rules). Defaults to "expense" for legacy rows.
     */
    @ColumnInfo(name = "kind", defaultValue = "expense")
    val kind: String = "expense",

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)