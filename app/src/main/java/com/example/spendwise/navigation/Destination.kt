package com.example.spendwise.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    BUDGET("budget", "Budget", Icons.Rounded.AccountBalanceWallet, "Budget"),
    INBOX("inbox", "Inbox", Icons.Rounded.Inbox, "Inbox"),
    UPI("scanner", "Scan & Pay", Icons.Rounded.QrCodeScanner, "Scan & Pay"),
    FRIENDS("friends", "Friends", Icons.Rounded.Group, "Friends"),
    MORE("more", "More", Icons.Rounded.MoreHoriz, "More");

    companion object {
        fun fromRoute(route: String?): Destination? {
            return entries.find { it.route == route }
        }
    }

}
