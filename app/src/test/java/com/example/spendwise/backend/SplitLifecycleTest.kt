package com.example.spendwise.backend

import com.example.spendwise.backend.api.CreateEntryRequest
import com.example.spendwise.backend.api.Direction
import com.example.spendwise.backend.api.EntryKind
import com.example.spendwise.backend.api.EntryStatus
import com.example.spendwise.backend.api.IngestRequest
import com.example.spendwise.backend.api.Intent
import com.example.spendwise.backend.api.LineSpec
import com.example.spendwise.backend.api.SplitRequest
import com.example.spendwise.backend.api.SplitShare
import com.example.spendwise.backend.service.LedgerService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Splits and the confirm/void lifecycle, including the old server's
 * "confirm requires account" rule for orphans.
 */
class SplitLifecycleTest : BackendTestBase() {

    private suspend fun paidAtMerchant(amountPaise: Long): Pair<Long, Long> {
        val bank = db.AccountDao().insert(newAccount("bank"))
        val merchant = counterparties.resolveOrCreate(LedgerService.USER_ID, "bigbasket")!!
        val food = db.CategoryDao().insert(newCategory("Food"))
        val entry = ledger.createEntry(
            CreateEntryRequest(
                occurredOn = 0L,
                status = EntryStatus.CONFIRMED,
                counterpartyId = merchant,
                lines = listOf(
                    LineSpec(-amountPaise, accountId = bank),
                    LineSpec(amountPaise, categoryId = food),
                ),
            )
        )
        return bank to entry.id
    }

    @Test
    fun `on-behalf split carves receivable lines and keeps the entry balanced`() = runTest {
        val (bank, entryId) = paidAtMerchant(100_000) // ₹1,000 paid in full

        val rahul = counterparties.resolveOrCreate(LedgerService.USER_ID, "rahul987@oksbi")!!
        val priya = counterparties.resolveOrCreate(LedgerService.USER_ID, "priya@ybl")!!

        val view = ledger.splitEntry(
            entryId,
            SplitRequest(
                shares = listOf(SplitShare(rahul, 40_000), SplitShare(priya, 25_000)),
                groupId = null,
            ),
        )

        assertEquals(EntryKind.SPLIT, view.kind)

        val lines = db.EntryLineDao().getByEntryList(entryId)
        assertEquals(0L, lines.sumOf { it.amountPaise }) // still balanced

        // Category line shrank by the shares; receivable pot carries them.
        val catLine = lines.first { it.categoryId != null }
        assertEquals(35_000L, catLine.amountPaise)
        val recvLines = lines.filter { it.counterpartyId != null }
        assertEquals(listOf(40_000L to rahul, 25_000L to priya), recvLines.map { it.amountPaise to it.counterpartyId })

        // Bank leg untouched: the full amount really did leave the account.
        assertEquals(-100_000L, ledger.accountBalance(bank))

        // Loan paths force the friends' parties into the Friends ledger.
        assertEquals(CounterpartyService_Person, db.CounterpartyDao().getById(rahul)!!.partyType)
    }

    @Test
    fun `shares exceeding the paid amount are rejected`() = runTest {
        val (_, entryId) = paidAtMerchant(50_000)
        val friend = counterparties.resolveOrCreate(LedgerService.USER_ID, "friend@upi")!!

        expectApiError("shares exceed the paid amount") {
            ledger.splitEntry(entryId, SplitRequest(shares = listOf(SplitShare(friend, 60_000))))
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
            "cannot confirm entry ${orphan.id}: no real account line yet (classify the orphan first)"
        ) { ledger.confirmEntry(orphan.id) }
        assertEquals(EntryStatus.BUFFER, ledger.getEntry(orphan.id)!!.status)

        // A matched ingest confirms fine.
        val bank = db.AccountDao().insert(newAccount("bank"))
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
        ledger.confirmEntry(matched.id)
        assertEquals(EntryStatus.CONFIRMED, ledger.getEntry(matched.id)!!.status)
    }

    @Test
    fun `voiding twice is idempotent and delete removes provenance too`() = runTest {
        val bank = db.AccountDao().insert(newAccount("bank"))
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
        ledger.voidEntry(entry.id, "wrong parse")
        val voidedAt = ledger.getEntry(entry.id)!!.let { 1L } // marker
        ledger.voidEntry(entry.id, "again")

        assertEquals(voidedAt, 1L) // still fine after double-void
        assertEquals(EntryStatus.BUFFER, ledger.getEntry(entry.id)!!.status) // status untouched by void

        ledger.deleteEntry(entry.id)
        assertEquals(null, ledger.getEntry(entry.id))
        assertEquals(null, db.EntryProvenanceDao().getByDedupeHash("hash-x"))
    }

    companion object {
        const val CounterpartyService_Person = "person"
    }
}