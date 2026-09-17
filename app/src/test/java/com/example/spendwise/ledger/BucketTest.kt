package com.example.spendwise.ledger

import com.example.spendwise.ledger.api.CreateTransactionRequest
import com.example.spendwise.ledger.api.LineSpec
import com.example.spendwise.ledger.api.TransactionKind
import com.example.spendwise.ledger.api.TransactionSource
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.service.LedgerService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Buckets are plain child accounts (Rust Option A): funded by a transfer from
 * the parent account, valued by the same computed-balance read path.
 */
class BucketTest : BackendTestBase() {

    @Test
    fun `allocation is a transfer to the bucket child and the bucket reads its balance`() = runTest {
        val accountId = newAccount("bank")
        val bucketId = newBucket(accountId, targetPaise = 200_000)

        val alloc = ledger.ingestTransfer(
            amountPaise = 200_000,
            fromAccountId = accountId,
            toAccountId = bucketId,
            occurredOn = 0L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
        )
        assertEquals(TransactionKind.TRANSFER, alloc.kind)
        assertEquals(bucketId, alloc.toAccountId)

        // The funding account is down by the allocation; the bucket child
        // carries it. Net worth (sum of all asset accounts) is unchanged.
        assertEquals(-200_000L, ledger.accountBalance(accountId))
        assertEquals(200_000L, ledger.accountBalance(bucketId))
    }

    @Test
    fun `spending from the funding account with a matching de-allocation keeps the bucket honest`() = runTest {
        val accountId = newAccount("bank")
        val bucketId = newBucket(accountId)
        ledger.ingestTransfer(
            amountPaise = 100_000,
            fromAccountId = accountId,
            toAccountId = bucketId,
            occurredOn = 0L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
        )

        // Spend 30_000 paid by the bank, then de-allocate the same amount
        // from the bucket back onto it (UI policy: spend = transfer down).
        ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 10L,
                lines = listOf(
                    LineSpec(-30_000, accountId = accountId),
                    LineSpec(30_000, accountId = newCategory("Travel")),
                ),
            )
        )
        val dealloc = ledger.ingestTransfer(
            amountPaise = 30_000,
            fromAccountId = bucketId,
            toAccountId = accountId,
            occurredOn = 10L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
        )
        assertEquals(TransactionKind.TRANSFER, dealloc.kind)

        // bank: -100_000 (allocation) - 30_000 (spend) + 30_000 (de-allocation)
        assertEquals(-100_000L, ledger.accountBalance(accountId))
        assertEquals(70_000L, ledger.accountBalance(bucketId))
    }

    @Test
    fun `unfunded bucket reads zero`() = runTest {
        val accountId = newAccount("bank")
        val bucketId = newBucket(accountId)
        assertEquals(0L, ledger.accountBalance(bucketId))
    }

    @Test
    fun `opening balance for a credit card books as money owed`() = runTest {
        val cardId = newAccount("card", accountClass = LedgerService.CLASS_LIABILITY)

        val opening = ledger.recordOpeningBalance(cardId, 45_000, onDate = 0L)!!
        assertEquals(TransactionKind.OPENING, opening.kind)
        assertEquals(45_000L, ledger.accountBalance(cardId)) // owed

        // The (merged) equity pot took the contra.
        val eq = db.AccountDao().findSystemBySubtype("equity")!!
        assertEquals(45_000L, ledger.accountBalance(eq.id))
    }
}