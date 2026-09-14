package com.example.spendwise.ledger.api

/**
 * The backend contract, named after its REST shape. The local Room-backed
 * [com.example.spendwise.ledger.service.LedgerService] implements this today
 * (offline-first); a sync server would implement the exact same interface over
 * HTTP later, so callers never change.
 */
interface LedgerApi {

    /** POST /transactions — create a balanced entry. Fails on invariant violations. */
    suspend fun createTransaction(request: CreateTransactionRequest): TransactionView

    /** GET /transactions/{id} */
    suspend fun getTransaction(id: Long): TransactionView?

    /** GET /transactions?status=&from=&to= — voided transactions are excluded. */
    suspend fun listTransactions(status: String? = null, from: Long? = null, to: Long? = null): List<TransactionView>

    /** DELETE /transactions/{id} */
    suspend fun deleteTransaction(id: Long)

    /**
     * POST /transactions/{id}/confirm — move buffer -> confirmed. Refused while the
     * money still sits only on the system 'unmatched' pot (an orphan ingest).
     * [extraTags] are unioned into the entry's tags at confirm time (used by
     * the active tagging context when importing from the inbox).
     */
    suspend fun confirmTransaction(id: Long, extraTags: List<String> = emptyList())

    /**
     * PUT /transactions/{id}/account — re-point the account leg of a buffer entry
     * onto [accountId]. Lets the user correct an auto-matched (or orphaned)
     * SMS attribution from the inbox.
     */
    suspend fun assignAccount(transactionId: Long, accountId: Long): TransactionView

    /** POST /transactions/{id}/void — audit-soft-delete; leaves balances alone. */
    suspend fun voidTransaction(id: Long, reason: String?)

    /** POST /transactions/{id}/split — carve receivable shares for friends out of a paid entry. */
    suspend fun splitTransaction(id: Long, request: SplitRequest): TransactionView

    /** POST /ingest — SMS/notification/scan write path with provenance + dedupe. */
    suspend fun ingest(request: IngestRequest): TransactionView

    /** POST /ingest/transfer — two account legs (e.g. ATM withdrawal bank -> cash). */
    suspend fun ingestTransfer(
        amountPaise: Long,
        fromAccountId: Long,
        toAccountId: Long,
        occurredOn: Long,
        status: String = TransactionStatus.BUFFER,
        source: String = TransactionSource.SMS,
        rawText: String? = null,
        dedupeHash: String? = null,
        bankRef: String? = null,
        balanceAfterPaise: Long? = null,
        note: String? = null,
    ): TransactionView

    /** GET /transactions/by-dedupe/{hash} — inbound-message de-duplication lookup. */
    suspend fun findTransactionByDedupeHash(dedupeHash: String): TransactionView?

    /** GET /accounts/{id}/balance — computed at read time; liabilities inverted (amount owed). Buckets are child accounts, so this is their value too. */
    suspend fun accountBalance(accountId: Long, through: Long? = null): Long

    /** PUT /accounts/{id}/opening-balance — idempotent opening-balance transaction vs opening equity. */
    suspend fun recordOpeningBalance(
        accountId: Long,
        amountPaise: Long,
        onDate: Long? = null,
    ): TransactionView?

    /**
     * GET /friends — net outstanding per person counterparty, from the loans
     * receivable pot. Only persons appear (loan paths force the type); settled
     * parties (net 0) are omitted. Positive = they owe you.
     */
    suspend fun friendsOutstanding(): List<FriendBalance>
}