package com.example.spendwise.ledger

import com.example.spendwise.ledger.api.Direction
import com.example.spendwise.ledger.api.TransactionKind
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.api.IngestRequest
import com.example.spendwise.ledger.service.LedgerService
import com.example.spendwise.ledger.service.Text
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
        val bank = newAccount("bank")

        val view = ledger.ingest(
            IngestRequest(
                amountPaise = 125_000,
                direction = Direction.OUT,
                occurredOn = 0L,
                accountId = bank,
                counterpartyId = null,
                intent = com.example.spendwise.ledger.api.Intent.EXPENSE,
                rawText = "Rs.1,250 debited from A/c XX1234",
                dedupeHash = Text.smsHash("SBIINB", "Rs.1,250 debited from A/c XX1234"),
            )
        )

        assertEquals(TransactionStatus.BUFFER, view.status)
        // Pending-review already moves the balance — the money has left.
        assertEquals(-125_000L, ledger.accountBalance(bank))

        // Contra line landed on the expense-side system category.
        val lines = db.TransactionLineDao().getByTransactionList(view.id)
        val contra = lines.first { ln -> db.AccountDao().getById(ln.accountId)!!.isSystem }
        val contraCat = db.AccountDao().getById(contra.accountId)!!
        assertEquals(LedgerService.CLASS_EXPENSE, contraCat.accountClass)
        assertEquals("Unclassified", contraCat.name)

        // Provenance stored for the audit trail.
        assertNotNull(db.TransactionProvenanceDao().getByTransaction(view.id)!!.rawText)
    }

    @Test
    fun `credit sms contra lands on Uncategorized income`() = runTest {
        val bank = newAccount("bank")
        val view = ledger.ingest(
            IngestRequest(
                amountPaise = 350_000,
                direction = Direction.IN,
                occurredOn = 0L,
                accountId = bank,
                counterpartyId = null,
                intent = com.example.spendwise.ledger.api.Intent.INCOME,
                status = TransactionStatus.CONFIRMED,
            )
        )
        val lines = db.TransactionLineDao().getByTransactionList(view.id)
        val contraCat = db.AccountDao().getById(lines.first { ln -> db.AccountDao().getById(ln.accountId)!!.isSystem }.accountId)!!
        assertEquals("Uncategorized income", contraCat.name)
        assertEquals(LedgerService.CLASS_INCOME, contraCat.accountClass)
        assertEquals(350_000L, ledger.accountBalance(bank))
    }

    @Test
    fun `same dedupe hash resolves to the same entry (idempotent ingestion)`() = runTest {
        val bank = newAccount("bank")
        val hash = Text.smsHash("SBIINB", "debited")

        val first = ledger.ingest(
            IngestRequest(
                amountPaise = 100_00, direction = Direction.OUT, occurredOn = 0L,
                accountId = bank, counterpartyId = null,
                intent = com.example.spendwise.ledger.api.Intent.EXPENSE, dedupeHash = hash,
            )
        )
        assertEquals(first.id, ledger.findTransactionByDedupeHash(hash)!!.id)
        assertNull(ledger.findTransactionByDedupeHash("nope"))

        // A second message with the same hash cannot double-book: the unique
        // index on transaction_provenance.dedupe_hash rejects it.
        val threw = try {
            ledger.ingest(
                IngestRequest(
                    amountPaise = 100_00, direction = Direction.OUT, occurredOn = 0L,
                    accountId = bank, counterpartyId = null,
                    intent = com.example.spendwise.ledger.api.Intent.EXPENSE, dedupeHash = hash,
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
        val bank = newAccount("bank")
        val cash = newAccount("cash", subtype = "cash")

        val view = ledger.ingestTransfer(
            amountPaise = 200_00,
            fromAccountId = bank,
            toAccountId = cash,
            occurredOn = 0L,
            status = TransactionStatus.CONFIRMED,
            balanceAfterPaise = 8_000_00,
        )

        assertEquals(TransactionKind.TRANSFER, view.kind)
        assertEquals(-200_00, ledger.accountBalance(bank))
        assertEquals(200_00, ledger.accountBalance(cash))
        assertEquals(8_000_00L, view.balanceAfterPaise)
    }

    @Test
    fun `transfer with identical legs is rejected`() = runTest {
        val bank = newAccount("bank")
        expectApiError("transfer legs must differ") {
            ledger.ingestTransfer(100_00, bank, bank, occurredOn = 0L)
        }
    }
}