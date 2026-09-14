package com.example.spendwise.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the money formatter used by [MoneyText].
 *
 * The formatter is hand-rolled (not `String.format`) because Indian digit
 * grouping is not "thousands" grouping: 1,53,250 not 153,250. These tests exist
 * so a future "simplification" to a plain `%,d` can't silently ship.
 */
class MoneyFormatTest {

    @Test
    fun `whole rupee amounts use indian grouping and drop decimals`() {
        assertEquals("₹1,53,250", formatPaise(15_325_000L))
        assertEquals("₹10,000", formatPaise(1_000_000L))
        assertEquals("₹100", formatPaise(10_000L))
        assertEquals("₹999", formatPaise(99_900L))
    }

    @Test
    fun `paise are shown only when present`() {
        assertEquals("₹1,234.56", formatPaise(123_456L))
        assertEquals("₹12.05", formatPaise(1_205L))
        assertEquals("₹0.50", formatPaise(50L))
    }

    @Test
    fun `zero renders without decimals`() {
        assertEquals("₹0", formatPaise(0L))
    }

    @Test
    fun `negative amounts keep a leading minus`() {
        assertEquals("-₹500", formatPaise(-50_000L))
        assertEquals("-₹1,234.56", formatPaise(-123_456L))
    }

    @Test
    fun `rupee doubles convert to paise before formatting`() {
        assertEquals("₹1,53,250", formatRupees(153_250.0))
        assertEquals("₹252.90", formatRupees(252.9))
        // Rounds rather than truncates (10.006 -> 1000.6 paise -> 1001).
        assertEquals("₹10.01", formatRupees(10.006))
    }
}