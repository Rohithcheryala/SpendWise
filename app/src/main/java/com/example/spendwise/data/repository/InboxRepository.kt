package com.example.spendwise.data.repository


import android.util.Log
import com.example.spendwise.BuildConfig
import com.example.spendwise.ledger.api.Direction
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.api.TransactionView
import com.example.spendwise.ledger.api.LedgerApi
import com.example.spendwise.ledger.service.IngestionService
import com.example.spendwise.ledger.service.LedgerService
import com.example.spendwise.core.messages.MessageReader
import com.example.spendwise.core.notifications.TransactionNotifier
import com.example.spendwise.core.parser_pw.TransactionType
import com.example.spendwise.core.parser_pw.bank.BankParserFactory
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.database.dao.CounterpartyDao
import com.example.spendwise.data.database.dao.EntryProvanceDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

/** One row of the Buffer Inbox — a ledger entry still awaiting review. */
data class InboxItem(
    val entryId: Long,
    val sender: String,
    val amountPaise: Long,
    val isDebit: Boolean,
    val account: String?,
    val date: Long,
    val rawSms: String,
    /** Exposed so the UI/VM can enforce Strict mode before confirming. */
    val categoryId: Long?,
    val tags: List<String>,
    /** Resolved category name for the row subtitle (null when unclassified). */
    val category: String? = null,
    /** The account the SMS was auto-matched to (null = orphan/unmatched). */
    val assignedAccountId: Long? = null,
    /** Real user accounts the user can re-point this entry to (id -> name). */
    val accountChoices: List<Pair<Long, String>> = emptyList(),
)

/**
 * The SMS -> ledger bridge for the Buffer Inbox.
 *
 * The inbox IS the ledger's buffer: every known-bank SMS since the sync
 * watermark is parsed and ingested (dedupe is handled inside
 * [IngestionService.ingestSms], so re-syncs are idempotent), and the screen
 * lists the ledger's `buffer` entries. Import = confirm, Dismiss = void.
 */
