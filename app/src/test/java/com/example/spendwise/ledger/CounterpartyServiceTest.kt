package com.example.spendwise.ledger

import com.example.spendwise.ledger.api.Intent
import com.example.spendwise.ledger.service.CounterpartyService
import com.example.spendwise.ledger.service.LedgerService
import com.example.spendwise.ledger.service.Text
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Counterparty resolution: one source of truth for "raw SMS name -> known
 * party or new party", ported from services/counterparties.py.
 */
class CounterpartyServiceTest : BackendTestBase() {

    private val uid = LedgerService.USER_ID

    @Test
    fun `resolveOrCreate creates once then resolves to the same id`() = runTest {
        val first = counterparties.resolveOrCreate(uid, "swiggy@ybl")!!
        val second = counterparties.resolveOrCreate(uid, "swiggy@ybl")!!
        assertEquals(first, second)
        // And the alias row points at exactly one party.
        assertEquals(1, db.CounterpartyAliasDao().countAlias(first, "swiggy@ybl"))
    }

    @Test
    fun `alias matching is normalized (case and whitespace insensitive)`() = runTest {
        val cp = counterparties.resolveOrCreate(uid, "Amazon Pay  ")!!
        assertEquals(cp, counterparties.findByAlias(uid, "amazon pay"))
        assertEquals(cp, counterparties.findByAlias(uid, "AMAZON PAY"))
    }

    @Test
    fun `displayName is the friendly name while raw stays as the alias`() = runTest {
        val contactName = "Rahul Sharma"
        val cp = counterparties.resolveOrCreate(
            uid, rawName = "rahul987@oksbi", displayName = contactName, aliasSource = "upi",
        )!!
        assertEquals(contactName, db.CounterpartyDao().getById(cp)!!.displayName)
        assertEquals(cp, counterparties.findByAlias(uid, "rahul987@oksbi"))
    }

    @Test
    fun `addAlias is idempotent and teaches new handles`() = runTest {
        val cp = counterparties.resolveOrCreate(uid, "netflix")!!
        counterparties.addAlias(cp, "NETFLIX.COM", source = "statement")
        counterparties.addAlias(cp, "netflix.com", source = "statement") // dup, ignored
        assertEquals(cp, counterparties.findByAny(uid, "unknown-brand", "NETFLIX.COM"))
    }

    @Test
    fun `findByAny respects candidate priority`() = runTest {
        val a = counterparties.resolveOrCreate(uid, "zomato")!!
        counterparties.resolveOrCreate(uid, "swiggy")
        assertEquals(a, counterparties.findByAny(uid, null, "zomato", "swiggy"))
        assertNull(counterparties.findByAny(uid, null, null))
    }

    @Test
    fun `blank input resolves to nothing instead of creating junk`() = runTest {
        assertNull(counterparties.resolveOrCreate(uid, ""))
        assertNull(counterparties.resolveOrCreate(uid, null))
        assertNull(counterparties.findByAlias(uid, "   "))
    }

    @Test
    fun `ensurePartyType flips a merchant into a person for loan paths`() = runTest {
        val cp = counterparties.resolveOrCreate(uid, "vikram")!! // defaults to merchant
        counterparties.ensurePartyType(cp, CounterpartyService.PARTY_PERSON)
        assertEquals(CounterpartyService.PARTY_PERSON, db.CounterpartyDao().getById(cp)!!.partyType)
        // No-op when already right.
        counterparties.ensurePartyType(cp, CounterpartyService.PARTY_PERSON)
        assertEquals(CounterpartyService.PARTY_PERSON, db.CounterpartyDao().getById(cp)!!.partyType)
    }

    // ── learned defaults ("remember for this merchant") ──────────────────

    @Test
    fun `rememberDefaults stores and learnedDefaults round-trips`() = runTest {
        val cp = counterparties.resolveOrCreate(uid, "swiggy")!!
        val category = newCategory("Food")
        counterparties.rememberDefaults(
            cp, intent = Intent.EXPENSE, tags = listOf("Food", " Lunch "), categoryId = category,
        )
        val learned = counterparties.learnedDefaults(cp)!!
        assertEquals(Intent.EXPENSE, learned.intent)
        assertEquals(listOf("Food", "Lunch"), learned.tags) // trimmed, order kept
        assertEquals(category, learned.categoryId)
        assertEquals("Food,Lunch", db.CounterpartyDao().getById(cp)!!.defaultTags)
    }

    @Test
    fun `saving with no tags clears the tag memory but keeps the category`() = runTest {
        val cp = counterparties.resolveOrCreate(uid, "swiggy")!!
        val category = newCategory("Eating out")
        counterparties.rememberDefaults(cp, Intent.EXPENSE, listOf("Food"), categoryId = category)
        // Later save: tags removed, no category applied (e.g. loan) — intent
        // and tags follow the last save, the category stays.
        counterparties.rememberDefaults(cp, Intent.LOAN, emptyList())
        val learned = counterparties.learnedDefaults(cp)!!
        assertEquals(Intent.LOAN, learned.intent)
        assertTrue(learned.tags.isEmpty())
        assertEquals(category, learned.categoryId)
    }

    @Test
    fun `learnedDefaults is null until something is learned`() = runTest {
        val cp = counterparties.resolveOrCreate(uid, "zomato")!!
        assertNull(counterparties.learnedDefaults(cp))
        counterparties.rememberDefaults(cp, Intent.EXPENSE, emptyList())
        assertNotNull(counterparties.learnedDefaults(cp))
    }

    @Test
    fun `learned defaults tolerate unknown ids and junk labels`() = runTest {
        assertNull(counterparties.learnedDefaults(424242L))
        assertNull(counterparties.learnedDefaults(-1L))
        val cp = counterparties.resolveOrCreate(uid, "swiggy")!!
        counterparties.rememberDefaults(cp, "   ", listOf("  ", "", "Food"), categoryId = null)
        val learned = counterparties.learnedDefaults(cp)!!
        assertNull(learned.intent)
        assertEquals(listOf("Food"), learned.tags)
        assertNull(learned.categoryId)
    }

    // ── pure text rules ──────────────────────────────────────────────────

    @Test
    fun `vpa extraction handles handles, hyphens and blanks`() {
        assertEquals("name@oksbi", Text.extractVpa("paid to name@oksbi via upi"))
        // Hyphen excluded from local-part: no surrounding tokens.
        assertEquals("paytmqr6ngjl2@ptys", Text.extractVpa("KU-paytmqr6ngjl2@ptys-NO"))
        assertNull(Text.extractVpa(null))
        assertNull(Text.extractVpa(""))
        assertNull(Text.extractVpa("no handle here"))
    }

    @Test
    fun `sms hash is stable across whitespace and sender case`() {
        assertEquals(
            Text.smsHash("SBIINB", "debited Rs.10"),
            Text.smsHash(" sbiinb ", " debited Rs.10 "),
        )
        org.junit.Assert.assertNotEquals(Text.smsHash("A", "x"), Text.smsHash("AB", "x"))
    }
}