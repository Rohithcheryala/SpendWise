package com.example.spendwise.ledger

import com.example.spendwise.ledger.api.ApiException
import com.example.spendwise.ledger.api.CreateTransactionRequest
import com.example.spendwise.ledger.api.Direction
import com.example.spendwise.ledger.api.IngestRequest
import com.example.spendwise.ledger.api.Intent
import com.example.spendwise.ledger.api.LineSpec
import com.example.spendwise.ledger.api.TransactionKind
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.service.LedgerService
import com.example.spendwise.ledger.service.LedgerService.SystemRole
import com.example.spendwise.data.database.entity.AccountEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The ledger invariants: a transaction needs >= 2 lines summing to exactly
 * zero, and every line posts onto an account node.
 */
class LedgerValidationTest : BackendTestBase() {

    @Test
    fun `transaction with a single line is rejected`() = runTest {
        val bank = newAccount("bank")

        expectApiErrorOf<ApiException.UnbalancedLines>("a transaction needs at least two lines") {
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

        expectApiErrorOf<ApiException.UnbalancedLines>("transaction lines must sum to zero (got -100)") {
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
    fun `reusing a dedupe hash across ingests is rejected`() = runTest {
        val bank = newAccount("bank")
        val hash = "SMS|TESTBANK|1234|2026-09-17|1250"

        ledger.ingest(
            IngestRequest(
                amountPaise = 125_000,
                direction = Direction.OUT,
                occurredOn = 0L,
                accountId = bank,
                intent = Intent.EXPENSE,
                dedupeHash = hash,
            )
        )

        expectApiErrorOf<ApiException.DuplicateDedupeHash>("duplicate dedupe hash '$hash'") {
            ledger.ingest(
                IngestRequest(
                    amountPaise = 125_000,
                    direction = Direction.OUT,
                    occurredOn = 0L,
                    accountId = bank,
                    intent = Intent.EXPENSE,
                    dedupeHash = hash,
                )
            )
        }
    }

    @Test
    fun `confirming a voided transaction is rejected`() = runTest {
        val bank = newAccount("bank")
        val food = newCategory("Food")
        val view = ledger.createTransaction(
            CreateTransactionRequest(
                occurredOn = 0L,
                status = TransactionStatus.BUFFER,
                lines = listOf(
                    LineSpec(-1000, accountId = bank),
                    LineSpec(1000, accountId = food),
                ),
            )
        )
        ledger.voidTransaction(view.id, "mistake")

        expectApiErrorOf<ApiException.InvalidLifecycleTransition>(
            "transaction ${view.id} is voided and cannot be confirmed"
        ) {
            ledger.confirmTransaction(view.id)
        }
    }

    @Test
    fun `equity leg without explicit kind is an equity guard violation`() = runTest {
        val food = newCategory("Food")
        val pot = systemEquityPot()

        expectApiErrorOf<ApiException.EquityGuardViolation>(
            "equity legs require an explicit kind (opening/reconciliation)"
        ) {
            ledger.createTransaction(
                CreateTransactionRequest(
                    occurredOn = 0L,
                    lines = listOf(
                        LineSpec(-1000, accountId = food),
                        LineSpec(1000, accountId = pot),
                    ),
                )
            )
        }
    }

    @Test
    fun `transfer with a terminal leg violates the kind shape`() = runTest {
        val bank = newAccount("bank")
        val cash = newAccount("cash")
        val food = newCategory("Food")

        expectApiErrorOf<ApiException.EquityGuardViolation>("transfer must contain only holder legs") {
            ledger.createTransaction(
                CreateTransactionRequest(
                    occurredOn = 0L,
                    kind = TransactionKind.TRANSFER,
                    lines = listOf(
                        LineSpec(-1000, accountId = bank),
                        LineSpec(800, accountId = cash),
                        LineSpec(200, accountId = food),
                    ),
                )
            )
        }
    }

    @Test
    fun `confirming an unknown transaction is a NotFound`() = runTest {
        expectApiErrorOf<ApiException.NotFound>("transaction 424242 not found") {
            ledger.confirmTransaction(424242L)
        }
    }

    /** The single shared equity pot (post-3b-2 merge). Returns its id. */
    protected suspend fun systemEquityPot(): Long =
        db.AccountDao().insert(
            AccountEntity(
                name = "equity-pot-test",
                accountClass = LedgerService.CLASS_EQUITY,
                subtype = SystemRole.EQUITY.subtype,
                isSystem = true,
                createdAt = 0L,
            )
        )

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