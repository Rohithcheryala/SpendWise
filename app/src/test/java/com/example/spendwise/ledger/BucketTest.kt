package com.example.spendwise.ledger

import com.example.spendwise.ledger.api.TransactionKind
import com.example.spendwise.ledger.service.LedgerService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Buckets are sub-pots of one account: value = sum of tagged lines, funded by
 * an idempotent self-transfer allocation entry (net zero on the account).
 */
class BucketTest : BackendTestBase() {

    @Test
    fun `allocation is a net-zero self-transfer and the bucket reads its lines`() = runTest {
        val accountId = db.AccountDao().insert(
            newAccount("bank", reconciledThrough = 1_000L)
        )
        val bucketId = db.BucketDao().insert(newBucket(accountId, allocation = 200_000))

        val alloc = ledger.recordBucketAllocation(bucketId)!!
        assertEquals(TransactionKind.ALLOCATION, alloc.kind)

        // Account total unchanged (net zero); bucket now carries its baseline.
        assertEquals(0L, ledger.accountBalance(accountId))
        assertEquals(200_000L, ledger.bucketValue(bucketId))

        // Idempotent replace: editing the allocation swaps the baseline.
        db.BucketDao().update(db.BucketDao().getById(bucketId)!!.copy(manualAllocationPaise = 150_000))
        ledger.recordBucketAllocation(bucketId)
        assertEquals(150_000L, ledger.bucketValue(bucketId))
        assertNull(ledger.getTransaction(alloc.id))
    }

    @Test
    fun `bucket spending and refunds move only the sub-pot`() = runTest {
        val accountId = db.AccountDao().insert(newAccount("bank"))
        val bucketId = db.BucketDao().insert(newBucket(accountId, allocation = 100_000))
        ledger.recordBucketAllocation(bucketId)

        // Spend from within the travel kitty.
        val view = ledger.createTransaction(
            com.example.spendwise.ledger.api.CreateTransactionRequest(
                occurredOn = 10L,
                lines = listOf(
                    com.example.spendwise.ledger.api.LineSpec(-30_000, accountId = accountId, bucketId = bucketId),
                    com.example.spendwise.ledger.api.LineSpec(
                        30_000, categoryId = db.CategoryDao().insert(newCategory("Travel")),
                    ),
                ),
            )
        )
        assertEquals(TransactionKind.EXPENSE, view.kind)
        assertEquals(-30_000L, ledger.accountBalance(accountId))
        assertEquals(70_000L, ledger.bucketValue(bucketId))
    }

    @Test
    fun `zero-allocation bucket records nothing`() = runTest {
        val accountId = db.AccountDao().insert(newAccount("bank"))
        val bucketId = db.BucketDao().insert(newBucket(accountId, allocation = 0))
        assertNull(ledger.recordBucketAllocation(bucketId))
        assertEquals(0L, ledger.bucketValue(bucketId))
    }

    @Test
    fun `opening balance for a credit card books as money owed`() = runTest {
        val cardId = db.AccountDao().insert(
            newAccount("card", kind = "liability", openingBalancePaise = 45_000)
        )

        val opening = ledger.recordOpeningBalance(cardId, onDate = 0L)!!
        assertEquals(TransactionKind.OPENING, opening.kind)
        assertEquals(45_000L, ledger.accountBalance(cardId)) // owed

        // The open-equity pot took the contra.
        val eq = db.AccountDao().getBySlug("sys-openeq-${LedgerService.USER_ID}")!!
        assertEquals(45_000L, ledger.accountBalance(eq.id))
    }
}