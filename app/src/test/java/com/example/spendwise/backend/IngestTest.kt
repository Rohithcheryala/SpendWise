package com.example.spendwise.backend

import com.example.spendwise.backend.api.Direction
import com.example.spendwise.backend.api.EntryKind
import com.example.spendwise.backend.api.EntryStatus
import com.example.spendwise.backend.api.IngestRequest
import com.example.spendwise.backend.service.LedgerService
import com.example.spendwise.backend.service.Text
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ingestion write path: intent-driven contra lines, provenance + dedupe
 * hashing, and the SMS->ledger bridge rules from the old server.
 */
class IngestTest : BackendTestBase() {

    @Test
    fun `debit sms books against Unclassified expense and stays in buffer`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))

        val view = ledger.ingest(
            IngestRequest(
                amountPaise = 125_000,
                direction = Direction.OUT,
                occurredOn = 0L,
                accountId = bank,
                counterpartyId = null,
                intent = com.example.spendwise.backend.api.Intent.EXPENSE,
                rawText = "Rs.1,250 debited from A/c XX1234",
                dedupeHash = Text.smsHash("SBIINB", "Rs.1,250 debited from A/c XX1234"),
            )
        )

        assertEquals(EntryStatus.BUFFER, view.status)
        assertEquals(0L, ledger.accountBalance(bank)) // buffer doesn't move balances

        // Contra line landed on the expense-side system category.
        val lines = db.EntryLineDao().getByEntryList(view.id)
        val contra = lines.first { it.categoryId != null }
        val contraCat = db.CategoryDao().getById(contra.categoryId!!)!!
        assertEquals(LedgerService.KIND_EXPENSE, contraCat.kind)
        assertEquals("Unclassified", contraCat.name)

        // Provenance stored for the audit trail.
        assertNotNull(db.EntryProvenanceDao().getByEntry(view.id)!!.rawText)
    }

    @Test
    fun `credit sms contra lands on Uncategorized income`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
        val view = ledger.ingest(
            IngestRequest(
                amountPaise = 350_000,
                direction = Direction.IN,
                occurredOn = 0L,
                accountId = bank,
                counterpartyId = null,
                intent = com.example.spendwise.backend.api.Intent.INCOME,
                status = EntryStatus.CONFIRMED,
            )
        )
        val lines = db.EntryLineDao().getByEntryList(view.id)
        val contraCat = db.CategoryDao().getById(lines.first { it.categoryId != null }.categoryId!!)!!
        assertEquals("Uncategorized income", contraCat.name)
        assertEquals(LedgerService.KIND_INCOME, contraCat.kind)
        assertEquals(350_000L, ledger.accountBalance(bank))
    }

    @Test
    fun `same dedupe hash resolves to the same entry (idempotent ingestion)`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
        val hash = Text.smsHash("SBIINB", "debited")

        val first = ledger.ingest(
            IngestRequest(
                amountPaise = 100_00, direction = Direction.OUT, occurredOn = 0L,
                accountId = bank, counterpartyId = null,
                intent = com.example.spendwise.backend.api.Intent.EXPENSE, dedupeHash = hash,
            )
        )
        assertEquals(first.id, ledger.findEntryByDedupeHash(hash)!!.id)
        assertNull(ledger.findEntryByDedupeHash("nope"))

        // A second message with the same hash cannot double-book: the unique
        // index on entry_provenance.dedupe_hash rejects it.
        val threw = try {
            ledger.ingest(
                IngestRequest(
                    amountPaise = 100_00, direction = Direction.OUT, occurredOn = 0L,
                    accountId = bank, counterpartyId = null,
                    intent = com.example.spendwise.backend.api.Intent.EXPENSE, dedupeHash = hash,
                )
            )
            false
        } catch (_: Exception) {
            true
        }
        assert(threw)
    }

    @Test
    fun `atm withdrawal ingests as a two-legged transfer bank to cash`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
        val cash = db.AccountDao().insert(newAccount("cash", kind = "cash"))

        val view = ledger.ingestTransfer(
            amountPaise = 200_00,
            fromAccountId = bank,
            toAccountId = cash,
            occurredOn = 0L,
            status = EntryStatus.CONFIRMED,
            balanceAfterPaise = 8_000_00,
        )

        assertEquals(EntryKind.TRANSFER, view.kind)
        assertEquals(-200_00, ledger.accountBalance(bank))
        assertEquals(200_00, ledger.accountBalance(cash))
        assertEquals(8_000_00L, view.balanceAfterPaise)
    }

    @Test
    fun `transfer with identical legs is rejected`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
        expectApiError("transfer legs must differ") {
            ledger.ingestTransfer(100_00, bank, bank, occurredOn = 0L)
        }
    }
}