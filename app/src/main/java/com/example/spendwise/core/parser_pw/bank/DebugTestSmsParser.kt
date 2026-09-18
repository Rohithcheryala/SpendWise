package com.example.spendwise.core.parser_pw.bank

import com.example.spendwise.BuildConfig
import com.example.spendwise.core.parser_pw.CompiledPatterns
import com.example.spendwise.core.parser_pw.TransactionType
import java.math.BigDecimal

/**
 * DEBUG-ONLY test hook so real bank transactions are never needed to exercise
 * the full SMS -> parse -> buffer -> notify pipeline.
 *
 * Claims ONLY the phone numbers in the `TEST_SENDERS` BuildConfig field
 * (from comma-separated `TEST_SENDERS=` in local.properties, gitignored).
 * `canHandle` is false in release builds and when the allowlist is empty, so
 * this parser is inert unless you explicitly configure it on a debug build.
 *
 * Unlike the old fake-notification probe, this returns a REAL parsed result:
 * amount via the shared amount patterns, direction via debit/credit keywords,
 * merchant via the shared merchant extractor — so the message flows through
 * [BankParser.parse] into the Buffer Inbox and the real notification path.
 *
 * Test SMS format from one of your numbers, e.g.:
 *   "Spent Rs 250 at D-Mart"
 *   "Received Rs 1000 from Rahul"
 */
class DebugTestSmsParser : BankParser() {

    override fun getBankName() = BANK_NAME

    override fun canHandle(sender: String): Boolean {
        if (!BuildConfig.DEBUG) return false
        return normalize(sender) in testSenders
    }

    // Skip the base-class transaction gate: test messages are free-form, so
    // the keyword gate in isTransactionMessage would reject anything that
    // doesn't read like a real bank SMS. Amount extraction below is the
    // real filter — no amount, no parse.
    public override fun isTransactionMessage(message: String): Boolean = true

    override fun extractAmount(message: String): BigDecimal? {
        for (pattern in CompiledPatterns.Amount.ALL_PATTERNS) {
            pattern.find(message)?.let {
                return it.groupValues[1].replace(",", "").toBigDecimalOrNull()
            }
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        if (INCOME_HINTS.any { lower.contains(it) }) return TransactionType.INCOME
        if (TRANSFER_HINTS.any { lower.contains(it) }) return TransactionType.TRANSFER
        // Default to spend — the common case under test.
        return TransactionType.EXPENSE
    }

    override fun extractMerchant(message: String, sender: String): String? =
        super.extractMerchant(message, sender)
            ?: message.trim().take(64).takeIf { it.isNotBlank() }

    companion object {
        /** Bank name stamped on test-parsed transactions. */
        const val BANK_NAME = "DEBUG"

        /** Last-10-digits allowlist from BuildConfig (tolerates +91/spaces). */
        private val testSenders: Set<String> =
            BuildConfig.TEST_SENDERS.split(",")
                .map { normalize(it) }
                .filter { it.length >= 10 }
                .map { it.takeLast(10) }
                .toSet()

        private fun normalize(raw: String): String =
            raw.filter { it.isDigit() }.takeIf { it.length >= 10 }
                ?.takeLast(10).orEmpty()

        private val INCOME_HINTS = listOf(
            "received", "credited", "deposited", "refunded", "refund",
            "cashback", "income", "salary",
        )
        private val TRANSFER_HINTS = listOf(
            "transferred to", "sent to", "paid to",
        )
    }
}
