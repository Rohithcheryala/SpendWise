package com.example.spendwise.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    BUDGET("budget", "Budget", Icons.Filled.AccountBalanceWallet, "Budget"),
    INBOX("inbox", "Inbox", Icons.Default.Inbox, "Inbox"),
    UPI("scanner", "Scan", Icons.Default.QrCodeScanner, "Scan"),
    FRIENDS("friends", "Friends", Icons.Default.Group, "Friends"),
    MORE("more", "MORE", Icons.Default.MoreHoriz, "MORE");

    companion object {
        fun fromRoute(route: String?): Destination? {
            return entries.find { it.route == route }
        }
    }

}
