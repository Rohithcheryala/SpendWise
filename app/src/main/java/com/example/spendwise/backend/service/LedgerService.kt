package com.example.spendwise.backend.service

import androidx.room.withTransaction
import com.example.spendwise.backend.api.ApiException
import com.example.spendwise.backend.api.CreateEntryRequest
import com.example.spendwise.backend.api.Direction
import com.example.spendwise.backend.api.EntryKind
import com.example.spendwise.backend.api.EntrySource
import com.example.spendwise.backend.api.EntryStatus
import com.example.spendwise.backend.api.EntryView
import com.example.spendwise.backend.api.FriendBalance
import com.example.spendwise.backend.api.IngestRequest
import com.example.spendwise.backend.api.Intent
import com.example.spendwise.backend.api.LedgerApi
import com.example.spendwise.backend.api.LineSpec
import com.example.spendwise.backend.api.SplitRequest
import com.example.spendwise.data.database.AppDatabase
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.BucketDao
import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.database.dao.EntryDao
import com.example.spendwise.data.database.dao.EntryLineDao
import com.example.spendwise.data.database.dao.EntryProvanceDao
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.data.database.entity.CategoryEntity
import com.example.spendwise.data.database.entity.EntryEntity
import com.example.spendwise.data.database.entity.EntryLineEntity
import com.example.spendwise.data.database.entity.EntryProvenanceEntity
import javax.inject.Inject

/**
 * Double-entry ledger service — port of the old server's services/ledger.py.
 * The one place entries are written and balances are read.
 *
 * Invariants enforced here (SQLite can't express them all):
 *  - an entry has >= 2 lines whose amounts sum to exactly zero
 *  - each line targets exactly one of accountId / categoryId
 *  - bucketId is only valid on a real-account line
 *
 * Balances are COMPUTED at read time, never stored: an account balance is the
 * signed sum of its confirmed, non-voided lines, inverted for liabilities
 * (where the balance is the amount owed). A bucket's value is the signed sum
 * of its tagged lines.
 */
