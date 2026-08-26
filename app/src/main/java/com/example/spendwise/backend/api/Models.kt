package com.example.spendwise.backend.api

/**
 * DTOs of the SpendWise ledger backend.
 *
 * These models ARE the REST contract: today they cross an in-process seam into
 * [LedgerApi]; tomorrow the same shapes can be served by a sync server without
 * touching business logic. All money is integer paise — no floats anywhere.
 */

enum class Direction { IN, OUT }

/** How an entry reads to the UI, derived from its lines (never stored). */
enum class EntryKind {
    OPENING, RECONCILIATION, ALLOCATION, SPLIT, LOAN, LOAN_REPAYMENT,
    INVESTMENT, TRANSFER, EXPENSE, INCOME, OTHER
}

object EntryStatus {
    const val BUFFER = "buffer"
    const val CONFIRMED = "confirmed"
}

object EntrySource {
    const val SMS = "sms"
    const val QR_SCAN = "qr_scan"
    const val MANUAL = "manual"
    const val SPLIT = "split"
    const val NOTIFICATION = "notification"
    const val STATEMENT = "statement"
}

/** Intents are orthogonal to tags: the *why* behind the movement. */
object Intent {
    const val EXPENSE = "expense"
    const val INCOME = "income"
    const val INVESTMENT = "investment"
    const val LOAN = "loan"
    const val LOAN_REPAYMENT = "loan_repayment"
    const val TRANSFER = "transfer"
    const val RECONCILIATION = "reconciliation"

    val ALL = setOf(EXPENSE, INCOME, INVESTMENT, LOAN, LOAN_REPAYMENT, TRANSFER, RECONCILIATION)
}

/** One posting. Targets exactly one of accountId/categoryId. */
data class LineSpec(
    val amountPaise: Long,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val bucketId: Long? = null,
    val balanceAfterPaise: Long? = null,
    val counterpartyId: Long? = null,
)

data class CreateEntryRequest(
    val occurredOn: Long,
    val lines: List<LineSpec>,
    val status: String = EntryStatus.CONFIRMED,
    val source: String = EntrySource.MANUAL,
    val happenedAt: Long? = null,
    val counterpartyId: Long? = null,
    val groupId: Long? = null,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val linkedEntryId: Long? = null,
)

/** POST /ingest — an SMS/notification/scan-derived movement. */
data class IngestRequest(
    val amountPaise: Long,
    val direction: Direction,
    val occurredOn: Long,
    val accountId: Long? = null,
    val counterpartyId: Long? = null,
    val intent: String,
    val tags: List<String> = emptyList(),
    val status: String = EntryStatus.BUFFER,
    val source: String = EntrySource.SMS,
    val happenedAt: Long? = null,
    val rawText: String? = null,
    val dedupeHash: String? = null,
    val bankRef: String? = null,
    val balanceAfterPaise: Long? = null,
    val bucketId: Long? = null,
    val note: String? = null,
    /** Parse facts frozen for orphan reclaim ("bank|last4|accountKind"). */
    val parsedFacts: String? = null,
)

data class SplitShare(val counterpartyId: Long, val amountPaise: Long)

/** POST /entries/{id}/split — carve friends' shares out of a paid entry. */
data class SplitRequest(
    val shares: List<SplitShare>,
    val groupId: Long? = null,
)

/**
 * Presentation summary derived from the lines so clients need no accounting
 * logic. For multi-sided entries (split/transfer) categoryId is null and the
 * client should render the lines themselves.
 */
data class EntryView(
    val id: Long,
    val kind: EntryKind,
    val direction: Direction?,
    val amountPaise: Long,
    val accountId: Long?,
    val toAccountId: Long?,
    val categoryId: Long?,
    val bucketId: Long?,
    val balanceAfterPaise: Long?,
    val occurredOn: Long,
    val status: String,
    val source: String,
    val counterpartyId: Long?,
    val groupId: Long?,
    val note: String?,
    val tags: List<String>,
    val linkedEntryId: Long?,
)

/** Thrown for 4xx-equivalent violations (bad input, invariant breaches). */
class ApiException(message: String) : Exception(message)