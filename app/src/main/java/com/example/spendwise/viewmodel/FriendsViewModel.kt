package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.backend.api.LedgerApi
import com.example.spendwise.backend.service.CounterpartyService
import com.example.spendwise.backend.service.LedgerService
import com.example.spendwise.data.database.dao.CounterpartyDao
import com.example.spendwise.ui.screens.friends.FriendUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Friends = person-type counterparties of the ledger. Balances come from
 * [LedgerApi.friendsOutstanding] (net outstanding on the loans receivable
 * pot); zero-balance friends are listed from the counterparty table.
 */
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val ledgerApi: LedgerApi,
    private val counterpartyDao: CounterpartyDao,
    private val counterpartyService: CounterpartyService,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val friends: List<FriendUi> = emptyList(),
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
                // Only party_type = person entries belong on this page.
                val persons = counterpartyDao
                    .getByPartyType(CounterpartyService.PARTY_PERSON)
                    .first()
                val outstanding = ledgerApi.friendsOutstanding()
                    .associate { it.counterpartyId to it.netPaise }

                persons.map { cp ->
                    val net = outstanding[cp.id] ?: 0L
                    FriendUi(
                        id = cp.id,
                        name = cp.displayName,
                        amountGiven = maxOf(net, 0L) / 100.0,
                        amountReceived = maxOf(-net, 0L) / 100.0,
                    )
                }
            }.onSuccess { friends ->
                uiState = uiState.copy(isLoading = false, friends = friends)
            }.onFailure { e ->
                uiState = uiState.copy(isLoading = false, error = e.message)
            }
        }
    }

    /** Creates a person counterparty in the ledger; shows up here after reload. */
    fun addFriend(name: String) {
        viewModelScope.launch {
            runCatching {
                val id = counterpartyService.resolveOrCreate(
                    userId = LedgerService.USER_ID,
                    rawName = name,
                    displayName = name,
                    aliasSource = "manual",
                )
                counterpartyService.ensurePartyType(id, CounterpartyService.PARTY_PERSON)
            }.onSuccess {
                load()
            }.onFailure { e ->
                uiState = uiState.copy(error = e.message)
            }
        }
    }
}
