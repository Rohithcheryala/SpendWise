package com.example.spendwise.ledger.service

import androidx.room.withTransaction
import com.example.spendwise.ledger.api.ApiException
import com.example.spendwise.ledger.api.CreateTransactionRequest
import com.example.spendwise.ledger.api.Direction
import com.example.spendwise.ledger.api.FriendBalance
import com.example.spendwise.ledger.api.IngestRequest
import com.example.spendwise.ledger.api.Intent
import com.example.spendwise.ledger.api.LedgerApi
import com.example.spendwise.ledger.api.LineSpec
import com.example.spendwise.ledger.api.SplitRequest
import com.example.spendwise.ledger.api.TransactionKind
import com.example.spendwise.ledger.api.TransactionSource
import com.example.spendwise.ledger.api.TransactionStatus
import com.example.spendwise.ledger.api.TransactionView
import com.example.spendwise.data.database.AppDatabase
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.BankAccountDetailsDao
import com.example.spendwise.data.database.dao.TransactionDao
import com.example.spendwise.data.database.dao.TransactionLineDao
import com.example.spendwise.data.database.dao.TransactionProvenanceDao
import com.example.spendwise.data.database.dao.TagDao
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.data.database.entity.TagEntity
import com.example.spendwise.data.database.entity.TransactionEntity
import com.example.spendwise.data.database.entity.TransactionLineEntity
import com.example.spendwise.data.database.entity.TransactionProvenanceEntity
import com.example.spendwise.data.database.entity.TransactionTagEntity
import javax.inject.Inject

/**
 * Double-entry ledger service. The one place transactions are written and
 * balances are read.
 *
 * Invariants enforced here (SQLite can't express them all):
 *  - a transaction has >= 2 lines whose amounts sum to exactly zero
 *  - every line posts onto exactly one account node — a real account, a
 *    category account (class income/expense) or a system pot
 *
 * Balances are COMPUTED at read time, never stored: an account balance is
 * the signed sum of its confirmed, non-voided lines, inverted for
 * liabilities (where the balance is the amount owed). Buckets are plain
 * child accounts, so their value is just [accountBalance] on the child.
 */
