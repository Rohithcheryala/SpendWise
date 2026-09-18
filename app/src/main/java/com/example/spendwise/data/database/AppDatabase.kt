package com.example.spendwise.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.AccountIdentifierDao
import com.example.spendwise.data.database.dao.AppMetadataDao
import com.example.spendwise.data.database.dao.BankAccountDetailsDao
import com.example.spendwise.data.database.dao.BudgetDao
import com.example.spendwise.data.database.dao.ContactDao
import com.example.spendwise.data.database.dao.CounterpartyAliasDao
import com.example.spendwise.data.database.dao.CounterpartyDao
import com.example.spendwise.data.database.dao.GroupDao
import com.example.spendwise.data.database.dao.TagDao
import com.example.spendwise.data.database.dao.TransactionDao
import com.example.spendwise.data.database.dao.TransactionLineDao
import com.example.spendwise.data.database.dao.TransactionProvenanceDao
import com.example.spendwise.data.database.entity.AccountBalanceRow
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.data.database.entity.AccountIdentifierEntity
import com.example.spendwise.data.database.entity.AppMetadataEntity
import com.example.spendwise.data.database.entity.BankAccountDetailsEntity
import com.example.spendwise.data.database.entity.BudgetEntity
import com.example.spendwise.data.database.entity.ContactEntity
import com.example.spendwise.data.database.entity.CounterpartyAliasEntity
import com.example.spendwise.data.database.entity.CounterpartyEntity
import com.example.spendwise.data.database.entity.GroupEntity
import com.example.spendwise.data.database.entity.GroupMemberEntity
import com.example.spendwise.data.database.entity.TagEntity
import com.example.spendwise.data.database.entity.TransactionEntity
import com.example.spendwise.data.database.entity.TransactionLineEntity
import com.example.spendwise.data.database.entity.TransactionProvenanceEntity
import com.example.spendwise.data.database.entity.TransactionTagEntity


@Database(
    entities = [
        AccountEntity::class,
        AccountIdentifierEntity::class,
        AppMetadataEntity::class,
        BankAccountDetailsEntity::class,
        BudgetEntity::class,
        ContactEntity::class,

        CounterpartyEntity::class,
        CounterpartyAliasEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        TagEntity::class,
        TransactionEntity::class,
        TransactionLineEntity::class,
        TransactionProvenanceEntity::class,
        TransactionTagEntity::class
    ],
    views = [AccountBalanceRow::class],
    version = 18
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun AccountDao(): AccountDao

    abstract fun AccountIdentifierDao(): AccountIdentifierDao

    abstract fun ContactDao(): ContactDao
    abstract fun AppMetadataDao(): AppMetadataDao

    abstract fun BankAccountDetailsDao(): BankAccountDetailsDao

    abstract fun BudgetDao(): BudgetDao

    abstract fun CounterpartyDao(): CounterpartyDao

    abstract fun CounterpartyAliasDao(): CounterpartyAliasDao

    abstract fun GroupDao(): GroupDao

    abstract fun TagDao(): TagDao

    abstract fun TransactionDao(): TransactionDao

    abstract fun TransactionLineDao(): TransactionLineDao

    abstract fun TransactionProvenanceDao(): TransactionProvenanceDao

    // Migration policy: the schema is being rebuilt the Rust way and data is
    // disposable — the builder uses fallbackToDestructiveMigration(); no
    // incremental migrations are kept (see STEP_TRACKER.md, step 3).
}