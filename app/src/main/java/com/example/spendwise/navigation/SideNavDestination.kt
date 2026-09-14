package com.example.spendwise.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdate
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
        Icons.Filled.ReceiptLong,
        "Transaction Detail"
    ),
    Accounts("accounts", "Accounts", Icons.Filled.AccountBalance, "Accounts & Cards"),
    Categories("categories", "Categories", Icons.Filled.Category, "Categories"),
    Counterparties(
        "counterparties",
        "Counterparties",
        Icons.Filled.Storefront,
        "All Counterparties"
    ),
    Friends("friends", "Friends & Split", Icons.Filled.Group, "Friends"),
    Inbox("inbox", "Buffer Inbox", Icons.Filled.Inbox, "Inbox"),
    Settings("settings", "Settings", Icons.Filled.Settings, "Settings"),
    Update("update", "App update", Icons.Filled.SystemUpdate, "App update"),
}

