package com.example.spendwise.backend

import com.example.spendwise.backend.api.ApiException
import com.example.spendwise.backend.api.CreateEntryRequest
import com.example.spendwise.backend.api.LineSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The ledger invariants, ported from the old server's ledger service: an entry
 * needs >= 2 lines summing to exactly zero; each line targets exactly one of
 * account / category; a bucket tag only rides on a real-account line.
 */
class LedgerValidationTest : BackendTestBase() {

    @Test
    fun `entry with a single line is rejected`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))

        expectApiError("an entry needs at least two lines") {
            ledger.createEntry(
                CreateEntryRequest(
                    occurredOn = 0L,
                    lines = listOf(LineSpec(-1000, accountId = bank)),
                )
            )
        }
    }

    @Test
    fun `unbalanced entry is rejected`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
        val food = db.CategoryDao().insert(newCategory("Food"))

        expectApiError("entry lines must sum to zero (got -100)") {
            ledger.createEntry(
                CreateEntryRequest(
                    occurredOn = 0L,
                    // 1000 - 900 != 0
                    lines = listOf(
                        LineSpec(-1000, accountId = bank),
                        LineSpec(900, categoryId = food),
                    ),
                )
            )
        }
    }

    @Test
    fun `line targeting both account and category is rejected`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
        val food = db.CategoryDao().insert(newCategory("Food"))

        expectApiError("each line targets exactly one of accountId / categoryId") {
            ledger.createEntry(
                CreateEntryRequest(
                    occurredOn = 0L,
                    lines = listOf(
                        LineSpec(-1000, accountId = bank),
                        LineSpec(500, categoryId = food),
                        LineSpec(500, accountId = bank, categoryId = food),
                    ),
                )
            )
        }
    }

    @Test
    fun `line targeting neither account nor category is rejected`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
        val food = db.CategoryDao().insert(newCategory("Food"))

        expectApiError("each line targets exactly one of accountId / categoryId") {
            ledger.createEntry(
                CreateEntryRequest(
                    occurredOn = 0L,
                    lines = listOf(
                        LineSpec(-1000, accountId = bank),
                        LineSpec(500, categoryId = food),
                        LineSpec(500), // no target at all
                    ),
                )
            )
        }
    }

    @Test
    fun `bucket tag on a category line is rejected`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
        val food = db.CategoryDao().insert(newCategory("Food"))
        db.BucketDao().insert(newBucket(bank))

        expectApiError("bucketId is only valid on a real-account line") {
            ledger.createEntry(
                CreateEntryRequest(
                    occurredOn = 0L,
                    lines = listOf(
                        LineSpec(-1000, accountId = bank),
                        LineSpec(1000, categoryId = food, bucketId = 1L),
                    ),
                )
            )
        }
    }

    @Test
    fun `balanced multi-line entry persists and its lines still sum to zero`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
        val food = db.CategoryDao().insert(newCategory("Food"))
        val fun_ = db.CategoryDao().insert(newCategory("Fun"))

        val view = ledger.createEntry(
            CreateEntryRequest(
                occurredOn = 0L,
                note = "groceries + movie",
                tags = listOf("night out"),
                lines = listOf(
                    LineSpec(-3000, accountId = bank),
                    LineSpec(2500, categoryId = food),
                    LineSpec(500, categoryId = fun_),
                ),
            )
        )

        assertEquals(listOf("night out"), view.tags)
        assertEquals("groceries + movie", view.note)
        val stored = db.EntryLineDao().getByEntryList(view.id)
        assertEquals(3, stored.size)
        assertEquals(0L, stored.sumOf { it.amountPaise })
    }
}