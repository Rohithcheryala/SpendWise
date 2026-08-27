package com.example.spendwise.backend

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.spendwise.backend.api.ApiException
import com.example.spendwise.backend.service.ContactsService
import com.example.spendwise.backend.service.CounterpartyService
import com.example.spendwise.backend.service.IngestionService
import com.example.spendwise.backend.service.LedgerService
import com.example.spendwise.data.database.AppDatabase
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.data.database.entity.BucketEntity
import com.example.spendwise.data.database.entity.CategoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Shared fixture for the backend suite: a fresh in-memory Room database per
 * test and the two services wired exactly like production Hilt does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
abstract class BackendTestBase {

    protected lateinit var db: AppDatabase
    protected lateinit var ledger: LedgerService
    protected lateinit var counterparties: CounterpartyService
    protected lateinit var contacts: ContactsService
    protected lateinit var ingestion: IngestionService

    @Before
    open fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        counterparties = CounterpartyService(
            db.CounterpartyDao(),
            db.CounterpartyAliasDao(),
        )
        ledger = LedgerService(
            db = db,
            accountDao = db.AccountDao(),
            bucketDao = db.BucketDao(),
            categoryDao = db.CategoryDao(),
            entryDao = db.EntryDao(),
            entryLineDao = db.EntryLineDao(),
            provenanceDao = db.EntryProvanceDao(),
            counterpartyService = counterparties,
        )
        contacts = ContactsService(
            contactDao = db.ContactDao(),
            counterpartyDao = db.CounterpartyDao(),
            aliasDao = db.CounterpartyAliasDao(),
            counterparties = counterparties,
        )
        ingestion = IngestionService(
            accountDao = db.AccountDao(),
            identifierDao = db.AccountIdentifierDao(),
            entryDao = db.EntryDao(),
            entryLineDao = db.EntryLineDao(),
            provenanceDao = db.EntryProvanceDao(),
            ledger = ledger,
            counterparties = counterparties,
            contacts = contacts,
        )
    }

    @After
    fun tearDownDb() {
        db.close()
    }

    // ── factories ────────────────────────────────────────────────────────

    /** Asserts the block raises [ApiException] and returns it (suspend-safe). */
    protected suspend fun expectApiError(
        expectedMessage: String? = null,
        block: suspend () -> Unit,
    ): ApiException {
        val thrown = try {
            block()
            null
        } catch (e: ApiException) {
            e
        }
        if (thrown == null) {
            throw AssertionError("expected ApiException" + (expectedMessage?.let { "('$it')" } ?: ""))
        }
        expectedMessage?.let { assertEquals(it, thrown.message) }
        return thrown
    }

    protected fun newAccount(
        slug: String,
        kind: String = "available",
        openingBalancePaise: Long = 0,
        reconciledThrough: Long? = null,
    ): AccountEntity = AccountEntity(
        slug = slug,
        name = slug,
        bank = "TESTBANK",
        kind = kind,
        last4 = "1234",
        openingBalancePaise = openingBalancePaise,
        isActive = true,
        createdAt = 0L,
        reconciledThrough = reconciledThrough,
    )

    protected fun newCategory(
        name: String,
        kind: String = LedgerService.KIND_EXPENSE,
    ): CategoryEntity = CategoryEntity(
        name = name,
        kind = kind,
        createdAt = 0L,
    )

    protected fun newBucket(accountId: Long, allocation: Long = 0): BucketEntity = BucketEntity(
        accountId = accountId,
        name = "bucket-$accountId",
        createdAt = 0L,
        manualAllocationPaise = allocation,
    )
}

/** Convenience for tests: runTest {} everywhere without repeating the runner. */
fun backendTest(body: suspend () -> Unit) = runTest { body() }