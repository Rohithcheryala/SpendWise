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
    fun `launch uri keeps a signed merchant qr byte-identical and only appends`() {
        // A dynamic merchant QR: signed (`sign=`) over the exact text, odd
        // param order, params SpendWise doesn't understand. Rebuilding it
        // (the old buildUpiUri) dropped `sign`/`tr`/`mid` and re-ordered the
        // rest — BHIM then rejected the payment with "Receiver bank failure".
        val raw = "upi://pay?cu=INR&pa=chaihub@ybl&pn=Chai%20Hub&tr=TXN889&mid=UKHSBX&sign=Zk9wA=="
        val uri = buildUpiLaunchUri(raw, amount = "15", note = "")

        assertEquals(
            "upi://pay?cu=INR&pa=chaihub@ybl&pn=Chai%20Hub&tr=TXN889&mid=UKHSBX&sign=Zk9wA==&am=15.00",
            uri,
        )
    }

    @Test
    fun `a locked qr amount is never overridden`() {
        val raw = "upi://pay?pa=shop@ybl&am=250.00&cu=INR"

        assertEquals(raw, buildUpiLaunchUri(raw, amount = "10", note = ""))
    }

    @Test
    fun `the user note replaces the qr note in place`() {
        val raw = "upi://pay?pa=shop@ybl&am=250.00&cu=INR&tn=merchant-phonepe"

        assertEquals(
            "upi://pay?pa=shop@ybl&am=250.00&cu=INR&tn=Dinner%20split",
            buildUpiLaunchUri(raw, amount = "250.00", note = "Dinner split"),
        )
    }

    @Test
    fun `an open qr gets the user amount and currency appended`() {
        val uri = buildUpiLaunchUri("upi://pay?pa=friend@ybl&pn=Rahul", amount = "15.5", note = "chai")

        assertEquals("upi://pay?pa=friend@ybl&pn=Rahul&am=15.50&cu=INR&tn=chai", uri)
    }

    @Test
    fun `launch uri round-trips through the parser`() {
        val raw = "upi://pay?pa=merchant@okhdfcbank&pn=Cotton%20Dhora&am=250.00"
        val uri = buildUpiLaunchUri(raw, amount = "250.00", note = "Order 12")
        val roundTripped = parseUpiQr(uri)

        assertNotNull(roundTripped)
        assertEquals("merchant@okhdfcbank", roundTripped!!.vpa)
        assertEquals("Cotton Dhora", roundTripped.name)
        assertEquals("250.00", roundTripped.qrAmount)
        assertEquals("Order 12", roundTripped.qrNote)
    }

    @Test
    fun `manual vpa without note stays launchable`() {
        val raw = "upi://pay?pa=9876543210@upi"
        val uri = buildUpiLaunchUri(raw, amount = "", note = "")

        assertEquals("upi://pay?pa=9876543210@upi&cu=INR", uri)
    }
}