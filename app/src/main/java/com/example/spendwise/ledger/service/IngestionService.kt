package com.example.spendwise.ledger.service

import com.example.spendwise.ledger.api.Direction
import com.example.spendwise.ledger.api.TransactionSource
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.api.IngestRequest
import com.example.spendwise.ledger.api.Intent
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.AccountIdentifierDao
import com.example.spendwise.data.database.dao.TransactionDao
import com.example.spendwise.data.database.dao.TransactionLineDao
import com.example.spendwise.data.database.dao.TransactionProvenanceDao
import com.example.spendwise.data.database.dao.TagDao
import com.example.spendwise.data.database.entity.AccountEntity
import javax.inject.Inject

/**
 * The SMS -> ledger bridge. Port of the old server's routers/sms.py core:
 * dedupe, account matching by bank + last-4 identifier (kind-aware), orphan
 * parking, QR-scan merge, and retroactive orphan claiming.
 *
 * The device-side parser hands over structured parse facts; everything here is
 * accounting policy and writes go through LedgerApi only.
 */
class IngestionService @Inject constructor(
    private val accountDao: AccountDao,
    private val identifierDao: AccountIdentifierDao,
    private val transactionDao: TransactionDao,
    private val transactionLineDao: TransactionLineDao,
    private val provenanceDao: TransactionProvenanceDao,
    private val tagDao: TagDao,
    private val ledger: LedgerService,
    private val counterparties: CounterpartyService,
    private val contacts: ContactsService,
) {

    /** What a device-side parser extracts from one bank SMS. */
    data class SmsParseFacts(
        val sender: String,
        val bank: String?,
        val last4: String?,
        /** Parser vocabulary: "savings" | "credit_card" (see KIND_BRIDGE). */
        val smsAccountKind: String?,
        /**
         * Channel constraint when known: "account" for UPI/NEFT narration,
         * "card" for POS/ATM. Null matches both identifier kinds.
         */
        val idKinds: Set<String>? = null,
        val amountPaise: Long,
        val direction: Direction,
        val counterpartyRaw: String? = null,
        val refId: String? = null,
        val rawText: String,
        val happenedAt: Long? = null,
        val occurredOn: Long,
    )

    sealed interface SmsIngestResult {
        /** Fresh buffer entry created (an orphan when accountId is null). */
        data class Parsed(val transactionId: Long, val accountId: Long?, val mergedQrEntryId: Long?) :
            SmsIngestResult

        /** The same SMS was ingested before — nothing booked twice. */
        data class Duplicate(val transactionId: Long) : SmsIngestResult
    }

    /**
     * Ingest one parsed bank SMS, in the old webhook's order: dedupe by stable
     * hash -> match account (bank contains + kind bridge + active identifier
     * on the SMS's channel) -> resolve counterparty (VPA first, then narration)
     * -> default intent by direction -> buffer entry with provenance carrying
     * frozen parse facts -> attempt the QR merge.
     */
    suspend fun ingestSms(facts: SmsParseFacts): SmsIngestResult {
        val hash = Text.smsHash(facts.sender, facts.rawText)
        ledger.findTransactionByDedupeHash(hash)?.let { return SmsIngestResult.Duplicate(it.id) }

        val account = findAccount(facts.bank, facts.last4, facts.smsAccountKind, facts.idKinds)

        // The VPA is the strongest cross-source key; fall back to the slug.
        // A phone-prefixed handle resolves to a saved contact name first —
        // that becomes the friendly display; the handle itself stays an alias.
        val vpa = Text.extractVpa(facts.rawText)
        val contactHint = contacts.resolveContactName(facts.rawText)
        val cpId = counterparties.findByAny(LedgerService.USER_ID, vpa, facts.counterpartyRaw)
            ?: counterparties.resolveOrCreate(
                LedgerService.USER_ID,
                facts.counterpartyRaw ?: vpa,
                displayName = contactHint,
                aliasSource = if (vpa != null && facts.counterpartyRaw == null) "upi" else "sms",
            )

        val view = ledger.ingest(
            IngestRequest(
                amountPaise = facts.amountPaise,
                direction = facts.direction,
                occurredOn = facts.occurredOn,
                accountId = account?.id,
                counterpartyId = cpId,
                intent = defaultIntent(facts.direction),
                status = TransactionStatus.BUFFER,
                source = TransactionSource.SMS,
                happenedAt = facts.happenedAt,
                rawText = facts.rawText,
                dedupeHash = hash,
                bankRef = facts.refId,
                parsedFacts = listOfNotNull(facts.bank, facts.last4, facts.smsAccountKind)
                    .joinToString("|").takeIf { it.isNotBlank() },
            )
        )

        val mergedQrId = tryMergeQrScanBuffer(view.id)
        return SmsIngestResult.Parsed(view.id, account?.id, mergedQrId)
    }

    /**
     * Port of `_find_account`: an SMS attaches only when the bank matches
     * (case-insensitive contains), the account is active, non-system and
     * non-category, its account class is reachable from the parser kind via
     * [KIND_BRIDGE], AND it owns an active identifier equal to the SMS last-4
     * on the channel used. Unknown parser kinds map to nothing — orphan,
     * never a wrong account.
     */
    suspend fun findAccount(
        bank: String?,
        last4: String?,
        smsAccountKind: String?,
        idKinds: Set<String>? = null,
    ): AccountEntity? {
        if (last4.isNullOrBlank() || bank == null) return null
        val dbClasses = KIND_BRIDGE[smsAccountKind] ?: return null
        val identifiers = identifierDao.getActiveByValue(last4)
            .filter { idKinds == null || it.kind in idKinds }
        val allowedIds = identifiers.map { it.accountId }.toSet()
        if (allowedIds.isEmpty()) return null
        return accountDao.listAll().firstOrNull { acct ->
            acct.id in allowedIds &&
                !acct.isArchived &&
                !acct.isSystem &&
                acct.accountClass in dbClasses &&
                acct.bank?.contains(bank, ignoreCase = true) == true &&
                // A card SMS may only ride an asset account through a CARD-kind
                // identifier (otherwise a coincidental account-number match
                // would misbook the spend). A liability is always fair game —
                // that is what a credit card is.
                (smsAccountKind != "credit_card" ||
                    acct.accountClass == LedgerService.CLASS_LIABILITY ||
                    identifiers.any { it.accountId == acct.id && it.kind == ID_KIND_CARD })
        }
    }

    /**
     * Retroactively attach account-less SMS transactions that now match [accountId].
     *
     * Orphans were frozen on the unmatched pot at ingest time. Instead of
     * re-parsing raw text (parser drift), we re-run matching against the parse
     * facts frozen into provenance. Attach-only — never detaches — so it is
     * safe to re-run on every account create/edit. Returns count claimed.
     */
    suspend fun claimOrphansForAccount(accountId: Long): Int {
        val account = accountDao.getById(accountId)
            ?: throw com.example.spendwise.ledger.api.ApiException.NotFound("account", accountId)
        val hasIdentifier = identifierDao.getByAccountList(accountId).any { it.isActive }
        if (account.bank == null || !hasIdentifier) return 0

        val unmatchedPot = ledger.systemAccount(LedgerService.SystemRole.UNMATCHED).id
        var claimed = 0
        for (transactionId in transactionLineDao.transactionIdsForAccount(unmatchedPot)) {
            val entry = transactionDao.getById(transactionId) ?: continue
            if (entry.source != TransactionSource.SMS || entry.voidedAt != null) continue

            val prov = provenanceDao.getByTransaction(transactionId) ?: continue
            val parts = prov.parsedFacts?.split("|") ?: continue
            if (parts.size < 3) continue

            val match = findAccount(parts[0], parts[1], parts[2])
            if (match?.id == accountId) {
                // The orphan's parked leg is the unmatched pot line itself.
                val line = transactionLineDao.getByTransactionList(transactionId)
                    .firstOrNull { it.accountId == unmatchedPot }
                    ?: continue
                transactionLineDao.update(line.copy(accountId = accountId))
                claimed++
            }
        }
        return claimed
    }

    /**
     * Port of `_try_merge_qr_scan_buffer`: when a bank SMS lands within the
     * merge window describing the same payment as exactly one recent QR-scan
     * buffer entry (same direction, amount within ±1%), they are ONE payment.
     * Bank facts stay on the SMS entry; the user's scan intent fills blanks
     * (account, party, note; category unless SMS contra is still suspense).
     * Tags union. Intent + proof = finished transaction: auto-confirm and
     * delete the redundant QR entry. Returns the deleted QR id or null.
     */
    suspend fun tryMergeQrScanBuffer(
        smsEntryId: Long,
        nowMillis: Long = System.currentTimeMillis(),
        tolerance: Double = QR_SMS_MERGE_TOLERANCE,
        windowMinutes: Long = QR_SMS_MERGE_WINDOW_MINUTES,
    ): Long? {
        val smsEntry = transactionDao.getById(smsEntryId) ?: return null
        val smsAd = amountDirection(smsEntryId) ?: return null

        val candidates = transactionDao.recentBufferQrScans(
            cutoffMillis = nowMillis - windowMinutes * 60_000,
            excludeId = smsEntryId,
        )
        val matches = candidates.filter { qr ->
            val ad = amountDirection(qr.id)
            ad != null && ad.second == smsAd.second &&
                amountsMatch(
                    a = ad.first,
                    b = smsAd.first,
                    tolerance = tolerance,
                    increaseTolerance = QR_SMS_MERGE_INCREASE_TOLERANCE,
                )
        }
        if (matches.size != 1) return null
        val qr = matches[0]

        val smsLines = transactionLineDao.getByTransactionList(smsEntryId).toMutableList()
        val qrLines = transactionLineDao.getByTransactionList(qr.id)

        // Tags: union, order-preserving, deduped — from the join tables now.
        val mergedTags = LinkedHashSet(tagDao.namesFor(smsEntryId))
        mergedTags.addAll(tagDao.namesFor(qr.id))

        // If the SMS couldn't name an account but the user's scan did, adopt it.
        val unmatchedPot = ledger.systemAccount(LedgerService.SystemRole.UNMATCHED).id
        suspend fun isRealAccountLeg(accountId: Long): Boolean {
            val acct = accountDao.getById(accountId) ?: return false
            return !acct.isSystem &&
                (acct.accountClass == LedgerService.CLASS_ASSET ||
                    acct.accountClass == LedgerService.CLASS_LIABILITY)
        }
        val smsAcctIdx = smsLines.indexOfFirst { isRealAccountLeg(it.accountId) }
        val qrAcct = qrLines.firstOrNull { it.accountId != unmatchedPot && isRealAccountLeg(it.accountId) }
        if (smsAcctIdx >= 0 && qrAcct != null && smsLines[smsAcctIdx].accountId == unmatchedPot) {
            smsLines[smsAcctIdx] = smsLines[smsAcctIdx].copy(accountId = qrAcct.accountId)
        }

        // Category: adopt the scan's deliberate pick when the SMS contra is
        // still the system suspense category account.
        val unclassified = ledger.systemCategoryAccount(LedgerService.CLASS_EXPENSE, "Unclassified").id
        suspend fun categoryLeg(accountId: Long): AccountEntity? {
            val acct = accountDao.getById(accountId) ?: return null
            return acct.takeIf {
                it.accountClass == LedgerService.CLASS_EXPENSE ||
                    it.accountClass == LedgerService.CLASS_INCOME
            }
        }
        val qrCat = qrLines.firstOrNull { categoryLeg(it.accountId) != null && it.accountId != unclassified }
        val smsCatIdx = smsLines.indexOfFirst {
            accountDao.getById(it.accountId)?.isSystem == true && categoryLeg(it.accountId) != null
        }
        if (qrCat != null && smsCatIdx >= 0 && smsLines[smsCatIdx].accountId == unclassified) {
            smsLines[smsCatIdx] = smsLines[smsCatIdx].copy(accountId = qrCat.accountId)
        }

        // Persist merged SMS entry: user intent + bank proof = confirmed.
        transactionDao.update(
            smsEntry.copy(
                counterpartyId = smsEntry.counterpartyId ?: qr.counterpartyId,
                note = smsEntry.note ?: qr.note,
                status = TransactionStatus.CONFIRMED,
            )
        )
        ledger.setTags(smsEntryId, mergedTags.toList())
        smsLines.forEach { transactionLineDao.update(it) }

        // The QR entry is now redundant.
        transactionLineDao.deleteByTransaction(qr.id)
        transactionDao.getById(qr.id)?.let { transactionDao.delete(it) }
        return qr.id
    }

    /** (magnitude, direction) of an ingested transaction, from its single real-account leg. */
    private suspend fun amountDirection(transactionId: Long): Pair<Long, Direction>? {
        val line = transactionLineDao.getByTransactionList(transactionId)
            .firstOrNull { ln ->
                val acct = accountDao.getById(ln.accountId)
                acct != null && !acct.isSystem &&
                    (acct.accountClass == LedgerService.CLASS_ASSET ||
                        acct.accountClass == LedgerService.CLASS_LIABILITY)
            } ?: return null
        return kotlin.math.abs(line.amountPaise) to
            (if (line.amountPaise > 0) Direction.IN else Direction.OUT)
    }

    companion object {
        /** Equal-or-within-tolerance, in paise. tolerance=0.01 means ±1%. */
        /**
         * Match rule for folding a QR scan (a) into the landing bank SMS (b).
         *
         * [tolerance] is the symmetric band from naa's `_amounts_match`
         * (±1% of the larger amount). [increaseTolerance] additionally lets
         * the bank SMS amount EXCEED the scanned amount by up to that
         * fraction: UPI apps add a convenience/platform fee at the last
         * confirmation step, so the debit is marginally larger than the QR
         * promised. A decrease beyond the symmetric band still fails — fees
         * never make you pay less, a mismatch means it's a different payment.
         */
        fun amountsMatch(
            a: Long,
            b: Long,
            tolerance: Double,
            increaseTolerance: Double = 0.0,
        ): Boolean {
            if (a == b) return true
            if (tolerance > 0) {
                val symmetricAllowed = maxOf(1L, Math.round(maxOf(a, b) * tolerance))
                if (Math.abs(a - b) <= symmetricAllowed) return true
            }
            if (increaseTolerance > 0 && b > a) {
                val increaseAllowed = maxOf(1L, Math.round(a * increaseTolerance))
                return b - a <= increaseAllowed
            }
            return false
        }

        /** Money in defaults to income, money out to expense. */
        fun defaultIntent(direction: Direction): String =
            if (direction == Direction.IN) Intent.INCOME else Intent.EXPENSE

        /**
         * Parser account-kind -> stored account-class bridge (the Rust shape:
         * accounts carry class/subtype, not the old free-form `kind`).
         * Unknown parser kinds intentionally map to nothing (stay orphans).
         *
         * "credit_card" covers BOTH plastic channels: a credit card
         * (liability account) and a debit card (the asset account owning
         * that card — the parser only says card vs non-card; the
         * identifier-kind check is what stops a card number from hijacking
         * an account-number match).
         * "savings" covers every asset pot the app creates (savings,
         * checking, cash, wallet).
         */
        val KIND_BRIDGE: Map<String, Set<String>> = mapOf(
            "credit_card" to setOf(LedgerService.CLASS_LIABILITY, LedgerService.CLASS_ASSET),
            "savings" to setOf(LedgerService.CLASS_ASSET),
        )

        const val QR_SMS_MERGE_TOLERANCE = 0.01

        /** Last-step convenience fees make the bank debit marginally LARGER
         *  than the scanned amount — allowed up to 2% on top of the symmetric
         *  ±1% band. See [amountsMatch]. */
        const val QR_SMS_MERGE_INCREASE_TOLERANCE = 0.02

        const val QR_SMS_MERGE_WINDOW_MINUTES = 30L
        const val NOTIFICATION_SMS_MERGE_WINDOW_MINUTES = 3L

        const val ID_KIND_ACCOUNT = "account"
        const val ID_KIND_CARD = "card"
    }
}