class LedgerService @Inject constructor(
    private val db: AppDatabase,
    private val accountDao: AccountDao,
    private val detailsDao: BankAccountDetailsDao,
    private val transactionDao: TransactionDao,
    private val transactionLineDao: TransactionLineDao,
    private val provenanceDao: TransactionProvenanceDao,
    private val tagDao: TagDao,
    private val counterpartyService: CounterpartyService,
) : LedgerApi {

    // ─────────────────────────────────────────────────────────────────────
    // Write path
    // ─────────────────────────────────────────────────────────────────────

    override suspend fun createTransaction(request: CreateTransactionRequest): TransactionView {
        validateLines(request.lines)
        // Kind is decided ONCE here (Rust migration 005): explicit when the
        // caller knows it (opening/reconciliation — indistinguishable since
        // the equity pots merged), else derived from the line shape, and
        // validated against the kind→shape matrix (backend
        // DESIGN_DECISIONS.md) before anything is written.
        val kind = request.kind ?: classifyKind(request.lines)
        validateKindShape(kind, request.lines)
        val id = db.withTransaction {
            val now = System.currentTimeMillis()
            val transactionId = transactionDao.insert(
                TransactionEntity(
                    occurredOn = request.occurredOn,
                    kind = kind.name,
                    happenedAt = request.happenedAt,
                    counterpartyId = request.counterpartyId,
                    groupId = request.groupId,
                    note = request.note,
                    status = request.status,
                    source = request.source,
                    linkedTransactionId = request.linkedTransactionId,
                    createdAt = now,
                )
            )
            transactionLineDao.insertAll(request.lines.map { it.toEntity(transactionId) })
            setTags(transactionId, request.tags)
            transactionId
        }
        return requireView(id)
    }

    /**
     * POST /ingest. The real account leg (or the 'unmatched' pot for an
     * orphan) signed by direction, plus one contra line chosen by intent —
     * loans to the receivable pot, reconciliation to reconciliation equity,
     * investments to the invest pot, everything else to an unclassified
     * income/expense category account. Attaches provenance when any is given.
     */
    override suspend fun ingest(request: IngestRequest): TransactionView {
        if (request.intent !in Intent.ALL) throw ApiException.InvalidRequest("unknown intent '${request.intent}'")
        if (request.amountPaise <= 0) throw ApiException.InvalidRequest("amount must be positive")
        requireFreshDedupeHash(request.dedupeHash)
        val sign = if (request.direction == Direction.IN) 1L else -1L
        val primaryAccount = request.accountId ?: systemAccount(SystemRole.UNMATCHED).id

        val lines = listOf(
            LineSpec(
                amountPaise = sign * request.amountPaise,
                accountId = primaryAccount,
            ),
            contraLine(request.intent, request.direction, request.amountPaise, request.counterpartyId),
        )

        val view = createTransaction(
            CreateTransactionRequest(
                occurredOn = request.occurredOn,
                lines = lines,
                // Reconciliation can't be line-derived now that the equity
                // pots merged — ingests with that intent pass it explicitly.
                kind = if (request.intent == Intent.RECONCILIATION) {
                    TransactionKind.RECONCILIATION
                } else {
                    null
                },
                status = request.status,
                source = request.source,
                happenedAt = request.happenedAt,
                counterpartyId = request.counterpartyId,
                note = request.note,
                tags = request.tags,
            )
        )

        if (request.rawText != null || request.dedupeHash != null ||
            request.bankRef != null || request.parsedFacts != null ||
            request.balanceAfterPaise != null
        ) {
            provenanceDao.insert(
                TransactionProvenanceEntity(
                    transactionId = view.id,
                    rawText = request.rawText,
                    dedupeHash = request.dedupeHash,
                    bankRef = request.bankRef,
                    parsedFacts = request.parsedFacts,
                    // The SMS-stated running balance anchors reconciliation
                    // continuity — on provenance, never on lines (Rust Q12).
                    statedBalancePaise = request.balanceAfterPaise,
                )
            )
        }
        // Re-fetch so the returned view includes the provenance just written
        // (the stated balance is presentation data on the view).
        return getTransaction(view.id) ?: view
    }

    /** Two real-account legs — reads back as kind=transfer. */
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
    ): TransactionView {
        if (fromAccountId == toAccountId) throw ApiException.InvalidRequest("transfer legs must differ")
        requireFreshDedupeHash(dedupeHash)
        val view = createTransaction(
            CreateTransactionRequest(
                occurredOn = occurredOn,
                kind = TransactionKind.TRANSFER,
                status = status,
                source = source,
                note = note,
                lines = listOf(
                    LineSpec(-amountPaise, accountId = fromAccountId),
                    LineSpec(amountPaise, accountId = toAccountId),
                ),
            )
        )
        if (rawText != null || dedupeHash != null || bankRef != null || balanceAfterPaise != null) {
            provenanceDao.insert(
                TransactionProvenanceEntity(
                    transactionId = view.id,
                    rawText = rawText,
                    dedupeHash = dedupeHash,
                    bankRef = bankRef,
                    statedBalancePaise = balanceAfterPaise,
                )
            )
        }
        // Re-fetch so the returned view includes the provenance just written.
        return getTransaction(view.id) ?: view
    }

    /** The single balancing line for an ingested transaction, chosen by intent. */
    private suspend fun contraLine(
        intent: String,
        direction: Direction,
        amountPaise: Long,
        counterpartyId: Long?,
    ): LineSpec {
        val contra = if (direction == Direction.OUT) amountPaise else -amountPaise
        return when (intent) {
            Intent.LOAN, Intent.LOAN_REPAYMENT -> {
                // A loan defines its counterparty as a person you transact
                // with — same rule as splits — so they land in Friends.
                counterpartyService.ensurePartyType(
                    counterpartyId, CounterpartyService.PARTY_PERSON
                )
                // The contra lands on the person's own receivable child pot
                // (Rust Q4): per-person outstanding is a BALANCE, not a
                // filtered aggregate over line metadata.
                LineSpec(
                    contra,
                    accountId = receivableAccountFor(counterpartyId).id,
                )
            }

            Intent.RECONCILIATION ->
                LineSpec(contra, accountId = systemAccount(SystemRole.EQUITY).id)

            Intent.INVESTMENT ->
                LineSpec(contra, accountId = systemAccount(SystemRole.INVESTMENT).id)

            else -> {
                val cat = if (direction == Direction.OUT) {
                    systemCategoryAccount(CLASS_EXPENSE, "Unclassified")
                } else {
                    systemCategoryAccount(CLASS_INCOME, "Uncategorized income")
                }
                LineSpec(contra, accountId = cat.id)
            }
        }
    }

    private fun validateLines(lines: List<LineSpec>) {
        if (lines.size < 2) throw ApiException.UnbalancedLines("a transaction needs at least two lines")
        val total = lines.sumOf { it.amountPaise }
        if (total != 0L) throw ApiException.UnbalancedLines("transaction lines must sum to zero (got $total)")
        for (ln in lines) {
            if (ln.accountId <= 0L) {
                throw ApiException.UnbalancedLines("each line must post onto an account")
            }
        }
    }

    private fun LineSpec.toEntity(transactionId: Long) = TransactionLineEntity(
        transactionId = transactionId,
        accountId = accountId,
        amountPaise = amountPaise,
    )

    // ─────────────────────────────────────────────────────────────────────
    // Read path
    // ─────────────────────────────────────────────────────────────────────

    override suspend fun getTransaction(id: Long): TransactionView? {
        val entry = transactionDao.getById(id) ?: return null
        // The stated balance is presentation data from provenance; list views
        // skip it (avoids an N+1) since list rows never render it.
        val stated = provenanceDao.getByTransaction(id)?.statedBalancePaise
        return buildView(entry, transactionLineDao.getByTransactionList(id), stated)
    }

    private suspend fun requireView(id: Long): TransactionView =
        getTransaction(id) ?: throw ApiException.NotFound("transaction", id)  // raced a concurrent delete

    override suspend fun listTransactions(
        status: String?,
        from: Long?,
        to: Long?,
    ): List<TransactionView> =
        transactionDao.listSuspend(status, from, to).map { entry ->
            buildView(entry, transactionLineDao.getByTransactionList(entry.id))
        }

    override suspend fun findTransactionByDedupeHash(dedupeHash: String): TransactionView? {
        val prov = provenanceDao.getByDedupeHash(dedupeHash) ?: return null
        return getTransaction(prov.transactionId)
    }

    /**
     * Write-path dedupe guard (3b-3): refuses to record the same SMS/scan
     * hash twice, no matter which ingest entry point was called. The
     * pre-check in [IngestionService] stays (it returns a friendly
     * Duplicate result); this is the invariant backstop.
     */
    private suspend fun requireFreshDedupeHash(dedupeHash: String?) {
        if (dedupeHash != null && provenanceDao.getByDedupeHash(dedupeHash) != null) {
            throw ApiException.DuplicateDedupeHash(dedupeHash)
        }
    }

    /**
     * Signed sum of an account's confirmed, non-voided lines. Liabilities are
     * inverted (balance = amount owed). [through] bounds it for reconciliation
     * continuity checks. Buckets — being plain child accounts — use the same
     * read path.
     */
    override suspend fun accountBalance(accountId: Long, through: Long?): Long {
        val account = accountDao.getById(accountId)
            ?: throw ApiException.NotFound("account", accountId)
        // Default read path: v_account_balances (the liability sign flip
        // happens IN the view — the only sign inversion anywhere). Only the
        // reconciliation continuity check needs a through-bounded sum; that
        // query stays raw and flips the sign here.
        if (through == null) {
            return accountDao.balanceOf(accountId)?.balancePaise ?: 0L
        }
        val total = transactionLineDao.sumConfirmedForAccount(accountId, through)
        return if (account.accountClass == CLASS_LIABILITY) -total else total
    }

    /**
     * Presentation view. Kind is READ from the stored column — never
     * re-derived (Rust migration 005); only presentation fields (direction,
     * amount, to-account/category legs) are computed from the lines.
     */
    private suspend fun buildView(
        entry: TransactionEntity,
        lines: List<TransactionLineEntity>,
        statedBalancePaise: Long? = null,
    ): TransactionView {
        val accounts = HashMap<Long, AccountEntity>()
        for (ln in lines) {
            accounts.getOrPut(ln.accountId) { accountDao.getById(ln.accountId) ?: fakeAccount(ln.accountId) }
        }

        fun roleOf(account: AccountEntity): String? =
            if (account.isSystem) {
                SystemRole.entries.firstOrNull { it.subtype == account.subtype }?.subtype
            } else {
                null
            }

        fun isCategoryAccount(account: AccountEntity) =
            account.accountClass == CLASS_EXPENSE || account.accountClass == CLASS_INCOME

        val userLines = lines.filter {
            val a = accounts.getValue(it.accountId)
            !a.isSystem && !isCategoryAccount(a)
        }
        val expenseLines = lines.filter { accounts.getValue(it.accountId).accountClass == CLASS_EXPENSE }
        val incomeLines = lines.filter { accounts.getValue(it.accountId).accountClass == CLASS_INCOME }

        // An orphan ingest's primary is the system unmatched pot — surfaced so
        // amount/direction aren't zeroed while awaiting a real account.
        val primary = userLines.firstOrNull()
            ?: lines.firstOrNull { roleOf(accounts.getValue(it.accountId)) == SystemRole.UNMATCHED.subtype }

        // Defence in depth: every row written by THIS service stores a valid
        // kind; the derive fallback only fires on rows predating the column
        // (impossible after the destructive bump, but cheap insurance).
        val kind = TransactionKind.entries.firstOrNull { it.name == entry.kind }
            ?: classifyKind(lines.map { LineSpec(it.amountPaise, it.accountId) })

        suspend fun viewOf(
            primaryLine: TransactionLineEntity?,
            toAccountId: Long? = null,
            categoryId: Long? = null,
        ): TransactionView {
            val amt = primaryLine?.amountPaise ?: 0L
            return TransactionView(
                id = entry.id,
                kind = kind,
                direction = when {
                    primaryLine == null -> null
                    amt > 0 -> Direction.IN
                    else -> Direction.OUT
                },
                amountPaise = kotlin.math.abs(amt),
                accountId = primaryLine?.accountId?.takeIf { roleOf(accounts.getValue(it)) == null },
                toAccountId = toAccountId,
                categoryId = categoryId,
                bucketId = lines.firstOrNull { accounts.getValue(it.accountId).subtype == SUBTYPE_BUCKET }
                    ?.accountId,
                // SMS-stated balance — from provenance now, never from lines.
                balanceAfterPaise = statedBalancePaise,
                occurredOn = entry.occurredOn,
                status = entry.status,
                source = entry.source,
                counterpartyId = entry.counterpartyId,
                groupId = entry.groupId,
                note = entry.note,
                tags = tagDao.namesFor(entry.id),
                linkedTransactionId = entry.linkedTransactionId,
            )
        }

        return when {
            userLines.size >= 2 -> {
                val src = userLines.firstOrNull { it.amountPaise < 0 } ?: userLines.first()
                val dst = userLines.firstOrNull { it.amountPaise > 0 }
                viewOf(src, toAccountId = dst?.accountId)
            }

            expenseLines.isNotEmpty() -> viewOf(
                primary, categoryId = expenseLines.singleOrNull()?.accountId
            )

            incomeLines.isNotEmpty() -> viewOf(
                primary, categoryId = incomeLines.singleOrNull()?.accountId
            )

            else -> viewOf(primary)
        }
    }

    private fun fakeAccount(id: Long) = AccountEntity(
        id = id, name = "#$id", accountClass = CLASS_ASSET, createdAt = 0
    )

    /**
     * Derive the kind from the line shape — the write-time successor of the
     * old read-time classification. Precedence: loans/splits on receivable
     * legs, investment pot, ≥2 holder legs (transfer), then expense/income
     * by category class. Equity legs REQUIRE an explicit kind: opening vs
     * reconciliation is caller intent, not line shape (one shared pot now).
     */
    private suspend fun classifyKind(lines: List<LineSpec>): TransactionKind {
        val accounts = HashMap<Long, AccountEntity>()
        for (ln in lines) {
            accounts.getOrPut(ln.accountId) { accountDao.getById(ln.accountId) ?: fakeAccount(ln.accountId) }
        }
        val sysRoles = lines.mapNotNull { systemRole(accounts.getValue(it.accountId)) }.toSet()
        val recvLegs = lines.filter {
            systemRole(accounts.getValue(it.accountId)) == SystemRole.RECEIVABLE.subtype
        }
        val expenseLegs = lines.filter { accounts.getValue(it.accountId).accountClass == CLASS_EXPENSE }
        val incomeLegs = lines.filter { accounts.getValue(it.accountId).accountClass == CLASS_INCOME }
        val holderLegs = lines.filter {
            val a = accounts.getValue(it.accountId)
            isBalanceSheetClass(a.accountClass) && !a.isSystem
        }
        return when {
            SystemRole.EQUITY.subtype in sysRoles ->
                throw ApiException.EquityGuardViolation("equity legs require an explicit kind (opening/reconciliation)")

            recvLegs.isNotEmpty() && expenseLegs.isNotEmpty() -> TransactionKind.SPLIT

            recvLegs.isNotEmpty() ->
                if (recvLegs[0].amountPaise > 0) TransactionKind.LOAN else TransactionKind.LOAN_REPAYMENT

            SystemRole.INVESTMENT.subtype in sysRoles -> TransactionKind.INVESTMENT

            holderLegs.size >= 2 -> TransactionKind.TRANSFER

            expenseLegs.isNotEmpty() -> TransactionKind.EXPENSE

            incomeLegs.isNotEmpty() -> TransactionKind.INCOME

            // Orphan buffers (unmatched pot + terminal) classify as expense
            // — OTHER was dropped with the stored kind (Rust 9-value vocab).
            else -> TransactionKind.EXPENSE
        }
    }

    /**
     * The kind→shape matrix from backend DESIGN_DECISIONS.md, enforced here
     * because the DB can't: must-contain / must-not-contain per kind. Kept
     * to the contract's columns — sign conventions stay the callers'.
     */
    private suspend fun validateKindShape(kind: TransactionKind, lines: List<LineSpec>) {
        val accounts = HashMap<Long, AccountEntity>()
        for (ln in lines) {
            accounts.getOrPut(ln.accountId) { accountDao.getById(ln.accountId) ?: fakeAccount(ln.accountId) }
        }
        val hasEquity = lines.any { accounts.getValue(it.accountId).accountClass == CLASS_EQUITY }
        val expenseLegs = lines.filter { accounts.getValue(it.accountId).accountClass == CLASS_EXPENSE }
        val incomeLegs = lines.filter { accounts.getValue(it.accountId).accountClass == CLASS_INCOME }
        val terminalLegs = expenseLegs + incomeLegs
        val recvLegs = lines.filter {
            systemRole(accounts.getValue(it.accountId)) == SystemRole.RECEIVABLE.subtype
        }
        val invLegs = lines.filter {
            systemRole(accounts.getValue(it.accountId)) == SystemRole.INVESTMENT.subtype
        }
        val holderLegs = lines.filter {
            val a = accounts.getValue(it.accountId)
            isBalanceSheetClass(a.accountClass) && !a.isSystem
        }

        if (hasEquity && kind != TransactionKind.OPENING && kind != TransactionKind.RECONCILIATION) {
            throw ApiException.EquityGuardViolation("equity legs are only allowed on opening/reconciliation transactions")
        }
        when (kind) {
            TransactionKind.OPENING, TransactionKind.RECONCILIATION -> {
                if (!hasEquity) throw ApiException.EquityGuardViolation("$kind must post an equity pot leg")
            }

            TransactionKind.TRANSFER -> {
                // ≥2 holder legs ONLY — no terminals, no pots.
                if (terminalLegs.isNotEmpty() || recvLegs.isNotEmpty() || invLegs.isNotEmpty()) {
                    throw ApiException.EquityGuardViolation("transfer must contain only holder legs")
                }
                if (holderLegs.size < 2) throw ApiException.EquityGuardViolation("transfer needs at least two holder legs")
            }

            TransactionKind.LOAN, TransactionKind.LOAN_REPAYMENT -> {
                if (terminalLegs.isNotEmpty()) throw ApiException.EquityGuardViolation("$kind must not contain terminal legs")
                if (recvLegs.isEmpty()) throw ApiException.EquityGuardViolation("$kind must post a receivable pot leg")
            }

            TransactionKind.SPLIT -> {
                if (terminalLegs.isEmpty()) throw ApiException.EquityGuardViolation("split must keep a terminal leg")
                if (recvLegs.isEmpty()) throw ApiException.EquityGuardViolation("split must add receivable legs")
            }

            TransactionKind.INVESTMENT -> {
                if (terminalLegs.isNotEmpty()) throw ApiException.EquityGuardViolation("investment must not contain terminal legs")
                if (invLegs.isEmpty()) throw ApiException.EquityGuardViolation("investment must post the investment pot")
            }

            TransactionKind.EXPENSE -> {
                if (expenseLegs.isEmpty()) throw ApiException.EquityGuardViolation("expense must post an expense terminal leg")
            }

            TransactionKind.INCOME -> {
                if (incomeLegs.isEmpty()) throw ApiException.EquityGuardViolation("income must post an income terminal leg")
            }
        }
    }

    /**
     * The per-person child pot under the receivable pot (Rust Q4/Q14):
     * is_system=1, subtype='receivable', parent=pot — minted lazily on first
     * loan/split and linked from counterparties.receivable_account_id, so
     * "how much does Rahul owe me" is the signed balance of his child pot.
     * A null [counterpartyId] (anonymous/legacy leg) falls back to the
     * top-level pot.
     */
    private suspend fun receivableAccountFor(counterpartyId: Long?): AccountEntity {
        val pot = systemAccount(SystemRole.RECEIVABLE)
        if (counterpartyId == null) return pot
        val cpDao = db.CounterpartyDao()
        val cp = cpDao.getById(counterpartyId)
            ?: throw ApiException.NotFound("counterparty", counterpartyId)
        cp.receivableAccountId?.let { id ->
            accountDao.getById(id)?.let { return it }
        }
        val id = accountDao.insert(
            AccountEntity(
                name = cp.displayName,
                accountClass = CLASS_ASSET,
                subtype = SystemRole.RECEIVABLE.subtype,
                parentId = pot.id,
                isSystem = true,
                sortOrder = 9999,
                createdAt = System.currentTimeMillis(),
            )
        )
        cpDao.update(cp.copy(receivableAccountId = id))
        return accountDao.getById(id)!!
    }

    /**
     * Replace a transaction's tags with [names] (Rust migration 006): real
     * tag rows + join rows. Orphaned tags (no transaction left) are swept.
     * Public: the QR↔SMS merge in IngestionService unions tags too.
     */
    suspend fun setTags(transactionId: Long, names: List<String>) {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        tagDao.detachAll(transactionId)
        val rows = cleaned.map { name ->
            val tagId = tagDao.idByName(name)
                ?: tagDao.insert(TagEntity(name = name)).takeIf { it != -1L }
                ?: tagDao.idByName(name)
                ?: throw ApiException.InvalidRequest("could not create tag '$name'")
            TransactionTagEntity(transactionId = transactionId, tagId = tagId)
        }
        if (rows.isNotEmpty()) tagDao.attachAll(rows)
        tagDao.deleteOrphanTags()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle: confirm / void / delete / split
    // ─────────────────────────────────────────────────────────────────────

    /**
     * buffer -> confirmed. Refused while the money sits only on the system
     * 'unmatched' pot: confirming an orphan would book a payment against no
     * real account (old rule: "confirm requires account").
     */
    override suspend fun confirmTransaction(id: Long, extraTags: List<String>) {
        db.withTransaction {
            val entry = transactionDao.getById(id) ?: throw ApiException.NotFound("transaction", id)
            val lines = transactionLineDao.getByTransactionList(id)
            val hasRealAccount = lines.map { it.accountId }.any { leg ->
                val acct = accountDao.getById(leg)
                acct != null && !acct.isSystem && isBalanceSheetClass(acct.accountClass)
            }
            if (!hasRealAccount) {
                throw ApiException.InvalidLifecycleTransition(
                    "cannot confirm transaction $id: no real account line yet (classify the orphan first)"
                )
            }
            if (entry.voidedAt != null) {
                throw ApiException.InvalidLifecycleTransition(
                    "transaction $id is voided and cannot be confirmed"
                )
            }
            if (extraTags.isEmpty()) {
                transactionDao.update(entry.copy(status = TransactionStatus.CONFIRMED))
            } else {
                // Union, order-preserving, deduped — same rule as QR bridging.
                val merged = LinkedHashSet(tagDao.namesFor(id))
                merged.addAll(extraTags)
                setTags(id, merged.toList())
                transactionDao.update(entry.copy(status = TransactionStatus.CONFIRMED))
            }
        }
    }

    /**
     * Re-point the account leg of a buffer transaction onto [accountId] (PUT
     * /transactions/{id}/account). Lets the user correct an auto-matched or
     * orphaned SMS attribution from the inbox. The "account leg" is the line
     * posting onto a real balance-sheet account (not a category or pot).
     */
    override suspend fun assignAccount(transactionId: Long, accountId: Long): TransactionView {
        val target = accountDao.getById(accountId)
            ?: throw ApiException.NotFound("account", accountId)
        if (target.isSystem) {
            throw ApiException.InvalidRequest("cannot assign a system account")
        }
        return db.withTransaction {
            transactionDao.getById(transactionId) ?: throw ApiException.NotFound("transaction", transactionId)
            val lines = transactionLineDao.getByTransactionList(transactionId).toMutableList()
            val idx = lines.indexOfFirst { ln ->
                val acct = accountDao.getById(ln.accountId)
                acct != null && !acct.isSystem && isBalanceSheetClass(acct.accountClass)
            }
            if (idx < 0) throw ApiException.InvalidLifecycleTransition("transaction $transactionId has no account leg to assign")
            lines[idx] = lines[idx].copy(accountId = accountId)
            transactionLineDao.update(lines[idx])
            requireView(transactionId)
        }
    }

    /** Audit-soft-delete. Voided transactions drop out of every balance and list. */
    override suspend fun voidTransaction(id: Long, reason: String?) {
        val entry = transactionDao.getById(id) ?: throw ApiException.NotFound("transaction", id)
        if (entry.voidedAt != null) return
        transactionDao.update(entry.copy(voidedAt = System.currentTimeMillis(), voidedReason = reason))
    }

    override suspend fun deleteTransaction(id: Long) {
        db.withTransaction {
            transactionDao.clearLinkedTransaction(id)
            transactionLineDao.deleteByTransaction(id)
            provenanceDao.getByTransaction(id)?.let { provenanceDao.delete(it) }
            transactionDao.getById(id)?.let { transactionDao.delete(it) }
        }
    }

    /**
     * POST /transactions/{id}/split — carve friends' shares out of a
     * transaction that paid in full. The category-account line shrinks by
     * each share; one receivable line per share posts onto that person's
     * own receivable child pot. Stays balanced. On-behalf splits pass
     * person counterparties; we force party_type=person so they land in
     * Friends.
     */
    override suspend fun splitTransaction(id: Long, request: SplitRequest): TransactionView {
        if (request.shares.isEmpty()) throw ApiException.InvalidRequest("split needs at least one share")
        return db.withTransaction {
            val entry = transactionDao.getById(id) ?: throw ApiException.NotFound("transaction", id)
            val lines = transactionLineDao.getByTransactionList(id).toMutableList()

            val plIdx = lines.indexOfFirst { ln ->
                val acct = accountDao.getById(ln.accountId)
                acct != null && (acct.accountClass == CLASS_EXPENSE || acct.accountClass == CLASS_INCOME)
            }
            if (plIdx < 0) throw ApiException.InvalidRequest("split target must have a single category line")

            for (share in request.shares) {
                val current = lines[plIdx]
                if (current.amountPaise - share.amountPaise < 0) {
                    throw ApiException.InvalidRequest("shares exceed the paid amount")
                }
                lines[plIdx] = current.copy(amountPaise = current.amountPaise - share.amountPaise)
                // Per-person pot (Rust Q4/Q14): each share posts onto that
                // person's own receivable child account — "how much does
                // Rahul owe" becomes the signed balance of his pot.
                lines.add(
                    TransactionLineEntity(
                        transactionId = id,
                        accountId = receivableAccountFor(share.counterpartyId).id,
                        amountPaise = share.amountPaise,
                    )
                )
                // A split defines its counterparties as people you transact with.
                counterpartyService.ensurePartyType(
                    share.counterpartyId, CounterpartyService.PARTY_PERSON
                )
            }

            val specs = lines.map { LineSpec(it.amountPaise, accountId = it.accountId) }
            validateLines(specs)
            validateKindShape(TransactionKind.SPLIT, specs)

            transactionLineDao.deleteByTransaction(id)
            transactionLineDao.insertAll(lines)
            transactionDao.update(
                entry.copy(kind = TransactionKind.SPLIT.name, groupId = request.groupId ?: entry.groupId)
            )

            requireView(id)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // System ledger nodes (get-or-create; is_system + subtype never collide)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * GET /friends — net outstanding per person via their receivable child
     * pot (a balance read, not a line aggregate). Positive net = they owe
     * you; settled parties are omitted. Sorted most-outstanding first.
     */
    override suspend fun friendsOutstanding(): List<FriendBalance> {
        val pot = systemAccount(SystemRole.RECEIVABLE)
        val cpDao = db.CounterpartyDao()
        return cpDao.receivableNets(pot.id)
            .filter { it.net != 0L }
            .mapNotNull { row ->
                val cp = cpDao.getById(row.cpId) ?: return@mapNotNull null
                FriendBalance(
                    counterpartyId = cp.id,
                    displayName = cp.displayName,
                    netPaise = row.net,
                )
            }
            .sortedByDescending { it.netPaise }
    }

    /**
     * The system pots, addressed by `is_system` + `subtype` (the Rust way —
     * no slugs). The two old equity pots are merged into one now that stored
     * `kind` distinguishes opening from reconciliation (Rust Q12).
     */
    enum class SystemRole(val subtype: String, val displayName: String, val accountClass: String) {
        RECEIVABLE("receivable", "Loans & advances", CLASS_ASSET),
        UNMATCHED("unmatched", "Unmatched (orphan SMS)", CLASS_ASSET),
        INVESTMENT("investment", "Investments (unallocated)", CLASS_ASSET),
        EQUITY("equity", "Opening & reconciliation", CLASS_EQUITY),
    }

    private fun systemRole(account: AccountEntity): String? =
        if (account.isSystem) {
            SystemRole.entries.firstOrNull { account.subtype == it.subtype }?.subtype
        } else {
            null
        }

    private fun isBalanceSheetClass(cls: String) = cls == CLASS_ASSET || cls == CLASS_LIABILITY

    suspend fun systemAccount(role: SystemRole): AccountEntity {
        accountDao.findSystemBySubtype(role.subtype)?.let { return it }
        val id = accountDao.insert(
            AccountEntity(
                name = role.displayName,
                accountClass = role.accountClass,
                subtype = role.subtype,
                isSystem = true,
                createdAt = System.currentTimeMillis(),
            )
        )
        return accountDao.getById(id)!!
    }

    /** System contra category accounts ("Unclassified" etc.) — get-or-create. */
    suspend fun systemCategoryAccount(accountClass: String, name: String): AccountEntity {
        accountDao.findSystemByName(accountClass, name)?.let { return it }
        val id = accountDao.insert(
            AccountEntity(
                name = name,
                accountClass = accountClass,
                isSystem = true,
                sortOrder = 9999,
                createdAt = System.currentTimeMillis(),
            )
        )
        return accountDao.getById(id)!!
    }

    // ─────────────────────────────────────────────────────────────────────
    // Opening balances
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Create (or replace) an account's opening-balance transaction: the
     * balance lands on the account, the contra on opening equity. Idempotent
     * — safe to call again when the opening balance is edited. The amount is
     * explicit now that accounts carry no stored balance.
     */
    override suspend fun recordOpeningBalance(
        accountId: Long,
        amountPaise: Long,
        onDate: Long?,
    ): TransactionView? {
        return db.withTransaction {
            val account = accountDao.getById(accountId)
                ?: throw ApiException.NotFound("account", accountId)
            val eq = systemAccount(SystemRole.EQUITY)

            // Prior opening transactions = transactions with lines on BOTH nodes.
            val onAccount = transactionLineDao.transactionIdsForAccount(account.id).toSet()
            val onEquity = transactionLineDao.transactionIdsForAccount(eq.id).toSet()
            (onAccount intersect onEquity).forEach { prior ->
                transactionLineDao.deleteByTransaction(prior)
                provenanceDao.getByTransaction(prior)?.let { provenanceDao.delete(it) }
                transactionDao.getById(prior)?.let { transactionDao.delete(it) }
            }

            if (amountPaise == 0L) return@withTransaction null

            val occurredOn = onDate ?: detailsDao.getForAccount(account.id)?.reconciledThrough
                ?: throw ApiException.InvalidRequest("cannot record opening balance without a date")

            val sign = if (account.accountClass == CLASS_LIABILITY) -1L else 1L
            val amount = sign * amountPaise
            createTransaction(
                CreateTransactionRequest(
                    occurredOn = occurredOn,
                    kind = TransactionKind.OPENING,
                    status = TransactionStatus.CONFIRMED,
                    source = TransactionSource.MANUAL,
                    note = "Opening balance",
                    lines = listOf(
                        LineSpec(amount, accountId = account.id),
                        LineSpec(-amount, accountId = eq.id),
                    ),
                )
            )
        }
    }

    companion object {
        /** The account-class vocabulary (Rust migration 002). */
        const val CLASS_ASSET = "asset"
        const val CLASS_LIABILITY = "liability"
        const val CLASS_EQUITY = "equity"
        const val CLASS_EXPENSE = "expense"
        const val CLASS_INCOME = "income"

        /** Buckets are plain child accounts with this subtype + a target. */
        const val SUBTYPE_BUCKET = "bucket"

        /**
         * Contra category accounts auto-assigned by ingest when the user
         * hasn't classified anything. They are bookkeeping placeholders,
         * never real user intent — UI surfaces must not present them as a
         * category.
         */
        val SYSTEM_CATEGORY_NAMES = setOf("Unclassified", "Uncategorized income")

        /** Single-user app today; a server would take this from auth. */
        const val USER_ID = 1L
    }
}