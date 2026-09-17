package com.example.spendwise.ledger.api

/**
 * DTOs of the SpendWise ledger backend.
 *
 * These models ARE the REST contract: today they cross an in-process seam into
 * [LedgerApi]; tomorrow the same shapes can be served by a sync server without
 * touching business logic. All money is integer paise — no floats anywhere.
 */

enum class Direction { IN, OUT }

/** How the transaction reads to the UI — written ONCE at creation, never re-derived. */
enum class TransactionKind {
    OPENING, RECONCILIATION, SPLIT, LOAN, LOAN_REPAYMENT,
    INVESTMENT, TRANSFER, EXPENSE, INCOME
}

object TransactionStatus {
    const val BUFFER = "buffer"
    const val CONFIRMED = "confirmed"
}

object TransactionSource {
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

/**
 * One posting, onto exactly one account node — a real account, a category
 * account (class income/expense) or a system pot. Pure (Rust migration 005):
 * who owes whom lives on per-person receivable pots, not on lines; SMS-stated
 * balances live on provenance.
 */
data class LineSpec(
    val amountPaise: Long,
    val accountId: Long,
)

data class CreateTransactionRequest(
    val occurredOn: Long,
    val lines: List<LineSpec>,
    /**
     * Kind written once at creation. Null = derive from the line shape
     * (expense/income/transfer/loan/loan_repayment/split/investment).
     * opening/reconciliation CANNOT be derived once the two equity pots merged
     * into one — those callers must pass it explicitly.
     */
    val kind: TransactionKind? = null,
    val status: String = TransactionStatus.CONFIRMED,
    val source: String = TransactionSource.MANUAL,
    val happenedAt: Long? = null,
    val counterpartyId: Long? = null,
    val groupId: Long? = null,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val linkedTransactionId: Long? = null,
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
    val status: String = TransactionStatus.BUFFER,
    val source: String = TransactionSource.SMS,
    val happenedAt: Long? = null,
    val rawText: String? = null,
    val dedupeHash: String? = null,
    val bankRef: String? = null,
    val balanceAfterPaise: Long? = null,
    val note: String? = null,
    /** Parse facts frozen for orphan reclaim ("bank|last4|accountKind"). */
    val parsedFacts: String? = null,
)

data class SplitShare(val counterpartyId: Long, val amountPaise: Long)

/** POST /transactions/{id}/split — carve friends' shares out of a paid entry. */
data class SplitRequest(
    val shares: List<SplitShare>,
    val groupId: Long? = null,
)

/**
 * Presentation summary derived from the lines so clients need no accounting
 * logic. For multi-sided transactions (split/transfer) categoryId is null and the
 * client should render the lines themselves.
 */
data class TransactionView(
    val id: Long,
    val kind: TransactionKind,
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
    val linkedTransactionId: Long?,
)

/** GET /friends — one row per person with activity on the loans receivable pot. */
data class FriendBalance(
    val counterpartyId: Long,
    val displayName: String,
    /** Positive = they owe you; negative = you owe them. Zero rows are omitted. */
    val netPaise: Long,
)

/** Thrown for 4xx-equivalent violations (bad input, invariant breaches). */
class ApiException(message: String) : Exception(message)