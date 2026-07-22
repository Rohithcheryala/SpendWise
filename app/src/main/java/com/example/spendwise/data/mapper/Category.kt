package com.example.spendwise.data.mapper

import com.example.spendwise.data.database.entity.CategoryEntity


data class Category(
    val id: Long = 0,
    val name: String,
    val color: Long? = null,
    val icon: String? = null
)


fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    color = color,
    icon = icon
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    color = color,
    icon = icon
)