package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.backend.api.Direction
import com.example.spendwise.backend.api.EntryKind
import com.example.spendwise.backend.api.EntryView
import com.example.spendwise.backend.api.LedgerApi
import com.example.spendwise.core.extensions.toAmountString
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.database.dao.CounterpartyDao
import com.example.spendwise.data.repository.SettingsRepository
import com.example.spendwise.ui.components.TransactionDirection
import com.example.spendwise.ui.screens.transactions.TransactionUi
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
 * The transactions list, read from the real ledger ([LedgerApi.listEntries]).
 * Includes buffer entries (shown alongside confirmed ones) and excludes voided.
 */
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val ledgerApi: LedgerApi,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val counterpartyDao: CounterpartyDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val items: List<TransactionUi> = emptyList(),
        val error: String? = null,
    )

    var uiState by mutableStateOf(UiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = uiState.items.isEmpty(), error = null)

            runCatching {
                val symbol = settingsRepository.settings.first().currencySymbol
                val accounts = accountDao.listAll().associate { it.id to it.name }
                val parties = counterpartyDao.listAll().associate { it.id to it.displayName }
                val entries = ledgerApi.listEntries()
                val categoryIds = entries.mapNotNull { it.categoryId }.distinct()
                val categories = if (categoryIds.isEmpty()) {
                    emptyMap()
                } else {
                    categoryDao.getByIds(categoryIds).associate { it.id to it.name }
                }
                entries.map { entry ->
                    entry.toUi(symbol, accounts, categories, parties)
                }
            }.onSuccess { items ->
                uiState = uiState.copy(isLoading = false, items = items)
            }.onFailure { e ->
                uiState = uiState.copy(isLoading = false, error = e.message)
            }
        }
    }

    private fun EntryView.toUi(
        symbol: String,
        accounts: Map<Long, String>,
        categories: Map<Long, String>,
        parties: Map<Long, String>,
    ): TransactionUi {
        val title = counterpartyId?.let { parties[it] }
            ?: categoryId?.let { categories[it] }
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

        return TransactionUi(
            id = id,
            title = title,
            account = accountName,
            amount = amountPaise.toAmountString(symbol),
            time = timeFormat.format(Date(timestamp)),
            direction = when (kind) {
                EntryKind.TRANSFER -> TransactionDirection.TRANSFER
                else -> if (direction == Direction.IN) {
                    TransactionDirection.INCOME
                } else {
                    TransactionDirection.EXPENSE
                }
            },
            tags = tags,
            dayLabel = dayLabel(occurredOn),
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
