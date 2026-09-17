package com.example.spendwise.ledger.service

import java.security.MessageDigest

/**
 * Canonical text helpers (port of the old server's services/text.py +
 * counterparties.py regex). One definition of "how we normalise a name" and
 * "what counts as a UPI handle" — shared by every ingestion path.
 */
object Text {

    /** Canonical form for alias/name matching: stripped + lowercased. */
    fun normalize(s: String): String = s.trim().lowercase()

    /**
     * A UPI VPA (e.g. `name@oksbi`). The local-part excludes '-' so a
     * hyphen-delimited narration like `...KU-paytmqr6ngjl2@ptys-NO` yields
     * `paytmqr6ngjl2@ptys`, not the surrounding tokens.
     */
    private val VPA_RE = Regex("[A-Za-z0-9._]+@[A-Za-z][A-Za-z0-9.]*")

    /** First UPI VPA in [text], or null. */
    fun extractVpa(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        return VPA_RE.find(text)?.value
    }

    /** Stable dedup key for an inbound SMS (sender upper-cased, body trimmed). */
    fun smsHash(sender: String, body: String): String {
        val raw = "${sender.trim().uppercase()}|${body.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ── contacts resolution ──────────────────────────────────────────────
    // Port of the old server's services/contacts.py. A UPI handle whose local
    // part IS a phone number (`9876543210@okaxis`) can resolve to a saved
    // contact; `name@bank` handles cannot.

    /** Phone-prefixed handle: 10-12 digits, optional `-N`, then `@provider`. */
    private val PHONE_HANDLE_RE = Regex("(\\+?\\d{10,12})(?:-\\d+)?@[A-Za-z][A-Za-z0-9.]*")

    /** Last 10 digits — strips +91, leading 0, separators. "" when too short. */
    fun normalizePhone(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else ""
    }

    /** Normalized phones for every phone-prefixed handle in [text], in order, deduped. */
    fun phonesIn(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        val out = mutableListOf<String>()
        for (m in PHONE_HANDLE_RE.findAll(text)) {
            val p = normalizePhone(m.groupValues[1])
            if (p.isNotEmpty() && p !in out) out.add(p)
        }
        return out
    }

    /** An unresolved display: a raw handle (`x@bank`), never a human name. */
    fun looksLikeHandle(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val s = name.trim()
        return "@" in s && " " !in s
    }
}

