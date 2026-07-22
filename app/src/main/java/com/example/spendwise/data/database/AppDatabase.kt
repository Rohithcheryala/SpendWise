package com.example.spendwise.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.spendwise.data.database.dao.AppMetadataDao
import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.database.dao.TransactionDao
import com.example.spendwise.data.database.entity.AppMetadataEntity
import com.example.spendwise.data.database.entity.CategoryEntity
import com.example.spendwise.data.database.entity.TransactionEntity


@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, AppMetadataEntity::class],
    version = 8
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun appMetadataDao(): AppMetadataDao
}