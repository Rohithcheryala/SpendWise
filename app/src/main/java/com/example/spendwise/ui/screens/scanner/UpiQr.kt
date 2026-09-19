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
 *  - `tn` (note): payer-editable per the UPI spec, so the user's note wins —
 *    replaced in place, or appended when the QR had none.
 *
 * Everything else — param order, percent-encoding, unknown fields (`sign`,
 * `tr`, `mc`, `mid`, …) — stays untouched.
 */
internal fun buildUpiLaunchUri(raw: String, amount: String, note: String): String {
    val base = raw.substringBefore('?')
    var query = raw.substringAfter('?', "")

    fun has(key: String): Boolean =
        Regex("(^|&)$key=", RegexOption.IGNORE_CASE).containsMatchIn(query)

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
    if (note.isNotBlank()) set("tn", Uri.encode(note))

    return if (query.isBlank()) base else "$base?$query"
}
