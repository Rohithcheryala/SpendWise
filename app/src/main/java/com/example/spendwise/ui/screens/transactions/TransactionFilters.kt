package com.example.spendwise.ui.screens.transactions

import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.ledger.service.LedgerService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The transactions filter model and its matching rules.
 *
 * Split out of `TransactionsScreen.kt` (the `UpiQr.kt` precedent) because it is
 * pure logic with no Compose in it, so the rules can be pinned by unit tests
 * instead of only being observable by opening the sheet on a device.
 *
 * Why this file grew past a data class: the sheet used to render five controls
 * with `onClick = {}`, and nothing anywhere could *set* `category`,
 * `fromAccount`, `toAccount`, `tag`, `fromDate` or `toDate` — the fields existed
 * on the state and rendered as chips, but no event wrote them and the list
 * predicate never read them. So filters are ids here ([FilterOption.id]), not
 * display strings: `removeChip` used to drop a filter by matching its *label*,
 * which cannot tell two same-named accounts apart (see `SCHEMA_SYNC.md` G1 —
 * `accounts` has no unique name), and matching on names would filter the wrong
 * rows.
 */

/** Stable chip keys. Chips are removed by key, never by the label drawn on them. */
object FilterKeys {
    const val STATUS = "status"
    const val CATEGORY = "category"
    const val FROM_ACCOUNT = "fromAccount"
    const val TO_ACCOUNT = "toAccount"
    const val TAG = "tag"
    const val DATE = "date"
}

/** One pickable filter value: [id] is what matching uses, [label] is what is drawn. */
data class FilterOption(val id: Long, val label: String)

/** Everything the sheet can offer, rebuilt on each refresh from the database. */
data class TransactionFilterOptions(
    val categories: List<FilterOption> = emptyList(),
    val accounts: List<FilterOption> = emptyList(),
    val tags: List<String> = emptyList(),
) {
    fun categoryLabel(id: Long?): String? = categories.firstOrNull { it.id == id }?.label
    fun accountLabel(id: Long?): String? = accounts.firstOrNull { it.id == id }?.label
}

/** One removable chip: [key] identifies the filter, [label] is what's drawn. */
data class ActiveFilterChip(val key: String, val label: String)

/**
 * Where a category/account option comes from. Income/expense rows double as
 * categories and asset/liability rows as money accounts, so the class is what
 * separates the two pickers; buckets are hidden from account pickers (they are
 * goal sub-pots, per `STEP_TRACKER.md`), as are system pots and archived rows.
 */
fun buildFilterOptions(
    accounts: List<AccountEntity>,
    tags: List<String>,
): TransactionFilterOptions {
    val pickable = accounts.filter { !it.isSystem && !it.isArchived }
    return TransactionFilterOptions(
        categories = pickable
            .filter {
                it.accountClass == LedgerService.CLASS_EXPENSE ||
                    it.accountClass == LedgerService.CLASS_INCOME
            }
            .map { FilterOption(it.id, it.name) },
        accounts = pickable
            .filter {
                it.accountClass == LedgerService.CLASS_ASSET ||
                    it.accountClass == LedgerService.CLASS_LIABILITY
            }
            .filter { it.subtype != LedgerService.SUBTYPE_BUCKET }
            .map { FilterOption(it.id, it.name) },
        tags = tags,
    )
}

