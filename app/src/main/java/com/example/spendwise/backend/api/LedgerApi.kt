package com.example.spendwise.backend.api

/**
 * The backend contract, named after its REST shape. The local Room-backed
 * [com.example.spendwise.backend.service.LedgerService] implements this today
 * (offline-first); a sync server would implement the exact same interface over
 * HTTP later, so callers never change.
 */
interface LedgerApi {

    /** POST /entries — create a balanced entry. Fails on invariant violations. */
    suspend fun createEntry(request: CreateEntryRequest): EntryView

    /** GET /entries/{id} */
    suspend fun getEntry(id: Long): EntryView?

    /** GET /entries?status=&from=&to= — voided entries are excluded. */
    suspend fun listEntries(status: String? = null, from: Long? = null, to: Long? = null): List<EntryView>

    /** DELETE /entries/{id} */
    suspend fun deleteEntry(id: Long)

    /**
     * POST /entries/{id}/confirm — move buffer -> confirmed. Refused while the
     * money still sits only on the system 'unmatched' pot (an orphan ingest).
     */
    suspend fun confirmEntry(id: Long)

    /** POST /entries/{id}/void — audit-soft-delete; leaves balances alone. */
    suspend fun voidEntry(id: Long, reason: String?)

    /** POST /entries/{id}/split — carve receivable shares for friends out of a paid entry. */
    suspend fun splitEntry(id: Long, request: SplitRequest): EntryView

    /** POST /ingest — SMS/notification/scan write path with provenance + dedupe. */
    suspend fun ingest(request: IngestRequest): EntryView

    /** POST /ingest/transfer — two account legs (e.g. ATM withdrawal bank -> cash). */
    suspend fun ingestTransfer(
        amountPaise: Long,
        fromAccountId: Long,
        toAccountId: Long,
        occurredOn: Long,
        status: String = EntryStatus.BUFFER,
        source: String = EntrySource.SMS,
        rawText: String? = null,
        dedupeHash: String? = null,
        bankRef: String? = null,
        balanceAfterPaise: Long? = null,
        note: String? = null,
    ): EntryView

    /** GET /entries/by-dedupe/{hash} — inbound-message de-duplication lookup. */
    suspend fun findEntryByDedupeHash(dedupeHash: String): EntryView?

    /** GET /accounts/{id}/balance — computed at read time; liabilities inverted (amount owed). */
    suspend fun accountBalance(accountId: Long, through: Long? = null): Long

    /** GET /buckets/{id}/value — sum of confirmed lines tagged to the bucket. */
    suspend fun bucketValue(bucketId: Long, through: Long? = null): Long

    /** PUT /accounts/{id}/opening-balance — idempotent opening-balance entry vs open equity. */
    suspend fun recordOpeningBalance(accountId: Long, onDate: Long? = null): EntryView?

    /** PUT /buckets/{id}/allocation — idempotent self-transfer baseline for a bucket. */
    suspend fun recordBucketAllocation(bucketId: Long): EntryView?
}