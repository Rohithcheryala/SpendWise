package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.ledger.api.ApiException
import com.example.spendwise.ledger.service.LedgerService
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.AccountIdentifierDao
import com.example.spendwise.data.database.entity.AccountIdentifierEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Edit screen for one account: rename, update bank/masked number, or close
 * the account. Changing the last-4 keeps the SMS identifier in sync so
 * matching continues to work.
 */
@HiltViewModel
class AccountEditViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val identifierDao: AccountIdentifierDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Emitted when the screen should close (save/close succeeded). */
    val finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Route argument. */
    private val accountId: Long = savedStateHandle.get<Long>("accountId") ?: -1L

    data class UiState(
        val isLoading: Boolean = true,
        val name: String = "",
        val bankName: String = "",
        val last4: String = "",
        val typeLabel: String = "",
        val isActive: Boolean = true,
        val isSaving: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val account = accountDao.getById(accountId)
            if (account == null || account.isSystem) {
                _uiState.value = UiState(isLoading = false, error = "Account not found")
                return@launch
            }
            // The masked number lives on the active identifier, not the account.
            val last4 = identifierDao.getByAccountList(accountId)
                .firstOrNull { it.isActive }?.value
            _uiState.value = UiState(
                isLoading = false,
                name = account.name,
                bankName = account.bank.orEmpty(),
                last4 = last4.orEmpty(),
                typeLabel = when {
                    account.accountClass == LedgerService.CLASS_LIABILITY -> "Credit card"
                    account.subtype == "cash" -> "Cash"
                    else -> "Bank account"
                },
                isActive = !account.isArchived,
            )
        }
    }

    fun updateState(transform: (UiState) -> UiState) {
        _uiState.value = transform(_uiState.value).copy(error = null)
    }

    fun save() {
        val s = _uiState.value
        if (s.isSaving) return
        if (s.name.isBlank()) {
            _uiState.value = s.copy(error = "Enter a name")
            return
        }
        val digits = s.last4.filter { it.isDigit() }
        if (digits.isNotEmpty() && digits.length != 4) {
            _uiState.value = s.copy(error = "Masked number must be 4 digits")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val account = accountDao.getById(accountId)
                    ?: throw ApiException.NotFound("account", accountId)
                accountDao.update(
                    account.copy(
                        name = s.name.trim(),
                        bank = s.bankName.trim().ifBlank { null },
                    )
                )

                // Keep the SMS identifier in sync when the last-4 changes.
                if (digits.isNotEmpty() && digits != s.last4) {
                    val existing = identifierDao.getByAccountList(accountId)
                    val kind = existing.firstOrNull()?.kind ?: "account"
                    existing.filter { it.isActive }.forEach { identifierDao.deactivate(it.id) }
                    identifierDao.insert(
                        AccountIdentifierEntity(
                            accountId = accountId,
                            value = digits,
                            kind = kind,
                            isActive = true,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
                finished.tryEmit(Unit)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Could not save",
                )
            }
        }
    }

    /** Close the account: deactivated, hidden everywhere, ledger history kept. */
    fun closeAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            runCatching {
                val account = accountDao.getById(accountId)
                    ?: throw ApiException.NotFound("account", accountId)
                // Archive: deactivated, hidden everywhere, ledger history kept.
                accountDao.update(account.copy(isArchived = true))
            }.onSuccess {
                finished.tryEmit(Unit)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Could not close account",
                )
            }
        }
    }
}
