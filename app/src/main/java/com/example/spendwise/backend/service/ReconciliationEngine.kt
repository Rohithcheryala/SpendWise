package com.example.spendwise.backend.service

import com.example.spendwise.backend.api.Direction
import java.time.LocalDate

/**
 * Statement reconciliation engine — pure, DB-free, unit-testable.
 * Faithful port of the old server's reconcile.py: diffs a parsed bank
 * statement against app activity over the *unreconciled window* (after the
 * account's reconciled_through watermark).
 *
 * Matching is layered, strongest key first:
 *   1. reference id (RRN/UPI ref) — exact; direction preferred on ref repeats
 *   2. amount + direction + date (±1 day), narration similarity breaking ties
 *
 * Integrity flags surfaced to the caller:
 *   - continuityOk : confirmed balance at the watermark == statement carry-in
 *   - gap          : statement starts > 1 day after the watermark
 */
object Reconciliation {

    private const val DATE_SLACK = 1L

    data class StatementLine(
        val txnDate: LocalDate,
        val amountPaise: Long,
        val direction: Direction,
        val narration: String? = null,
        val refId: String? = null,
        val balanceAfterPaise: Long? = null,
    )

    data class ParsedStatement(
        val periodStart: LocalDate?,
        val periodEnd: LocalDate?,
        val openingBalancePaise: Long?,
        val closingBalancePaise: Long?,
        val expectedDebitCount: Int? = null,
        val expectedCreditCount: Int? = null,
        val lines: List<StatementLine>,
    )

    /** Lightweight view of an app entry for matching. */
    data class AppTxn(
        val id: Long,
        val amountPaise: Long,
        val direction: Direction,
        val occurredOn: LocalDate,
        val refId: String? = null,
        val counterpartyName: String? = null,
    )

    enum class Confidence { REF, EXACT, FUZZY }

    data class MatchedPair(val line: StatementLine, val txnId: Long, val confidence: Confidence)

    data class Result(
        val matched: List<MatchedPair>,
        val missing: List<StatementLine>,
        val extra: List<AppTxn>,
        /** Lines on/before the watermark — already settled, shown locked. */
        val locked: List<StatementLine>,
        val windowStart: LocalDate?,
        val continuityOk: Boolean?,
        val carryInPaise: Long?,
        val gap: Boolean,
        /** opening + signed(lines) == closing, per account-kind sign convention. */
        val parseReconciles: Boolean?,
        val expectedLineCount: Int?,
        val parsedLineCount: Int,
    )

