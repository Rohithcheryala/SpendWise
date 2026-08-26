package com.example.spendwise.backend

import com.example.spendwise.backend.api.CreateEntryRequest
import com.example.spendwise.backend.api.Direction
import com.example.spendwise.backend.api.EntryKind
import com.example.spendwise.backend.api.EntrySource
import com.example.spendwise.backend.api.EntryStatus
import com.example.spendwise.backend.api.Intent
import com.example.spendwise.backend.api.LineSpec
import com.example.spendwise.backend.service.IngestionService
import com.example.spendwise.backend.service.LedgerService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SMS -> ledger bridge: kind-aware account matching, orphan parking and
 * retroactive claim, dedupe, and the QR<->SMS merge. Ported from the old
 * server's routers/sms.py.
 */
class IngestionServiceTest : BackendTestBase() {

    private suspend fun bankWithIdentifiers(
        slug: String,
        bank: String,
        last4: String,
        kind: String = "available",
        identifierKind: String = IngestionService.ID_KIND_ACCOUNT,
    ): Long {
        val id = db.AccountDao().insert(newAccount(slug, kind = kind).copy(bank = bank))
        db.AccountIdentifierDao().insert(
            com.example.spendwise.data.database.entity.AccountIdentifierEntity(
                accountId = id, value = last4, kind = identifierKind, createdAt = 0,
            )
        )
        return id
    }

    private fun sms(
        raw: String,
        last4: String,
        amountPaise: Long = 100_00L,
        direction: Direction = Direction.OUT,
        smsKind: String = "savings",
        idKinds: Set<String>? = null,
        bank: String = "HDFC",
    ) = IngestionService.SmsParseFacts(
        sender = "SBIINB",
        bank = bank,
        last4 = last4,
        smsAccountKind = smsKind,
        idKinds = idKinds,
        amountPaise = amountPaise,
        direction = direction,
        counterpartyRaw = "SWIGGY",
        rawText = raw,
        occurredOn = 0L,
    )

    @Test
    fun `sms matching bank+last4 attaches to the account`() = runTest {
        val accountId = bankWithIdentifiers("hdfc", "HDFC Bank", "4921")

        val result = ingestion.ingestSms(sms("debited from A/c XX4921", "4921"))

        assertTrue(result is IngestionService.SmsIngestResult.Parsed)
        assertEquals(accountId, (result as IngestionService.SmsIngestResult.Parsed).accountId)
        // Buffer entries don't move balances yet.
        assertEquals(0L, ledger.accountBalance(accountId))
    }

    @Test
    fun `unknown last4 parks the sms as an orphan on the unmatched pot`() = runTest {
        bankWithIdentifiers("hdfc", "HDFC Bank", "4921")

        val result = ingestion.ingestSms(sms("debited from A/c XX9999", "9999"))

        val parsed = result as IngestionService.SmsIngestResult.Parsed
        assertNull(parsed.accountId)
        val view = ledger.getEntry(parsed.entryId)!!
        assertEquals(EntryKind.EXPENSE, view.kind)
        assertNull(view.accountId) // surfaced as needing classification
    }

    @Test
    fun `credit-card sms never attaches to a savings account`() = runTest {
        bankWithIdentifiers("hdfc", "HDFC Bank", "7712")

        val result = ingestion.ingestSms(
            sms("card ending 7712 spent", "7712", smsKind = "credit_card")
        )
        assertNull((result as IngestionService.SmsIngestResult.Parsed).accountId)
    }

    @Test
    fun `card-channel sms does not match an account-number identifier`() = runTest {
        bankWithIdentifiers("axis", "Axis Bank", "1345") // identifier kind = 'account'

        val cardChannel = ingestion.ingestSms(
            sms(
                "spent using Debit Card XX1345", "1345",
                idKinds = setOf(IngestionService.ID_KIND_CARD), bank = "Axis",
            )
        ) as IngestionService.SmsIngestResult.Parsed
        assertNull(cardChannel.accountId)

        // Same last-4 matches once the SMS comes through the account channel.
        val accountChannel = ingestion.ingestSms(
            sms("UPI from A/c XX1345", "1345", bank = "Axis")
        ) as IngestionService.SmsIngestResult.Parsed
        assertNotNull(accountChannel.accountId)
    }

    @Test
    fun `same sms body ingested twice dedupes to one entry`() = runTest {
        bankWithIdentifiers("hdfc", "HDFC Bank", "4921")
        val body = "Rs.500 debited from A/c XX4921"

        val first = ingestion.ingestSms(sms(body, "4921")) as IngestionService.SmsIngestResult.Parsed
        val second = ingestion.ingestSms(sms(body, "4921"))

        assertTrue(second is IngestionService.SmsIngestResult.Duplicate)
        assertEquals(first.entryId, (second as IngestionService.SmsIngestResult.Duplicate).entryId)
    }

