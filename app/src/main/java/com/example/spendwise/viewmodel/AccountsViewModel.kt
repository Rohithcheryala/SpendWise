package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.ledger.api.LedgerApi
import com.example.spendwise.ledger.service.IngestionService
import com.example.spendwise.ledger.service.LedgerService
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.AccountIdentifierDao
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.data.database.entity.AccountIdentifierEntity
import com.example.spendwise.ui.screens.accounts.AccountType
import com.example.spendwise.ui.screens.accounts.AccountUiModel
import com.example.spendwise.data.repository.AppMetadataRepository
import com.example.spendwise.data.repository.InboxRepository
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
    private val inboxRepository: InboxRepository,
    private val appMetadataRepository: AppMetadataRepository,
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
                // System pots (unmatched, receivable, equity, …), category
                // accounts and archived accounts are not user accounts — the
                // Accounts screen shows only balance-sheet pots.
                val last4ByAccount = identifierDao.getAllActive()
                    .associateBy { it.accountId }
                    .mapValues { it.value.value }
                accountDao.listAll()
                    .filter {
                        !it.isSystem && !it.isArchived &&
                            (it.accountClass == LedgerService.CLASS_ASSET ||
                                it.accountClass == LedgerService.CLASS_LIABILITY)
                    }
                    .map { entity ->
                        entity.toUi(computeBalance(entity), last4ByAccount[entity.id])
                    }
            }.onSuccess { accounts ->
                uiState = uiState.copy(isLoading = false, accounts = accounts)
            }.onFailure { e ->
                uiState = uiState.copy(isLoading = false, error = e.message)
            }
        }
    }

    /** Real current balance, in rupees. Liabilities read as amount owed. */
    private suspend fun computeBalance(entity: AccountEntity): Double =
        runCatching { ledgerApi.accountBalance(entity.id) }.getOrDefault(0L) / 100.0

    /** Persist a user-added account + opening balance + last-4 identifier. */
    fun addAccount(ui: AccountUiModel) {
        viewModelScope.launch {
            runCatching {
                val accountClass: String
                val subtype: String
                when (ui.type) {
                    AccountType.CREDIT_CARD -> {
                        accountClass = LedgerService.CLASS_LIABILITY
                        subtype = "credit_card"
                    }
                    AccountType.CASH -> {
                        accountClass = LedgerService.CLASS_ASSET
                        subtype = "cash"
                    }
                    AccountType.CHECKING -> {
                        accountClass = LedgerService.CLASS_ASSET
                        subtype = "current"
                    }
                    AccountType.SAVINGS -> {
                        accountClass = LedgerService.CLASS_ASSET
                        subtype = "savings"
                    }
                }
                val last4Digits = ui.accountNumber.removePrefix("•••• ").trim()
                    .takeIf { it.isNotBlank() && !it.contains("•") }
                val openingPaise = abs(ui.balance).toLong() * 100

                val id = accountDao.insert(
                    AccountEntity(
                        name = ui.name,
                        accountClass = accountClass,
                        subtype = subtype,
                        bank = ui.bankName.ifBlank { null },
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

                // Book the opening balance against opening equity (idempotent).
                if (openingPaise != 0L) {
                    ledgerApi.recordOpeningBalance(id, openingPaise, System.currentTimeMillis())
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

    // ── Detect-from-SMS ──

    /** Live status of a "detect from SMS" run. */
    data class DetectionState(
        val isDetecting: Boolean = false,
        val scanSummary: String? = null,
        val detected: List<InboxRepository.DetectedAccount> = emptyList(),
        val selectedKeys: Set<String> = emptySet(),
        val isImporting: Boolean = false,
        val error: String? = null,
    )

    var detectionState by mutableStateOf(DetectionState())
        private set

    /**
     * Read bank SMS since the app's tracking start date (the day the user
     * picked during onboarding — "day one" of the ledger) and surface the
     * accounts the ledger doesn't know yet. Falls back to the last
     * [DETECT_LOOKBACK_DAYS] days when no start date was recorded.
     *
     * Detection is READ-ONLY: it parses messages in memory and reports
     * accounts, but writes nothing to the ledger and does not advance the
     * sync watermark (that would silently swallow this history — the regular
     * sync must still be able to read the same window later).
     */
    fun detectFromSms() {
        if (detectionState.isDetecting || detectionState.isImporting) return
        detectionState = DetectionState(isDetecting = true)
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val fromMillis = appMetadataRepository.get()?.trackingStartDate
                    ?.takeIf { it in 1 until now }
                    ?: (now - DETECT_LOOKBACK_DAYS * 24L * 60L * 60L * 1000L)
                inboxRepository.scanFrom(fromMillis, ingest = false)
            }.onSuccess { report ->
                detectionState = if (report.accounts.isEmpty()) {
                    DetectionState(
                        scanSummary = "Read ${report.messagesRead} messages — " +
                            "no new accounts found. Add one manually below.",
                    )
                } else {
                    DetectionState(
                        scanSummary = "${report.messagesRead} messages read — " +
                            "${report.transactionsDetected} bank transactions recognized " +
                            "(nothing saved yet)",
                        detected = report.accounts,
                        // Auto-select everything — the user unticks what they
                        // don't want (same flow as onboarding).
                        selectedKeys = report.accounts.mapTo(mutableSetOf()) { it.key },
                    )
                }
            }.onFailure { e ->
                detectionState = DetectionState(error = e.message ?: "Could not read your messages")
            }
        }
    }

    fun toggleDetected(key: String) {
        val s = detectionState
        detectionState = s.copy(
            selectedKeys = s.selectedKeys.toMutableSet().apply {
                if (!add(key)) remove(key)
            }
        )
    }

    fun dismissDetection() {
        if (!detectionState.isDetecting && !detectionState.isImporting) {
            detectionState = DetectionState()
        }
    }

    /** Create the ticked accounts; orphaned SMS transactions attach automatically. */
    fun importDetectedAccounts() {
        val selected = detectionState.detected.filter { it.key in detectionState.selectedKeys }
        if (selected.isEmpty() || detectionState.isImporting) return
        detectionState = detectionState.copy(isImporting = true, error = null)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                for (account in selected) {
                    val id = accountDao.insert(
                        AccountEntity(
                            name = account.suggestedName,
                            accountClass = if (account.isCard) LedgerService.CLASS_LIABILITY
                            else LedgerService.CLASS_ASSET,
                            subtype = if (account.isCard) "credit_card" else "savings",
                            bank = account.bank,
                            createdAt = now,
                        )
                    )
                    identifierDao.insert(
                        AccountIdentifierEntity(
                            accountId = id,
                            value = account.last4,
                            kind = if (account.isCard) "card" else "account",
                            isActive = true,
                            createdAt = now,
                        )
                    )
                    runCatching { ingestionService.claimOrphansForAccount(id) }
                }
                runCatching { inboxRepository.refreshBuffer() }
            }.onSuccess {
                detectionState = DetectionState()
                refresh()
            }.onFailure { e ->
                detectionState = detectionState.copy(isImporting = false, error = e.message)
            }
        }
    }

    companion object {
        /** How far back the Accounts-screen SMS scan looks (onboarding asks). */
        private const val DETECT_LOOKBACK_DAYS = 365L
    }

    private fun AccountEntity.toUi(balance: Double, last4: String?): AccountUiModel = AccountUiModel(
        id = id,
        name = name,
        accountNumber = if (!last4.isNullOrBlank()) "•••• $last4" else "No account number",
        balance = if (accountClass == LedgerService.CLASS_LIABILITY) -abs(balance) else balance,
        type = when {
            accountClass == LedgerService.CLASS_LIABILITY -> AccountType.CREDIT_CARD
            subtype == "cash" -> AccountType.CASH
            subtype == "current" -> AccountType.CHECKING
            bank != null -> AccountType.SAVINGS
            else -> AccountType.CASH
        },
        bankName = bank ?: "Bank",
    )
}
