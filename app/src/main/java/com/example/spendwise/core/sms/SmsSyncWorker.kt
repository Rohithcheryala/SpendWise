package com.example.spendwise.core.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.spendwise.data.repository.InboxRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background safety net for SMS capture. On stock Android the live
 * [SmsBroadcastReceiver] catches every bank SMS, but OEM power management
 * (Samsung's sleeping apps, aggressive doze, …) can silently skip the
 * broadcast while the device is idle — the ₹1.5-lakh NEFT at 3:35 am was
 * caught by competing apps and missed by us exactly this way.
 *
 * This worker polls the SMS inbox on a schedule and notifies for captures
 * newer than [POLL_FRESH_WINDOW_MS] (wide enough to cover a delayed run),
 * so a missed broadcast costs minutes, not silence.
 *
 * Dedupe is handled inside [InboxRepository.ingestRawSms]: an SMS the live
 * receiver already booked returns null here and never double-alerts.
 */
@HiltWorker
class SmsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val inboxRepository: InboxRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        inboxRepository.syncFromSms(freshWindowMs = POLL_FRESH_WINDOW_MS)
        Result.success()
    } catch (_: Exception) {
        // Transient failure (DB busy, etc.) — retry with backoff.
        Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "sms_periodic_sync"

        /** WorkManager's minimum periodic interval. */
        private const val PERIOD_MINUTES = 15L

        /**
         * Wide enough to bridge a delayed/deferred periodic run (doze can
         * push WorkManager well past its slot), narrow enough that a worker
         * resuming after weeks of app disuse doesn't fire stale alerts.
         */
        private const val POLL_FRESH_WINDOW_MS = 45L * 60 * 1000

        /** Idempotent — call from Application.onCreate on every process start. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SmsSyncWorker>(
                PERIOD_MINUTES, TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}