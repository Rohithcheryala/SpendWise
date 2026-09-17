package com.example.spendwise.ui.screens.transactions

import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.ledger.service.LedgerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * Chips, pickable options and the date bridging.
 *
 * The chip tests are the regression guard for the bug that motivated the id
 * rewrite: `removeChip` used to match on the chip's *label*, so with two
 * accounts named the same it always dropped the first one it found — the wrong
 * filter.
 */
class TransactionFilterStateTest {

    private fun account(
        id: Long,
        name: String,
        accountClass: String = LedgerService.CLASS_ASSET,
        subtype: String? = null,
        isSystem: Boolean = false,
        isArchived: Boolean = false,
    ) = AccountEntity(
        id = id,
        name = name,
        accountClass = accountClass,
        subtype = subtype,
        isSystem = isSystem,
        isArchived = isArchived,
        createdAt = 0L,
    )

    private val options = TransactionFilterOptions(
        categories = listOf(FilterOption(7L, "Food")),
        accounts = listOf(FilterOption(1L, "HDFC"), FilterOption(2L, "ICICI")),
        tags = listOf("Home"),
    )

    // ── chips ─────────────────────────────────────────────────────────────

    @Test
    fun `active chips resolve ids to their labels`() {
        val state = TransactionFilterState(
            status = TransactionFilterStatus.Confirmed,
            categoryId = 7L,
            fromAccountId = 2L,
            tag = "Home",
            fromDate = LocalDate.of(2026, 9, 1),
            toDate = LocalDate.of(2026, 9, 20),
            options = options,
        )

        assertEquals(
            listOf(
                ActiveFilterChip(FilterKeys.STATUS, "Confirmed"),
                ActiveFilterChip(FilterKeys.CATEGORY, "Food"),
                ActiveFilterChip(FilterKeys.FROM_ACCOUNT, "ICICI"),
                ActiveFilterChip(FilterKeys.TAG, "Home"),
                ActiveFilterChip(FilterKeys.DATE, "1 Sep – 20 Sep"),
            ),
            state.activeFilters
        )
        assertEquals(6, state.activeFilterCount)
    }

    @Test
    fun `a chip whose account was deleted is dropped instead of drawn blank`() {
        val state = TransactionFilterState(categoryId = 999L, options = options)

        assertTrue(state.activeFilters.isEmpty())
        // The count still sees it — it is set, it just has no label to show.
        assertEquals(1, state.activeFilterCount)
    }

    /**
     * The regression: both legs point at accounts called "HDFC". Removing the
     * *to* chip must clear `toAccountId` only. The old label-keyed lookup
     * matched `fromAccount` first and cleared the wrong one.
     */
    @Test
    fun `removing a chip clears the leg it names, not the first same-named one`() {
        val sameName = TransactionFilterOptions(
            accounts = listOf(FilterOption(1L, "HDFC"), FilterOption(2L, "HDFC")),
        )
        val state = TransactionFilterState(
            fromAccountId = 1L,
            toAccountId = 2L,
            options = sameName,
        )

        assertEquals(listOf("HDFC", "HDFC"), state.activeFilters.map { it.label })

        val afterRemoveTo = state.removeChip(FilterKeys.TO_ACCOUNT)

        assertEquals(1L, afterRemoveTo.fromAccountId)
        assertEquals(null, afterRemoveTo.toAccountId)
    }

    @Test
    fun `removing the date chip clears both bounds`() {
        val state = TransactionFilterState(
            fromDate = LocalDate.of(2026, 9, 1),
            toDate = LocalDate.of(2026, 9, 20),
        )

        val cleared = state.removeChip(FilterKeys.DATE)

        assertEquals(null, cleared.fromDate)
        assertEquals(null, cleared.toDate)
    }

    @Test
    fun `an unknown chip key is a no-op`() {
        val state = TransactionFilterState(categoryId = 7L, options = options)

        assertEquals(state, state.removeChip("nonsense"))
    }

    // ── pickable options ──────────────────────────────────────────────────

    @Test
    fun `options split categories from money accounts`() {
        val built = buildFilterOptions(
            accounts = listOf(
                account(1L, "HDFC"),
                account(2L, "Card", accountClass = LedgerService.CLASS_LIABILITY),
                account(3L, "Food", accountClass = LedgerService.CLASS_EXPENSE),
                account(4L, "Salary", accountClass = LedgerService.CLASS_INCOME),
                account(5L, "Goa trip", subtype = LedgerService.SUBTYPE_BUCKET),
                account(6L, "Unmatched", isSystem = true),
                account(7L, "Old account", isArchived = true),
            ),
            tags = listOf("Home"),
        )

        assertEquals(listOf("Food", "Salary"), built.categories.map { it.label })
        // Buckets are goal sub-pots and system pots are bookkeeping: neither is
        // something a user filters transactions by.
        assertEquals(listOf("HDFC", "Card"), built.accounts.map { it.label })
        assertEquals(listOf("Home"), built.tags)
    }

    // ── dates ─────────────────────────────────────────────────────────────

    @Test
    fun `the date label never renders a bare null`() {
        assertEquals("Any date", dateRangeLabel(null, null))
        assertEquals("From 1 Sep", dateRangeLabel(LocalDate.of(2026, 9, 1), null))
        assertEquals("Until 20 Sep", dateRangeLabel(null, LocalDate.of(2026, 9, 20)))
        assertEquals(
            "1 Sep – 20 Sep",
            dateRangeLabel(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20))
        )
    }

    /**
     * The picker reports UTC midnight; the ledger thinks in local days. Try the
     * round trip in a zone *behind* UTC, where a naive `systemDefault()`
     * conversion loses a day — this is the whole reason the helpers exist.
     */
    @Test
    fun `picker millis survive the round trip in a behind-UTC zone`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val picked = LocalDate.of(2026, 9, 1)

            assertEquals(picked, picked.toUtcPickerMillis().toUtcPickerDate())
            assertNotEquals(
                "a local-zone conversion is the bug this guards against",
                picked,
                Instant.ofEpochMilli(picked.toUtcPickerMillis())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            )
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
