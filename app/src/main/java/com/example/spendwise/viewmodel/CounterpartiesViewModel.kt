package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.ledger.api.LedgerApi
import com.example.spendwise.data.database.dao.CounterpartyAliasDao
import com.example.spendwise.data.database.dao.CounterpartyDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One row of the Counterparties page — every party the ledger knows about,
 * persons and merchants alike (unlike Friends, which filters to persons).
 */
data class CounterpartyUi(
    val id: Long,
    val name: String,
    /** CounterpartyService.PARTY_PERSON / PARTY_MERCHANT. */
    val partyType: String,
    /** Known aliases (UPI handles, SMS sender names, phone numbers...). */
    val aliases: List<String>,
    /** Net outstanding for persons only; +paise = they owe you. Null otherwise. */
    val netPaise: Long?,
)

/**
 * Counterparties = the full party table: persons AND merchants, including the
 * ones auto-created from SMS/UPI ingestion. Balances reuse
 * [LedgerApi.friendsOutstanding] (persons only, loans receivable pot).
 */
@HiltViewModel
class CounterpartiesViewModel @Inject constructor(
    private val counterpartyDao: CounterpartyDao,
    private val aliasDao: CounterpartyAliasDao,
    private val ledgerApi: LedgerApi,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val counterparties: List<CounterpartyUi> = emptyList(),
        val error: String? = null,
    )

    var uiState by mutableStateOf(UiState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)

            runCatching {
                val parties = counterpartyDao.getAll().first()
                val aliases = aliasDao.getAll().first().groupBy { it.counterpartyId }
                // Persons only appear in the outstanding pot; a failure here
                // shouldn't blank the whole page — degrade to no balances.
                val outstanding = runCatching { ledgerApi.friendsOutstanding() }
                    .getOrDefault(emptyList())
                    .associate { it.counterpartyId to it.netPaise }

                parties.map { cp ->
                    CounterpartyUi(
                        id = cp.id,
                        name = cp.displayName,
                        partyType = cp.partyType,
                        aliases = aliases[cp.id].orEmpty().map { it.aliasDisplay },
                        netPaise = outstanding[cp.id],
                    )
                }
            }.onSuccess { parties ->
                uiState = uiState.copy(isLoading = false, counterparties = parties)
            }.onFailure { e ->
                uiState = uiState.copy(isLoading = false, error = e.message)
            }
        }
    }
}