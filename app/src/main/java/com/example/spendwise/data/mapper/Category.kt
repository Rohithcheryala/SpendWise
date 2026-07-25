package com.example.spendwise.data.mapper

import com.example.spendwise.data.database.entity.CategoryEntity
import java.time.Instant
import java.time.LocalDateTime


data class Category(
    val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val isExcluded: Boolean = false,
    val sortOrder: Int = 0,
    val icon: String? = null,
    val createdAt: Long = Instant.now().toEpochMilli()
)


fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    parentId = parentId,
    isExcluded = isExcluded,
    sortOrder = sortOrder,
    icon = icon,
    createdAt = createdAt,
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    parentId = parentId,
    isExcluded = isExcluded,
    sortOrder = sortOrder,
    icon = icon,
    createdAt = createdAt,
)