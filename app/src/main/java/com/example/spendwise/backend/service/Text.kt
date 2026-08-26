package com.example.spendwise.backend.service

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
}

/** Tiny JSON-string-list codec matching the old server's tags storage. */
object TagCodec {

    fun encode(tags: List<String>): String =
        "[" + tags.joinToString(",") { tag ->
            "\"" + tag.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } + "]"

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = mutableListOf<String>()
        var i = 1
        val end = raw.length - 1
        val sb = StringBuilder()
        var inString = false
        while (i < end) {
            val c = raw[i]
            when {
                c == '\\' && inString && i + 1 < end -> {
                    sb.append(raw[i + 1]); i++
                }

                c == '"' -> {
                    if (inString) {
                        out.add(sb.toString()); sb.clear()
                    }
                    inString = !inString
                }

                inString -> sb.append(c)
            }
            i++
        }
        return out
    }
}