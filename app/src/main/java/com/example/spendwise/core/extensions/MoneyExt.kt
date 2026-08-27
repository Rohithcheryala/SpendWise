package com.example.spendwise.core.extensions

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val moneyFormat = DecimalFormat("#,##0.00").apply {
    decimalFormatSymbols = DecimalFormatSymbols(Locale.US)
}

private val moneyFormatWhole = DecimalFormat("#,##0").apply {
    decimalFormatSymbols = DecimalFormatSymbols(Locale.US)
}

/** 125000 paise -> "₹1,250.00" (or "₹1,250" when there are no paise). */
fun Long.toAmountString(symbol: String = "₹"): String {
    val negative = this < 0
    val abs = kotlin.math.abs(this)
    val whole = abs / 100
    val paise = abs % 100
    val body = if (paise == 0L) {
        moneyFormatWhole.format(whole)
    } else {
        moneyFormat.format(whole + paise / 100.0)
    }
    return (if (negative) "−" else "") + symbol + body
}

/** 125000 paise -> "1250" or "1250.50" — for prefilling amount text fields. */
fun Long.toRupeeInput(): String {
    val whole = kotlin.math.abs(this) / 100
    val paise = kotlin.math.abs(this) % 100
    val sign = if (this < 0) "-" else ""
    return if (paise == 0L) "$sign$whole" else "$sign$whole.${paise.toString().padStart(2, '0')}"
}

/** A user-entered rupee amount string ("1250", "1,250.50") -> paise, or null. */
fun String.toPaiseOrNull(): Long? {
    val cleaned = filter { it.isDigit() || it == '.' }
    if (cleaned.isEmpty()) return null
    val value = cleaned.toBigDecimalOrNull() ?: return null
    return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
}
