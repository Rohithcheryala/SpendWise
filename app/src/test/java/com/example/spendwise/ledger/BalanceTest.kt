package com.example.spendwise.ledger

import com.example.spendwise.ledger.api.CreateTransactionRequest
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.api.LineSpec
import com.example.spendwise.ledger.service.LedgerService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Balances are computed, never stored: signed sum of money-real (confirmed or
 * pending-review) non-voided lines; liabilities inverted (balance = owed).
 * Ported from ledger.py's account_balance and its deletion-test discipline.
 */
class BalanceTest : BackendTestBase() {

    private suspend fun setupAccount(accountClass: String = LedgerService.CLASS_ASSET): Long =
        newAccount("acct", accountClass = accountClass)

    @Test
    fun `expenses reduce and income raises a confirmed balance`() = runTest {
        val bank = setupAccount()

        ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 10L,
                lines = listOf(
                    LineSpec(-500_00, accountId = bank),
                    LineSpec(500_00, accountId = newCategory("Food")),
                ),
            )
        )
        assertEquals(-500_00, ledger.accountBalance(bank))

        ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 11L,
                lines = listOf(
                    LineSpec(1_200_00, accountId = bank),
                    LineSpec(-1_200_00, accountId = newCategory("Salary", LedgerService.CLASS_INCOME)),
                ),
            )
        )
        assertEquals(700_00, ledger.accountBalance(bank))
    }

    @Test
    fun `pending-review sms already count, approving never moves the balance`() = runTest {
        val bank = setupAccount()
        val food = newCategory("Food")

        // Awaiting-review SMS: the money has moved (the bank says so), so the
        // balance counts it — this is what makes the "current balance" typed
        // at import true the moment import finishes.
        val pending = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                status = TransactionStatus.BUFFER,
                lines = listOf(LineSpec(-300_00, accountId = bank), LineSpec(300_00, accountId = food)),
            )
        )
        assertEquals(-300_00, ledger.accountBalance(bank))

        // Approving is classification only — the balance must not move,
        // no matter how old the transaction is.
        ledger.confirmTransaction(pending.id)
        assertEquals(-300_00, ledger.accountBalance(bank))

        // Dismissing means "this never happened" — the money comes back.
        ledger.voidTransaction(pending.id, "duplicate")
        assertEquals(0L, ledger.accountBalance(bank))
    }

    @Test
    fun `voided transactions drop out of balances`() = runTest {
        val bank = setupAccount()
        val food = newCategory("Food")

        val entry = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                lines = listOf(LineSpec(-700_00, accountId = bank), LineSpec(700_00, accountId = food)),
            )
        )
        assertEquals(-700_00, ledger.accountBalance(bank))

        ledger.voidTransaction(entry.id, "duplicate")
        assertEquals(0L, ledger.accountBalance(bank))
        // And it disappears from lists.
        assertEquals(0, ledger.listTransactions().size)
    }

    @Test
    fun `liability balance is inverted to the amount owed`() = runTest {
        val card = newAccount("card", accountClass = LedgerService.CLASS_LIABILITY)
        val shopping = newCategory("Shopping")

        // Spending on a credit card increases what you owe.
        ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                lines = listOf(
                    LineSpec(-250_00, accountId = card),
                    LineSpec(250_00, accountId = shopping),
                ),
            )
        )
        assertEquals(250_00, ledger.accountBalance(card))
    }

    @Test
    fun `through bounds the balance for reconciliation continuity`() = runTest {
        val bank = setupAccount()
        val food = newCategory("Food")

        suspend fun spend(onDay: Long, paise: Long) = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = onDay,
                lines = listOf(LineSpec(-paise, accountId = bank), LineSpec(paise, accountId = food)),
            )
        )

        spend(1L, 100_00)
        spend(5L, 50_00)
        spend(9L, 25_00)

        assertEquals(-150_00, ledger.accountBalance(bank, through = 6L))
        assertEquals(-175_00, ledger.accountBalance(bank, through = 9L))
        assertEquals(-175_00, ledger.accountBalance(bank))
    }

    @Test
    fun `opening balance entry contributes to the computed balance`() = runTest {
        val bank = newAccount("bank")

        val opening = ledger.recordOpeningBalance(bank, 12_000_00, onDate = 0L)!!
        assertEquals(12_000_00, ledger.accountBalance(bank))
        assertEquals("Opening balance", opening.note)

        // Idempotent replace: editing the opening balance swaps the baseline.
        ledger.recordOpeningBalance(bank, 15_000_00, onDate = 0L)
        assertEquals(15_000_00, ledger.accountBalance(bank))
        assertEquals(null, ledger.getTransaction(opening.id))
    }

    @Test
    fun `balance adjustment books only the delta vs equity`() = runTest {
        val bank = newAccount("bank")
        ledger.recordOpeningBalance(bank, 10_000_00, onDate = 0L)

        // Interest the bank credited without an SMS: user says 10_500.
        val adj = ledger.recordBalanceAdjustment(bank, 10_500_00)!!
        assertEquals(10_500_00, ledger.accountBalance(bank))
        assertEquals("Balance adjustment", adj.note)

        // An in-sync target books nothing.
        assertEquals(null, ledger.recordBalanceAdjustment(bank, 10_500_00))
        assertEquals(10_500_00, ledger.accountBalance(bank))
    }

    @Test
    fun `balance adjustment on a liability moves the owed amount`() = runTest {
        val card = newAccount("card", accountClass = LedgerService.CLASS_LIABILITY)
        ledger.recordOpeningBalance(card, 45_000, onDate = 0L)
        assertEquals(45_000, ledger.accountBalance(card))

        // A payment the bank processed without an SMS: ledger reads 25_000 owed.
        ledger.recordBalanceAdjustment(card, 25_000)!!
        assertEquals(25_000, ledger.accountBalance(card))
    }
}