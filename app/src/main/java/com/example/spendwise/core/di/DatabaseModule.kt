package com.example.spendwise.core.di


import android.content.Context
import androidx.room.Room
import com.example.spendwise.data.database.AppDatabase
import com.example.spendwise.data.database.dao.AppMetadataDao
import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.database.dao.TransactionDao


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
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.CategoryDao()

    @Provides
    fun provideAppMetadataDao(db: AppDatabase): AppMetadataDao = db.AppMetadataDao()

    // add one @Provides per DAO as you add tables:
    // fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
}