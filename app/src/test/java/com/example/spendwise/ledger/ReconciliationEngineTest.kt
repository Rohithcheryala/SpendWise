package com.example.spendwise.ledger

import com.example.spendwise.ledger.api.Direction
import com.example.spendwise.ledger.service.Reconciliation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The pure reconciliation engine, ported from the old server's reconcile.py.
 * No DB involved — statements and app activity are plain values.
 */
class ReconciliationEngineTest {

    private val d = LocalDate.of(2026, 8, 1)

    private fun line(
        day: Int,
        amountPaise: Long,
        direction: Direction = Direction.OUT,
        narration: String? = null,
        refId: String? = null,
        balanceAfter: Long? = null,
    ) = Reconciliation.StatementLine(
        txnDate = d.plusDays(day.toLong()),
        amountPaise = amountPaise,
        direction = direction,
        narration = narration,
        refId = refId,
        balanceAfterPaise = balanceAfter,
    )

    private fun txn(
        id: Long,
        day: Int,
        amountPaise: Long,
        direction: Direction = Direction.OUT,
        refId: String? = null,
        name: String? = null,
    ) = Reconciliation.AppTxn(
        id = id, amountPaise = amountPaise, direction = direction,
        occurredOn = d.plusDays(day.toLong()), refId = refId, counterpartyName = name,
    )

    private fun stmt(lines: List<Reconciliation.StatementLine>) =
        Reconciliation.ParsedStatement(
            periodStart = null, periodEnd = null,
            openingBalancePaise = null, closingBalancePaise = null,
            lines = lines,
        )

    @Test
    fun `reference id matches first and wins over everything else`() {
        val r = Reconciliation.reconcile(
            stmt(listOf(line(2, 50_00, refId = "RRN1"))),
            listOf(txn(1, 2, 50_00, refId = "RRN1")),
            accountKind = "available",
        )
        assertEquals(1, r.matched.size)
        assertEquals(Reconciliation.Confidence.REF, r.matched[0].confidence)
        assertEquals(1L, r.matched[0].txnId)
        assertTrue(r.missing.isEmpty() && r.extra.isEmpty())
    }

    @Test
    fun `amount direction and date matches exactly - wrong-direction txns stay extra`() {
        val stmt = stmt(lines = listOf(line(3, 25_00)))
        val txns = listOf(
            txn(1, 3, 25_00),                           // exact candidate
            txn(2, 3, 25_00, direction = Direction.IN), // wrong direction -> extra
        )
        val r = Reconciliation.reconcile(stmt, txns, "available")

        assertEquals(Reconciliation.Confidence.EXACT, r.matched.single().confidence)
        assertEquals(1L, r.matched.single().txnId)
        assertEquals(listOf(2L), r.extra.map { it.id })
    }

    @Test
    fun `date slack of one day is tolerated`() {
        val r = Reconciliation.reconcile(
            stmt(listOf(line(5, 10_00))),
            listOf(txn(1, 6, 10_00)), // SMS landed a day late vs the statement
            "available",
        )
        assertEquals(1, r.matched.size)
        assertTrue(r.missing.isEmpty())
    }

    @Test
    fun `statement lines with no app twin are missing`() {
        // A bank fee the SMS never announced.
        val r = Reconciliation.reconcile(stmt(listOf(line(2, 99_00))), emptyList(), "available")

        assertEquals(1, r.missing.size)
        assertEquals(0, r.matched.size)
        // Missing debit on an asset lowers the projected balance.
        assertEquals(-99_00L, Reconciliation.missingNetDelta(r.missing, "available"))
    }

    @Test
    fun `lines on or before the watermark lock instead of re-diffing`() {
        val watermark = d.plusDays(4)
        val r = Reconciliation.reconcile(
            stmt(lines = listOf(line(2, 10_00), line(5, 20_00))),
            appTxns = listOf(txn(1, 5, 20_00)),
            accountKind = "available",
            reconciledThrough = watermark,
            balanceAtWatermark = 10_00L,
        )

        assertEquals(listOf(d.plusDays(2)), r.locked.map { it.txnDate }) // settled
        assertEquals(1, r.matched.size)                                  // active diffed
        assertFalse(r.gap)                                               // contiguous chain
    }

