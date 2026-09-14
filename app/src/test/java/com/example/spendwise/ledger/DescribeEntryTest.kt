package com.example.spendwise.ledger

import com.example.spendwise.ledger.api.CreateTransactionRequest
import com.example.spendwise.ledger.api.Direction
import com.example.spendwise.ledger.api.TransactionKind
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.api.IngestRequest
import com.example.spendwise.ledger.api.LineSpec
import com.example.spendwise.ledger.service.LedgerService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * describe_entry classification: the UI never does accounting — the backend
 * derives kind/direction/amount from the raw lines. Precedence ported 1:1 from
 * ledger.py.
 */
class DescribeEntryTest : BackendTestBase() {

    @Test
    fun `single account plus single expense category reads as expense`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
        val food = db.CategoryDao().insert(newCategory("Food"))

        val view = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                lines = listOf(LineSpec(-450_00, accountId = bank), LineSpec(450_00, categoryId = food)),
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
        val bank = db.AccountDao().insert(newAccount("bank"))
        val food = db.CategoryDao().insert(newCategory("Food"))
        val fun_ = db.CategoryDao().insert(newCategory("Fun"))

        val view = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                lines = listOf(
                    LineSpec(-1000, accountId = bank),
                    LineSpec(600, categoryId = food),
                    LineSpec(400, categoryId = fun_),
                ),
            )
        )
        // Multi-sided: category is null so the client renders the lines.
        assertNull(view.categoryId)
        assertEquals(TransactionKind.EXPENSE, view.kind)
    }

    @Test
    fun `two account legs read as a transfer with a destination`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
        val cash = db.AccountDao().insert(newAccount("cash", kind = "cash"))

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
    fun `bucket self-transfer reads as an allocation`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
        val bucket = db.BucketDao().insert(newBucket(bank))

        val view = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                note = "Bucket opening allocation",
                lines = listOf(
                    LineSpec(-50_000, accountId = bank),
                    LineSpec(50_000, accountId = bank, bucketId = bucket),
                ),
            )
        )
        assertEquals(TransactionKind.ALLOCATION, view.kind)
        assertEquals(bucket, view.bucketId)
    }

    @Test
    fun `ingested loan reads as LOAN against the receivable pot`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
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
        val loansPot = db.AccountDao().getBySlug("sys-loans-${LedgerService.USER_ID}")!!
        assertEquals(0L, ledger.accountBalance(loansPot.id)) // system pot itself is 'asset'
        val recvLines = db.EntryLineDao().getByEntryList(view.id).filter { it.accountId == loansPot.id }
        assertEquals(listOf(500_00L), recvLines.map { it.amountPaise })
    }

    @Test
    fun `orphan ingest parks money on the unmatched pot with no visible account`() = runTest {
        val food = db.CategoryDao().insert(newCategory("Food"))

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

        // Balances count only confirmed entries, so the buffered orphan hasn't
        // moved anything yet.
        val unmatchedPot = db.AccountDao().getBySlug("sys-unmatched-${LedgerService.USER_ID}")!!
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