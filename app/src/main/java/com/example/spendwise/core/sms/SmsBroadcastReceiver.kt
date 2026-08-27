package com.example.spendwise.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.spendwise.data.repository.InboxRepository
import com.example.spendwise.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Auto-detection entry point: the system delivers every incoming SMS here and
 * known-bank messages are parsed + ingested into the ledger's buffer (dedupe
 * happens inside [InboxRepository.ingestRawSms], so the inbox pull and this
 * live path never double-book a message).
 *
 * Gated by the Settings "SMS Auto-Detection" toggle.
 */
@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var inboxRepository: InboxRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val autoDetect = settingsRepository.settings.first().smsAutoDetect
                if (!autoDetect) return@launch

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                // Multi-part SMS arrive as several message segments — join them.
                val body = messages.joinToString("") { it.messageBody.orEmpty() }
                val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
                val timestamp = messages.firstOrNull()?.timestampMillis
                    ?: System.currentTimeMillis()

                if (sender.isNotBlank() && body.isNotBlank()) {
                    inboxRepository.ingestRawSms(sender, body, timestamp)
                }
            } catch (_: Exception) {
                // Never crash on a system broadcast; the next inbox sync
                // re-reads everything since the watermark anyway.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
