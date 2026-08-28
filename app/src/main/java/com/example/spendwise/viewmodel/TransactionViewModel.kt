package com.example.spendwise.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.backend.api.CreateEntryRequest
import com.example.spendwise.backend.api.Direction
import com.example.spendwise.backend.api.EntryKind
import com.example.spendwise.backend.api.EntrySource
import com.example.spendwise.backend.api.EntryStatus
import com.example.spendwise.backend.api.IngestRequest
import com.example.spendwise.backend.api.Intent
import com.example.spendwise.backend.api.LedgerApi
import com.example.spendwise.backend.api.LineSpec
import com.example.spendwise.core.extensions.toPaiseOrNull
import com.example.spendwise.core.extensions.toRupeeInput
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.database.dao.CounterpartyDao
import com.example.spendwise.data.database.dao.EntryProvanceDao
import com.example.spendwise.data.repository.InboxRepository
import com.example.spendwise.data.repository.SettingsRepository
import com.example.spendwise.ui.components.TransactionDirection
import com.example.spendwise.ui.screens.transaction.DropdownOption
import com.example.spendwise.ui.screens.transaction.TagUiModel
import com.example.spendwise.ui.screens.transaction.TransactionMode
import com.example.spendwise.ui.screens.transaction.TransactionType
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
 * entries through [LedgerApi] instead of just popping the back stack.
 */
@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val ledgerApi: LedgerApi,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val counterpartyDao: CounterpartyDao,
    private val provenanceDao: EntryProvanceDao,
    private val settingsRepository: SettingsRepository,
    private val inboxRepository: InboxRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Emitted when the screen should close (save/delete/void succeeded). */
    val finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState = _uiState.asStateFlow()

    /** Route argument: -1 = create mode, otherwise edit the given entry. */
    private val entryId: Long = savedStateHandle.get<Long>("entryId") ?: -1L

    private var realCounterparties: List<DropdownOption> = emptyList()

    init {
        loadOptions()
        if (entryId > 0) {
            viewModelScope.launch { loadEntry(entryId) }
        }
    }

    fun updateState(transform: (TransactionUiState) -> TransactionUiState) {
        _uiState.update { current ->
            val next = transform(current)
            if (next.type != current.type) {
                // Type switches change what the second dropdown means:
                // TRANSFER -> destination accounts, CATEGORY/LOAN -> counterparties.
                next.copy(
                    counterparty = null,
                    counterparties = if (next.type == TransactionType.TRANSFER) {
                        next.accounts.filter { it.id != next.account?.id }
                    } else {
                        realCounterparties
                    },
                    error = null,
                )
            } else {
                next.copy(error = null)
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
                when (s.type) {
                    TransactionType.CATEGORY -> saveCategoryEntry(s, amountPaise!!)
                    TransactionType.TRANSFER -> saveTransfer(s, amountPaise!!)
                    TransactionType.LOAN -> saveLoan(s, amountPaise!!)
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

    private suspend fun saveCategoryEntry(s: TransactionUiState, amountPaise: Long) {
        val category = s.category ?: throw IllegalArgumentException("Select a category")

        val strict = settingsRepository.settings.first().strictMode
        if (strict && s.tags.isEmpty()) {
            throw IllegalArgumentException("Strict mode: add at least one tag before saving")
        }

        if (s.mode == TransactionMode.EDIT && s.id != null) {
            ledgerApi.voidEntry(s.id!!, "edited")
        }

        val accountAmount = if (s.direction == TransactionDirection.EXPENSE) {
            -amountPaise
        } else {
            amountPaise
        }

        ledgerApi.createEntry(
            CreateEntryRequest(
                occurredOn = occurredOn(s.date),
                lines = listOf(
                    LineSpec(accountAmount, accountId = s.account!!.id.toLong()),
                    LineSpec(-accountAmount, categoryId = category.id.toLong()),
                ),
                status = EntryStatus.CONFIRMED,
                source = EntrySource.MANUAL,
                counterpartyId = s.counterparty?.id?.toLongOrNull(),
                note = s.note.ifBlank { null },
                tags = s.tags.map { it.label },
            )
        )
    }

    private suspend fun saveTransfer(s: TransactionUiState, amountPaise: Long) {
        val toAccountId = s.counterparty?.id?.toLongOrNull()
            ?: throw IllegalArgumentException("Select the destination account")

        if (s.mode == TransactionMode.EDIT && s.id != null) {
            ledgerApi.voidEntry(s.id!!, "edited")
        }

        ledgerApi.ingestTransfer(
            amountPaise = amountPaise,
            fromAccountId = s.account!!.id.toLong(),
            toAccountId = toAccountId,
            occurredOn = occurredOn(s.date),
            status = EntryStatus.CONFIRMED,
            source = EntrySource.MANUAL,
            note = s.note.ifBlank { null },
        )
    }

    private suspend fun saveLoan(s: TransactionUiState, amountPaise: Long) {
        val counterpartyId = s.counterparty?.id?.toLongOrNull()
            ?: throw IllegalArgumentException("Select a counterparty")

        if (s.mode == TransactionMode.EDIT && s.id != null) {
            ledgerApi.voidEntry(s.id!!, "edited")
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
                tags = s.tags.map { it.label },
                status = EntryStatus.CONFIRMED,
                source = EntrySource.MANUAL,
                note = s.note.ifBlank { null },
            )
        )
    }

    fun delete() {
        val id = _uiState.value.id ?: return
        viewModelScope.launch {
            runCatching { ledgerApi.voidEntry(id, "deleted from app") }
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
            runCatching { ledgerApi.voidEntry(id, null) }
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
                    .filter { !it.slug.startsWith("sys-") }
                    .map { DropdownOption(it.id.toString(), it.name) }
                _uiState.update { s ->
                    if (s.type == TransactionType.TRANSFER) {
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
                    if (s.type == TransactionType.TRANSFER) {
                        s
                    } else {
                        s.copy(counterparties = realCounterparties)
                    }
                }
            }
        }

        viewModelScope.launch {
            categoryDao.getAll().collect { list ->
                _uiState.update { s ->
                    s.copy(categories = list.map {
                        DropdownOption(it.id.toString(), it.name)
                    })
                }
            }
        }
    }

    private suspend fun loadEntry(id: Long) {
        val view = ledgerApi.getEntry(id) ?: return
        val account = view.accountId?.let { accountDao.getById(it) }
        val toAccount = view.toAccountId?.let { accountDao.getById(it) }
        val category = view.categoryId?.let { categoryDao.getById(it) }
        val rawSms = provenanceDao.getByEntry(id)?.rawText.orEmpty()
            .ifBlank { view.note.orEmpty() }
        val counterparty = when (view.kind) {
            EntryKind.TRANSFER -> toAccount?.let {
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
                type = when (view.kind) {
                    EntryKind.TRANSFER -> TransactionType.TRANSFER
                    EntryKind.LOAN, EntryKind.LOAN_REPAYMENT -> TransactionType.LOAN
                    else -> TransactionType.CATEGORY
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
                counterparties = if (view.kind == EntryKind.TRANSFER) {
                    s.accounts.filter { account == null || it.id != account.id.toString() }
                } else {
                    realCounterparties
                },
            )
        }
    }

    private fun occurredOn(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun setError(message: String) {
        _uiState.update { it.copy(error = message) }
    }
}
