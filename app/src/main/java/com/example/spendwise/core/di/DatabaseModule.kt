package com.example.spendwise.core.di


import android.content.Context
import androidx.room.Room
import com.example.spendwise.data.database.AppDatabase
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.AccountIdentifierDao
import com.example.spendwise.data.database.dao.AppMetadataDao
import com.example.spendwise.data.database.dao.BucketDao
import com.example.spendwise.data.database.dao.BudgetDao
import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.database.dao.ContactDao
import com.example.spendwise.data.database.dao.CounterpartyAliasDao
import com.example.spendwise.data.database.dao.CounterpartyDao
import com.example.spendwise.data.database.dao.TransactionDao
import com.example.spendwise.data.database.dao.TransactionLineDao
import com.example.spendwise.data.database.dao.TransactionProvenanceDao


import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "spendwise.db")
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.CategoryDao()

    @Provides
    fun provideAppMetadataDao(db: AppDatabase): AppMetadataDao = db.AppMetadataDao()

    // DAOs needed by the backend services now reachable through constructor
    // injection (IngestionService / LedgerService / counterparty services).

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.AccountDao()

    @Provides
    fun provideAccountIdentifierDao(db: AppDatabase): AccountIdentifierDao =
        db.AccountIdentifierDao()

    @Provides
    fun provideBucketDao(db: AppDatabase): BucketDao = db.BucketDao()

    @Provides
    fun provideBudgetDao(db: AppDatabase): BudgetDao = db.BudgetDao()

    @Provides
    fun provideContactDao(db: AppDatabase): ContactDao = db.ContactDao()

    @Provides
    fun provideCounterpartyDao(db: AppDatabase): CounterpartyDao = db.CounterpartyDao()

    @Provides
    fun provideCounterpartyAliasDao(db: AppDatabase): CounterpartyAliasDao =
        db.CounterpartyAliasDao()

    @Provides
    fun provideEntryDao(db: AppDatabase): TransactionDao = db.TransactionDao()

    @Provides
    fun provideEntryLineDao(db: AppDatabase): TransactionLineDao = db.TransactionLineDao()

    @Provides
    fun provideEntryProvenanceDao(db: AppDatabase): TransactionProvenanceDao = db.TransactionProvenanceDao()

    // add one @Provides per DAO as you add tables:
    // fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
}