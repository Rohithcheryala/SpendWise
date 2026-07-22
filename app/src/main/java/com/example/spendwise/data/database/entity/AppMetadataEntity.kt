package com.example.spendwise.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(

    @PrimaryKey
    val id: Int = 1,

    val databaseId: String,

    val revision: Long,

    val trackingStartDate: Long,

    val lastSmsSync: Long,

    val lastBackupTime: Long?
)