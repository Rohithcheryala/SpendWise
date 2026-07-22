package com.example.spendwise.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    Transaction("transaction", "Transaction", Icons.Filled.AccountBalanceWallet, "Transaction"),
    Transactions("transactions", "Transactions", Icons.Filled.AccountBalanceWallet, "Transactions"),
}
