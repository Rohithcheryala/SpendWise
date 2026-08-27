package com.example.spendwise.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.AccountIdentifierDao
import com.example.spendwise.data.database.dao.AppMetadataDao
import com.example.spendwise.data.database.dao.BucketDao
import com.example.spendwise.data.database.dao.BudgetDao
import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.database.dao.ContactDao
import com.example.spendwise.data.database.dao.CounterpartyAliasDao
import com.example.spendwise.data.database.dao.CounterpartyDao
import com.example.spendwise.data.database.dao.EntryDao
import com.example.spendwise.data.database.dao.EntryLineDao
import com.example.spendwise.data.database.dao.EntryProvanceDao
import com.example.spendwise.data.database.dao.TransactionDao
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.data.database.entity.AccountIdentifierEntity
import com.example.spendwise.data.database.entity.AppMetadataEntity
import com.example.spendwise.data.database.entity.BucketEntity
import com.example.spendwise.data.database.entity.BudgetEntity
import com.example.spendwise.data.database.entity.CategoryEntity
import com.example.spendwise.data.database.entity.ContactEntity
import com.example.spendwise.data.database.entity.CounterpartyAliasEntity
import com.example.spendwise.data.database.entity.CounterpartyEntity
import com.example.spendwise.data.database.entity.EntryEntity
import com.example.spendwise.data.database.entity.EntryLineEntity
import com.example.spendwise.data.database.entity.EntryProvenanceEntity
import com.example.spendwise.data.database.entity.TransactionEntity


@Database(
    entities = [
        AccountEntity::class,
        AccountIdentifierEntity::class,
        AppMetadataEntity::class,
        BucketEntity::class,
        BudgetEntity::class,
        CategoryEntity::class,
        ContactEntity::class,

        CounterpartyEntity::class,
        CounterpartyAliasEntity::class,
        EntryEntity::class,
        EntryLineEntity::class,
        EntryProvenanceEntity::class,
        TransactionEntity::class
    ],
    version = 14
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun AccountDao(): AccountDao

    abstract fun AccountIdentifierDao(): AccountIdentifierDao

    abstract fun ContactDao(): ContactDao
    abstract fun AppMetadataDao(): AppMetadataDao

    abstract fun BucketDao(): BucketDao

    abstract fun BudgetDao(): BudgetDao

    abstract fun CategoryDao(): CategoryDao

    abstract fun CounterpartyDao(): CounterpartyDao

    abstract fun CounterpartyAliasDao(): CounterpartyAliasDao

    abstract fun EntryDao(): EntryDao

    abstract fun EntryLineDao(): EntryLineDao

    abstract fun EntryProvanceDao(): EntryProvanceDao

    abstract fun transactionDao(): TransactionDao

    companion object {
        /**
         * v11 -> v12: categories gained `kind` ("income" | "expense") so the
         * ledger service can classify entries and pick ingestion contra
         * categories without guessing from the tree position. Existing rows
         * default to "expense".
         */
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE categories ADD COLUMN kind TEXT NOT NULL DEFAULT 'expense'"
                )
            }
        }

        /**
         * v12 -> v13: per-kind account identifiers (the old server's
         * account_identifiers table) so SMS matching knows whether a last-4 is
         * an account number or a card number, plus `parsed_facts` on
         * provenance so orphan reclaim re-matches without re-parsing.
         * Existing accounts' `last4` seeds an active 'account' identifier.
         */
        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS account_identifiers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        account_id INTEGER NOT NULL,
                        value TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        label TEXT,
                        is_active INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_account_identifiers_account_id " +
                        "ON account_identifiers(account_id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_account_identifiers_value_is_active " +
                        "ON account_identifiers(value, is_active)"
                )
                db.execSQL(
                    """
                    INSERT INTO account_identifiers (account_id, value, kind, is_active, created_at)
                    SELECT id, last4, 'account', 1, 0 FROM accounts
                    WHERE last4 IS NOT NULL AND length(last4) > 0
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE entry_provenance ADD COLUMN parsed_facts TEXT")
            }
        }

        /**
         * v13 -> v14: synced-contacts cache (`contacts`), keyed by last-10
         * digits. A lookup cache only — counterparties link to contacts softly
         * via phone-prefixed aliases, never by foreign key.
         */
        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS contacts (
                        phone_last10 TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        photo_uri TEXT,
                        PRIMARY KEY(phone_last10)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}