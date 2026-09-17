package com.example.spendwise.viewmodel

import com.example.spendwise.ledger.api.Intent
import com.example.spendwise.ui.components.TransactionDirection
import com.example.spendwise.ui.screens.transactiondetail.OtherSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Learned-default intent → editor selector mapping (the prefill rule). */
class TransactionIntentPrefillTest {

    @Test
    fun `expense and income map to category mode with matching direction`() {
        assertEquals(
            TransactionDirection.EXPENSE to OtherSide.CATEGORY,
            selectionForIntent(Intent.EXPENSE),
        )
        assertEquals(
            TransactionDirection.INCOME to OtherSide.CATEGORY,
            selectionForIntent(Intent.INCOME),
        )
    }

    @Test
    fun `loan and repayment map to loan mode with matching direction`() {
        assertEquals(
            TransactionDirection.EXPENSE to OtherSide.LOAN,
            selectionForIntent(Intent.LOAN),
        )
        assertEquals(
            TransactionDirection.INCOME to OtherSide.LOAN,
            selectionForIntent(Intent.LOAN_REPAYMENT),
        )
    }

    @Test
    fun `transfer maps to transfer mode`() {
        assertEquals(
            TransactionDirection.EXPENSE to OtherSide.TRANSFER,
            selectionForIntent(Intent.TRANSFER),
        )
    }

    @Test
    fun `intents without an editor mode never prefill`() {
        assertNull(selectionForIntent(Intent.INVESTMENT))
        assertNull(selectionForIntent(Intent.RECONCILIATION))
        assertNull(selectionForIntent("opening"))
        assertNull(selectionForIntent("junk"))
    }
}