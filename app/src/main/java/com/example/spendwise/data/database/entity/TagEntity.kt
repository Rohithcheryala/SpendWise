package com.example.spendwise.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A free-form label (Rust migration 006). user_id is dropped: single-user app. */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
)
