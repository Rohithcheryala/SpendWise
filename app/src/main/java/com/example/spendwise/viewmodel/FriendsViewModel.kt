package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.backend.api.ApiException
import com.example.spendwise.backend.api.LedgerApi
import com.example.spendwise.backend.service.ContactsService
import com.example.spendwise.backend.service.CounterpartyService
import com.example.spendwise.backend.service.LedgerService
import com.example.spendwise.core.contacts.Contact
import com.example.spendwise.core.contacts.ContactReader
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
 *
 * The address book side (device contacts) backs the "Add Friend" sheet:
 * [loadContacts] reads it through [ContactReader] once READ_CONTACTS is
 * granted, and [syncContactCache] mirrors it into the contacts cache so
 * ingestion can resolve UPI handles to saved names.
 */
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val ledgerApi: LedgerApi,
    private val counterpartyDao: CounterpartyDao,
    private val counterpartyService: CounterpartyService,
    private val contactReader: ContactReader,
    private val contactsService: ContactsService,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val friends: List<FriendUi> = emptyList(),
        val error: String? = null,
        /** Device address book, loaded on demand for the add-friend sheet. */
        val contacts: List<Contact> = emptyList(),
        val contactsLoading: Boolean = false,
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

    /**
     * Reads the device address book. Call only once READ_CONTACTS is granted —
     * the UI gates this behind the runtime permission flow. Idempotent per
     * process: results are cached in state, so re-opening the sheet doesn't
     * re-query the ContactsProvider.
     */
    fun loadContacts() {
        if (uiState.contactsLoading || uiState.contacts.isNotEmpty()) return
        viewModelScope.launch {
            uiState = uiState.copy(contactsLoading = true)
            runCatching {
                contactReader.getContacts()
                    .filter { it.name.isNotBlank() }
                    .distinctBy { it.name.trim().lowercase() to it.phoneNumber }
                    .sortedBy { it.name.lowercase() }
            }.onSuccess { contacts ->
                uiState = uiState.copy(contactsLoading = false, contacts = contacts)
                if (contacts.isNotEmpty()) syncContactCache(contacts)
            }.onFailure { e ->
                uiState = uiState.copy(contactsLoading = false, error = e.message)
            }
        }
    }

    /**
     * Mirrors the address book into the contacts cache and renames any
     * counterparty still displayed as a raw UPI handle to the saved contact
     * name (the documented ContactsService design — cache is fully
     * replaceable, counterparties link softly via phone aliases).
     */
    private fun syncContactCache(contacts: List<Contact>) {
        viewModelScope.launch {
            runCatching {
                contactsService.replaceAll(
                    contacts.map {
                        ContactsService.SyncedContact(
                            phone = it.phoneNumber,
                            name = it.name,
                        )
                    }
                )
                contactsService.applyToExisting()
            }.onSuccess { renamed ->
                if (renamed > 0) load()
            }
        }
    }

    /**
     * Creates a person counterparty in the ledger; shows up here after reload.
     * [phone], when given, is recorded as a contact alias so future SMS/UPI
     * traffic from that number resolves to this friend.
     */
    fun addFriend(name: String, phone: String? = null) {
        viewModelScope.launch {
            runCatching {
                val trimmed = name.trim()
                if (trimmed.isBlank()) throw ApiException("friend name must not be blank")

                val id = counterpartyService.resolveOrCreate(
                    userId = LedgerService.USER_ID,
                    rawName = trimmed,
                    displayName = trimmed,
                    aliasSource = "manual",
                ) ?: throw ApiException("friend name must not be blank")

                counterpartyService.ensurePartyType(id, CounterpartyService.PARTY_PERSON)
                if (!phone.isNullOrBlank()) {
                    counterpartyService.addAlias(id, phone, source = "contact")
                }
            }.onSuccess {
                load()
            }.onFailure { e ->
                uiState = uiState.copy(error = e.message)
            }
        }
    }
}
