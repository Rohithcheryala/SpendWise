package com.example.spendwise.ledger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.spendwise.ledger.api.ApiException
import com.example.spendwise.ledger.service.ContactsService
import com.example.spendwise.ledger.service.CounterpartyService
import com.example.spendwise.ledger.service.IngestionService
import com.example.spendwise.ledger.service.LedgerService
import com.example.spendwise.data.database.AppDatabase
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.data.database.entity.AccountIdentifierEntity
import kotlin.reflect.KClass
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            detailsDao = db.BankAccountDetailsDao(),
            transactionDao = db.TransactionDao(),
            transactionLineDao = db.TransactionLineDao(),
            provenanceDao = db.TransactionProvenanceDao(),
            tagDao = db.TagDao(),
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
            transactionDao = db.TransactionDao(),
            transactionLineDao = db.TransactionLineDao(),
            provenanceDao = db.TransactionProvenanceDao(),
            tagDao = db.TagDao(),
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

    /**
     * Asserts the block raises [ApiException] — narrowed to [klass] when
     * given — and returns it (suspend-safe). The sealed hierarchy (3b-3)
     * lets each test pin the *cause*, not just the message. Existing
     * `expectApiError("msg") {}` calls keep compiling (they assert only
     * "some ApiException").
     */
    protected suspend fun expectApiError(
        expectedMessage: String? = null,
        klass: KClass<out ApiException>? = null,
        block: suspend () -> Unit,
    ): ApiException {
        val thrown: ApiException = try {
            block()
            throw AssertionError(
                "expected ApiException" + (expectedMessage?.let { "('$it')" } ?: "")
            )
        } catch (e: ApiException) {
            e
        }
        if (klass != null) {
            assertTrue(
                "expected ${klass.simpleName}, got ${thrown::class.simpleName}: ${thrown.message}",
                klass.isInstance(thrown),
            )
        }
        expectedMessage?.let { assertEquals(it, thrown.message) }
        return thrown
    }

    /** Typed sugar: `expectApiError<ApiException.NotFound>("...") { ... }`. */
    protected suspend inline fun <reified T : ApiException> expectApiErrorOf(
        expectedMessage: String? = null,
        noinline block: suspend () -> Unit,
    ): T =
        @Suppress("UNCHECKED_CAST")
        expectApiError(expectedMessage, T::class, block) as T

    /**
     * Insert a user account (asset by default) with a TESTBANK last-4
     * identifier, returning its id. Identifiers are what SMS matching needs.
     */
    protected suspend fun newAccount(
        name: String,
        accountClass: String = LedgerService.CLASS_ASSET,
        subtype: String? = null,
        bank: String? = "TESTBANK",
        last4: String? = "1234",
    ): Long {
        val id = db.AccountDao().insert(
            AccountEntity(
                name = name,
                accountClass = accountClass,
                subtype = subtype,
                bank = bank,
                createdAt = 0L,
            )
        )
        last4?.let {
            db.AccountIdentifierDao().insert(
                AccountIdentifierEntity(
                    accountId = id,
                    value = it,
                    kind = if (accountClass == LedgerService.CLASS_LIABILITY) "card" else "account",
                    isActive = true,
                    createdAt = 0L,
                )
            )
        }
        return id
    }

    /** Categories ARE accounts now (class income/expense). Returns the id. */
    protected suspend fun newCategory(
        name: String,
        accountClass: String = LedgerService.CLASS_EXPENSE,
    ): Long = db.AccountDao().insert(
        AccountEntity(name = name, accountClass = accountClass, createdAt = 0L)
    )

    /** Buckets are plain child accounts of their funding account. Returns the id. */
    protected suspend fun newBucket(parentId: Long, targetPaise: Long? = null): Long =
        db.AccountDao().insert(
            AccountEntity(
                name = "bucket-$parentId",
                accountClass = LedgerService.CLASS_ASSET,
                subtype = LedgerService.SUBTYPE_BUCKET,
                parentId = parentId,
                targetPaise = targetPaise,
                isSystem = false,
                createdAt = 0L,
            )
        )
}

/** Convenience for tests: runTest {} everywhere without repeating the runner. */
fun backendTest(body: suspend () -> Unit) = runTest { body() }