@Singleton
class InboxRepository @Inject constructor(
    private val messageReader: MessageReader,
    private val appMetadataRepository: AppMetadataRepository,
    private val ingestionService: IngestionService,
    private val ledgerApi: LedgerApi,
    private val provenanceDao: EntryProvanceDao,
    private val accountDao: AccountDao,
    private val counterpartyDao: CounterpartyDao,
    private val categoryDao: CategoryDao,
    private val settingsRepository: SettingsRepository,
    private val notifier: TransactionNotifier,
) {

    private val _bufferItems = MutableStateFlow<List<InboxItem>>(emptyList())
    val bufferItems: StateFlow<List<InboxItem>> = _bufferItems.asStateFlow()

    /**
     * One successfully captured (parsed + freshly ingested) bank SMS.
     * Returned by [ingestRawSms] so bulk syncs can alert on just-happened
     * transactions the live SMS receiver missed. Duplicates return null.
     */
    data class CapturedSms(
        val party: String,
        val amountPaise: Long,
        val isDebit: Boolean,
        val happenedAt: Long,
    )

    /**
     * Read real bank SMS since the last sync watermark, parse and ingest each
     * one, then advance the watermark and reload the buffer list.
     *
     * Notifications: the LIVE receive path ([SmsBroadcastReceiver]) alerts on
     * capture. But on many OEM ROMs that broadcast is suppressed while the app
     * is backgrounded — in that case the transaction is only caught HERE, on
     * the next inbox sync. So a sync also alerts for captures that happened
     * within the last [FRESH_CAPTURE_WINDOW_MS] (bounded by
     * [MAX_FRESH_NOTIFICATIONS]); older backfill stays silent.
     */
    suspend fun syncFromSms(freshWindowMs: Long = FRESH_CAPTURE_WINDOW_MS) {
        val metadata = appMetadataRepository.get()
        val from = metadata?.lastSmsSync ?: (System.currentTimeMillis() - DEFAULT_LOOKBACK_MS)

        val freshCutoff = System.currentTimeMillis() - freshWindowMs
        val freshCaptures = mutableListOf<CapturedSms>()

        val messages = messageReader.readSince(from)
        for (message in messages) {
            ingestRawSms(
                sender = message.address.orEmpty(),
                body = message.body.orEmpty(),
                timestamp = message.date,
            )?.takeIf { it.happenedAt >= freshCutoff }
                ?.let(freshCaptures::add)
        }

        appMetadataRepository.updateLastSmsSync(System.currentTimeMillis())
        refreshBuffer()

        freshCaptures
            .take(MAX_FRESH_NOTIFICATIONS)
            .forEach {
                notifier.postCapturedTransaction(
                    party = it.party,
                    amountPaise = it.amountPaise,
                    isDebit = it.isDebit,
                    accountName = null,
                )
            }
    }

    /**
     * Parse + ingest one SMS. Unrecognized senders/bodies are ignored.
     *
     * Returns the capture for freshly parsed entries (null for
     * unrecognized/empty/duplicate messages) so bulk syncs can alert.
     *
     * [notifyUser] posts a "captured" notification immediately — only the
     * live receive path sets it. Bulk syncs pass false and notify themselves
     * for just-happened captures (see [syncFromSms]).
     */
    suspend fun ingestRawSms(
        sender: String,
        body: String,
        timestamp: Long,
        notifyUser: Boolean = false,
    ): CapturedSms? {
        if (sender.isBlank() || body.isBlank()) return null

        val parsed = BankParserFactory.parse(
            smsBody = body,
            sender = sender,
            timestamp = timestamp
        )
        if (parsed == null) {
            // DEBUG-ONLY probe: in debug builds, ANY message from ANY sender
            // is treated as a bank message so the notification flow can be
            // exercised without waiting for a real bank SMS. Nothing is
            // ingested into the ledger — only the alert path fires.
            if (BuildConfig.DEBUG) notifyDebugCapture(sender, body, notifyUser)
            return null
        }

        val amountPaise = parsed.amount
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        if (amountPaise <= 0) return null

        // INCOME is money in. Everything else — EXPENSE, and CREDIT (which
        // every parser uses for credit-card SPEND, e.g. "Spent INR 80 … Avl
        // Limit") — is money out. Mapping CREDIT to IN wrongly booked credit
        // card purchases as income.
        val direction = when (parsed.type) {
            TransactionType.INCOME -> Direction.IN
            else -> Direction.OUT
        }

        val result = ingestionService.ingestSms(
            IngestionService.SmsParseFacts(
                sender = sender,
                bank = parsed.bankName,
                last4 = parsed.accountLast4,
                smsAccountKind = if (parsed.isFromCard) "credit_card" else "savings",
                idKinds = if (parsed.isFromCard) {
                    setOf(IngestionService.ID_KIND_CARD)
                } else {
                    null
                },
                amountPaise = amountPaise,
                direction = direction,
                counterpartyRaw = parsed.merchant,
                refId = parsed.reference,
                rawText = body,
                happenedAt = timestamp,
                occurredOn = timestamp,
            )
        )

        // Duplicates are silently ignored — no capture, no notification.
        if (result !is IngestionService.SmsIngestResult.Parsed) return null

        if (notifyUser) {
            notifier.postCapturedTransaction(
                party = parsed.merchant ?: parsed.bankName,
                amountPaise = amountPaise,
                isDebit = direction == Direction.OUT,
                accountName = null,
            )
        }
        return CapturedSms(
            party = parsed.merchant ?: parsed.bankName,
            amountPaise = amountPaise,
            isDebit = direction == Direction.OUT,
            happenedAt = timestamp,
        )
    }

    // ── buffer list & actions ──

    /**
     * Re-run orphan claiming for every active user account. Attach-only and
     * near-free when the unmatched pot is empty, so it can run on every buffer
     * refresh — this heals entries stranded by a late account edit or a
     * matching-rule fix without waiting for the next account creation.
     */
    private suspend fun claimOrphans() {
        accountDao.listAll()
            .filter { it.isActive && !it.slug.startsWith("sys-") && it.bank != null }
            .forEach { account ->
                runCatching { ingestionService.claimOrphansForAccount(account.id) }
            }
    }

    /** Reload the ledger's buffer entries — this IS the inbox. */
    suspend fun refreshBuffer() {
        claimOrphans()
        val entries = ledgerApi.listTransactions(status = TransactionStatus.BUFFER)
        val accountNames = accountDao.listAll().associate { it.id to it.name }
        val userAccounts = accountDao.listAll()
            .filter { !it.slug.startsWith("sys-") }
            .map { it.id to it.name }
        val partyNames = counterpartyDao.listAll().associate { it.id to it.displayName }
        val categoryIds = entries.mapNotNull { it.categoryId }.distinct()
        val categoryNames = if (categoryIds.isEmpty()) {
            emptyMap()
        } else {
            categoryDao.getByIds(categoryIds).associate { it.id to it.name }
        }
        _bufferItems.value = entries.map { entry ->
            enrich(entry, accountNames, userAccounts, partyNames, categoryNames)
        }
    }

    /**
     * Import = confirm the buffer entry (moves it into the real ledger).
     * If an [ActiveContext] is running (e.g. a trip tag), it is stamped onto
     * the entry at confirm time — the bulk ✓-through-a-trip workflow.
     */
    suspend fun import(entryId: Long) {
        val contextTag = settingsRepository.activeContext.first()?.tag
        if (contextTag == null) {
            ledgerApi.confirmTransaction(entryId)
        } else {
            ledgerApi.confirmTransaction(entryId, listOf(contextTag))
        }
        refreshBuffer()
    }

    /** Re-point an entry's account leg onto the user's chosen account. */
    suspend fun assignAccount(entryId: Long, accountId: Long) {
        ledgerApi.assignAccount(entryId, accountId)
        refreshBuffer()
    }

    /** Dismiss = soft-delete (void) the buffer entry. */
    suspend fun dismiss(entryId: Long) {
        ledgerApi.voidTransaction(entryId, "Dismissed from inbox")
        refreshBuffer()
    }

    /**
     * DEBUG-ONLY: fire the capture alert for a message no bank parser matched,
     * so the notification flow (including every gate inside
     * [TransactionNotifier.postCapturedTransaction] and its reason logging) can
     * be tested from an arbitrary sender. Respects the same [notifyUser]
     * contract as the real path — only the live receive path alerts.
     */
    private suspend fun notifyDebugCapture(sender: String, body: String, notifyUser: Boolean) {
        // Same tag as TransactionNotifier so one logcat filter shows the whole flow.
        Log.i("TransactionNotifier", "DEBUG capture: sender '$sender' matched no bank parser — treating as bank message")
        if (!notifyUser) return
        notifier.postCapturedTransaction(
            party = "$sender (debug)",
            amountPaise = debugAmountPaise(body),
            isDebit = !DEBUG_INCOME_HINTS.any { body.lowercase().contains(it) },
            accountName = "DEBUG",
        )
    }

    /** First number in the body as paise; falls back to ₹100.00 when none. */
    private fun debugAmountPaise(body: String): Long {
        val fallback = 100_00L
        val value = Regex("""\d+(?:,\d{3})*(?:\.\d+)?""").find(body)
            ?.value
            ?.replace(",", "")
            ?.toBigDecimalOrNull()
            ?: return fallback
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
            .takeIf { it > 0 } ?: fallback
    }

    private suspend fun enrich(
        entry: TransactionView,
        accountNames: Map<Long, String>,
        userAccounts: List<Pair<Long, String>>,
        partyNames: Map<Long, String>,
        categoryNames: Map<Long, String>,
    ): InboxItem {
        val provenance = provenanceDao.getByEntry(entry.id)
        val bank = provenance?.parsedFacts
            ?.substringBefore('|')
            ?.takeIf { it.isNotBlank() }
        val party = entry.counterpartyId?.let { partyNames[it] }
        val accountName = entry.accountId?.let { accountNames[it] }
        val userAccountIds = userAccounts.mapTo(mutableSetOf()) { it.first }
        return InboxItem(
            entryId = entry.id,
            // Title priority: the resolved payee, then the bank. The ACCOUNT
            // must never be the title — it says nothing about who the
            // transaction was with, and it made every row read as "account +
            // time". (The account still shows as the red correction callout
            // for unmatched entries, and in the detail view.)
            sender = party ?: bank ?: "Bank SMS",
            amountPaise = entry.amountPaise,
            isDebit = entry.direction == Direction.OUT,
            account = accountName,
            date = entry.occurredOn,
            rawSms = provenance?.rawText.orEmpty().ifBlank { entry.note.orEmpty() },
            categoryId = entry.categoryId,
            tags = entry.tags,
            // The system contra category ("Unclassified") is bookkeeping
            // noise, not a category — hide it until a real one is assigned.
            category = entry.categoryId
                ?.let { categoryNames[it] }
                ?.takeIf { it !in LedgerService.SYSTEM_CATEGORY_NAMES },
            assignedAccountId = entry.accountId?.takeIf { it in userAccountIds },
            accountChoices = userAccounts,
        )
    }

    companion object {
        /** DEBUG capture: body hints that make it alert as INCOME instead of spend. */
        private val DEBUG_INCOME_HINTS = listOf("received", "credited", "deposited", "refunded")

        /** When no watermark exists yet, scan the last 30 days of SMS. */
        const val DEFAULT_LOOKBACK_MS = 30L * 24 * 60 * 60 * 1000

        /**
         * A capture newer than this is "just happened" — a sync that catches
         * one alerts the user (the live receiver likely never fired). Older
         * backfill is silent: a 30-day first sync must not fire 50 alerts.
         */
        const val FRESH_CAPTURE_WINDOW_MS = 10L * 60 * 1000

        /** Cap on per-capture notifications from a single sync. */
        const val MAX_FRESH_NOTIFICATIONS = 5
    }

    /** One bank account spotted in the SMS history but not yet in the app. */
    data class DetectedAccount(
        val bank: String,
        val last4: String,
        val isCard: Boolean,
        val transactionCount: Int,
    ) {
        /** Stable key for selection state in the UI. */
        val key: String get() = "$bank|$last4"
        val suggestedName: String get() = "$bank ${if (isCard) "card" else "a/c"} ••$last4"
    }

    data class ScanReport(
        val messagesRead: Int,
        val transactionsDetected: Int,
        val accounts: List<DetectedAccount>,
    )

    /**
     * Onboarding scan: read bank SMS from [startMillis], ingest everything
     * (dedupe makes re-runs idempotent), advance the watermark, and report the
     * distinct bank accounts seen in the messages that the app doesn't know
     * about yet — so the user can create them in one tap.
     */
    suspend fun scanFrom(startMillis: Long): ScanReport {
        val messages = messageReader.readSince(startMillis)

        val existingLast4 = accountDao.listAll().mapNotNull { it.last4 }.toSet()
        val detected = LinkedHashMap<String, DetectedAccount>()
        var parsedCount = 0

        for (message in messages) {
            val body = message.body.orEmpty()
            val sender = message.address.orEmpty()
            val parsed = BankParserFactory.parse(body, sender, message.date) ?: continue
            parsedCount++
            ingestRawSms(sender = sender, body = body, timestamp = message.date)

            val last4 = parsed.accountLast4
            if (last4.isNullOrBlank() || last4 in existingLast4) continue
            val key = "${parsed.bankName}|$last4"
            val existing = detected[key]
            detected[key] = if (existing == null) {
                DetectedAccount(
                    bank = parsed.bankName,
                    last4 = last4,
                    isCard = parsed.isFromCard,
                    transactionCount = 1,
                )
            } else {
                existing.copy(transactionCount = existing.transactionCount + 1)
            }
        }

        appMetadataRepository.updateLastSmsSync(System.currentTimeMillis())
        refreshBuffer()

        return ScanReport(
            messagesRead = messages.size,
            transactionsDetected = parsedCount,
            accounts = detected.values.sortedByDescending { it.transactionCount },
        )
    }
}
