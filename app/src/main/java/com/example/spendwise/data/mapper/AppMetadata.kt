package com.example.spendwise.data.mapper

import com.example.spendwise.data.database.entity.AppMetadataEntity


data class AppMetadata(
    val id: Int = 1,
    val databaseId: String,
    val revision: Long,
    val trackingStartDate: Long,
    val lastSmsSync: Long,
    val lastBackupTime: Long?
)


fun AppMetadataEntity.toDomain() = AppMetadata(
    id = id,
    databaseId = databaseId,
    revision = revision,
    trackingStartDate = trackingStartDate,
    lastSmsSync = lastSmsSync,
    lastBackupTime = lastBackupTime,
)

fun AppMetadata.toEntity() = AppMetadataEntity(
    id = id,
    databaseId = databaseId,
    revision = revision,
    trackingStartDate = trackingStartDate,
    lastSmsSync = lastSmsSync,
    lastBackupTime = lastBackupTime,
)