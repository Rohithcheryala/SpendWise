package com.example.spendwise.ledger

import com.example.spendwise.ledger.api.CreateTransactionRequest
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.api.LineSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Balances are computed, never stored: signed sum of confirmed, non-voided
 * lines; liabilities inverted (balance = owed). Ported from ledger.py's
 * account_balance and its deletion-test discipline.
 */
class BalanceTest : BackendTestBase() {

    private suspend fun setupAccount(kind: String = "available"): Long =
        db.AccountDao().insert(newAccount("acct", kind = kind))

    @Test
    fun `expenses reduce and income raises a confirmed balance`() = runTest {
        val bank = setupAccount()

        ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 10L,
                lines = listOf(
                    LineSpec(-500_00, accountId = bank),
                    LineSpec(500_00, categoryId = db.CategoryDao().insert(newCategory("Food"))),
                ),
            )
        )
        assertEquals(-500_00, ledger.accountBalance(bank))

        ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 11L,
                lines = listOf(
                    LineSpec(1_200_00, accountId = bank),
                    LineSpec(-1_200_00, categoryId = db.CategoryDao().insert(newCategory("Salary", "income"))),
                ),
            )
        )
        assertEquals(700_00, ledger.accountBalance(bank))
    }

    @Test
    fun `buffer transactions do not count until confirmed`() = runTest {
        val bank = setupAccount()
        val food = db.CategoryDao().insert(newCategory("Food"))

        val buffered = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                status = TransactionStatus.BUFFER,
                lines = listOf(LineSpec(-300_00, accountId = bank), LineSpec(300_00, categoryId = food)),
            )
        )
        assertEquals(0L, ledger.accountBalance(bank))

        ledger.confirmTransaction(buffered.id)
        assertEquals(-300_00, ledger.accountBalance(bank))
    }

    @Test
    fun `voided transactions drop out of balances`() = runTest {
        val bank = setupAccount()
        val food = db.CategoryDao().insert(newCategory("Food"))

        val entry = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                lines = listOf(LineSpec(-700_00, accountId = bank), LineSpec(700_00, categoryId = food)),
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
        val card = db.AccountDao().insert(newAccount("card", kind = "liability"))
        val shopping = db.CategoryDao().insert(newCategory("Shopping"))

        // Spending on a credit card increases what you owe.
        ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                lines = listOf(
                    LineSpec(-250_00, accountId = card),
                    LineSpec(250_00, categoryId = shopping),
                ),
            )
        )
        assertEquals(250_00, ledger.accountBalance(card))
    }

    @Test
    fun `through bounds the balance for reconciliation continuity`() = runTest {
        val bank = setupAccount()
        val food = db.CategoryDao().insert(newCategory("Food"))

        suspend fun spend(onDay: Long, paise: Long) = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = onDay,
                lines = listOf(LineSpec(-paise, accountId = bank), LineSpec(paise, categoryId = food)),
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
        val id = db.AccountDao().insert(newAccount("bank", openingBalancePaise = 12_000_00))
        val bank = db.AccountDao().getById(id)!!

        val opening = ledger.recordOpeningBalance(bank.id, onDate = 0L)!!
        assertEquals(12_000_00, ledger.accountBalance(bank.id))
        assertEquals("Opening balance", opening.note)

        // Idempotent replace: editing the opening balance swaps the baseline.
        db.AccountDao().update(bank.copy(openingBalancePaise = 15_000_00))
        ledger.recordOpeningBalance(bank.id, onDate = 0L)
        assertEquals(15_000_00, ledger.accountBalance(bank.id))
        assertEquals(null, ledger.getTransaction(opening.id))
    }
}