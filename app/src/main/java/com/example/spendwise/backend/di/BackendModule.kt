package com.example.spendwise.backend.di

import com.example.spendwise.backend.api.LedgerApi
import com.example.spendwise.backend.service.CounterpartyService
import com.example.spendwise.backend.service.IngestionService
import com.example.spendwise.backend.service.LedgerService
import com.example.spendwise.data.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wiring for the offline-first backend seam. [LedgerApi] is bound to the local
 * Room implementation; a future sync server would swap this binding (or wrap
 * it behind a network-first policy) without touching callers.
 */
@Module
@InstallIn(SingletonComponent::class)
object BackendModule {

    @Provides
    @Singleton
    fun provideCounterpartyService(db: AppDatabase): CounterpartyService =
        CounterpartyService(db.CounterpartyDao(), db.CounterpartyAliasDao())

    @Provides
    @Singleton
    fun provideIngestionService(
        db: AppDatabase,
        ledger: LedgerService,
        counterpartyService: CounterpartyService,
    ): IngestionService =
        IngestionService(
            accountDao = db.AccountDao(),
            identifierDao = db.AccountIdentifierDao(),
            entryDao = db.EntryDao(),
            entryLineDao = db.EntryLineDao(),
            provenanceDao = db.EntryProvenanceDao(),
            ledger = ledger,
            counterparties = counterpartyService,
        )

    @Provides
    @Singleton
    fun provideLedgerApi(
        db: AppDatabase,
        counterpartyService: CounterpartyService,
    ): LedgerApi =
        LedgerService(
            db = db,
            accountDao = db.AccountDao(),
            bucketDao = db.BucketDao(),
            categoryDao = db.CategoryDao(),
            entryDao = db.EntryDao(),
            entryLineDao = db.EntryLineDao(),
            provenanceDao = db.EntryProvenanceDao(),
            counterpartyService = counterpartyService,
        )
}