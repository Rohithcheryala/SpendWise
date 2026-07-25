package com.example.spendwise.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.AppMetadataDao
import com.example.spendwise.data.database.dao.BucketDao
import com.example.spendwise.data.database.dao.BudgetDao
import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.database.dao.CounterpartyAliasDao
import com.example.spendwise.data.database.dao.CounterpartyDao
import com.example.spendwise.data.database.dao.EntryDao
import com.example.spendwise.data.database.dao.EntryLineDao
import com.example.spendwise.data.database.dao.EntryProvenanceDao
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.data.database.entity.AppMetadataEntity
import com.example.spendwise.data.database.entity.BucketEntity
import com.example.spendwise.data.database.entity.BudgetEntity
import com.example.spendwise.data.database.entity.CategoryEntity
import com.example.spendwise.data.database.entity.CounterpartyAliasEntity
import com.example.spendwise.data.database.entity.CounterpartyEntity
import com.example.spendwise.data.database.entity.EntryEntity
import com.example.spendwise.data.database.entity.EntryLineEntity
import com.example.spendwise.data.database.entity.EntryProvenanceEntity


@Database(
    entities = [
        AccountEntity::class,
        AppMetadataEntity::class,
        BucketEntity::class,
        BudgetEntity::class,
        CategoryEntity::class,
        CounterpartyEntity::class,
        CounterpartyAliasEntity::class,
        EntryEntity::class,
        EntryLineEntity::class,
        EntryProvenanceEntity::class,
//        TransactionEntity::class
    ],
    version = 10
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun AccountDao(): AccountDao

    abstract fun AppMetadataDao(): AppMetadataDao

    abstract fun BucketDao(): BucketDao

    abstract fun BudgetDao(): BudgetDao

    abstract fun CategoryDao(): CategoryDao

    abstract fun CounterpartyDao(): CounterpartyDao

    abstract fun CounterpartyAliasDao(): CounterpartyAliasDao

    abstract fun EntryDao(): EntryDao

    abstract fun EntryLineDao(): EntryLineDao

    abstract fun EntryProvenanceDao(): EntryProvenanceDao

//    abstract fun transactionDao(): TransactionDao
}