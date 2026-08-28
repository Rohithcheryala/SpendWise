package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.backend.service.LedgerService
import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.repository.InboxItem
import com.example.spendwise.data.repository.InboxRepository
import com.example.spendwise.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: InboxRepository,
    private val settingsRepository: SettingsRepository,
    private val categoryDao: CategoryDao,
) : ViewModel() {

    data class InboxUiState(
        val isLoading: Boolean = true,
        val items: List<InboxItem> = emptyList(),
        val error: String? = null,
        val actionMessage: String? = null
    )

    var uiState by mutableStateOf(InboxUiState())
        private set

    /** The system suspense categories an SMS lands on before classification. */
    private var suspenseCategoryIds: Set<Long> = emptySet()

    init {
        viewModelScope.launch {
            val ids = setOfNotNull(
                categoryDao.findByKindAndName(LedgerService.KIND_EXPENSE, "Unclassified")?.id,
                categoryDao.findByKindAndName(LedgerService.KIND_INCOME, "Uncategorized income")?.id,
            )
            suspenseCategoryIds = ids
            refresh()
        }

        // Shared singleton flow — the bottom-nav badge in MainScaffold and this
        // screen always show the same buffer, whatever instance reads it.
        viewModelScope.launch {
            repository.bufferItems.collect { items ->
                uiState = uiState.copy(items = items)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, actionMessage = null)

            runCatching {
                repository.syncFromSms()
            }.onSuccess {
                uiState = uiState.copy(isLoading = false)
            }.onFailure {
                // Still show whatever buffer entries exist even if SMS read failed
                // (e.g. permission denied).
                runCatching { repository.refreshBuffer() }
                uiState = uiState.copy(
                    isLoading = false,
                    error = it.message ?: "Could not read SMS"
                )
            }
        }
    }

    /** Silent reload of the buffer list — no spinner. Used on screen resume. */
    fun refreshBuffer() {
        viewModelScope.launch {
            runCatching { repository.refreshBuffer() }
        }
    }

    /** Import = confirm the buffer entry into the real ledger. */
    fun import(entryId: Long) {
        viewModelScope.launch {
            val item = uiState.items.firstOrNull { it.entryId == entryId }

            val strict = settingsRepository.settings.first().strictMode
            if (strict && item != null) {
                val unclassified = item.categoryId == null || item.categoryId in suspenseCategoryIds
                if (unclassified || item.tags.isEmpty()) {
                    uiState = uiState.copy(
                        actionMessage = "Strict mode: categorize and tag this transaction before confirming."
                    )
                    return@launch
                }
            }

            runCatching {
                repository.import(entryId)
            }.onSuccess {
                uiState = uiState.copy(actionMessage = "Imported to Transactions")
            }.onFailure {
                uiState = uiState.copy(error = it.message ?: "Import failed")
            }
        }
    }

    /** Dismiss = void the buffer entry. */
    fun dismiss(entryId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.dismiss(entryId)
            }.onSuccess {
                uiState = uiState.copy(actionMessage = "Dismissed")
            }.onFailure {
                uiState = uiState.copy(error = it.message ?: "Dismiss failed")
            }
        }
    }

    fun importAll() {
        val ids = uiState.items.map { it.entryId }
        viewModelScope.launch {
            val strict = settingsRepository.settings.first().strictMode
            if (strict && uiState.items.isNotEmpty()) {
                uiState = uiState.copy(
                    actionMessage = "Strict mode: review each transaction individually before confirming."
                )
                return@launch
            }
            var failed = 0
            for (id in ids) {
                runCatching { repository.import(id) }.onFailure { failed++ }
            }
            uiState = uiState.copy(
                actionMessage = if (failed == 0) "All imported" else "$failed import(s) failed"
            )
        }
    }

    /** Re-point the matched (or orphaned) account on a buffer entry. */
    fun assignAccount(entryId: Long, accountId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.assignAccount(entryId, accountId)
            }.onSuccess {
                uiState = uiState.copy(actionMessage = "Account updated")
            }.onFailure {
                uiState = uiState.copy(error = it.message ?: "Could not update account")
            }
        }
    }

    fun consumeActionMessage() {
        if (uiState.actionMessage != null) {
            uiState = uiState.copy(actionMessage = null)
        }
    }

    fun consumeError() {
        if (uiState.error != null) {
            uiState = uiState.copy(error = null)
        }
    }
}


