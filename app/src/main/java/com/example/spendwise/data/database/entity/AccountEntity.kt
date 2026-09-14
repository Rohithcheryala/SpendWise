package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [
        Index("parent_id"),
        Index("account_class"),
        Index(value = ["is_system", "subtype"]),
        Index("bank")
    ]
)
/**
 * One ledger node — bank accounts, wallets, credit cards, categories,
 * buckets and system pots are ALL rows here (Rust-shaped schema):
 *  - [accountClass]: asset | liability | equity | income | expense
 *  - [subtype]: savings/current/cash/wallet/credit_card/investment for user
 *    accounts; receivable/unmatched/opening_equity/reconciliation_equity for
 *    system pots; "bucket" for goal sub-pots.
 *  - [parentId]: buckets hang off their funding account; category trees.
 *  - [isSystem]: bookkeeping pots/categories — never user-pickable.
 *  - [targetPaise]: bucket goal (deliberate extension beyond the Rust schema).
 * Money on an account is never stored — it is the signed sum of its
 * confirmed transaction lines (see LedgerService.accountBalance).
 */
data class AccountEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    /** asset | liability | equity | income | expense */
    @ColumnInfo(name = "account_class")
    val accountClass: String,

    val subtype: String? = null,

    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,

    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    val icon: String? = null,

    /** Android extension: bank name used for SMS account matching. */
    val bank: String? = null,

    @ColumnInfo(name = "target_paise")
    val targetPaise: Long? = null,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)