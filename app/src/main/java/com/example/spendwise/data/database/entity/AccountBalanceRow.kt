package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

/**
 * One row per account — the ONLY balance read path (Rust view v_account_balances,
 * migration 007): the signed sum of the account's confirmed, non-voided lines,
 * inverted for liabilities so "amount owed" reads as a positive magnitude.
 * Buckets and per-person receivable children are plain accounts, so this view
 * is their value too. No other query may invert signs.
 */
@DatabaseView(
    viewName = "v_account_balances",
    value = """
        SELECT a.id AS account_id,
               CASE WHEN a.account_class = 'liability'
                    THEN -COALESCE(SUM(CASE WHEN e.status = 'confirmed'
                                             AND e.voided_at IS NULL
                                            THEN l.amount_paise ELSE 0 END), 0)
                    ELSE COALESCE(SUM(CASE WHEN e.status = 'confirmed'
                                            AND e.voided_at IS NULL
                                           THEN l.amount_paise ELSE 0 END), 0)
               END AS balance_paise
        FROM accounts a
        LEFT JOIN transaction_lines l ON l.account_id = a.id
        LEFT JOIN transactions e ON e.id = l.transaction_id
        GROUP BY a.id
    """
)
data class AccountBalanceRow(

    @ColumnInfo(name = "account_id")
    val accountId: Long,

    @ColumnInfo(name = "balance_paise")
    val balancePaise: Long,
)
