package com.example.spendwise.backend

import com.example.spendwise.backend.api.Direction
import com.example.spendwise.backend.api.EntryStatus
import com.example.spendwise.backend.api.Intent
import com.example.spendwise.backend.service.ContactsService
import com.example.spendwise.backend.service.IngestionService
import com.example.spendwise.backend.service.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsServiceTest : BackendTestBase() {

    @Test
    fun normalizePhone_stripsCountryCodeAndSeparators() {
        assertEquals("9876543210", Text.normalizePhone("+91 98765 43210"))
        assertEquals("9876543210", Text.normalizePhone("0-98765-43210"))
        assertEquals("9988776655", Text.normalizePhone("+919988776655"))
        assertEquals("", Text.normalizePhone("12345"))
        assertEquals("", Text.normalizePhone(null))
        assertEquals("", Text.normalizePhone(""))
    }

    @Test
    fun phonesIn_findsOnlyPhonePrefixedHandles_inOrderDeduped() {
        val text = "paid to 9876543210@okybl from rahul@oksbi, then 9876543210@upi and 9000090000@apl"
        assertEquals(listOf("9876543210", "9000090000"), Text.phonesIn(text))
    }

    @Test
    fun phonesIn_ignoresPlainNamesAndRefs() {
        assertTrue(Text.phonesIn("NEFT from Rahul Sharma ref 123456").isEmpty())
        assertTrue(Text.phonesIn("name@paytm no handle here").isEmpty())
    }

    @Test
    fun looksLikeHandle_distinguishesHandlesFromNames() {
        assertTrue(Text.looksLikeHandle("9876543210@okaxis"))
        assertTrue(Text.looksLikeHandle("qwe-rty@ptys"))
        assertTrue(!Text.looksLikeHandle("Rahul Sharma"))
        assertTrue(!Text.looksLikeHandle(null))
    }

    private suspend fun syncContacts(vararg pairs: Pair<String, String>) {
        contacts.replaceAll(pairs.map { ContactsService.SyncedContact(it.first, it.second) })
    }

    @Test
    fun replaceAll_normalizesAndDedupes_skipsBlankNames() = backendTest {
        val n = contacts.replaceAll(
            listOf(
                ContactsService.SyncedContact("+91 98765 43210", "Rahul"),
                ContactsService.SyncedContact("9876543210", "Rahul Duplicate"),
                ContactsService.SyncedContact("9000090000", ""),
            )
        )
        assertEquals(1, n)
        assertEquals("Rahul", db.ContactDao().getByPhone("9876543210")?.displayName)
        assertNull(db.ContactDao().getByPhone("9000090000"))
    }

    @Test
    fun replaceAll_secondSyncReplaces_staleEntriesDisappear() = backendTest {
        syncContacts("9876543210" to "Rahul", "9000090000" to "Old Friend")
        syncContacts("9876543210" to "Rahul Sharma")
        assertNull(db.ContactDao().getByPhone("9000090000"))
        assertEquals("Rahul Sharma", db.ContactDao().getByPhone("9876543210")?.displayName)
    }

    @Test
    fun resolveContactName_hitsCache_forPhoneHandle_only() = backendTest {
        syncContacts("9876543210" to "Rahul Sharma")
        assertEquals("Rahul Sharma", contacts.resolveContactName("paid to 9876543210@okaxis"))
        assertNull(contacts.resolveContactName("paid to rahul@okaxis"))
        assertNull(contacts.resolveContactName(null))
    }

    private fun sms(rawText: String) = IngestionService.SmsParseFacts(
        sender = "SBIINB",
        bank = null,
        last4 = null,
        smsAccountKind = null,
        amountPaise = 500_00L,
        direction = Direction.OUT,
        rawText = rawText,
        occurredOn = 0L,
    )

    @Test
    fun ingest_usesContactNameAsDisplay_handleStaysAlias() = backendTest {
        syncContacts("9876543210" to "Rahul Sharma")
        val result = ingestion.ingestSms(sms("Sent Rs500 to 9876543210@okax. UPI/5432101"))
        val parsed = result as IngestionService.SmsIngestResult.Parsed
        // The counterparty rides on the entry, not the contra line.
        val entry = ledger.getEntry(parsed.entryId)!!
        val cp = db.CounterpartyDao().getById(entry.counterpartyId!!)!!
        assertEquals("Rahul Sharma", cp.displayName)
        // The handle itself was learned as an alias.
        val vpa = Text.extractVpa("Sent Rs500 to 9876543210@okax. UPI/5432101")!!
        assertEquals(
            cp.id,
            db.CounterpartyAliasDao().findByNormalizedAlias(vpa.trimEnd('.').lowercase())
                ?.counterpartyId
                ?: db.CounterpartyAliasDao().findByNormalizedAlias(vpa.lowercase())
                    ?.counterpartyId
        )
    }

    @Test
    fun ingest_withoutSyncedContact_fallsBackToRawSlug() = backendTest {
        val result = ingestion.ingestSms(sms("Sent Rs500 to 9876543210@okax. UPI/5432102"))
        val entryId = (result as IngestionService.SmsIngestResult.Parsed).entryId
        val entry = ledger.getEntry(entryId)!!
        val cp = db.CounterpartyDao().getById(entry.counterpartyId!!)!!
        // Falls back to the raw VPA as the display name.
        assertEquals(
            Text.extractVpa("Sent Rs500 to 9876543210@okax. UPI/5432102"),
            cp.displayName
        )
    }

    private suspend fun loanReq(amountPaise: Long, direction: Direction, cpId: Long) {
        ledger.ingest(
            com.example.spendwise.backend.api.IngestRequest(
                amountPaise = amountPaise,
                direction = direction,
                occurredOn = 0L,
                intent = if (direction == Direction.OUT) Intent.LOAN else Intent.LOAN_REPAYMENT,
                counterpartyId = cpId,
                status = EntryStatus.CONFIRMED,
            )
        )
    }

    @Test
    fun friendsOutstanding_netsPerPerson_settledOmitted_sortedMostFirst() = backendTest {
        val alice = counterparties.resolveOrCreate(1L, "Alice")!!
        val bob = counterparties.resolveOrCreate(1L, "Bob")!!
        val carol = counterparties.resolveOrCreate(1L, "Carol")!!

        loanReq(1000_00L, Direction.OUT, alice)
        loanReq(400_00L, Direction.IN, alice)
        loanReq(250_00L, Direction.IN, bob)
        loanReq(300_00L, Direction.OUT, carol)
        loanReq(300_00L, Direction.IN, carol)

        val friends = ledger.friendsOutstanding()
        assertEquals(2, friends.size)
        assertEquals(alice, friends[0].counterpartyId)
        assertEquals(600_00L, friends[0].netPaise)
        assertEquals(bob, friends[1].counterpartyId)
        assertEquals(-250_00L, friends[1].netPaise)
    }
}