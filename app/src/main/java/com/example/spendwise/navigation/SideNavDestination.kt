package com.example.spendwise.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    Transactions(
        "transactions",
        "Transactions",
        Icons.AutoMirrored.Filled.List,
        "All Transactions"
    ),
    Transaction(
        "transaction",
        "Transaction Detail",
        Icons.Rounded.ReceiptLong,
        "Transaction Detail"
    ),
    Accounts("accounts", "Accounts", Icons.Rounded.AccountBalance, "Accounts & Cards"),
    Categories("categories", "Categories", Icons.Rounded.Category, "Categories"),
    Counterparties(
        "counterparties",
        "Counterparties",
        Icons.Rounded.Storefront,
        "All Counterparties"
    ),
    Friends("friends", "Friends & Split", Icons.Rounded.Group, "Friends"),
    Inbox("inbox", "Buffer Inbox", Icons.Rounded.Inbox, "Inbox"),
    Settings("settings", "Settings", Icons.Rounded.Settings, "Settings"),
    Update("update", "App update", Icons.Rounded.SystemUpdate, "App update"),
}

