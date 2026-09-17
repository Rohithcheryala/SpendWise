package com.example.spendwise.ui.components

import com.example.spendwise.core.extensions.toDisplayDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Pins the transaction form's date formatting.
 *
 * The form used to print `LocalDate.toString()` — i.e. `2026-09-15` straight
 * at the user. These tests exist because the readable form has a real edge:
 * `d MMM` must stay **day-first** and locale-independent, or a device set to
 * `en-US` would render "Sep 15 2026", which reads as a different date and
 * breaks the field's alignment with the amount beside it.
 */
class DateDisplayFormatTest {

    @Test
    fun `dates render day-first with a short month name`() {
        assertEquals("15 Sep 2026", LocalDate.of(2026, 9, 15).toDisplayDate())
        assertEquals("1 Jan 2026", LocalDate.of(2026, 1, 1).toDisplayDate())
        assertEquals("31 Dec 2026", LocalDate.of(2026, 12, 31).toDisplayDate())
    }

    @Test
    fun `single digit days are not zero padded`() {
        // "9 Sep 2026", never "09 Sep 2026" — the form field is narrow and
        // shares its row with the amount.
        assertEquals("9 Sep 2026", LocalDate.of(2026, 9, 9).toDisplayDate())
    }
}