    @Test
    fun `a statement starting more than a day after the watermark is a gap`() {
        val parsed = Reconciliation.ParsedStatement(
            periodStart = d.plusDays(9), periodEnd = d.plusDays(19),
            openingBalancePaise = null, closingBalancePaise = null,
            lines = listOf(line(10, 20_00)),
        )
        val r = Reconciliation.reconcile(
            parsed, emptyList(), "available", reconciledThrough = d.plusDays(3),
        )
        assertTrue(r.gap)
    }

    @Test
    fun `continuity fails loudly when the carried-in balance does not tally`() {
        val watermark = d.plusDays(3)
        // Statement states its own opening (= carry-in when nothing is locked).
        val stmtWithBalances = stmt(lines = listOf(line(5, 20_00, balanceAfter = 9_50_00)))
            .copy(
                periodStart = d.plusDays(4),
                periodEnd = d.plusDays(9),
                openingBalancePaise = 9_00_00,
            )

        val bad = Reconciliation.reconcile(
            stmtWithBalances, emptyList(), "available",
            reconciledThrough = watermark, balanceAtWatermark = 8_00_00,
        )
        assertFalse(bad.continuityOk!!)

        val good = Reconciliation.reconcile(
            stmtWithBalances, emptyList(), "available",
            reconciledThrough = watermark, balanceAtWatermark = 9_00_00,
        )
        assertTrue(good.continuityOk!!)
        assertEquals(9_00_00L, good.carryInPaise)
    }

    @Test
    fun `continuity is null on a first reconcile - nothing to be continuous with`() {
        val r = Reconciliation.reconcile(stmt(listOf(line(1, 100))), emptyList(), "available")
        assertNull(r.continuityOk)
        assertNull(r.carryInPaise)
    }

    @Test
    fun `parse reconciles checks opening + signed lines against closing`() {
        val asset = Reconciliation.ParsedStatement(
            periodStart = d, periodEnd = d.plusDays(9),
            openingBalancePaise = 10_000, closingBalancePaise = 8_000,
            lines = listOf(line(2, 2_000)), // one debit
        )
        assertTrue(Reconciliation.reconcile(asset, emptyList(), "available").parseReconciles!!)

        val broken = asset.copy(closingBalancePaise = 7_000)
        assertFalse(Reconciliation.reconcile(broken, emptyList(), "available").parseReconciles!!)
    }

    @Test
    fun `liability sign convention - charges raise what is owed`() {
        val card = Reconciliation.ParsedStatement(
            periodStart = d, periodEnd = d.plusDays(9),
            openingBalancePaise = 1_000, closingBalancePaise = 2_500,
            lines = listOf(line(2, 2_000, Direction.OUT), line(4, 500, Direction.IN)),
        )
        assertTrue(Reconciliation.reconcile(card, emptyList(), "liability").parseReconciles!!)
    }

    @Test
    fun `fuzzy narration similarity breaks ties between equal candidates`() {
        val r = Reconciliation.reconcile(
            stmt(lines = listOf(line(2, 30_00, narration = "SWIGGY BANGALORE"))),
            listOf(
                txn(1, 2, 30_00, name = "ZOMATO ORDER"),
                txn(2, 3, 30_00, name = "SWIGGY ORDER"),
            ),
            "available",
        )
        assertEquals(Reconciliation.Confidence.FUZZY, r.matched.single().confidence)
        assertEquals(2L, r.matched.single().txnId) // SWIGGY beats ZOMATO
    }

    @Test
    fun `expected line count sums debit and credit counts when present`() {
        val parsed = Reconciliation.ParsedStatement(
            periodStart = d, periodEnd = d.plusDays(9),
            openingBalancePaise = null, closingBalancePaise = null,
            expectedDebitCount = 12, expectedCreditCount = 3,
            lines = listOf(line(1, 100)),
        )
        assertEquals(15, Reconciliation.reconcile(parsed, emptyList(), "available").expectedLineCount)
    }
}