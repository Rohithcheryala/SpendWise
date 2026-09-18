package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.ledger.api.Direction
import com.example.spendwise.ledger.api.TransactionKind
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.api.TransactionView
import com.example.spendwise.ledger.api.LedgerApi
import com.example.spendwise.ledger.service.LedgerService
import com.example.spendwise.core.extensions.toAmountString
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.CounterpartyDao
import com.example.spendwise.data.database.dao.TagDao
import com.example.spendwise.data.repository.SettingsRepository
import com.example.spendwise.ui.components.TransactionDirection
import com.example.spendwise.ui.screens.transactions.TransactionEvent
import com.example.spendwise.ui.screens.transactions.TransactionFilterState
import com.example.spendwise.ui.screens.transactions.TransactionFilterStatus
import com.example.spendwise.ui.screens.transactions.TransactionUi
import com.example.spendwise.ui.screens.transactions.buildFilterOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * How many of the most-used labels the tag filter offers. A picker, not an
 * index — the long tail of one-off tags is noise in a filter menu.
 */
private const val TAG_OPTION_LIMIT = 50

/**
 * The transactions list, read from the real ledger ([LedgerApi.listTransactions]).
 *
 * Defaults to CONFIRMED (finalized) transactions only; buffer/orphan rows belong on
 * the Inbox screen. Applying a status filter re-queries the ledger for buffer
 * transactions ("Pending") instead. Voided transactions are excluded by the ledger.
 */
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val ledgerApi: LedgerApi,
    private val accountDao: AccountDao,
    private val counterpartyDao: CounterpartyDao,
    private val tagDao: TagDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val items: List<TransactionUi> = emptyList(),
        val error: String? = null,
    )

    var uiState by mutableStateOf(UiState())
        private set

    var filterState by mutableStateOf(TransactionFilterState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = uiState.items.isEmpty(), error = null)

            runCatching {
                val symbol = settingsRepository.settings.first().currencySymbol
                val allAccounts = accountDao.listAll()
                val accounts = allAccounts.associate { it.id to it.name }
                val parties = counterpartyDao.listAll().associate { it.id to it.displayName }

                // The sheet's pickable values. Refreshed here (rather than in a
                // separate flow) so whatever the user can filter by always
                // matches the accounts/tags that exist right now.
                val options = buildFilterOptions(
                    accounts = allAccounts,
                    tags = tagDao.popularLabels(TAG_OPTION_LIMIT),
                )

                val statusArg = when (filterState.status) {
                    TransactionFilterStatus.Pending -> TransactionStatus.BUFFER
                    else -> TransactionStatus.CONFIRMED
                }
                val transactions = ledgerApi.listTransactions(status = statusArg)
                val categoryIds = transactions.mapNotNull { it.categoryId }.distinct()
                val categories = if (categoryIds.isEmpty()) {
                    emptyMap()
                } else {
                    accountDao.getByIds(categoryIds).associate { it.id to it.name }
                }

                options to transactions.map { entry ->
                    entry.toUi(symbol, accounts, categories, parties)
                }
            }.onSuccess { (options, items) ->
                // Keep the active filters; only the option lists change. The
                // filter ids may now point at a deleted account — `activeFilters`
                // silently drops those chips rather than drawing a blank one.
                filterState = filterState.copy(options = options)
                uiState = uiState.copy(isLoading = false, items = items)
            }.onFailure { e ->
                uiState = uiState.copy(isLoading = false, error = e.message)
            }
        }
    }

    // ── Filter events ─────────────────────────────────────────────────────
    //
    // The filter UI is a screen, not a sheet, so there are no open/close events
    // here — navigation owns that. Only the two actions that need the ledger
    // remain.

    fun onEvent(event: TransactionEvent) {
        when (event) {
            TransactionEvent.ApplyFilters -> refresh()

            TransactionEvent.ResetFilters -> {
                // Keep `options`: they are the pickable values, not a filter.
                // Blowing them away left every dropdown empty until the next
                // refresh finished.
                filterState = TransactionFilterState(options = filterState.options)
                refresh()
            }

            is TransactionEvent.RemoveFilter -> {
                filterState = filterState.removeChip(event.key)
                refresh()
            }

            // Everything below is answered locally by the predicate in
            // `TransactionFilters.kt` — no query, so the list updates on tap.
            // Status is absent deliberately: it goes through `setStatusFilter`,
            // which re-queries because the ledger set itself differs between
            // CONFIRMED and BUFFER.
            is TransactionEvent.SetCategory ->
                filterState = filterState.copy(categoryId = event.categoryId)

            is TransactionEvent.SetFromAccount ->
                filterState = filterState.copy(fromAccountId = event.accountId)

            is TransactionEvent.SetToAccount ->
                filterState = filterState.copy(toAccountId = event.accountId)

            is TransactionEvent.SetTag ->
                filterState = filterState.copy(tag = event.tag)

            is TransactionEvent.SetDateRange ->
                filterState = filterState.copy(
                    fromDate = event.from,
                    toDate = event.to,
                )
        }
    }

    fun setStatusFilter(status: TransactionFilterStatus?) {
        filterState = filterState.copy(status = status)
        refresh()
    }

    private fun TransactionView.toUi(
        symbol: String,
        accounts: Map<Long, String>,
        categories: Map<Long, String>,
        parties: Map<Long, String>,
    ): TransactionUi {
        val title = counterpartyId?.let { parties[it] }
            ?: categoryId?.let { categories[it] }
                ?.takeIf { it !in LedgerService.SYSTEM_CATEGORY_NAMES }
            ?: note?.takeIf { it.isNotBlank() }
            ?: kind.name.lowercase().replace('_', ' ')
                .replaceFirstChar { it.uppercaseChar() }

        val accountName = when {
            accountId != null && toAccountId != null ->
                "${accounts[accountId].orEmpty()} → ${accounts[toAccountId].orEmpty()}"
            accountId != null -> accounts[accountId]
            else -> null
        }

        val timestamp = occurredOn
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        // "Paid on behalf of" labeling: the title names the merchant (who got
        // the money); the subtitle gets the covered person appended so the row
        // reads "Food • for Aasim".
        val onBehalfOfName = onBehalfOfCounterpartyId?.let { parties[it] }
        val categoryLabel = categoryId
            ?.let { categories[it] }
            ?.takeIf { it !in LedgerService.SYSTEM_CATEGORY_NAMES }
            ?.let { if (onBehalfOfName != null) "$it • for $onBehalfOfName" else it }

        return TransactionUi(
            id = id,
            title = title,
            account = accountName,
            amount = amountPaise.toAmountString(symbol),
            // Numeric value kept alongside the display string so charts and
            // count-up animations don't have to re-parse "₹1,234.56".
            amountPaise = amountPaise,
            time = timeFormat.format(Date(timestamp)),
            // The system contra category ("Unclassified") is bookkeeping
            // noise, not a category — hide it until a real one is assigned.
            category = categoryLabel,
            direction = when (kind) {
                TransactionKind.TRANSFER -> TransactionDirection.TRANSFER
                else -> if (direction == Direction.IN) {
                    TransactionDirection.INCOME
                } else {
                    TransactionDirection.EXPENSE
                }
            },
            tags = tags,
            dayLabel = dayLabel(occurredOn),
            status = if (status == TransactionStatus.BUFFER) {
                TransactionFilterStatus.Pending
            } else {
                TransactionFilterStatus.Confirmed
            },
            occurredOn = occurredOn,
            // Ids too: the sheet filters on these, never on the display names.
            categoryId = categoryId,
            accountId = accountId,
            toAccountId = toAccountId,
        )
    }

    private fun dayLabel(millis: Long): String {
        val today = LocalDate.now()
        val date = Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMM"))
            else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
    }
}
