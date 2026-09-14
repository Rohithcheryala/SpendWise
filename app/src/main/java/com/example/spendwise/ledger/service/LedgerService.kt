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
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.data.database.entity.TransactionEntity
import com.example.spendwise.data.database.entity.TransactionLineEntity
import com.example.spendwise.data.database.entity.TransactionProvenanceEntity
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
    private val counterpartyService: CounterpartyService,
) : LedgerApi {

    // ─────────────────────────────────────────────────────────────────────
    // Write path
    // ─────────────────────────────────────────────────────────────────────

    override suspend fun createTransaction(request: CreateTransactionRequest): TransactionView {
        validateLines(request.lines)
        val id = db.withTransaction {
            val now = System.currentTimeMillis()
            val transactionId = transactionDao.insert(
                TransactionEntity(
                    occurredOn = request.occurredOn,
                    happenedAt = request.happenedAt,
                    counterpartyId = request.counterpartyId,
                    groupId = request.groupId,
                    note = request.note,
                    tags = TagCodec.encode(request.tags),
                    status = request.status,
                    source = request.source,
                    linkedTransactionId = request.linkedTransactionId,
                    createdAt = now,
                )
            )
            transactionLineDao.insertAll(request.lines.map { it.toEntity(transactionId) })
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
        if (request.intent !in Intent.ALL) throw ApiException("unknown intent '${request.intent}'")
        if (request.amountPaise <= 0) throw ApiException("amount must be positive")
        val sign = if (request.direction == Direction.IN) 1L else -1L
        val primaryAccount = request.accountId ?: systemAccount(SystemRole.UNMATCHED).id

        val lines = listOf(
            LineSpec(
                amountPaise = sign * request.amountPaise,
                accountId = primaryAccount,
                balanceAfterPaise = request.balanceAfterPaise,
            ),
            contraLine(request.intent, request.direction, request.amountPaise, request.counterpartyId),
        )

        val view = createTransaction(
            CreateTransactionRequest(
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
                TransactionProvenanceEntity(
                    transactionId = view.id,
                    rawText = request.rawText,
                    dedupeHash = request.dedupeHash,
                    bankRef = request.bankRef,
                    parsedFacts = request.parsedFacts,
                )
            )
        }
        return view
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
        if (fromAccountId == toAccountId) throw ApiException("transfer legs must differ")
        val view = createTransaction(
            CreateTransactionRequest(
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
                TransactionProvenanceEntity(
                    transactionId = view.id,
                    rawText = rawText,
                    dedupeHash = dedupeHash,
                    bankRef = bankRef,
                )
            )
        }
        return view
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
                    systemCategoryAccount(CLASS_EXPENSE, "Unclassified")
                } else {
                    systemCategoryAccount(CLASS_INCOME, "Uncategorized income")
                }
                LineSpec(contra, accountId = cat.id)
            }
        }
    }

    private fun validateLines(lines: List<LineSpec>) {
        if (lines.size < 2) throw ApiException("a transaction needs at least two lines")
        val total = lines.sumOf { it.amountPaise }
        if (total != 0L) throw ApiException("transaction lines must sum to zero (got $total)")
        for (ln in lines) {
            if (ln.accountId <= 0L) {
                throw ApiException("each line must post onto an account")
            }
        }
    }

    private fun LineSpec.toEntity(transactionId: Long) = TransactionLineEntity(
        transactionId = transactionId,
        accountId = accountId,
        counterpartyId = counterpartyId,
        amountPaise = amountPaise,
        balanceAfterPaise = balanceAfterPaise,
    )

    // ─────────────────────────────────────────────────────────────────────
    // Read path
    // ─────────────────────────────────────────────────────────────────────

    override suspend fun getTransaction(id: Long): TransactionView? {
        val entry = transactionDao.getById(id) ?: return null
        return buildView(entry, transactionLineDao.getByTransactionList(id))
    }

    private suspend fun requireView(id: Long): TransactionView =
        getTransaction(id) ?: throw ApiException("transaction $id vanished after write")

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
     * Signed sum of an account's confirmed, non-voided lines. Liabilities are
     * inverted (balance = amount owed). [through] bounds it for reconciliation
     * continuity checks. Buckets — being plain child accounts — use the same
     * read path.
     */
    override suspend fun accountBalance(accountId: Long, through: Long?): Long {
        val account = accountDao.getById(accountId)
            ?: throw ApiException("account $accountId not found")
        val total = transactionLineDao.sumConfirmedForAccount(accountId, through)
        return if (account.accountClass == CLASS_LIABILITY) -total else total
    }

    /**
     * Derive a [TransactionView] from the lines so the client needs no
     * accounting logic. Classification precedence: opening/reconciliation
     * equity, loans/splits on the receivable pot, investment, transfer, then
     * expense/income by category-account class. (Stored `kind` in 3b-2 makes
     * this derivation write-time only.)
     */
    private suspend fun buildView(entry: TransactionEntity, lines: List<TransactionLineEntity>): TransactionView {
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
        val sysPresent = lines.mapNotNull { roleOf(accounts.getValue(it.accountId)) }.toSet()
        val recvLines = lines.filter {
            roleOf(accounts.getValue(it.accountId)) == SystemRole.RECEIVABLE.subtype
        }

        // An orphan ingest's primary is the system unmatched pot — surfaced so
        // amount/direction aren't zeroed while awaiting a real account.
        val primary = userLines.firstOrNull()
            ?: lines.firstOrNull { roleOf(accounts.getValue(it.accountId)) == SystemRole.UNMATCHED.subtype }

        suspend fun viewOf(
            kind: TransactionKind,
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
                balanceAfterPaise = primaryLine?.balanceAfterPaise,
                occurredOn = entry.occurredOn,
                status = entry.status,
                source = entry.source,
                counterpartyId = entry.counterpartyId,
                groupId = entry.groupId,
                note = entry.note,
                tags = TagCodec.decode(entry.tags),
                linkedTransactionId = entry.linkedTransactionId,
            )
        }

        return when {
            SystemRole.OPEN_EQUITY.subtype in sysPresent -> viewOf(TransactionKind.OPENING, primary)
            SystemRole.RECON_EQUITY.subtype in sysPresent -> viewOf(TransactionKind.RECONCILIATION, primary)

            recvLines.isNotEmpty() ->
                if (expenseLines.isNotEmpty()) viewOf(TransactionKind.SPLIT, primary)
                else viewOf(
                    if (recvLines[0].amountPaise > 0) TransactionKind.LOAN else TransactionKind.LOAN_REPAYMENT,
                    primary,
                )

            SystemRole.INVESTMENT.subtype in sysPresent -> viewOf(TransactionKind.INVESTMENT, primary)

            userLines.size >= 2 -> {
                val src = userLines.firstOrNull { it.amountPaise < 0 } ?: userLines.first()
                val dst = userLines.firstOrNull { it.amountPaise > 0 }
                viewOf(TransactionKind.TRANSFER, src, toAccountId = dst?.accountId)
            }

            expenseLines.isNotEmpty() -> viewOf(
                TransactionKind.EXPENSE, primary, categoryId = expenseLines.singleOrNull()?.accountId
            )

            incomeLines.isNotEmpty() -> viewOf(
                TransactionKind.INCOME, primary, categoryId = incomeLines.singleOrNull()?.accountId
            )

            else -> viewOf(TransactionKind.OTHER, primary)
        }
    }

    private fun fakeAccount(id: Long) = AccountEntity(
        id = id, name = "#$id", accountClass = CLASS_ASSET, createdAt = 0
    )

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
            val entry = transactionDao.getById(id) ?: throw ApiException("transaction $id not found")
            val lines = transactionLineDao.getByTransactionList(id)
            val hasRealAccount = lines.map { it.accountId }.any { leg ->
                val acct = accountDao.getById(leg)
                acct != null && !acct.isSystem && isBalanceSheetClass(acct.accountClass)
            }
            if (!hasRealAccount) {
                throw ApiException(
                    "cannot confirm transaction $id: no real account line yet (classify the orphan first)"
                )
            }
            if (extraTags.isEmpty()) {
                transactionDao.update(entry.copy(status = TransactionStatus.CONFIRMED))
            } else {
                // Union, order-preserving, deduped — same rule as QR bridging.
                val tags = LinkedHashSet(TagCodec.decode(entry.tags))
                tags.addAll(extraTags)
                transactionDao.update(
                    entry.copy(status = TransactionStatus.CONFIRMED, tags = TagCodec.encode(tags.toList()))
                )
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
            ?: throw ApiException("account $accountId not found")
        if (target.isSystem) {
            throw ApiException("cannot assign a system account")
        }
        return db.withTransaction {
            transactionDao.getById(transactionId) ?: throw ApiException("transaction $transactionId not found")
            val lines = transactionLineDao.getByTransactionList(transactionId).toMutableList()
            val idx = lines.indexOfFirst { ln ->
                val acct = accountDao.getById(ln.accountId)
                acct != null && !acct.isSystem && isBalanceSheetClass(acct.accountClass)
            }
            if (idx < 0) throw ApiException("transaction $transactionId has no account leg to assign")
            lines[idx] = lines[idx].copy(accountId = accountId)
            transactionLineDao.update(lines[idx])
            requireView(transactionId)
        }
    }

    /** Audit-soft-delete. Voided transactions drop out of every balance and list. */
    override suspend fun voidTransaction(id: Long, reason: String?) {
        val entry = transactionDao.getById(id) ?: throw ApiException("transaction $id not found")
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
     * each share; one receivable line per share names who owes (line-level
     * counterparty). Stays balanced. On-behalf splits pass person
     * counterparties; we force party_type=person so they land in Friends.
     */
    override suspend fun splitTransaction(id: Long, request: SplitRequest): TransactionView {
        if (request.shares.isEmpty()) throw ApiException("split needs at least one share")
        return db.withTransaction {
            val entry = transactionDao.getById(id) ?: throw ApiException("transaction $id not found")
            val lines = transactionLineDao.getByTransactionList(id).toMutableList()

            val plIdx = lines.indexOfFirst { ln ->
                val acct = accountDao.getById(ln.accountId)
                acct != null && (acct.accountClass == CLASS_EXPENSE || acct.accountClass == CLASS_INCOME)
            }
            if (plIdx < 0) throw ApiException("split target must have a single category line")

            for (share in request.shares) {
                val current = lines[plIdx]
                if (current.amountPaise - share.amountPaise < 0) {
                    throw ApiException("shares exceed the paid amount")
                }
                lines[plIdx] = current.copy(amountPaise = current.amountPaise - share.amountPaise)
                lines.add(
                    TransactionLineEntity(
                        transactionId = id,
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
                LineSpec(it.amountPaise, accountId = it.accountId, counterpartyId = it.counterpartyId)
            })

            transactionLineDao.deleteByTransaction(id)
            transactionLineDao.insertAll(lines)
            transactionDao.update(entry.copy(groupId = request.groupId ?: entry.groupId))

            requireView(id)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // System ledger nodes (get-or-create; is_system + subtype never collide)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * GET /friends — net outstanding per person on the receivable pot.
     * Confirmed, non-void lines only (enforced in SQL). Positive net = they
     * owe you; settled parties are omitted. Sorted most-outstanding first.
     */
    override suspend fun friendsOutstanding(): List<FriendBalance> {
        val pot = systemAccount(SystemRole.RECEIVABLE)
        val cpDao = db.CounterpartyDao()
        return transactionLineDao.netByCounterpartyOnAccount(pot.id)
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

    /**
     * The system pots, addressed by `is_system` + `subtype` (the Rust way —
     * no slugs). Opening and reconciliation equity stay separate subtypes
     * until stored `kind` (3b-2) disambiguates them, after which they merge
     * into a single equity pot.
     */
    enum class SystemRole(val subtype: String, val displayName: String, val accountClass: String) {
        RECEIVABLE("receivable", "Loans & advances", CLASS_ASSET),
        UNMATCHED("unmatched", "Unmatched (orphan SMS)", CLASS_ASSET),
        INVESTMENT("investment", "Investments (unallocated)", CLASS_ASSET),
        OPEN_EQUITY("opening_equity", "Opening balance", CLASS_EQUITY),
        RECON_EQUITY("reconciliation_equity", "Reconciliation adjustments", CLASS_EQUITY),
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
                ?: throw ApiException("account $accountId not found")
            val eq = systemAccount(SystemRole.OPEN_EQUITY)

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
                ?: throw ApiException("cannot record opening balance without a date")

            val sign = if (account.accountClass == CLASS_LIABILITY) -1L else 1L
            val amount = sign * amountPaise
            createTransaction(
                CreateTransactionRequest(
                    occurredOn = occurredOn,
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