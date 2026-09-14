package com.example.spendwise.ui.screens.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The UPI QR decode/encode pair that Scan & Pay depends on. Extracted from
 * `ScannerScreen.kt` into `UpiQr.kt` specifically so this logic could be
 * covered without a camera: a regression here means payments go to the wrong
 * payee or lose the amount the QR carried.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpiQrTest {

    @Test
    fun `parses a standard upi qr with amount and note`() {
        val target = parseUpiQr(
            "upi://pay?pa=merchant@okhdfcbank&pn=Cotton%20Dhora&am=250.00&cu=INR&tn=Order%2012"
        )

        assertNotNull(target)
        assertEquals("merchant@okhdfcbank", target!!.vpa)
        assertEquals("Cotton Dhora", target.name)
        assertEquals("250.00", target.qrAmount)
        assertEquals("Order 12", target.qrNote)
    }

    @Test
    fun `leaves amount open when the qr does not carry one`() {
        val target = parseUpiQr("upi://pay?pa=friend@ybl&pn=Rahul")

        assertNotNull(target)
        assertEquals("Rahul", target!!.name)
        assertNull(target.qrAmount)
        assertNull(target.qrNote)
    }

    @Test
    fun `rejects non-upi content`() {
        assertNull(parseUpiQr("https://example.com/pay?pa=x@ybl"))
        assertNull(parseUpiQr("WIFI:S:MyNetwork;T:WPA;P:secret;;"))
        assertNull(parseUpiQr(""))
    }

    @Test
    fun `rejects upi payloads without a payee address`() {
        assertNull(parseUpiQr("upi://pay?pn=Someone&am=100"))
    }

    @Test
    fun `ignores a malformed amount rather than passing it through`() {
        val target = parseUpiQr("upi://pay?pa=shop@ybl&am=not-a-number")

        assertNotNull(target)
        assertNull(target!!.qrAmount)
    }

    @Test
    fun `builds a deep link that round-trips through the parser`() {
        val uri = buildUpiUri(
            vpa = "merchant@okhdfcbank",
            name = "Cotton Dhora",
            amount = "250.00",
            note = "Order 12"
        )

        assertEquals("upi", uri.scheme)
        val roundTripped = parseUpiQr(uri.toString())
        assertNotNull(roundTripped)
        assertEquals("merchant@okhdfcbank", roundTripped!!.vpa)
        assertEquals("Cotton Dhora", roundTripped.name)
        assertEquals("250.00", roundTripped.qrAmount)
        assertEquals("Order 12", roundTripped.qrNote)
    }

    @Test
    fun `falls back to the vpa as the payee name when none was given`() {
        val uri = buildUpiUri(vpa = "shop@ybl", name = "", amount = "10", note = "")

        assertEquals("shop@ybl", parseUpiQr(uri.toString())!!.name)
    }
}