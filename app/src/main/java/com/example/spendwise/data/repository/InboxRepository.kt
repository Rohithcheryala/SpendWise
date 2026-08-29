package com.example.spendwise.data.repository


import com.example.spendwise.backend.api.Direction
import com.example.spendwise.backend.api.EntryStatus
import com.example.spendwise.backend.api.EntryView
import com.example.spendwise.backend.api.LedgerApi
import com.example.spendwise.backend.service.IngestionService
import com.example.spendwise.core.messages.MessageReader
import com.example.spendwise.core.parser_pw.TransactionType
import com.example.spendwise.core.parser_pw.bank.BankParserFactory
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.CounterpartyDao
import com.example.spendwise.data.database.dao.EntryProvanceDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {

    private val _bufferItems = MutableStateFlow<List<InboxItem>>(emptyList())
    val bufferItems: StateFlow<List<InboxItem>> = _bufferItems.asStateFlow()

    /**
     * Read real bank SMS since the last sync watermark, parse and ingest each
     * one, then advance the watermark and reload the buffer list.
     */
    suspend fun syncFromSms() {
        val metadata = appMetadataRepository.get()
        val from = metadata?.lastSmsSync ?: (System.currentTimeMillis() - DEFAULT_LOOKBACK_MS)

        val messages = messageReader.readSince(from)
        for (message in messages) {
            ingestRawSms(
                sender = message.address.orEmpty(),
                body = message.body.orEmpty(),
                timestamp = message.date,
            )
        }

        appMetadataRepository.updateLastSmsSync(System.currentTimeMillis())
        refreshBuffer()
    }

    /** Parse + ingest one SMS. Unrecognized senders/bodies are ignored. */
    suspend fun ingestRawSms(sender: String, body: String, timestamp: Long) {
        if (sender.isBlank() || body.isBlank()) return

        val parsed = BankParserFactory.parse(
            smsBody = body,
            sender = sender,
            timestamp = timestamp
        ) ?: return

        val amountPaise = parsed.amount
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        if (amountPaise <= 0) return

        // INCOME is money in. Everything else — EXPENSE, and CREDIT (which
        // every parser uses for credit-card SPEND, e.g. "Spent INR 80 … Avl
        // Limit") — is money out. Mapping CREDIT to IN wrongly booked credit
        // card purchases as income.
        val direction = when (parsed.type) {
            TransactionType.INCOME -> Direction.IN
            else -> Direction.OUT
        }

        ingestionService.ingestSms(
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
        val entries = ledgerApi.listEntries(status = EntryStatus.BUFFER)
        val accountNames = accountDao.listAll().associate { it.id to it.name }
        val userAccounts = accountDao.listAll()
            .filter { !it.slug.startsWith("sys-") }
            .map { it.id to it.name }
        val partyNames = counterpartyDao.listAll().associate { it.id to it.displayName }
        _bufferItems.value = entries.map { entry ->
            enrich(entry, accountNames, userAccounts, partyNames)
        }
    }

    /** Import = confirm the buffer entry (moves it into the real ledger). */
    suspend fun import(entryId: Long) {
        ledgerApi.confirmEntry(entryId)
        refreshBuffer()
    }

    /** Re-point an entry's account leg onto the user's chosen account. */
    suspend fun assignAccount(entryId: Long, accountId: Long) {
        ledgerApi.assignAccount(entryId, accountId)
        refreshBuffer()
    }

    /** Dismiss = soft-delete (void) the buffer entry. */
    suspend fun dismiss(entryId: Long) {
        ledgerApi.voidEntry(entryId, "Dismissed from inbox")
        refreshBuffer()
    }

    private suspend fun enrich(
        entry: EntryView,
        accountNames: Map<Long, String>,
        userAccounts: List<Pair<Long, String>>,
        partyNames: Map<Long, String>,
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
            // Title priority: receiver/counterparty, then the matched account,
            // then the bank. The bank alone is redundant — the account name
            // already implies it — and hides who the transaction was with.
            sender = party ?: accountName ?: bank ?: "Bank SMS",
            amountPaise = entry.amountPaise,
            isDebit = entry.direction == Direction.OUT,
            account = accountName,
            date = entry.occurredOn,
            rawSms = provenance?.rawText.orEmpty().ifBlank { entry.note.orEmpty() },
            categoryId = entry.categoryId,
            tags = entry.tags,
            assignedAccountId = entry.accountId?.takeIf { it in userAccountIds },
            accountChoices = userAccounts,
        )
    }

    companion object {
        /** When no watermark exists yet, scan the last 30 days of SMS. */
        const val DEFAULT_LOOKBACK_MS = 30L * 24 * 60 * 60 * 1000
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