class LedgerService @Inject constructor(
    private val db: AppDatabase,
    private val accountDao: AccountDao,
    private val bucketDao: BucketDao,
    private val categoryDao: CategoryDao,
    private val entryDao: EntryDao,
    private val entryLineDao: EntryLineDao,
    private val provenanceDao: EntryProvanceDao,
    private val counterpartyService: CounterpartyService,
) : LedgerApi {

    // ─────────────────────────────────────────────────────────────────────
    // Write path
    // ─────────────────────────────────────────────────────────────────────

    override suspend fun createEntry(request: CreateEntryRequest): EntryView {
        validateLines(request.lines)
        val id = db.withTransaction {
            val now = System.currentTimeMillis()
            val entryId = entryDao.insert(
                EntryEntity(
                    occurredOn = request.occurredOn,
                    happenedAt = request.happenedAt,
                    counterpartyId = request.counterpartyId,
                    groupId = request.groupId,
                    note = request.note,
                    tags = TagCodec.encode(request.tags),
                    status = request.status,
                    source = request.source,
                    linkedEntryId = request.linkedEntryId,
                    createdAt = now,
                )
            )
            entryLineDao.insertAll(request.lines.map { it.toEntity(entryId) })
            entryId
        }
        return requireView(id)
    }

    /**
     * POST /ingest. The real account leg (or the 'unmatched' pot for an
     * orphan) signed by direction, plus one contra line chosen by intent —
     * loans to the sys-loans receivable pot, reconciliation to recon equity,
     * investments to the invest pot, everything else to an unclassified
     * income/expense category. Attaches provenance when any is given.
     */
    override suspend fun ingest(request: IngestRequest): EntryView {
        if (request.intent !in Intent.ALL) throw ApiException("unknown intent '${request.intent}'")
        if (request.amountPaise <= 0) throw ApiException("amount must be positive")
        val sign = if (request.direction == Direction.IN) 1L else -1L
        val primaryAccount = request.accountId ?: systemAccount(SystemRole.UNMATCHED).id

        val lines = listOf(
            LineSpec(
                amountPaise = sign * request.amountPaise,
                accountId = primaryAccount,
                bucketId = request.bucketId,
                balanceAfterPaise = request.balanceAfterPaise,
            ),
            contraLine(request.intent, request.direction, request.amountPaise, request.counterpartyId),
        )

        val view = createEntry(
            CreateEntryRequest(
                occurredOn = request.occurredOn,
                lines = lines,
                status = request.status,
                source = request.source,
                happenedAt = request.happenedAt,
                counterpartyId = request.counterpartyId,
                note = request.note,
                tags = request.tags,
            )
        )

        if (request.rawText != null || request.dedupeHash != null ||
            request.bankRef != null || request.parsedFacts != null
        ) {
            provenanceDao.insert(
                EntryProvenanceEntity(
                    entryId = view.id,
                    rawText = request.rawText,
                    dedupeHash = request.dedupeHash,
                    bankRef = request.bankRef,
                    parsedFacts = request.parsedFacts,
                )
            )
        }
        return view
    }

    /** Two account legs, no category — reads back as kind=transfer. */
    override suspend fun ingestTransfer(
        amountPaise: Long,
        fromAccountId: Long,
        toAccountId: Long,
        occurredOn: Long,
        status: String,
        source: String,
        rawText: String?,
        dedupeHash: String?,
        bankRef: String?,
        balanceAfterPaise: Long?,
        note: String?,
    ): EntryView {
        if (fromAccountId == toAccountId) throw ApiException("transfer legs must differ")
        val view = createEntry(
            CreateEntryRequest(
                occurredOn = occurredOn,
                status = status,
                source = source,
                note = note,
                lines = listOf(
                    LineSpec(-amountPaise, accountId = fromAccountId, balanceAfterPaise = balanceAfterPaise),
                    LineSpec(amountPaise, accountId = toAccountId),
                ),
            )
        )
        if (rawText != null || dedupeHash != null || bankRef != null) {
            provenanceDao.insert(
                EntryProvenanceEntity(
                    entryId = view.id,
                    rawText = rawText,
                    dedupeHash = dedupeHash,
                    bankRef = bankRef,
                )
            )
        }
        return view
    }

    /** The single balancing line for an ingested entry, chosen by intent. */
    private suspend fun contraLine(
        intent: String,
        direction: Direction,
        amountPaise: Long,
        counterpartyId: Long?,
    ): LineSpec {
        val contra = if (direction == Direction.OUT) amountPaise else -amountPaise
        return when (intent) {
            Intent.LOAN, Intent.LOAN_REPAYMENT ->
                LineSpec(
                    contra,
                    accountId = systemAccount(SystemRole.RECEIVABLE).id,
                    counterpartyId = counterpartyId,
                )

            Intent.RECONCILIATION ->
                LineSpec(contra, accountId = systemAccount(SystemRole.RECON_EQUITY).id)

            Intent.INVESTMENT ->
                LineSpec(contra, accountId = systemAccount(SystemRole.INVESTMENT).id)

            else -> {
                val cat = if (direction == Direction.OUT) {
                    systemCategory(KIND_EXPENSE, "Unclassified")
                } else {
                    systemCategory(KIND_INCOME, "Uncategorized income")
                }
                LineSpec(contra, categoryId = cat.id)
            }
        }
    }

    private fun validateLines(lines: List<LineSpec>) {
        if (lines.size < 2) throw ApiException("an entry needs at least two lines")
        val total = lines.sumOf { it.amountPaise }
        if (total != 0L) throw ApiException("entry lines must sum to zero (got $total)")
        for (ln in lines) {
            if ((ln.accountId == null) == (ln.categoryId == null)) {
                throw ApiException("each line targets exactly one of accountId / categoryId")
            }
            if (ln.bucketId != null && ln.accountId == null) {
                throw ApiException("bucketId is only valid on a real-account line")
            }
        }
    }

    private fun LineSpec.toEntity(entryId: Long) = EntryLineEntity(
        entryId = entryId,
        accountId = accountId,
        categoryId = categoryId,
        bucketId = bucketId,
        counterpartyId = counterpartyId,
        amountPaise = amountPaise,
        balanceAfterPaise = balanceAfterPaise,
    )

    // ─────────────────────────────────────────────────────────────────────
    // Read path
    // ─────────────────────────────────────────────────────────────────────

    override suspend fun getEntry(id: Long): EntryView? {
        val entry = entryDao.getById(id) ?: return null
        return buildView(entry, entryLineDao.getByEntryList(id))
    }

    private suspend fun requireView(id: Long): EntryView =
        getEntry(id) ?: throw ApiException("entry $id vanished after write")

    override suspend fun listEntries(
        status: String?,
        from: Long?,
        to: Long?,
    ): List<EntryView> =
        entryDao.listSuspend(status, from, to).map { entry ->
            buildView(entry, entryLineDao.getByEntryList(entry.id))
        }

    override suspend fun findEntryByDedupeHash(dedupeHash: String): EntryView? {
        val prov = provenanceDao.getByDedupeHash(dedupeHash) ?: return null
        return getEntry(prov.entryId)
    }

    /**
     * Signed sum of an account's confirmed, non-voided lines. Liabilities are
     * inverted (balance = amount owed). [through] bounds it for reconciliation
     * continuity checks.
     */
    override suspend fun accountBalance(accountId: Long, through: Long?): Long {
        val account = accountDao.getById(accountId)
            ?: throw ApiException("account $accountId not found")
        val total = entryLineDao.sumConfirmedForAccount(accountId, through)
        return if (account.kind == "liability") -total else total
    }

    /** Sum of confirmed, non-voided lines tagged to this bucket. */
    override suspend fun bucketValue(bucketId: Long, through: Long?): Long =
        entryLineDao.sumConfirmedForBucket(bucketId, through)


    /**
     * Derive an [EntryView] from the lines so the client needs no accounting
     * logic. Classification precedence mirrors the old server's describe_entry:
     * opening/reconciliation equity, bucket allocation, loans/splits on the
     * receivable pot, investment, transfer, then expense/income by category kind.
     */
    private suspend fun buildView(entry: EntryEntity, lines: List<EntryLineEntity>): EntryView {
        val accounts = HashMap<Long, AccountEntity>()
        val categories = HashMap<Long, CategoryEntity>()
        for (ln in lines) {
            ln.accountId?.let { aid ->
                accounts.getOrPut(aid) { accountDao.getById(aid) ?: fakeAccount(aid) }
            }
            ln.categoryId?.let { cid ->
                categories.getOrPut(cid) { categoryDao.getById(cid) ?: fakeCategory(cid) }
            }
        }

        fun role(accountId: Long?): String? =
            accountId?.let { systemRole(accounts.getValue(it)) }

        val acctIds = lines.mapNotNull { it.accountId }.toSet()
        val catIds = lines.mapNotNull { it.categoryId }.toSet()
        val userLines = lines.filter { it.accountId != null && role(it.accountId) == null }
        val expenseLines = lines.filter {
            it.categoryId != null && categories.getValue(it.categoryId).kind == KIND_EXPENSE
        }
        val incomeLines = lines.filter {
            it.categoryId != null && categories.getValue(it.categoryId).kind == KIND_INCOME
        }
        val sysPresent = lines.mapNotNull { role(it.accountId) }.toSet()
        val recvLines = lines.filter {
            it.accountId != null && role(it.accountId) == SystemRole.RECEIVABLE.slugPrefix
        }

        // An orphan ingest's primary is the system unmatched pot — surfaced so
        // amount/direction aren't zeroed while awaiting a real account.
        var primary = userLines.firstOrNull()
            ?: lines.firstOrNull { role(it.accountId) == SystemRole.UNMATCHED.slugPrefix }

        suspend fun viewOf(
            kind: EntryKind,
            primaryLine: EntryLineEntity?,
            toAccountId: Long? = null,
            categoryId: Long? = null,
        ): EntryView {
            val amt = primaryLine?.amountPaise ?: 0L
            return EntryView(
                id = entry.id,
                kind = kind,
                direction = when {
                    primaryLine == null -> null
                    amt > 0 -> Direction.IN
                    else -> Direction.OUT
                },
                amountPaise = kotlin.math.abs(amt),
                accountId = primaryLine?.accountId?.takeIf { role(it) == null },
                toAccountId = toAccountId,
                categoryId = categoryId,
                bucketId = primaryLine?.bucketId,
                balanceAfterPaise = primaryLine?.balanceAfterPaise,
                occurredOn = entry.occurredOn,
                status = entry.status,
                source = entry.source,
                counterpartyId = entry.counterpartyId,
                groupId = entry.groupId,
                note = entry.note,
                tags = TagCodec.decode(entry.tags),
                linkedEntryId = entry.linkedEntryId,
            )
        }

        return when {
            "sys-openeq-" in sysPresent -> viewOf(EntryKind.OPENING, primary)
            "sys-reconeq-" in sysPresent -> viewOf(EntryKind.RECONCILIATION, primary)

            lines.size == 2 && catIds.isEmpty() && acctIds.size == 1 &&
                lines.any { it.bucketId != null } ->
                viewOf(EntryKind.ALLOCATION, lines.first { it.bucketId != null })

            recvLines.isNotEmpty() ->
                if (expenseLines.isNotEmpty()) viewOf(EntryKind.SPLIT, primary)
                else viewOf(
                    if (recvLines[0].amountPaise > 0) EntryKind.LOAN else EntryKind.LOAN_REPAYMENT,
                    primary,
                )

            "sys-invest-" in sysPresent -> viewOf(EntryKind.INVESTMENT, primary)

            userLines.size >= 2 && catIds.isEmpty() -> {
                val src = userLines.firstOrNull { it.amountPaise < 0 } ?: userLines.first()
                val dst = userLines.firstOrNull { it.amountPaise > 0 }
                viewOf(EntryKind.TRANSFER, src, toAccountId = dst?.accountId)
            }

            expenseLines.isNotEmpty() -> viewOf(
                EntryKind.EXPENSE, primary, categoryId = expenseLines.singleOrNull()?.categoryId
            )

            incomeLines.isNotEmpty() -> viewOf(
                EntryKind.INCOME, primary, categoryId = incomeLines.singleOrNull()?.categoryId
            )

            else -> viewOf(EntryKind.OTHER, primary)
        }
    }
    private fun fakeAccount(id: Long) = AccountEntity(
        id = id, slug = "#$id", name = "#$id", kind = "", openingBalancePaise = 0, isActive = true, createdAt = 0
    )

    private fun fakeCategory(id: Long) = CategoryEntity(id = id, name = "#$id", createdAt = 0)

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle: confirm / void / delete / split
    // ─────────────────────────────────────────────────────────────────────

    /**
     * buffer -> confirmed. Refused while the money sits only on the system
     * 'unmatched' pot: confirming an orphan would book a payment against no
     * real account (old rule: "confirm requires account").
     */
    override suspend fun confirmEntry(id: Long, extraTags: List<String>) {
        db.withTransaction {
            val entry = entryDao.getById(id) ?: throw ApiException("entry $id not found")
            val lines = entryLineDao.getByEntryList(id)
            val hasRealAccount = lines.mapNotNull { it.accountId }.any { leg ->
                val acct = accountDao.getById(leg)
                acct != null && systemRole(acct) == null
            }
            if (!hasRealAccount) {
                throw ApiException(
                    "cannot confirm entry $id: no real account line yet (classify the orphan first)"
                )
            }
            if (extraTags.isEmpty()) {
                entryDao.update(entry.copy(status = EntryStatus.CONFIRMED))
            } else {
                // Union, order-preserving, deduped — same rule as QR bridging.
                val tags = LinkedHashSet(TagCodec.decode(entry.tags))
                tags.addAll(extraTags)
                entryDao.update(
                    entry.copy(status = EntryStatus.CONFIRMED, tags = TagCodec.encode(tags.toList()))
                )
            }
        }
    }

    /**
     * Re-point the account leg of a buffer entry onto [accountId] (PUT
     * /entries/{id}/account). Lets the user correct an auto-matched or orphaned
     * SMS attribution from the inbox. The "account leg" for an SMS entry is the
     * single line that isn't a category/bucket contra.
     */
    override suspend fun assignAccount(entryId: Long, accountId: Long): EntryView {
        val target = accountDao.getById(accountId)
            ?: throw ApiException("account $accountId not found")
        if (target.slug.startsWith("sys-")) {
            throw ApiException("cannot assign a system account")
        }
        return db.withTransaction {
            entryDao.getById(entryId) ?: throw ApiException("entry $entryId not found")
            val lines = entryLineDao.getByEntryList(entryId).toMutableList()
            val idx = lines.indexOfFirst {
                it.accountId != null && it.categoryId == null && it.bucketId == null
            }
            if (idx < 0) throw ApiException("entry $entryId has no account leg to assign")
            lines[idx] = lines[idx].copy(accountId = accountId)
            entryLineDao.update(lines[idx])
            requireView(entryId)
        }
    }

    /** Audit-soft-delete. Voided entries drop out of every balance and list. */
    override suspend fun voidEntry(id: Long, reason: String?) {
        val entry = entryDao.getById(id) ?: throw ApiException("entry $id not found")
        if (entry.voidedAt != null) return
        entryDao.update(entry.copy(voidedAt = System.currentTimeMillis(), voidedReason = reason))
    }

    override suspend fun deleteEntry(id: Long) {
        db.withTransaction {
            entryDao.clearLinkedEntry(id)
            entryLineDao.deleteByEntry(id)
            provenanceDao.getByEntry(id)?.let { provenanceDao.delete(it) }
            entryDao.getById(id)?.let { entryDao.delete(it) }
        }
    }

    /**
     * POST /entries/{id}/split — carve friends' shares out of an entry that
     * paid in full. The category line shrinks by each share; one receivable
     * line per share names who owes (line-level counterparty). Stays balanced.
     * On-behalf splits pass person counterparties; we force party_type=person
     * so they land in the Friends ledger.
     */
    override suspend fun splitEntry(id: Long, request: SplitRequest): EntryView {
        if (request.shares.isEmpty()) throw ApiException("split needs at least one share")
        return db.withTransaction {
            val entry = entryDao.getById(id) ?: throw ApiException("entry $id not found")
            val lines = entryLineDao.getByEntryList(id).toMutableList()

            val catIdx = lines.indexOfFirst { it.categoryId != null && it.bucketId == null }
            if (catIdx < 0) throw ApiException("split target must have a single category line")

            for (share in request.shares) {
                val current = lines[catIdx]
                if (current.amountPaise - share.amountPaise < 0) {
                    throw ApiException("shares exceed the paid amount")
                }
                lines[catIdx] = current.copy(amountPaise = current.amountPaise - share.amountPaise)
                lines.add(
                    EntryLineEntity(
                        entryId = id,
                        accountId = systemAccount(SystemRole.RECEIVABLE).id,
                        counterpartyId = share.counterpartyId,
                        amountPaise = share.amountPaise,
                    )
                )
                // A loan defines its counterparty as a person you transact with.
                counterpartyService.ensurePartyType(
                    share.counterpartyId, CounterpartyService.PARTY_PERSON
                )
            }

            validateLines(lines.map {
                LineSpec(it.amountPaise, it.accountId, it.categoryId, it.bucketId, it.balanceAfterPaise, it.counterpartyId)
            })

            entryLineDao.deleteByEntry(id)
            entryLineDao.insertAll(lines)
            entryDao.update(entry.copy(groupId = request.groupId ?: entry.groupId))

            requireView(id)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // System ledger nodes (get-or-create; slugs never duplicate)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * GET /friends — net outstanding per person on the receivable pot.
     * Confirmed, non-void lines only (enforced in SQL). Positive net = they
     * owe you; settled parties are omitted. Sorted most-outstanding first.
     */
    override suspend fun friendsOutstanding(): List<FriendBalance> {
        val pot = systemAccount(SystemRole.RECEIVABLE)
        val cpDao = db.CounterpartyDao()
        return entryLineDao.netByCounterpartyOnAccount(pot.id)
            .filter { it.cpId != null && it.net != 0L }
            .mapNotNull { row ->
                val cp = cpDao.getById(row.cpId!!) ?: return@mapNotNull null
                FriendBalance(
                    counterpartyId = cp.id,
                    displayName = cp.displayName,
                    netPaise = row.net,
                )
            }
            .sortedByDescending { it.netPaise }
    }

    enum class SystemRole(val slugPrefix: String, val displayName: String) {
        RECEIVABLE("sys-loans-", "Loans & advances"),
        UNMATCHED("sys-unmatched-", "Unmatched (orphan SMS)"),
        INVESTMENT("sys-invest-", "Investments (unallocated)"),
        RECON_EQUITY("sys-reconeq-", "Reconciliation adjustments"),
        OPEN_EQUITY("sys-openeq-", "Opening balance"),
    }

    private fun systemRole(account: AccountEntity): String? =
        SystemRole.entries.firstOrNull { account.slug.startsWith(it.slugPrefix) }?.slugPrefix

    suspend fun systemAccount(role: SystemRole): AccountEntity {
        val slug = "${role.slugPrefix}$USER_ID"
        accountDao.getBySlug(slug)?.let { return it }
        val id = accountDao.insert(
            AccountEntity(
                slug = slug,
                name = role.displayName,
                kind = if (role == SystemRole.RECON_EQUITY || role == SystemRole.OPEN_EQUITY) {
                    "equity"
                } else {
                    "asset"
                },
                openingBalancePaise = 0,
                isActive = true,
                createdAt = System.currentTimeMillis(),
            )
        )
        return accountDao.getById(id)!!
    }

    suspend fun systemCategory(kind: String, name: String): CategoryEntity {
        categoryDao.findByKindAndName(kind, name)?.let { return it }
        val id = categoryDao.insert(
            CategoryEntity(
                name = name, kind = kind, sortOrder = 9999, createdAt = System.currentTimeMillis()
            )
        )
        return categoryDao.getById(id)!!
    }

    // ─────────────────────────────────────────────────────────────────────
    // Opening balances & buckets
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Create (or replace) an account's opening-balance entry: the balance
     * lands on the account, the contra on open equity. Idempotent — safe to
     * call again when the opening balance is edited.
     */
    override suspend fun recordOpeningBalance(accountId: Long, onDate: Long?): EntryView? {
        return db.withTransaction {
            val account = accountDao.getById(accountId)
                ?: throw ApiException("account $accountId not found")
            val eq = systemAccount(SystemRole.OPEN_EQUITY)

            // Prior opening entries = entries with lines on BOTH pots.
            val onAccount = entryLineDao.entryIdsForAccount(account.id).toSet()
            val onEquity = entryLineDao.entryIdsForAccount(eq.id).toSet()
            (onAccount intersect onEquity).forEach { prior ->
                entryLineDao.deleteByEntry(prior)
                provenanceDao.getByEntry(prior)?.let { provenanceDao.delete(it) }
                entryDao.getById(prior)?.let { entryDao.delete(it) }
            }

            if (account.openingBalancePaise == 0L) return@withTransaction null

            val occurredOn = onDate ?: account.reconciledThrough
                ?: throw ApiException("cannot record opening balance without a date")

            val sign = if (account.kind == "liability") -1L else 1L
            val amount = sign * account.openingBalancePaise
            createEntry(
                CreateEntryRequest(
                    occurredOn = occurredOn,
                    status = EntryStatus.CONFIRMED,
                    source = EntrySource.MANUAL,
                    note = "Opening balance",
                    lines = listOf(
                        LineSpec(amount, accountId = account.id),
                        LineSpec(-amount, accountId = eq.id),
                    ),
                )
            )
        }
    }

    /**
     * Create (or replace) a bucket's baseline as a self-transfer entry: the
     * account's general pool down, the sub-pot up — net zero on the account,
     * so the bucket's value is then purely the sum of its tagged lines.
     * Idempotent.
     */
    override suspend fun recordBucketAllocation(bucketId: Long): EntryView? {
        return db.withTransaction {
            val bucket = bucketDao.getById(bucketId)
                ?: throw ApiException("bucket $bucketId not found")

            entryDao.bucketAllocationEntryIds(bucketId, BUCKET_ALLOCATION_NOTE).forEach { prior ->
                entryLineDao.deleteByEntry(prior)
                provenanceDao.getByEntry(prior)?.let { provenanceDao.delete(it) }
                entryDao.getById(prior)?.let { entryDao.delete(it) }
            }

            if (bucket.manualAllocationPaise == 0L) return@withTransaction null

            val account = accountDao.getById(bucket.accountId)
            val occurredOn = account?.reconciledThrough
                ?: 946684800000L // 2000-01-01 anchor when nothing reconciles yet

            createEntry(
                CreateEntryRequest(
                    occurredOn = occurredOn,
                    status = EntryStatus.CONFIRMED,
                    source = EntrySource.MANUAL,
                    note = BUCKET_ALLOCATION_NOTE,
                    lines = listOf(
                        LineSpec(-bucket.manualAllocationPaise, accountId = bucket.accountId),
                        LineSpec(
                            bucket.manualAllocationPaise,
                            accountId = bucket.accountId,
                            bucketId = bucket.id,
                        ),
                    ),
                )
            )
        }
    }

    companion object {
        const val KIND_EXPENSE = "expense"
        const val KIND_INCOME = "income"

        /**
         * Contra categories auto-assigned by ingest when the user hasn't
         * classified anything. They are bookkeeping placeholders, never real
         * user intent — UI surfaces must not present them as a category.
         */
        val SYSTEM_CATEGORY_NAMES = setOf("Unclassified", "Uncategorized income")

        /** Single-user app today; a server would take this from auth. */
        const val USER_ID = 1L

        const val BUCKET_ALLOCATION_NOTE = "Bucket opening allocation"
    }
}