data class TransactionFilterState(
    val status: TransactionFilterStatus? = null,
    val categoryId: Long? = null,

    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,

    val tag: String? = null,

    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,

    /** Pickable values, supplied by the ViewModel. Preserved across resets. */
    val options: TransactionFilterOptions = TransactionFilterOptions(),
) {

    val activeFilterCount: Int
        get() = listOf(
            status,
            categoryId,
            fromAccountId,
            toAccountId,
            tag,
            fromDate,
            toDate
        ).count { it != null }

    val activeFilters: List<ActiveFilterChip>
        get() = buildList {

            status?.let {
                add(ActiveFilterChip(FilterKeys.STATUS, it.label))
            }

            options.categoryLabel(categoryId)?.let {
                add(ActiveFilterChip(FilterKeys.CATEGORY, it))
            }

            options.accountLabel(fromAccountId)?.let {
                add(ActiveFilterChip(FilterKeys.FROM_ACCOUNT, it))
            }

            options.accountLabel(toAccountId)?.let {
                add(ActiveFilterChip(FilterKeys.TO_ACCOUNT, it))
            }

            tag?.let {
                add(ActiveFilterChip(FilterKeys.TAG, it))
            }

            if (fromDate != null || toDate != null) {
                add(ActiveFilterChip(FilterKeys.DATE, dateRangeLabel(fromDate, toDate)))
            }
        }

    /** Drop an active-filter chip by its key (used by RemoveFilter events). */
    fun removeChip(key: String): TransactionFilterState = when (key) {
        FilterKeys.STATUS -> copy(status = null)
        FilterKeys.CATEGORY -> copy(categoryId = null)
        FilterKeys.FROM_ACCOUNT -> copy(fromAccountId = null)
        FilterKeys.TO_ACCOUNT -> copy(toAccountId = null)
        FilterKeys.TAG -> copy(tag = null)
        FilterKeys.DATE -> copy(fromDate = null, toDate = null)
        else -> this
    }
}

enum class TransactionFilterStatus(
    val label: String
) {
    Confirmed("Confirmed"),
    Pending("Pending"),
    Voided("Voided")
}

/**
 * One row's fate under the current filters plus the free-text search.
 *
 * Search keeps its original scope (title + account). Status, category, both
 * account legs and tags are the filters the sheet now actually sets; the date
 * range stays inclusive on both bounds, compared in the local zone so "today"
 * means the user's today.
 */
fun matchesTransactionFilters(
    tx: TransactionUi,
    state: TransactionFilterState,
    searchQuery: String,
): Boolean {
    val query = searchQuery.trim()

    val matchesSearch = query.isEmpty() ||
        tx.title.contains(query, ignoreCase = true) ||
        (tx.account?.contains(query, ignoreCase = true) == true)

    val matchesStatus = state.status == null || tx.status == state.status

    val matchesCategory = state.categoryId == null || tx.categoryId == state.categoryId

    val matchesFromAccount = state.fromAccountId == null || tx.accountId == state.fromAccountId

    // The "to" leg only exists on transfers, so a to-filter excludes everything
    // without a destination — which is exactly what that filter is asking for.
    val matchesToAccount = state.toAccountId == null || tx.toAccountId == state.toAccountId

    val matchesTag = state.tag == null ||
        tx.tags.any { it.equals(state.tag, ignoreCase = true) }

    val txDate = Instant.ofEpochMilli(tx.occurredOn)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val matchesDate = (state.fromDate == null || !txDate.isBefore(state.fromDate)) &&
        (state.toDate == null || !txDate.isAfter(state.toDate))

    return matchesSearch &&
        matchesStatus &&
        matchesCategory &&
        matchesFromAccount &&
        matchesToAccount &&
        matchesTag &&
        matchesDate
}

// ── dates ────────────────────────────────────────────────────────────────

private val chipDateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.US)

/** `15 Sep`, `15 Sep – 20 Sep`, `From 15 Sep`, `Until 20 Sep`. */
fun dateRangeLabel(from: LocalDate?, to: LocalDate?): String = when {
    from != null && to != null -> "${chipDateFormat.format(from)} – ${chipDateFormat.format(to)}"
    from != null -> "From ${chipDateFormat.format(from)}"
    to != null -> "Until ${chipDateFormat.format(to)}"
    else -> "Any date"
}

/**
 * `DateRangePicker` reports its selection as **UTC** midnight millis, while a
 * ledger row's day is a *local* date. Bridge the two explicitly here instead of
 * letting `ZoneId.systemDefault()` leak into the picker.
 *
 * The direction that bites is a zone *behind* UTC: for a New York user, UTC
 * midnight on 1 Sep is 8pm on 31 Aug locally, so a naive
 * `atZone(systemDefault())` on the picker's millis hands back the 31st — one day
 * earlier than the date the user tapped. (Ahead-of-UTC zones like IST happen to
 * survive it, which is exactly why this needs pinning by a test.)
 */
fun LocalDate.toUtcPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.toUtcPickerDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