    fun reconcile(
        parsed: ParsedStatement,
        appTxns: List<AppTxn>,
        accountKind: String?,
        reconciledThrough: LocalDate? = null,
        balanceAtWatermark: Long? = null,
    ): Result {
        val (periodLo, periodHi) = period(parsed)
        // Actionable window = everything after the watermark; a first
        // reconcile has no watermark, so it's the whole statement.
        val windowStart = reconciledThrough?.plusDays(DATE_SLACK) ?: periodLo

        val locked = parsed.lines.filter {
            reconciledThrough != null && it.txnDate <= reconciledThrough
        }
        val active = parsed.lines.filter {
            reconciledThrough == null || it.txnDate > reconciledThrough
        }

        // Matching pool never reaches into settled territory (<= watermark).
        val pool = appTxns.filter { t ->
            (reconciledThrough == null || t.occurredOn > reconciledThrough) &&
                t.occurredOn >= windowStart.minusDays(DATE_SLACK) &&
                t.occurredOn <= periodHi.plusDays(DATE_SLACK)
        }
        val consumed = mutableSetOf<Long>()
        val matched = mutableListOf<MatchedPair>()

        // Pass 1 — reference id (prefer same direction when a ref repeats).
        val byRef = pool.filter { it.refId != null }.groupBy { it.refId!! }
        val remaining = mutableListOf<StatementLine>()
        for (line in active) {
            val cands = line.refId
                ?.let { byRef[it].orEmpty() }
                ?.filter { it.id !in consumed }
                .orEmpty()
            val pick = cands.firstOrNull { it.direction == line.direction }
                ?: cands.firstOrNull()
            if (pick != null) {
                consumed.add(pick.id)
                matched.add(MatchedPair(line, pick.id, Confidence.REF))
            } else {
                remaining.add(line)
            }
        }

        // Pass 2 — amount + direction + date(±1d), narration breaking ties.
        val missing = mutableListOf<StatementLine>()
        for (line in remaining) {
            val cands = pool.filter { t ->
                t.id !in consumed &&
                    t.amountPaise == line.amountPaise &&
                    t.direction == line.direction &&
                    kotlin.math.abs(daysBetween(t.occurredOn, line.txnDate)) <= DATE_SLACK
            }
            if (cands.isEmpty()) {
                missing.add(line)
                continue
            }
            val pick = if (cands.size == 1) {
                cands[0] to Confidence.EXACT
            } else {
                cands.sortedWith(
                    compareByDescending<AppTxn> { similar(line.narration, it.counterpartyName) }
                        .thenBy { kotlin.math.abs(daysBetween(it.occurredOn, line.txnDate)) }
                )[0] to Confidence.FUZZY
            }
            consumed.add(pick.first.id)
            matched.add(MatchedPair(line, pick.first.id, pick.second))
        }

        // Extra = unmatched app txns still inside the statement period.
        val extra = pool.filter { it.id !in consumed && it.occurredOn <= periodHi }

        // Carry-in = bank running balance at the watermark: closing balance of
        // the last locked line, else the stated opening.
        var carryIn = parsed.openingBalancePaise
        for (line in locked.reversed()) {
            if (line.balanceAfterPaise != null) {
                carryIn = line.balanceAfterPaise
                break
            }
        }

        val continuityOk =
            if (reconciledThrough != null && balanceAtWatermark != null && carryIn != null) {
                balanceAtWatermark == carryIn
            } else {
                null
            }

        val gap = reconciledThrough != null &&
            parsed.periodStart != null &&
            parsed.periodStart.isAfter(reconciledThrough.plusDays(DATE_SLACK))

        val parseReconciles =
            if (parsed.openingBalancePaise != null && parsed.closingBalancePaise != null) {
                parsed.openingBalancePaise +
                    parsed.lines.sumOf { signed(it, accountKind) } == parsed.closingBalancePaise
            } else {
                null
            }

        val expected = if (
            parsed.expectedDebitCount != null && parsed.expectedCreditCount != null
        ) {
            parsed.expectedDebitCount + parsed.expectedCreditCount
        } else {
            null
        }

        return Result(
            matched = matched,
            missing = missing,
            extra = extra,
            locked = locked,
            windowStart = windowStart,
            continuityOk = continuityOk,
            carryInPaise = carryIn,
            gap = gap,
            parseReconciles = parseReconciles,
            expectedLineCount = expected,
            parsedLineCount = parsed.lines.size,
        )
    }

    /**
     * Signed contribution of a line to the closing balance, per account kind:
     * assets — inflow raises, outflow lowers; liabilities — the balance is
     * what's owed, so a charge (out) raises and a payment (in) lowers it.
     */
    fun signed(line: StatementLine, accountKind: String?): Long =
        when {
            accountKind == "liability" && line.direction == Direction.OUT -> line.amountPaise
            accountKind == "liability" -> -line.amountPaise
            line.direction == Direction.IN -> line.amountPaise
            else -> -line.amountPaise
        }

    /** Net change the missing lines would apply to the balance once created. */
    fun missingNetDelta(missing: List<StatementLine>, accountKind: String?): Long =
        missing.sumOf { signed(it, accountKind) }

    // ── internals ────────────────────────────────────────────────────────

    private fun period(parsed: ParsedStatement): Pair<LocalDate, LocalDate> {
        if (parsed.periodStart != null && parsed.periodEnd != null) {
            return parsed.periodStart to parsed.periodEnd
        }
        require(parsed.lines.isNotEmpty()) { "statement has no period and no lines" }
        return parsed.lines.minOf { it.txnDate } to parsed.lines.maxOf { it.txnDate }
    }

    private fun daysBetween(a: LocalDate, b: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(a, b)

    private val NON_ALNUM = Regex("[^A-Z0-9 ]")

    private fun norm(s: String?): String =
        NON_ALNUM.replace((s ?: "").uppercase(), "").trim()

    /** Length-based LCS similarity in [0,1] (SequenceMatcher.ratio analogue). */
    internal fun similar(a: String?, b: String?): Double {
        val na = norm(a)
        val nb = norm(b)
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        val m = lcsLength(na, nb).toDouble()
        return 2 * m / (na.length + nb.length)
    }

    private fun lcsLength(a: String, b: String): Int {
        var prev = IntArray(b.length + 1)
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                cur[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1 else maxOf(prev[j], cur[j - 1])
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }
}