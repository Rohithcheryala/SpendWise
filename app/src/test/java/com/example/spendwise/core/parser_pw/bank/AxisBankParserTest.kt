package com.example.spendwise.core.parser_pw.bank

import com.example.spendwise.core.parser_pw.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Regression tests for Axis Bank credit/debit SMS parsing — written after a
 * real ₹1,53,250 NEFT credit notification never fired: these tests pin the
 * Samsung-style Axis formats so parser gaps are caught at test time.
 */
class AxisBankParserTest {

    private fun parse(body: String, sender: String = "AXISBK") =
        BankParserFactory.parse(smsBody = body, sender = sender, timestamp = 0L)

    @Test
    fun `rupee-symbol neft credit from samsung messaging format parses as income`() {
        val parsed = parse(
            "₹ 1,53,250.00 credited to your A/c XX1234 on 30-08-26 by " +
                "NEFT/CHASH/REF123456. Avl Bal: ₹2,00,000.00 -Axis Bank"
        )
        assertNotNull("Samsung-style ₹ NEFT credit SMS must parse", parsed)
        assertEquals(0, parsed!!.amount.compareTo(BigDecimal("153250.00")))
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun `neft narration credit format parses as income`() {
        val parsed = parse(
            "Your A/c XX1234 is credited by NEFT/CHASH on 30-08-26 for " +
                "Rs. 1,53,250.00. Avl Bal Rs. 2,00,000.00"
        )
        assertNotNull("NEFT narration credit SMS must parse", parsed)
        assertEquals(0, parsed!!.amount.compareTo(BigDecimal("153250.00")))
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun `inr credited format parses as income`() {
        val parsed = parse(
            "INR 1,53,250.00 credited to your account XX1234 on 30-08-26. " +
                "Avl Bal INR 2,00,000.00 - Axis Bank"
        )
        assertNotNull(parsed)
        assertEquals(0, parsed!!.amount.compareTo(BigDecimal("153250.00")))
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun `dlt sender id routes to axis parser`() {
        // Real DLT transaction senders look like "AD-AXISBK-S" / "AX-AXIS-S".
        val parsed = parse(
            "₹ 500.00 debited from A/c XX1234 on 30-08-26 for UPI purchase. Avl Bal ₹1,000.00",
            sender = "AD-AXISBK-S",
        )
        assertNotNull("DLT sender must route to the Axis parser", parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
    }

    @Test
    fun `otp messages never parse as transactions`() {
        assertNull(parse("Your OTP for Axis Bank login is 123456. Do not share."))
    }

    @Test
    fun `non-axis senders are not handled`() {
        assertNull(
            BankParserFactory.parse(
                smsBody = "₹ 1,53,250.00 credited to your A/c XX1234",
                sender = "HDFCBK",
                timestamp = 0L,
            )?.takeIf { it.bankName == "Axis Bank" }
        )
    }
}