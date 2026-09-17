package com.example.spendwise.ui.screens.transactions

import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.ledger.service.LedgerService
import com.example.spendwise.ui.components.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * The transactions filter rules.
 *
 * These exist because the filter sheet shipped with five of its seven controls
 * as `onClick = {}` and no event that could set the state behind them — a
 * defect that was invisible to the compiler and to any test that only rendered
 * the screen. The rules are pure functions in `TransactionFilters.kt`
 * precisely so they can be asserted here.
 */
class TransactionFiltersTest {

    private fun tx(
        id: Long,
        title: String = "Row $id",
        category: String? = null,
        categoryId: Long? = null,
        accountId: Long? = null,
        toAccountId: Long? = null,
        tags: List<String> = emptyList(),
        occurredOn: Long = 0L,
        status: TransactionFilterStatus = TransactionFilterStatus.Confirmed,
    ) = TransactionUi(
        id = id,
        title = title,
        account = null,
        amount = "₹1",
        time = "1:00 PM",
        direction = TransactionDirection.EXPENSE,
        category = category,
        tags = tags,
        status = status,
        occurredOn = occurredOn,
        categoryId = categoryId,
        accountId = accountId,
        toAccountId = toAccountId,
    )

    /** Noon local, so the row's local date is unambiguous in any zone. */
    private fun localMillis(date: LocalDate): Long =
        date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ── the filters actually filter ───────────────────────────────────────

    @Test
    fun `an empty filter state keeps every row`() {
        val rows = listOf(tx(1), tx(2, categoryId = 7L), tx(3, tags = listOf("Home")))

        assertEquals(
            rows,
            rows.filter { matchesTransactionFilters(it, TransactionFilterState(), "") }
        )
    }

    @Test
    fun `category filter keeps only that category`() {
        val food = tx(1, categoryId = 7L)
        val travel = tx(2, categoryId = 9L)

        val state = TransactionFilterState(categoryId = 7L)

        assertTrue(matchesTransactionFilters(food, state, ""))
        assertFalse(matchesTransactionFilters(travel, state, ""))
    }

    @Test
    fun `from-account filter matches the account leg`() {
        val hdfc = tx(1, accountId = 1L)
        val icici = tx(2, accountId = 2L)

        val state = TransactionFilterState(fromAccountId = 1L)

        assertTrue(matchesTransactionFilters(hdfc, state, ""))
        assertFalse(matchesTransactionFilters(icici, state, ""))
    }

    @Test
    fun `to-account filter keeps only rows with that destination`() {
        val transfer = tx(1, accountId = 1L, toAccountId = 2L)
        val spend = tx(2, accountId = 1L)

        val state = TransactionFilterState(toAccountId = 2L)

        assertTrue(matchesTransactionFilters(transfer, state, ""))
        // A row with no destination cannot be a row "to" that account.
        assertFalse(matchesTransactionFilters(spend, state, ""))
    }

    @Test
    fun `tag filter ignores case`() {
        val tagged = tx(1, tags = listOf("Home", "Repairs"))

        assertTrue(
            matchesTransactionFilters(
                tagged,
                TransactionFilterState(tag = "repairs"),
                ""
            )
        )
        assertFalse(
            matchesTransactionFilters(
                tagged,
                TransactionFilterState(tag = "Travel"),
                ""
            )
        )
    }

    @Test
    fun `date range is inclusive on both bounds`() {
        val from = LocalDate.of(2026, 9, 10)
        val to = LocalDate.of(2026, 9, 20)
        val state = TransactionFilterState(fromDate = from, toDate = to)

        assertTrue(matchesTransactionFilters(tx(1, occurredOn = localMillis(from)), state, ""))
        assertTrue(matchesTransactionFilters(tx(2, occurredOn = localMillis(to)), state, ""))
        assertTrue(
            matchesTransactionFilters(
                tx(3, occurredOn = localMillis(LocalDate.of(2026, 9, 15))),
                state,
                ""
            )
        )
        assertFalse(
            matchesTransactionFilters(
                tx(4, occurredOn = localMillis(LocalDate.of(2026, 9, 9))),
                state,
                ""
            )
        )
        assertFalse(
            matchesTransactionFilters(
                tx(5, occurredOn = localMillis(LocalDate.of(2026, 9, 21))),
                state,
                ""
            )
        )
    }

    @Test
    fun `status and search still apply on top of the new filters`() {
        val pending = tx(1, title = "Coffee", categoryId = 7L, status = TransactionFilterStatus.Pending)
        val confirmed = tx(2, title = "Coffee", categoryId = 7L)

        val state = TransactionFilterState(
            status = TransactionFilterStatus.Pending,
            categoryId = 7L,
        )

        assertTrue(matchesTransactionFilters(pending, state, "coffee"))
        assertFalse(matchesTransactionFilters(confirmed, state, "coffee"))
        assertFalse(matchesTransactionFilters(pending, state, "tea"))
    }
}
