package com.example.spendwise.backend.service

import com.example.spendwise.backend.api.ApiException
import com.example.spendwise.data.database.dao.CounterpartyAliasDao
import com.example.spendwise.data.database.dao.CounterpartyDao
import com.example.spendwise.data.database.entity.CounterpartyAliasEntity
import com.example.spendwise.data.database.entity.CounterpartyEntity
import javax.inject.Inject

/**
 * Counterparty resolution — the single source of truth for "given a raw name
 * from an SMS / UPI notification / manual entry, find the matching
 * counterparty or create one". Port of the old server's services/counterparties.py,
 * where the same logic lived in three routers and each was subtly different.
 */
class CounterpartyService @Inject constructor(
    private val counterpartyDao: CounterpartyDao,
    private val aliasDao: CounterpartyAliasDao,
) {

    /**
     * Resolve by normalized alias, or create the counterparty (+ its first
     * alias) when nothing matches. [rawName] is whatever the caller pulled
     * from the message/form — usually a UPI handle or merchant slug.
     * [displayName], when given, is the friendly first-creation name; the raw
     * string is still saved as an alias so the next message resolves without
     * the hint. Returns null for a blank input.
     */
    suspend fun resolveOrCreate(
        userId: Long,
        rawName: String?,
        displayName: String? = null,
        aliasSource: String = "sms",
    ): Long? {
        if (rawName.isNullOrBlank()) return null

        findByAlias(userId, rawName)?.let { return it }

        val cpId = counterpartyDao.insert(
            CounterpartyEntity(
                displayName = displayName ?: rawName,
                partyType = PARTY_MERCHANT,
                createdAt = System.currentTimeMillis(),
            )
        )
        addAlias(cpId, rawName, source = aliasSource)
        return cpId
    }

    /** Counterparty id whose alias matches [rawName] (normalized), or null. */
    suspend fun findByAlias(userId: Long, rawName: String?): Long? {
        if (rawName.isNullOrBlank()) return null
        val norm = Text.normalize(rawName)
        if (norm.isEmpty()) return null
        val alias = aliasDao.findByNormalizedAlias(norm) ?: return null
        // Alias uniqueness is global; ownership is checked by the caller-scoped
        // user model (single-user today). Kept as a lookup seam for multi-user.
        return alias.counterpartyId
    }

    /** First counterparty matching any candidate, in priority order. */
    suspend fun findByAny(userId: Long, vararg candidates: String?): Long? {
        for (raw in candidates) {
            if (!raw.isNullOrBlank()) {
                findByAlias(userId, raw)?.let { return it }
            }
        }
        return null
    }

    /** Attach [raw] as an alias if absent — how the system learns. Idempotent. */
    suspend fun addAlias(counterpartyId: Long, raw: String?, source: String) {
        if (raw.isNullOrBlank()) return
        val norm = Text.normalize(raw)
        if (norm.isEmpty()) return
        if (aliasDao.countAlias(counterpartyId, norm) > 0) return
        aliasDao.insert(
            CounterpartyAliasEntity(
                counterpartyId = counterpartyId,
                aliasNorm = norm,
                aliasDisplay = raw,
                source = source,
            )
        )
    }

    /**
     * Set a counterparty's party_type if it differs. A loan/repayment defines
     * its counterparty as a person you transact with personally, so loan paths
     * call this to guarantee it lands in the Friends ledger. No-op otherwise.
     */
    suspend fun ensurePartyType(counterpartyId: Long?, partyType: String) {
        if (counterpartyId == null) return
        val cp = counterpartyDao.getById(counterpartyId) ?: return
        if (cp.partyType != partyType) {
            counterpartyDao.update(cp.copy(partyType = partyType))
        }
    }

    suspend fun requireCounterparty(id: Long): CounterpartyEntity =
        counterpartyDao.getById(id) ?: throw ApiException("counterparty $id not found")

    companion object {
        const val PARTY_MERCHANT = "merchant"
        const val PARTY_PERSON = "person"
    }
}