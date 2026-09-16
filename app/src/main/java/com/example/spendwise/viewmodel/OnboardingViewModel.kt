package com.example.spendwise.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.AccountIdentifierDao
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.data.database.entity.AccountIdentifierEntity
import com.example.spendwise.ledger.service.IngestionService
import com.example.spendwise.ledger.service.LedgerService
import com.example.spendwise.data.repository.AppMetadataRepository
import com.example.spendwise.data.repository.InboxRepository
import com.example.spendwise.data.repository.OnboardingRepository
import com.example.spendwise.ui.screens.onboarding.OnboardingState
import com.example.spendwise.ui.screens.onboarding.OnboardingStep


import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


// For Budget, Friends, Groups, Accounts — each one is just:
//
// XEntity.kt + XDao.kt in features/x/data/
// One line added to AppDatabase.kt's entities = [...] list
// One @Provides fun provideXDao(...) line in DatabaseModule.kt
// XRepository @Inject constructor(private val xDao: XDao)
// @HiltViewModel class XViewModel @Inject constructor(private val repository: XRepository)
// val viewModel: XViewModel = hiltViewModel() in the screen
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
    private val inboxRepository: InboxRepository,
    private val ingestionService: IngestionService,
    private val accountDao: AccountDao,
    private val identifierDao: AccountIdentifierDao,
    private val appMetadataRepository: AppMetadataRepository,
) : ViewModel() {

    /** Phases of the SMS-scan onboarding step. */
    enum class ScanPhase { IDLE, SCANNING, DONE, ERROR }

    data class ScanUiState(
        val phase: ScanPhase = ScanPhase.IDLE,
        val messagesRead: Int = 0,
        val transactionsDetected: Int = 0,
        val accounts: List<InboxRepository.DetectedAccount> = emptyList(),
        val selectedKeys: Set<String> = emptySet(),
        val error: String? = null,
        /** The scan start the user picked — persisted as the tracking start date. */
        val scanStartMillis: Long? = null,
    )

    private val _scanState = MutableStateFlow(ScanUiState())
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    /** Accounts that already exist (used by the account-selection step). */
    val userAccounts: StateFlow<List<AccountEntity>> = flow {
        emit(
            accountDao.listAll().filter {
                !it.isSystem && !it.isArchived &&
                    (it.accountClass == LedgerService.CLASS_ASSET ||
                        it.accountClass == LedgerService.CLASS_LIABILITY)
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Null until the persisted state has hydrated: the nav graph renders
    // nothing for that first instant, so a user who already finished
    // onboarding never sees the Welcome screen flash on relaunch.
    val currentStep: StateFlow<OnboardingStep?> = combine(repository.ready, repository.state) { ready, state ->
        if (ready) computeStep(state) else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isOnboardingComplete: StateFlow<Boolean> = currentStep
        .map { it == OnboardingStep.Finished }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun completeWelcome() = repository.update { it.copy(welcomeSeen = true) }
    fun completeProfile() = repository.update { it.copy(profileCompleted = true) }
    fun completePermissions() = repository.update { it.copy(permissionGranted = true) }

    /**
     * Finish the SMS-scan step, persisting the date the user scanned from as
     * the app's tracking start date ("day one" of the ledger). The chosen
     * date previously lived only in the scan UI state and was lost — nothing
     * downstream (Settings, later SMS scans) could see it.
     */
    fun completeScan() {
        val startMillis = _scanState.value.scanStartMillis
        viewModelScope.launch {
            if (startMillis != null) {
                runCatching { appMetadataRepository.setTrackingStartDate(startMillis) }
            }
            repository.update { it.copy(smsScanCompleted = true) }
        }
    }
    fun completeAccounts() = repository.update { it.copy(accountsSelected = true) }

    fun completeOnboarding() = repository.update {
        it.copy(
            permissionGranted = true,
            smsScanCompleted = true,
            accountsSelected = true
        )
    }

    // ── SMS scan step ──

    fun startScan(fromMillis: Long) {
        if (_scanState.value.phase == ScanPhase.SCANNING) return
        _scanState.value = ScanUiState(phase = ScanPhase.SCANNING, scanStartMillis = fromMillis)
        viewModelScope.launch {
            runCatching { inboxRepository.scanFrom(fromMillis) }
                .onSuccess { report ->
                    _scanState.value = ScanUiState(
                        phase = ScanPhase.DONE,
                        messagesRead = report.messagesRead,
                        transactionsDetected = report.transactionsDetected,
                        accounts = report.accounts,
                        // Auto-select every detected account — the user just
                        // unticks what they don't want.
                        selectedKeys = report.accounts.mapTo(mutableSetOf()) { it.key },
                    )
                }
                .onFailure { e ->
                    _scanState.value = ScanUiState(
                        phase = ScanPhase.ERROR,
                        error = e.message ?: "Could not read messages",
                    )
                }
        }
    }

    fun toggleAccount(key: String) {
        val s = _scanState.value
        _scanState.value = s.copy(
            selectedKeys = s.selectedKeys.toMutableSet().apply {
                if (!add(key)) remove(key)
            }
        )
    }

    /** Create the ticked accounts (no opening balance — the ledger starts here). */
    fun createSelectedAccounts() {
        val s = _scanState.value
        val selected = s.accounts.filter { it.key in s.selectedKeys }
        if (selected.isEmpty()) {
            completeScan()
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                val createdIds = mutableListOf<Long>()
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
                    createdIds += id
                    identifierDao.insert(
                        AccountIdentifierEntity(
                            accountId = id,
                            value = account.last4,
                            kind = if (account.isCard) "card" else "account",
                            isActive = true,
                            createdAt = now,
                        )
                    )
                }
                // The scan ingested SMS before these accounts existed, so those
                // transactions sit orphaned on the unmatched pot. Re-run matching
                // (attach-only) so they attach to the new accounts.
                for (id in createdIds) {
                    runCatching { ingestionService.claimOrphansForAccount(id) }
                }
                runCatching { inboxRepository.refreshBuffer() }
                completeScan()
            }.onFailure { e ->
                _scanState.value = _scanState.value.copy(phase = ScanPhase.ERROR, error = e.message)
            }
        }
    }

    private fun computeStep(state: OnboardingState): OnboardingStep = when {
        !state.welcomeSeen -> OnboardingStep.Welcome
        !state.profileCompleted -> OnboardingStep.Profile
        !state.permissionGranted -> OnboardingStep.Permissions
        !state.smsScanCompleted -> OnboardingStep.ScanMessages
        !state.accountsSelected -> OnboardingStep.SelectAccounts
        else -> OnboardingStep.Finished
    }
}