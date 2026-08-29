package com.example.spendwise.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
 */
@Singleton
class TransactionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    /** One auto-detected bank SMS turned into a buffer entry. */
    suspend fun postCapturedTransaction(
        party: String,
        amountPaise: Long,
        isDebit: Boolean,
        accountName: String?,
    ) {
        if (!settingsRepository.settings.first().notificationsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel()

        val amount = amountPaise.toAmountString()
        val title = if (isDebit) "Spent $amount" else "Received $amount"
        val body = buildString {
            append(if (isDebit) "to " else "from ")
            append(party)
            if (!accountName.isNullOrBlank()) append(" • ").append(accountName)
        }

        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) }

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
                            (System.currentTimeMillis() and 0xFFFF).toInt(),
                            openApp,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    )
                }
            }
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() and 0x7FFFFFFF).toInt(), notification)
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
        const val CHANNEL_ID = "transaction_alerts"
    }
}
