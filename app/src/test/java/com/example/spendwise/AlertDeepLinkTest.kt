package com.example.spendwise

import android.content.Intent
import com.example.spendwise.core.notifications.TransactionNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Notification tap -> in-app route. This mapping is the whole difference
 * between "the alert opens the entry it is about" and "the alert resumes the
 * app wherever the user left it", so it is pinned here rather than discovered
 * on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertDeepLinkTest {

    @Test
    fun `alert tap routes to that entry's detail screen`() {
        val intent = Intent(Intent.ACTION_VIEW)
            .putExtra(TransactionNotifier.EXTRA_TRANSACTION_ID, 42L)

        assertEquals("transaction?transactionId=42", intent.toAlertRoute())
    }

    @Test
    fun `launcher tap is left alone`() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        assertNull(launcher.toAlertRoute())
    }

    @Test
    fun `an absent or non-positive id does not route`() {
        assertNull(null.toAlertRoute())
        assertNull(Intent(Intent.ACTION_VIEW).toAlertRoute())
        assertNull(
            Intent(Intent.ACTION_VIEW)
                .putExtra(TransactionNotifier.EXTRA_TRANSACTION_ID, 0L)
                .toAlertRoute()
        )
        assertNull(
            Intent(Intent.ACTION_VIEW)
                .putExtra(TransactionNotifier.EXTRA_TRANSACTION_ID, -1L)
                .toAlertRoute()
        )
    }

    @Test
    fun `alert uri names the transaction`() {
        assertEquals(
            "spendwise://transaction/42",
            TransactionNotifier.deepLinkUri(42L).toString()
        )
    }
}
