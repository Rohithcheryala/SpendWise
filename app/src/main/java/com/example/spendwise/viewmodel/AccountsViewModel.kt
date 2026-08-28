package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.backend.api.LedgerApi
import com.example.spendwise.backend.service.IngestionService
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.AccountIdentifierDao
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.data.database.entity.AccountIdentifierEntity
import com.example.spendwise.ui.screens.accounts.AccountType
import com.example.spendwise.ui.screens.accounts.AccountUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * Real source of truth for the Accounts screen: reads persisted accounts from
 * [AccountDao] and their live balances from [LedgerApi] — no placeholder rows.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val identifierDao: AccountIdentifierDao,
    private val ledgerApi: LedgerApi,
    private val ingestionService: IngestionService,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val accounts: List<AccountUiModel> = emptyList(),
        val error: String? = null,
    )

    var uiState by mutableStateOf(UiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Stale-while-revalidate: only show the loading state when there is
            // nothing to paint yet — avoids a full-screen spinner (flash) on
            // every revisit.
            uiState = uiState.copy(isLoading = uiState.accounts.isEmpty(), error = null)
            runCatching {
                // System pots (sys-unmatched, sys-loans, sys-openeq, …) are
                // ledger plumbing, not user accounts — never show them here.
                // The same filter is used by InboxRepository.refreshBuffer().
                accountDao.listAll()
                    .filter { !it.slug.startsWith("sys-") && it.isActive }
                    .map { entity ->
                    entity.toUi(computeBalance(entity))
                }
            }.onSuccess { accounts ->
                uiState = uiState.copy(isLoading = false, accounts = accounts)
            }.onFailure { e ->
                uiState = uiState.copy(isLoading = false, error = e.message)
            }
        }
    }

    /** Real current balance, in rupees. Liabilities are stored negative so the
     *  screen's net-worth math (assets − liabilities) stays correct. */
    private suspend fun computeBalance(entity: AccountEntity): Double {
        val paise = runCatching { ledgerApi.accountBalance(entity.id) }.getOrNull()
            ?: (if (entity.kind == "liability") -entity.openingBalancePaise
                else entity.openingBalancePaise)
        return paise / 100.0
    }

    /** Persist a user-added account + opening balance + last-4 identifier. */
    fun addAccount(ui: AccountUiModel) {
        viewModelScope.launch {
            runCatching {
                val kind = when (ui.type) {
                    AccountType.CREDIT_CARD -> "liability"
                    AccountType.CASH -> "cash"
                    AccountType.CHECKING -> "asset"
                    AccountType.SAVINGS -> "asset"
                }
                val last4Digits = ui.accountNumber.removePrefix("•••• ").trim()
                    .takeIf { it.isNotBlank() && !it.contains("•") }
                val openingPaise = abs(ui.balance).toLong() * 100

                val id = accountDao.insert(
                    AccountEntity(
                        slug = "acct-${System.currentTimeMillis()}",
                        name = ui.name,
                        bank = ui.bankName.ifBlank { null },
                        kind = kind,
                        last4 = last4Digits,
                        openingBalancePaise = openingPaise,
                        isActive = true,
                        createdAt = System.currentTimeMillis(),
                    )
                )

                last4Digits?.let { digits ->
                    identifierDao.insert(
                        AccountIdentifierEntity(
                            accountId = id,
                            value = digits,
                            kind = if (ui.type == AccountType.CREDIT_CARD) "card" else "account",
                            isActive = true,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }

                // Book the opening balance against open equity (idempotent).
                if (openingPaise != 0L) {
                    ledgerApi.recordOpeningBalance(id, System.currentTimeMillis())
                }

                // Any SMS already ingested for this card/account sits orphaned
                // on the unmatched pot — claim it now that we have the
                // identifier (attach-only, safe to re-run).
                if (last4Digits != null) {
                    ingestionService.claimOrphansForAccount(id)
                }
            }.onSuccess {
                refresh()
            }.onFailure { e ->
                uiState = uiState.copy(error = e.message)
            }
        }
    }

    fun consumeError() {
        uiState = uiState.copy(error = null)
    }

    private fun AccountEntity.toUi(balance: Double): AccountUiModel = AccountUiModel(
        id = id,
        name = name,
        accountNumber = if (last4 != null && last4.isNotBlank()) "•••• $last4" else "No account number",
        balance = if (kind == "liability") -abs(balance) else balance,
        type = when {
            kind == "liability" -> AccountType.CREDIT_CARD
            kind == "cash" -> AccountType.CASH
            bank != null -> AccountType.SAVINGS
            else -> AccountType.CASH
        },
        bankName = bank ?: "Bank",
    )
}
