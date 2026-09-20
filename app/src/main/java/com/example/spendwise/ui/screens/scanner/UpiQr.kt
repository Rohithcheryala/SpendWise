package com.example.spendwise.ui.screens.scanner

import android.net.Uri

/**
 * The UPI QR side of Scan & Pay: decoding a scanned QR into a payment target and
 * re-encoding an edited target back into the `upi://pay` deep link handed to the
 * user's UPI app.
 *
 * Split out of `ScannerScreen.kt` because it is pure, framework-light logic with
 * no Compose or CameraX in it — the part of the scanner that is worth unit
 * testing on its own.
 *//** A decoded UPI payment target: who to pay, and any amount/note the QR carried. */
data class UpiTarget(
    val name: String,
    val vpa: String,
    /** Amount pre-encoded in the QR ("am=" param), null when the QR leaves it open. */
    val qrAmount: String?,
    /** Note pre-encoded in the QR ("tn=" param). */
    val qrNote: String?,
    /**
     * The raw scanned URI, byte-for-byte. The launch URI handed to the UPI
     * app is derived from THIS — never re-encoded from the parsed fields
     * (see [buildUpiLaunchUri] for why).
     */
    val raw: String,
)

/** `upi://pay?pa=...&pn=...&am=...&tn=...` -> [UpiTarget], null for non-UPI QRs. */
fun parseUpiQr(raw: String): UpiTarget? {
    if (!raw.startsWith("upi://", ignoreCase = true)) return null
    val parsed = Uri.parse(raw)
    val payeeAddress = parsed.getQueryParameter("pa")?.takeIf { it.isNotBlank() } ?: return null
    return UpiTarget(
        name = parsed.getQueryParameter("pn").orEmpty(),
        vpa = payeeAddress,
        qrAmount = parsed.getQueryParameter("am")?.takeIf { it.toDoubleOrNull() != null },
        qrNote = parsed.getQueryParameter("tn")?.takeIf { it.isNotBlank() },
        raw = raw,
    )
}

/**
 * The launch URI handed to the user's UPI app. The scanned string passes
 * through BYTE-IDENTICAL except for three controlled edits.
 *
 * Why not rebuild from the parsed fields (`Uri.buildUpon() +
 * appendQueryParameter`)? Merchant QRs are frequently signed — `sign=` and
 * `tr=` are computed over the QR's exact text — and re-encoding or
 * reordering params invalidates the signature. NPCI-side validation then
 * fails and the UPI app reports "Receiver bank failure", even though the
 * payee VPA is fine. This exact bug was hit (and solved) in naa-accounting's
 * scan page; the logic below is its port.
 *
 *  - `am` (amount): appended ONLY when the QR didn't fix one — a dynamic QR's
 *    amount is authoritative, never overridden (the form locks it too).
 *  - `cu` (currency): appended when missing.
 *  - `tn` (note): replaced in place ONLY when the user actually edited it —
 *    the overlay prefills the field from the QR's note, and re-encoding it
 *    is not guaranteed byte-identical to what the merchant wrote.
 *
 * Signed QRs (`sign=`, `tr=`, `trid=`) get NONE of these: they are launched
 * byte-identical, full stop (see below). Everything else — param order,
 * percent-encoding, unknown fields (`mc`, `mid`, …) — stays untouched.
 *
 * The hard rule: a SIGNED or dynamic QR is never edited, not even by
 * appending a missing param. The signature covers the query's exact text;
 * NPCI validates it at PAY time, so the UPI app happily displays the
 * payee/amount and then rejects the transfer ("Receiver bank failure") —
 * the worst failure shape because it looks like the bank's fault. The v1.0.4
 * passthrough fix still appended `am`/`cu` and re-encoded `tn` on such QRs,
 * which is why payments kept failing after it. The UPI apps' own scanners
 * never edit either; when a signed QR leaves the amount open, the UPI app
 * collects it — SpendWise's form value is then just the ledger record.
 */
internal fun buildUpiLaunchUri(raw: String, amount: String, note: String): String {
    val base = raw.substringBefore('?')
    var query = raw.substringAfter('?', "")

    fun has(key: String): Boolean =
        Regex("(^|&)$key=", RegexOption.IGNORE_CASE).containsMatchIn(query)

    // Signed / dynamic QRs: hands off, completely.
    if (has("sign") || has("tr") || has("trid")) return raw

    // Replace a single param's value IN PLACE (leaving every other byte of
    // the query untouched), or append it if absent.
    fun set(key: String, value: String) {
        val re = Regex("(^|&)$key=[^&]*", RegexOption.IGNORE_CASE)
        query = if (re.containsMatchIn(query)) {
            re.replace(query) { match -> "${match.groupValues[1]}$key=$value" }
        } else if (query.isBlank()) {
            "$key=$value"
        } else {
            "$query&$key=$value"
        }
    }

    // NPCI apps expect two decimals; format explicitly so no locale gives
    // us a comma decimal separator.
    val rupees = amount.toDoubleOrNull()
    if (!has("am") && rupees != null && rupees > 0) {
        set("am", String.format(java.util.Locale.US, "%.2f", rupees))
    }
    if (!has("cu")) set("cu", "INR")
    // Only touch `tn` when the user actually changed the note. The overlay
    // prefills the field with the QR's note; an unedited note re-encoded
    // through Uri.encode() is not guaranteed to match the merchant's
    // original bytes, and one changed byte in a signed query is fatal.
    if (note.isNotBlank() && note != Uri.parse(raw).getQueryParameter("tn")) {
        set("tn", Uri.encode(note))
    }

    return if (query.isBlank()) base else "$base?$query"
}
