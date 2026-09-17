package com.example.spendwise.core.extensions

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

fun Long.toFormattedDateTime(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(formatter)

// `Locale.US` explicitly, matching MoneyExt: the month name must not change
// with the device locale or the day-first order breaks ("Sep 15 2026" reads as
// a different date than "15 Sep 2026").
private val displayDateFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

/** `2026-09-15` → `"15 Sep 2026"` — ISO is a storage format, not a readable one. */
fun LocalDate.toDisplayDate(): String = format(displayDateFormatter)