    @Test
    fun `orphans are re-claimed when their account is created later`() = runTest {
        // SMS arrives before any account exists.
        val orphan = ingestion.ingestSms(
            sms("Rs.250 debited from A/c XX9021 towards UPI to SWIGGY", "9021")
        ) as IngestionService.SmsIngestResult.Parsed
        assertNull(orphan.accountId)

        // User then adds the account with matching bank + last-4.
        val newAccountId = bankWithIdentifiers("hdfc-main", "HDFC Bank", "9021")

        assertEquals(1, ingestion.claimOrphansForAccount(newAccountId))
        assertEquals(newAccountId, ledger.getEntry(orphan.entryId)!!.accountId)

        // The line really moved: no account leg left on the unmatched pot.
        val unmatchedPot = ledger.systemAccount(LedgerService.SystemRole.UNMATCHED).id
        org.junit.Assert.assertFalse(
            db.EntryLineDao().getByEntryList(orphan.entryId).any { it.accountId == unmatchedPot }
        )
    }

    @Test
    fun `claim is attach-only - entries already on an account are never stolen`() = runTest {
        val existing = bankWithIdentifiers("hdfc", "HDFC Bank", "4921")
        val attached = ingestion.ingestSms(sms("A/c XX4921 debit", "4921"))
            as IngestionService.SmsIngestResult.Parsed

        // Another bank reuses the same last-4; claiming for it must not steal.
        val other = bankWithIdentifiers("icici", "ICICI Bank", "4921")
        assertEquals(0, ingestion.claimOrphansForAccount(other))
        assertEquals(existing, ledger.getEntry(attached.entryId)!!.accountId)
    }

    @Test
    fun `qr scan merges into the landing sms - confirmed, intent adopted, qr removed`() = runTest {
        val bank = bankWithIdentifiers("hdfc", "HDFC Bank", "4921")
        val merchant = counterparties.resolveOrCreate(LedgerService.USER_ID, "swiggy@ybl")!!
        val food = db.CategoryDao().insert(newCategory("Food"))

        // User scanned the QR first: deliberate category/party/note, buffer status.
        val qrView = ledger.createEntry(
            CreateEntryRequest(
                occurredOn = 0L,
                status = EntryStatus.BUFFER,
                source = EntrySource.QR_SCAN,
                counterpartyId = merchant,
                note = "lunch with team",
                tags = listOf("lunch"),
                lines = listOf(
                    LineSpec(-235_50, accountId = bank),
                    LineSpec(235_50, categoryId = food),
                ),
            )
        )

        // Then the bank SMS lands describing the same payment. The narration
        // carries the same VPA the scan resolved, so both point at one party.
        val result = ingestion.ingestSms(
            sms(
                "debited Rs.235.50 from A/c XX4921 to swiggy@ybl",
                "4921",
                amountPaise = 235_50,
            )
        ) as IngestionService.SmsIngestResult.Parsed

        assertNotNull(result.mergedQrEntryId)
        assertEquals(qrView.id, result.mergedQrEntryId)

        val merged = ledger.getEntry(result.entryId)!!
        assertEquals(EntryStatus.CONFIRMED, merged.status)
        assertEquals(merchant, merged.counterpartyId)
        assertEquals(food, merged.categoryId)
        assertEquals(bank, merged.accountId)
        assertEquals(listOf("lunch"), merged.tags)

        // The QR entry itself is gone; the money actually moved.
        assertNull(ledger.getEntry(qrView.id))
        assertEquals(-235_50L, ledger.accountBalance(bank))
    }

    // ── pure helpers ─────────────────────────────────────────────────────

    @Test
    fun `amount tolerance is about +-1 percent of the larger side`() {
        assertTrue(IngestionService.amountsMatch(10_000, 10_000, 0.01))
        assertTrue(IngestionService.amountsMatch(10_000, 9_950, 0.01)) // 0.5% under
        assertTrue(!IngestionService.amountsMatch(10_000, 9_800, 0.01)) // 2% under
        assertTrue(!IngestionService.amountsMatch(10_000, 9_800, 0.0))
    }

    @Test
    fun `default intent follows direction`() {
        assertEquals(Intent.INCOME, IngestionService.defaultIntent(Direction.IN))
        assertEquals(Intent.EXPENSE, IngestionService.defaultIntent(Direction.OUT))
    }
}