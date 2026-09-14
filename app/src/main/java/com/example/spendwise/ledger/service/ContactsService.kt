package com.example.spendwise.ledger.service

import com.example.spendwise.data.database.dao.ContactDao
import com.example.spendwise.data.database.dao.CounterpartyAliasDao
import com.example.spendwise.data.database.dao.CounterpartyDao

/**
 * Contact-name resolution — port of the old server's services/contacts.py.
 *
 * NOTE: intentionally NOT @Inject-constructible. It is provided explicitly by
 * BackendModule (which pulls the Room DAO off AppDatabase). Keeping this plain
 * avoids Dagger having to resolve the brand-new Room DAO type in its
 * InjectProcessingStep (a KSP2 multi-round resolution order issue).
 *
 * Design (unchanged from the server, and deliberate): the contacts table is a
 * CACHE of the device address book keyed by last-10 digits. Counterparties are
 * NEVER foreign-keyed to contacts. The link is soft:
 *   - automatic: a phone-prefixed handle (`9876543210@okaxis`) in ingestion
 *     text resolves through this cache to a saved name, used as the friendly
 *     display on first creation; the raw handle stays as the alias.
 *   - manual: linking a party to a contact = set its display name + record the
 *     phone as an alias. Survives re-syncs because nothing points AT the
 *     contact row.
 */
class ContactsService(
    private val contactDao: ContactDao,
    private val counterpartyDao: CounterpartyDao,
    private val aliasDao: CounterpartyAliasDao,
    private val counterparties: CounterpartyService,
) {

    /** One synced address-book entry, already normalized by the caller or here. */
    data class SyncedContact(
        val phone: String,
        val name: String,
        val photoUri: String? = null,
    )

    /**
     * Replace the whole cache with [items] (device ContactsProvider read).
     * Normalizes phones, dedupes per number, upserts so unchanged numbers
     * keep their row. Returns count stored.
     */
    suspend fun replaceAll(items: List<SyncedContact>): Int {
        val seen = LinkedHashMap<String, ContactRow>()
        for (item in items) {
            val p = Text.normalizePhone(item.phone)
            if (p.isEmpty() || item.name.isBlank()) continue
            seen.putIfAbsent(p, ContactRow(p, item.name.trim(), item.photoUri))
        }
        contactDao.clear()
        // clear+insert inside one transaction via the DAO pair would race with
        // readers; upsert-after-clear keeps it simple and correct for a cache.
        contactDao.insertAll(
            seen.values.map { (p, n, uri) ->
                com.example.spendwise.data.database.entity.ContactEntity(
                    phoneLast10 = p, displayName = n, photoUri = uri,
                )
            }
        )
        return seen.size
    }

    /**
     * Saved contact name for the first phone-prefixed handle in [text] that
     * matches the cache, else null. Handle order in the text is preserved.
     */
    suspend fun resolveContactName(text: String?): String? {
        val phones = Text.phonesIn(text)
        if (phones.isEmpty()) return null
        val byPhone = contactDao.getByPhones(phones).associateBy { it.phoneLast10 }
        for (p in phones) {
            byPhone[p]?.let { return it.displayName }
        }
        return null
    }

    /**
     * Rename existing counterparties still displayed as a raw handle to the
     * saved contact name, matched via phones in their aliases/display. Called
     * after a contacts sync. Conservative: only touches handle-looking names,
     * never human-readable ones. Returns number renamed.
     */
    suspend fun applyToExisting(): Int {
        var renamed = 0
        for (cp in counterpartyDao.listAll()) {
            if (!Text.looksLikeHandle(cp.displayName)) continue
            val aliases = aliasDao.getByCounterpartyList(cp.id)
            val text = buildString {
                append(cp.displayName)
                aliases.forEach { append(' ').append(it.aliasDisplay) }
            }
            val match = Text.phonesIn(text)
                .firstNotNullOfOrNull { contactDao.getByPhone(it) }
            if (match != null) {
                counterpartyDao.update(cp.copy(displayName = match.displayName))
                renamed++
            }
        }
        return renamed
    }

    /**
     * Manual link: the user says "this party IS this contact". Sets the
     * display name, records the phone as an alias so future matching sticks,
     * and forces party_type=person so the party lands in the Friends ledger.
     * This is the whole link — no FK, so it survives contact re-syncs and
     * device contact deletion.
     */
    suspend fun linkWithContact(counterpartyId: Long, phone: String?, name: String) {
        val cp = counterpartyDao.getById(counterpartyId)
            ?: throw com.example.spendwise.ledger.api.ApiException("counterparty $counterpartyId not found")
        if (name.isBlank()) {
            throw com.example.spendwise.ledger.api.ApiException("contact name must not be blank")
        }

        counterpartyDao.update(cp.copy(displayName = name.trim()))
        if (!phone.isNullOrBlank()) {
            counterparties.addAlias(counterpartyId, phone.trim(), source = "contact")
        }
        counterparties.ensurePartyType(counterpartyId, CounterpartyService.PARTY_PERSON)
    }

    private data class ContactRow(val phone: String, val name: String, val photoUri: String?)
}