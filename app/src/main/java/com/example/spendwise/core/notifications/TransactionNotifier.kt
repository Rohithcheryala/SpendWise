package com.example.spendwise.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.spendwise.R
import com.example.spendwise.core.extensions.toAmountString
import com.example.spendwise.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the "transaction captured" alerts. The single notification entry point
 * so the gating lives in exactly one place:
 *
 *  1. the Settings "Notifications" toggle (the toggle is now real, not dead wiring), and
 *  2. the system POST_NOTIFICATIONS runtime grant (Android 13+).
 *
 * Everything else stays silent — a denied permission or an off toggle must
 * never throw into the SMS receive path.
 *
 * Tap routing lives here too: each alert carries the ledger entry id it is
 * about, so the tap can open *that* entry instead of merely resuming the app.
 */
@Singleton
class TransactionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    /** One auto-detected bank SMS turned into a buffer entry. */
    suspend fun postCapturedTransaction(
        transactionId: Long,
        party: String,
        amountPaise: Long,
        isDebit: Boolean,
        accountName: String?,
    ) {
        if (!settingsRepository.settings.first().notificationsEnabled) {
            Log.i(TAG, "No alert: Notifications toggle is OFF in app Settings")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "No alert: POST_NOTIFICATIONS runtime grant denied (Android 13+)")
            return
        }
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(TAG, "No alert: app notifications disabled system-wide in system settings")
            return
        }

        ensureChannel()
        notificationManager.getNotificationChannel(CHANNEL_ID)?.let { channel ->
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                Log.w(TAG, "No alert: '$CHANNEL_ID' channel is muted in system settings")
                return
            }
        }

        val amount = amountPaise.toAmountString()
        val title = if (isDebit) "Spent $amount" else "Received $amount"
        val body = buildString {
            append(if (isDebit) "to " else "from ")
            append(party)
            if (!accountName.isNullOrBlank()) append(" • ").append(accountName)
        }

        // Tap routing. `getLaunchIntentForPackage` returns the bare
        // MAIN/LAUNCHER intent, which names no destination — tapping the alert
        // then did nothing but resume the task wherever the user had left it.
        // Re-point that same (explicit-component) intent at ACTION_VIEW with a
        // `spendwise://transaction/<id>` URI and the id as an extra, so the tap
        // can open the entry it is actually about.
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                action = Intent.ACTION_VIEW
                data = deepLinkUri(transactionId)
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_transaction)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\nTap to review in the Buffer Inbox."))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply {
                if (openApp != null) {
                    setContentIntent(
                        PendingIntent.getActivity(
                            context,
                            transactionId.toInt(),
                            openApp,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    )
                }
            }
            .build()

        runCatching {
            // Keyed on the entry, not the clock: a re-alert for the same
            // transaction replaces its notification instead of stacking a
            // duplicate, and it keeps the PendingIntent request code distinct
            // per transaction. That matters because extras are NOT part of
            // Intent.filterEquals — with a shared request code plus
            // FLAG_UPDATE_CURRENT, one alert's tap would open another's entry.
            notificationManager.notify(transactionId.toInt(), notification)
        }.onFailure {
            Log.w(TAG, "Alert post failed", it)
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Captured transactions",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Alerts when a bank SMS is auto-captured into the Buffer Inbox"
            }
        )
    }

    companion object {
        private const val TAG = "TransactionNotifier"
        const val CHANNEL_ID = "transaction_alerts"

        /**
         * Read back by `MainActivity.toAlertRoute()` to turn a tap into an
         * in-app route. Declared here because this class is what produces the
         * intent; the consumer lives in the app layer.
         */
        const val EXTRA_TRANSACTION_ID = "com.example.spendwise.extra.TRANSACTION_ID"

        /** `spendwise://transaction/42` — the alert's machine-readable target. */
        fun deepLinkUri(transactionId: Long): Uri = Uri.parse("spendwise://transaction/$transactionId")
    }
}
