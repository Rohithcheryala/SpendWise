package com.example.spendwise.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.backend.api.CreateEntryRequest
import com.example.spendwise.backend.api.Direction
import com.example.spendwise.backend.api.EntrySource
import com.example.spendwise.backend.api.EntryStatus
import com.example.spendwise.backend.api.IngestRequest
import com.example.spendwise.backend.api.Intent
import com.example.spendwise.backend.api.LedgerApi
import com.example.spendwise.backend.api.LineSpec
import com.example.spendwise.backend.service.CounterpartyService
import com.example.spendwise.backend.service.LedgerService
import com.example.spendwise.core.extensions.toPaiseOrNull
import com.example.spendwise.core.parser_pw.md5Hex
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.repository.InboxRepository
import com.example.spendwise.ui.screens.transaction.DropdownOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Scan & Pay write path. The screen itself owns the camera; this VM owns the
 * ledger: loading the user's accounts/categories for the payment sheet, and
 * recording the payment intent as a real buffer entry (source `qr_scan`).
 *
 * Buffer on purpose: when the bank SMS for the same payment lands within the
 * merge window, `IngestionService.tryMergeQrScanBuffer` unites them, fills the
 * bank facts in, auto-confirms, and deletes the QR entry — one payment, one
 * transaction, zero extra taps.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val ledgerApi: LedgerApi,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val counterpartyService: CounterpartyService,
    private val inboxRepository: InboxRepository,
) : ViewModel() {

    data class UiState(
        val accounts: List<DropdownOption> = emptyList(),
        val categories: List<DropdownOption> = emptyList(),
        val isSaving: Boolean = false,
        /** One-shot flag: the payment intent was recorded into the buffer. */
        val saved: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadOptions()
    }

    private fun loadOptions() {
        viewModelScope.launch {
            accountDao.getActive().collect { accounts ->
                _uiState.update { s ->
                    s.copy(
                        accounts = accounts
                            .filter { !it.slug.startsWith("sys-") }
                            .map { DropdownOption(it.id.toString(), it.name) }
                    )
                }
            }
        }
        viewModelScope.launch {
            categoryDao.getAll().collect { list ->
                _uiState.update { s ->
                    s.copy(categories = list.map { DropdownOption(it.id.toString(), it.name) })
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun dismissSaved() = _uiState.update { it.copy(saved = false) }

    /**
     * Record what the user intended to pay. Called when the user returns from
     * the UPI app — Android cannot prove the payment went through (UPI result
     * intents are unreliable across apps), so the entry lands in the buffer
     * and the bank SMS either merges + confirms it, or the user reviews it in
     * the inbox. [amount] is the user-entered rupee string.
     */
    fun recordQrPayment(
        payeeName: String,
        vpa: String,
        amount: String,
        accountId: Long?,
        categoryId: Long?,
        tags: List<String>,
        note: String,
        upiUri: String?,
    ) {
        if (_uiState.value.isSaving) return

        val amountPaise = amount.toPaiseOrNull()
        when {
            amountPaise == null || amountPaise <= 0 ->
                return setError("Enter a valid amount")
            accountId == null ->
                return setError("Select an account")
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val now = System.currentTimeMillis()
                val occurredOn = LocalDate.now()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant().toEpochMilli()

                // The VPA is the strongest cross-source key: resolve against
                // existing aliases (a previous SMS from this payee matches),
                // else create the counterparty with the QR's display name.
                val counterpartyId = counterpartyService.resolveOrCreate(
                    userId = LedgerService.USER_ID,
                    rawName = vpa.ifBlank { payeeName },
                    displayName = payeeName.ifBlank { null },
                    aliasSource = "upi",
                )

                if (categoryId != null) {
                    // User picked a category: write a proper expense shape
                    // (account leg + category leg) so no unclassified contra
                    // line has to be re-classified later.
                    ledgerApi.createEntry(
                        CreateEntryRequest(
                            occurredOn = occurredOn,
                            lines = listOf(
                                LineSpec(-amountPaise, accountId = accountId),
                                LineSpec(amountPaise, categoryId = categoryId),
                            ),
                            status = EntryStatus.BUFFER,
                            source = EntrySource.QR_SCAN,
                            happenedAt = now,
                            counterpartyId = counterpartyId,
                            note = note.ifBlank { null },
                            tags = tags,
                        )
                    )
                } else {
                    ledgerApi.ingest(
                        IngestRequest(
                            amountPaise = amountPaise,
                            direction = Direction.OUT,
                            occurredOn = occurredOn,
                            accountId = accountId,
                            counterpartyId = counterpartyId,
                            intent = Intent.EXPENSE,
                            tags = tags,
                            status = EntryStatus.BUFFER,
                            source = EntrySource.QR_SCAN,
                            happenedAt = now,
                            rawText = upiUri,
                            dedupeHash = md5Hex("qr|$vpa|$amountPaise|$note|${now / 60_000}"),
                            note = note.ifBlank { null },
                        )
                    )
                }

                // The inbox badge and list read this shared flow — update now.
                runCatching { inboxRepository.refreshBuffer() }
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "Could not save")
                }
            }
        }
    }

    private fun setError(message: String) {
        _uiState.update { it.copy(error = message) }
    }
}

