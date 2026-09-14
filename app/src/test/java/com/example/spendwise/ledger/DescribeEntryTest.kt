package com.example.spendwise.ledger

import com.example.spendwise.ledger.api.CreateTransactionRequest
import com.example.spendwise.ledger.api.Direction
import com.example.spendwise.ledger.api.TransactionKind
import com.example.spendwise.ledger.api.TransactionSource
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.api.IngestRequest
import com.example.spendwise.ledger.api.LineSpec
import com.example.spendwise.ledger.service.LedgerService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * describe_transaction classification: the UI never does accounting — the
 * backend derives kind/direction/amount from the raw lines.
 */
class DescribeEntryTest : BackendTestBase() {

    @Test
    fun `single account plus single expense category reads as expense`() = runTest {
        val bank = newAccount("bank")
        val food = newCategory("Food")

        val view = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                lines = listOf(LineSpec(-450_00, accountId = bank), LineSpec(450_00, accountId = food)),
            )
        )

        assertEquals(TransactionKind.EXPENSE, view.kind)
        assertEquals(Direction.OUT, view.direction)
        assertEquals(450_00, view.amountPaise)
        assertEquals(bank, view.accountId)
        assertEquals(food, view.categoryId)
    }

    @Test
    fun `multi-category expense hides its category and shows OTHER-side lines`() = runTest {
        val bank = newAccount("bank")
        val food = newCategory("Food")
        val fun_ = newCategory("Fun")

        val view = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                lines = listOf(
                    LineSpec(-1000, accountId = bank),
                    LineSpec(600, accountId = food),
                    LineSpec(400, accountId = fun_),
                ),
            )
        )
        // Multi-sided: category is null so the client renders the lines.
        assertNull(view.categoryId)
        assertEquals(TransactionKind.EXPENSE, view.kind)
    }

    @Test
    fun `two account legs read as a transfer with a destination`() = runTest {
        val bank = newAccount("bank")
        val cash = newAccount("cash", subtype = "cash")

        val view = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                lines = listOf(LineSpec(-200_00, accountId = bank), LineSpec(200_00, accountId = cash)),
            )
        )
        assertEquals(TransactionKind.TRANSFER, view.kind)
        assertEquals(bank, view.accountId)
        assertEquals(cash, view.toAccountId)
        assertNull(view.categoryId)
    }

    @Test
    fun `transfer to a bucket child names the bucket`() = runTest {
        val bank = newAccount("bank")
        val bucket = newBucket(bank)

        val view = ledger.ingestTransfer(
            amountPaise = 50_000,
            fromAccountId = bank,
            toAccountId = bucket,
            occurredOn = 0L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
        )
        assertEquals(TransactionKind.TRANSFER, view.kind)
        assertEquals(bucket, view.bucketId)
    }

    @Test
    fun `ingested loan reads as LOAN against the receivable pot`() = runTest {
        val bank = newAccount("bank")
        val friend = counterparties.resolveOrCreate(LedgerService.USER_ID, "rahul@upi")!!

        val view = ledger.ingest(
            IngestRequest(
                amountPaise = 500_00,
                direction = Direction.OUT,
                occurredOn = 0L,
                accountId = bank,
                counterpartyId = friend,
                intent = com.example.spendwise.ledger.api.Intent.LOAN,
            )
        )
        assertEquals(TransactionKind.LOAN, view.kind)

        // The receivable pot now carries +500 (money lent out).
        val loansPot = db.AccountDao().findSystemBySubtype("receivable")!!
        assertEquals(0L, ledger.accountBalance(loansPot.id)) // system pot itself is 'asset'
        val recvLines = db.TransactionLineDao().getByTransactionList(view.id).filter { it.accountId == loansPot.id }
        assertEquals(listOf(500_00L), recvLines.map { it.amountPaise })
    }

    @Test
    fun `orphan ingest parks money on the unmatched pot with no visible account`() = runTest {
        val view = ledger.ingest(
            IngestRequest(
                amountPaise = 199_00,
                direction = Direction.OUT,
                occurredOn = 0L,
                accountId = null, // no account matched yet
                counterpartyId = null,
                intent = com.example.spendwise.ledger.api.Intent.EXPENSE,
            )
        )

        assertEquals(TransactionKind.EXPENSE, view.kind)
        assertEquals(199_00, view.amountPaise)
        assertNull(view.accountId) // surfaced as "needs an account"

        // Balances count only confirmed transactions, so the buffered orphan hasn't
        // moved anything yet.
        val unmatchedPot = db.AccountDao().findSystemBySubtype("unmatched")!!
        assertEquals(0L, ledger.accountBalance(unmatchedPot.id))

        // A statement-imported (already-proven) orphan books straight through.
        val provenOrphan = ledger.ingest(
            IngestRequest(
                amountPaise = 80_00,
                direction = Direction.OUT,
                occurredOn = 0L,
                accountId = null,
                counterpartyId = null,
                intent = com.example.spendwise.ledger.api.Intent.RECONCILIATION,
                status = TransactionStatus.CONFIRMED,
            )
        )
        assertEquals(TransactionKind.RECONCILIATION, provenOrphan.kind)
        assertEquals(-80_00, ledger.accountBalance(unmatchedPot.id))
    }
}