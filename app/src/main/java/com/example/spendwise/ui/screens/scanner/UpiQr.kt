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
    )
}

internal fun buildUpiUri(vpa: String, name: String, amount: String, note: String): Uri =
    Uri.parse("upi://pay").buildUpon()
        .appendQueryParameter("pa", vpa)
        .appendQueryParameter("pn", name.ifBlank { vpa })
        .appendQueryParameter("am", amount)
        .appendQueryParameter("cu", "INR")
        .apply { if (note.isNotBlank()) appendQueryParameter("tn", note) }
        .build()
