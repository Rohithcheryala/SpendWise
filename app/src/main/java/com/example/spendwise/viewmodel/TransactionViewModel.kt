package com.example.spendwise.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.ledger.api.CreateTransactionRequest
import com.example.spendwise.ledger.api.Direction
import com.example.spendwise.ledger.api.TransactionKind
import com.example.spendwise.ledger.api.TransactionSource
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.api.IngestRequest
import com.example.spendwise.ledger.api.Intent
import com.example.spendwise.ledger.api.LedgerApi
import com.example.spendwise.ledger.api.LineSpec
import com.example.spendwise.ledger.service.CounterpartyService
import com.example.spendwise.ledger.service.LedgerService
import com.example.spendwise.core.extensions.toPaiseOrNull
import com.example.spendwise.core.extensions.toRupeeInput
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.CounterpartyDao
import com.example.spendwise.data.database.dao.TransactionProvenanceDao
import com.example.spendwise.data.repository.InboxRepository
import com.example.spendwise.data.repository.SettingsRepository
import com.example.spendwise.ui.components.TransactionDirection
import com.example.spendwise.ui.screens.transaction.DropdownOption
import com.example.spendwise.ui.screens.transaction.TagUiModel
import com.example.spendwise.ui.screens.transaction.TransactionMode
import com.example.spendwise.ui.screens.transaction.OtherSide
import com.example.spendwise.ui.screens.transaction.TransactionUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Create/edit screen state, wired to the real ledger: save() writes balanced
 * transactions through [LedgerApi] instead of just popping the back stack.
 */
