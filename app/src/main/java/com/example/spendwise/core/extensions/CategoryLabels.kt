package com.example.spendwise.core.extensions

import com.example.spendwise.data.database.entity.AccountEntity

/**
 * Display label for a category account in pickers that flatten the tree
 * (transaction editor, payment overlay, filter sheet — all read
 * `getCategoryAccounts()`, roots and sub-categories in one list).
 *
 * Sub-categories are prefixed with their parent's name ("Food › Eating Out")
 * so the hierarchy survives flattening; roots render as-is. The [byId] map is
 * the id → account index of the *same* category list, so a parent that was
 * archived out of the list degrades gracefully to the plain name.
 */
fun AccountEntity.categoryLabel(byId: Map<Long, AccountEntity>): String =
    parentId?.let { parent -> byId[parent]?.name?.let { "$it › $name" } } ?: name

/** Single-category variant: resolve the parent with a direct lookup. */
suspend fun AccountEntity.categoryLabel(
    lookupParent: suspend (Long) -> AccountEntity?,
): String {
    val parent = parentId?.let { lookupParent(it) } ?: return name
    return "${parent.name} › $name"
}

