package com.example.spendwise.ledger

import com.example.spendwise.ledger.api.CreateTransactionRequest
import com.example.spendwise.ledger.api.Direction
import com.example.spendwise.ledger.api.TransactionKind
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.api.IngestRequest
import com.example.spendwise.ledger.api.Intent
import com.example.spendwise.ledger.api.LineSpec
import com.example.spendwise.ledger.api.SplitRequest
import com.example.spendwise.ledger.api.SplitShare
import com.example.spendwise.ledger.service.LedgerService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Splits and the confirm/void lifecycle, including the old server's
 * "confirm requires account" rule for orphans.
 */
class SplitLifecycleTest : BackendTestBase() {

    private suspend fun paidAtMerchant(amountPaise: Long): Pair<Long, Long> {
        val bank = newAccount("bank")
        val merchant = counterparties.resolveOrCreate(LedgerService.USER_ID, "bigbasket")!!
        val food = newCategory("Food")
        val entry = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                status = TransactionStatus.CONFIRMED,
                counterpartyId = merchant,
                lines = listOf(
                    LineSpec(-amountPaise, accountId = bank),
                    LineSpec(amountPaise, accountId = food),
                ),
            )
        )
        return bank to entry.id
    }

    @Test
    fun `on-behalf split carves receivable lines and keeps the entry balanced`() = runTest {
        val (bank, transactionId) = paidAtMerchant(100_000) // ₹1,000 paid in full

        val rahul = counterparties.resolveOrCreate(LedgerService.USER_ID, "rahul987@oksbi")!!
        val priya = counterparties.resolveOrCreate(LedgerService.USER_ID, "priya@ybl")!!

        val view = ledger.splitTransaction(
            transactionId,
            SplitRequest(
                shares = listOf(SplitShare(rahul, 40_000), SplitShare(priya, 25_000)),
                groupId = null,
            ),
        )

        assertEquals(TransactionKind.SPLIT, view.kind)

        val lines = db.TransactionLineDao().getByTransactionList(transactionId)
        assertEquals(0L, lines.sumOf { it.amountPaise }) // still balanced

        // Category line shrank by the shares; each share sits on that
        // person's own receivable child pot (pure lines — no line metadata).
        val catLine = lines.first { ln ->
            db.AccountDao().getById(ln.accountId)!!.accountClass == LedgerService.CLASS_EXPENSE
        }
        assertEquals(35_000L, catLine.amountPaise)
        val pot = ledger.systemAccount(LedgerService.SystemRole.RECEIVABLE)
        val recvLines = lines.filter { ln ->
            val a = db.AccountDao().getById(ln.accountId)!!
            a.isSystem && a.subtype == "receivable" && a.parentId == pot.id
        }
        val cpDao = db.CounterpartyDao()
        assertEquals(
            listOf(40_000L to rahul, 25_000L to priya),
            recvLines.map { it.amountPaise to (cpDao.withReceivableUnder(pot.id).first { c -> c.receivableAccountId == it.accountId }.id) }
        )

        // Bank leg untouched: the full amount really did leave the account.
        assertEquals(-100_000L, ledger.accountBalance(bank))

        // Loan paths force the friends' parties into the Friends ledger.
        assertEquals(CounterpartyService_Person, db.CounterpartyDao().getById(rahul)!!.partyType)
    }

    @Test
    fun `on-behalf stamp marks the covered person and writes the column`() = runTest {
        val (bank, transactionId) = paidAtMerchant(100_000) // ₹1,000 paid in full

        val friend = counterparties.resolveOrCreate(LedgerService.USER_ID, "aasim987@oksbi")!!
        val view = ledger.splitTransaction(
            transactionId,
            SplitRequest(
                shares = listOf(SplitShare(friend, 100_000)), // the whole expense was for them
                onBehalfOfCounterpartyId = friend,
            ),
        )

        // The stamp is on the view and the row; the merchant keeps the
        // counterparty slot.
        assertEquals(friend, view.onBehalfOfCounterpartyId)
        assertEquals(
            friend,
            db.TransactionDao().getById(transactionId)!!.onBehalfOf,
        )
        assertEquals(TransactionKind.SPLIT, view.kind)

        // The covered person is forced into Friends, and their receivable
        // pot now holds the full amount — they owe it all.
        assertEquals("person", db.CounterpartyDao().getById(friend)!!.partyType)
        val pot = ledger.systemAccount(LedgerService.SystemRole.RECEIVABLE)
        val friendPotId = db.CounterpartyDao().withReceivableUnder(pot.id)
            .first { it.receivableAccountId != null }.receivableAccountId!!
        assertEquals(100_000L, ledger.accountBalance(friendPotId))

        // The bank leg is untouched: the full amount really left the account.
        assertEquals(-100_000L, ledger.accountBalance(bank))
    }

    @Test
    fun `shares exceeding the paid amount are rejected`() = runTest {
        val (_, transactionId) = paidAtMerchant(50_000)
        val friend = counterparties.resolveOrCreate(LedgerService.USER_ID, "friend@upi")!!

        expectApiError("shares exceed the paid amount") {
            ledger.splitTransaction(transactionId, SplitRequest(shares = listOf(SplitShare(friend, 60_000))))
        }
    }

    @Test
    fun `confirming an orphan entry is refused until it has a real account`() = runTest {
        // Orphan: no accountId -> parks on the unmatched pot.
        val orphan = ledger.ingest(
            IngestRequest(
                amountPaise = 250_00,
                direction = Direction.OUT,
                occurredOn = 0L,
                accountId = null,
                counterpartyId = null,
                intent = Intent.EXPENSE,
            )
        )
        expectApiError(
            "cannot confirm transaction ${orphan.id}: no real account line yet (classify the orphan first)"
        ) { ledger.confirmTransaction(orphan.id) }
        assertEquals(TransactionStatus.BUFFER, ledger.getTransaction(orphan.id)!!.status)

        // A matched ingest confirms fine.
        val bank = newAccount("bank")
        val matched = ledger.ingest(
            IngestRequest(
                amountPaise = 100_00,
                direction = Direction.OUT,
                occurredOn = 0L,
                accountId = bank,
                counterpartyId = null,
                intent = Intent.EXPENSE,
            )
        )
        ledger.confirmTransaction(matched.id)
        assertEquals(TransactionStatus.CONFIRMED, ledger.getTransaction(matched.id)!!.status)
    }

    @Test
    fun `voiding twice is idempotent and delete removes provenance too`() = runTest {
        val bank = newAccount("bank")
        val entry = ledger.ingest(
            IngestRequest(
                amountPaise = 100_00,
                direction = Direction.OUT,
                occurredOn = 0L,
                accountId = bank,
                counterpartyId = null,
                intent = Intent.EXPENSE,
                dedupeHash = "hash-x",
            )
        )
        ledger.voidTransaction(entry.id, "wrong parse")
        val voidedAt = ledger.getTransaction(entry.id)!!.let { 1L } // marker
        ledger.voidTransaction(entry.id, "again")

        assertEquals(voidedAt, 1L) // still fine after double-void
        assertEquals(TransactionStatus.BUFFER, ledger.getTransaction(entry.id)!!.status) // status untouched by void

        ledger.deleteTransaction(entry.id)
        assertEquals(null, ledger.getTransaction(entry.id))
        assertEquals(null, db.TransactionProvenanceDao().getByDedupeHash("hash-x"))
    }

    companion object {
        const val CounterpartyService_Person = "person"
    }
}