@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val ledgerApi: LedgerApi,
    private val accountDao: AccountDao,
    private val counterpartyDao: CounterpartyDao,
    private val counterpartyService: CounterpartyService,
    private val provenanceDao: TransactionProvenanceDao,
    private val settingsRepository: SettingsRepository,
    private val inboxRepository: InboxRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Emitted when the screen should close (save/delete/void succeeded). */
    val finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState = _uiState.asStateFlow()

    /** Route argument: -1 = create mode, otherwise edit the given entry. */
    private val transactionId: Long = savedStateHandle.get<Long>("transactionId") ?: -1L

    private var realCounterparties: List<DropdownOption> = emptyList()

    /**
     * The active tagging context's tag, if any. New saves get it stamped on
     * (trip mode); removing the tag chip in the editor suppresses it for that
     * one save instead of fighting the user.
     */
    private var activeContextTag: String? = null
    private var activeTagSuppressed = false

    init {
        loadOptions()
        if (transactionId > 0) {
            viewModelScope.launch { loadEntry(transactionId) }
        }
        // Seed the context tag so it is visible (and removable) in the editor;
        // save() unions it for transactions opened from the inbox too.
        viewModelScope.launch {
            val tag = settingsRepository.activeContext.first()?.tag
            activeContextTag = tag
            if (transactionId <= 0 && tag != null) {
                _uiState.update { s ->
                    if (s.tags.any { it.id == tag }) s
                    else s.copy(tags = s.tags + TagUiModel(tag, tag))
                }
            }
        }
    }

    fun updateState(transform: (TransactionUiState) -> TransactionUiState) {
        val before = _uiState.value
        _uiState.update { current ->
            val next = transform(current)
            // The user explicitly removed the context tag chip — respect that
            // for this transaction instead of re-adding it at save.
            val ctxTag = activeContextTag
            if (ctxTag != null &&
                current.tags.any { it.id == ctxTag } &&
                next.tags.none { it.id == ctxTag }
            ) {
                activeTagSuppressed = true
            }
            if (next.otherSide != current.otherSide) {
                // Type switches change what the second dropdown means:
                // TRANSFER -> destination accounts, CATEGORY/LOAN -> counterparties.
                next.copy(
                    counterparty = null,
                    counterparties = if (next.otherSide == OtherSide.TRANSFER) {
                        next.accounts.filter { it.id != next.account?.id }
                    } else {
                        realCounterparties
                    },
                    error = null,
                )
            } else if (next.otherSide == OtherSide.TRANSFER && next.account != current.account) {
                // From-account changed while in transfer mode: a transfer to
                // the same account is not a movement — re-exclude it.
                next.copy(counterparties = next.accounts.filter { it.id != next.account?.id })
            } else {
                next.copy(error = null)
            }
        }
        // The "owes you" context line only makes sense in loan mode, and must
        // track both the mode and the picked person.
        val after = _uiState.value
        if (after.otherSide != before.otherSide || after.counterparty != before.counterparty) {
            viewModelScope.launch { refreshLoanOutstanding() }
        }
    }

    private suspend fun refreshLoanOutstanding() {
        val s = _uiState.value
        val counterpartyId = s.counterparty?.id?.toLongOrNull()
        if (s.otherSide != OtherSide.LOAN || counterpartyId == null) {
            _uiState.update { it.copy(loanOutstandingPaise = null) }
            return
        }
        // friendsOutstanding omits settled parties — null here reads as "no
        // outstanding balance", a useful check figure before saving.
        val net = runCatching {
            ledgerApi.friendsOutstanding()
                .firstOrNull { it.counterpartyId == counterpartyId }?.netPaise
        }.getOrNull()
        _uiState.update { it.copy(loanOutstandingPaise = net) }
    }

    /**
     * Create (or resolve) a counterparty from a name typed inline in the
     * picker — the only manual creation path in the app. Loan mode marks it
     * as a person so it appears on the Friends ledger.
     */
    fun addCounterparty(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val id = counterpartyService.resolveOrCreate(
                    userId = LedgerService.USER_ID,
                    rawName = trimmed,
                    displayName = trimmed,
                    aliasSource = "manual",
                ) ?: return@launch
                if (_uiState.value.otherSide == OtherSide.LOAN) {
                    counterpartyService.ensurePartyType(id, CounterpartyService.PARTY_PERSON)
                }
                val option = DropdownOption(id.toString(), trimmed)
                _uiState.update {
                    // The Room flow on counterpartyDao refreshes the list;
                    // select it immediately so the picker can close.
                    it.copy(counterparty = option)
                }
                refreshLoanOutstanding()
            } catch (e: Exception) {
                setError(e.message ?: "Could not add")
            }
        }
    }

    // ── save path ──

    fun save() {
        val s = _uiState.value
        if (s.isSaving) return

        val amountPaise = s.amount.toPaiseOrNull()
        when {
            amountPaise == null || amountPaise <= 0 ->
                return setError("Enter a valid amount")
            s.account == null ->
                return setError("Select an account")
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val contextTag = activeContextTag?.takeUnless { activeTagSuppressed }
                when (s.otherSide) {
                    OtherSide.CATEGORY -> saveCategoryEntry(s, amountPaise!!, contextTag)
                    OtherSide.TRANSFER -> saveTransfer(s, amountPaise!!)
                    OtherSide.LOAN -> saveLoan(s, amountPaise!!, contextTag)
                }
                // The save path can confirm/replace a buffer entry (opened from
                // the inbox) — invalidate the shared inbox flow so the list and
                // the bottom-nav badge update immediately.
                runCatching { inboxRepository.refreshBuffer() }
                finished.tryEmit(Unit)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "Could not save")
                }
            }
        }
    }

    private suspend fun saveCategoryEntry(
        s: TransactionUiState,
        amountPaise: Long,
        contextTag: String?,
    ) {
        val category = s.category ?: throw IllegalArgumentException("Select a category")

        val strict = settingsRepository.settings.first().strictMode
        if (strict && s.tags.isEmpty()) {
            throw IllegalArgumentException("Strict mode: add at least one tag before saving")
        }

        if (s.mode == TransactionMode.EDIT && s.id != null) {
            ledgerApi.voidTransaction(s.id!!, "edited")
        }

        val accountAmount = if (s.direction == TransactionDirection.EXPENSE) {
            -amountPaise
        } else {
            amountPaise
        }

        ledgerApi.createTransaction(
            CreateTransactionRequest(
                occurredOn = occurredOn(s.date),
                lines = listOf(
                    LineSpec(accountAmount, accountId = s.account!!.id.toLong()),
                    LineSpec(-accountAmount, accountId = category.id.toLong()),
                ),
                status = TransactionStatus.CONFIRMED,
                source = TransactionSource.MANUAL,
                counterpartyId = s.counterparty?.id?.toLongOrNull(),
                note = s.note.ifBlank { null },
                tags = withContextTag(s.tags.map { it.label }, contextTag),
            )
        )
    }

    private suspend fun saveTransfer(s: TransactionUiState, amountPaise: Long) {
        val toAccountId = s.counterparty?.id?.toLongOrNull()
            ?: throw IllegalArgumentException("Select the destination account")

        if (s.mode == TransactionMode.EDIT && s.id != null) {
            ledgerApi.voidTransaction(s.id!!, "edited")
        }

        ledgerApi.ingestTransfer(
            amountPaise = amountPaise,
            fromAccountId = s.account!!.id.toLong(),
            toAccountId = toAccountId,
            occurredOn = occurredOn(s.date),
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            note = s.note.ifBlank { null },
        )
    }

    private suspend fun saveLoan(
        s: TransactionUiState,
        amountPaise: Long,
        contextTag: String?,
    ) {
        val counterpartyId = s.counterparty?.id?.toLongOrNull()
            ?: throw IllegalArgumentException("Select a counterparty")

        if (s.mode == TransactionMode.EDIT && s.id != null) {
            ledgerApi.voidTransaction(s.id!!, "edited")
        }

        val moneyIn = s.direction == TransactionDirection.INCOME
        ledgerApi.ingest(
            IngestRequest(
                amountPaise = amountPaise,
                direction = if (moneyIn) Direction.IN else Direction.OUT,
                occurredOn = occurredOn(s.date),
                accountId = s.account!!.id.toLong(),
                counterpartyId = counterpartyId,
                intent = if (moneyIn) Intent.LOAN_REPAYMENT else Intent.LOAN,
                tags = withContextTag(s.tags.map { it.label }, contextTag),
                status = TransactionStatus.CONFIRMED,
                source = TransactionSource.MANUAL,
                note = s.note.ifBlank { null },
            )
        )
    }

    /** [tags] plus the active context tag (deduped) — the trip-mode stamp. */
    private fun withContextTag(tags: List<String>, contextTag: String?): List<String> {
        if (contextTag == null || contextTag in tags) return tags
        return tags + contextTag
    }

    fun delete() {
        val id = _uiState.value.id ?: return
        viewModelScope.launch {
            runCatching { ledgerApi.voidTransaction(id, "deleted from app") }
                .onSuccess {
                    runCatching { inboxRepository.refreshBuffer() }
                    finished.tryEmit(Unit)
                }
                .onFailure { e -> setError(e.message ?: "Delete failed") }
        }
    }

    fun void() {
        val id = _uiState.value.id ?: return
        viewModelScope.launch {
            runCatching { ledgerApi.voidTransaction(id, null) }
                .onSuccess {
                    runCatching { inboxRepository.refreshBuffer() }
                    finished.tryEmit(Unit)
                }
                .onFailure { e -> setError(e.message ?: "Void failed") }
        }
    }

    // ── load path ──

    private fun loadOptions() {
        viewModelScope.launch {
            accountDao.getActive().collect { accounts ->
                val options = accounts
                    .filter { !it.isSystem }
                    .map { DropdownOption(it.id.toString(), it.name) }
                _uiState.update { s ->
                    if (s.otherSide == OtherSide.TRANSFER) {
                        s.copy(
                            accounts = options,
                            counterparties = options.filter { it.id != s.account?.id },
                        )
                    } else {
                        s.copy(accounts = options)
                    }
                }
            }
        }

        viewModelScope.launch {
            counterpartyDao.getAll().collect { list ->
                realCounterparties = list.map {
                    DropdownOption(it.id.toString(), it.displayName)
                }
                _uiState.update { s ->
                    if (s.otherSide == OtherSide.TRANSFER) {
                        s
                    } else {
                        s.copy(counterparties = realCounterparties)
                    }
                }
            }
        }

        viewModelScope.launch {
            accountDao.getCategoryAccounts().collect { list ->
                _uiState.update { s ->
                    s.copy(categories = list.map {
                        DropdownOption(it.id.toString(), it.name)
                    })
                }
            }
        }
    }

    private suspend fun loadEntry(id: Long) {
        val view = ledgerApi.getTransaction(id) ?: return
        val account = view.accountId?.let { accountDao.getById(it) }
        val toAccount = view.toAccountId?.let { accountDao.getById(it) }
        val category = view.categoryId?.let { accountDao.getById(it) }
        val rawSms = provenanceDao.getByTransaction(id)?.rawText.orEmpty()
            .ifBlank { view.note.orEmpty() }
        val counterparty = when (view.kind) {
            TransactionKind.TRANSFER -> toAccount?.let {
                DropdownOption(it.id.toString(), it.name)
            }
            else -> view.counterpartyId?.let { counterpartyDao.getById(it) }?.let {
                DropdownOption(it.id.toString(), it.displayName)
            }
        }

        _uiState.update { s ->
            s.copy(
                id = view.id,
                mode = TransactionMode.EDIT,
                direction = if (view.direction == Direction.IN) {
                    TransactionDirection.INCOME
                } else {
                    TransactionDirection.EXPENSE
                },
                otherSide = when (view.kind) {
                    TransactionKind.TRANSFER -> OtherSide.TRANSFER
                    TransactionKind.LOAN, TransactionKind.LOAN_REPAYMENT -> OtherSide.LOAN
                    else -> OtherSide.CATEGORY
                },
                amount = view.amountPaise.toRupeeInput(),
                date = Instant.ofEpochMilli(view.occurredOn)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate(),
                account = account?.let { DropdownOption(it.id.toString(), it.name) } ?: s.account,
                counterparty = counterparty ?: s.counterparty,
                category = category?.let { DropdownOption(it.id.toString(), it.name) },
                tags = view.tags.map { TagUiModel(it, it) },
                note = view.note.orEmpty(),
                source = view.source,
                rawSms = rawSms.takeIf { it.isNotBlank() },
                canDelete = true,
                counterparties = if (view.kind == TransactionKind.TRANSFER) {
                    s.accounts.filter { account == null || it.id != account.id.toString() }
                } else {
                    realCounterparties
                },
            )
        }
        // Edit mode: show the check figure for the loaded loan counterparty.
        refreshLoanOutstanding()
    }

    private fun occurredOn(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun setError(message: String) {
        _uiState.update { it.copy(error = message) }
    }
}
