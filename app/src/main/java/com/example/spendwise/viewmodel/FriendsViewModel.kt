package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.ledger.api.ApiException
import com.example.spendwise.ledger.api.LedgerApi
import com.example.spendwise.ledger.service.ContactsService
import com.example.spendwise.ledger.service.CounterpartyService
import com.example.spendwise.ledger.service.LedgerService
import com.example.spendwise.ledger.service.Text
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
                // Only party_type = person transactions belong on this page.
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
                    .distinctByPhone()
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
                if (trimmed.isBlank()) throw ApiException.InvalidRequest("friend name must not be blank")

                val id = counterpartyService.resolveOrCreate(
                    userId = LedgerService.USER_ID,
                    rawName = trimmed,
                    displayName = trimmed,
                    aliasSource = "manual",
                ) ?: throw ApiException.NotFound("counterparty", -1L)  // resolveOrCreate found nothing usable

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

/**
 * Dedupe for the Add-Friend contact picker. The ContactsProvider returns one
 * row per raw-contact phone entry, and a person merged from device + Google +
 * WhatsApp accounts carries the same number once per copy — each in that
 * copy's own format ("+91 91217 95607" vs "+919121795607"). Collapse on the
 * same last-10 key the contacts cache uses (Text.normalizePhone) so the
 * picker shows one row per real number; different names sharing a number
 * stay separate rows. Unnormalizable phones (too short to be a real number,
 * also skipped by the contacts cache) are dropped.
 */
internal fun List<Contact>.distinctByPhone(): List<Contact> {
    val seen = LinkedHashMap<Pair<String, String>, Contact>()
    for (c in this) {
        val phone = Text.normalizePhone(c.phoneNumber)
        if (phone.isEmpty()) continue
        seen.putIfAbsent(phone to c.name.trim().lowercase(), c)
    }
    return seen.values.toList()
}
