package com.example.spendwise.ledger

import com.example.spendwise.ledger.api.ApiException
import com.example.spendwise.ledger.api.CreateTransactionRequest
import com.example.spendwise.ledger.api.LineSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The ledger invariants: a transaction needs >= 2 lines summing to exactly
 * zero, and every line posts onto an account node.
 */
class LedgerValidationTest : BackendTestBase() {

    @Test
    fun `transaction with a single line is rejected`() = runTest {
        val bank = newAccount("bank")

        expectApiError("a transaction needs at least two lines") {
            ledger.createTransaction(
                CreateTransactionRequest(
                    occurredOn = 0L,
                    lines = listOf(LineSpec(-1000, accountId = bank)),
                )
            )
        }
    }

    @Test
    fun `unbalanced transaction is rejected`() = runTest {
        val bank = newAccount("bank")
        val food = newCategory("Food")

        expectApiError("transaction lines must sum to zero (got -100)") {
            ledger.createTransaction(
                CreateTransactionRequest(
                    occurredOn = 0L,
                    // 1000 - 900 != 0
                    lines = listOf(
                        LineSpec(-1000, accountId = bank),
                        LineSpec(900, accountId = food),
                    ),
                )
            )
        }
    }

    @Test
    fun `balanced multi-line transaction persists and its lines still sum to zero`() = runTest {
        val bank = newAccount("bank")
        val food = newCategory("Food")
        val fun_ = newCategory("Fun")

        val view = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                note = "groceries + movie",
                tags = listOf("night out"),
                lines = listOf(
                    LineSpec(-3000, accountId = bank),
                    LineSpec(2500, accountId = food),
                    LineSpec(500, accountId = fun_),
                ),
            )
        )

        assertEquals(listOf("night out"), view.tags)
        assertEquals("groceries + movie", view.note)
        val stored = db.TransactionLineDao().getByTransactionList(view.id)
        assertEquals(3, stored.size)
        assertEquals(0L, stored.sumOf { it.amountPaise })
    }
}