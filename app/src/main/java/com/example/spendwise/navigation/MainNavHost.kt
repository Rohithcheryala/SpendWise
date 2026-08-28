package com.example.spendwise.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.spendwise.ui.screens.budget.BudgetScreen
import com.example.spendwise.ui.screens.friends.FriendsScreen
import com.example.spendwise.ui.screens.inbox.InboxScreen
import com.example.spendwise.ui.screens.scanner.ScannerScreen


@Composable
fun MainNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onOpenTransaction: (Long) -> Unit
) {

    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Destination.BUDGET.route,
        // Bottom-nav tab switches: quick, calm crossfade (no slide).
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {

        composable(Destination.BUDGET.route) {
            BudgetScreen()
        }

        composable(Destination.INBOX.route) {
            InboxScreen(
                onEditItem = onOpenTransaction
            )
        }

        composable(Destination.FRIENDS.route) {
            FriendsScreen()
        }

        composable(Destination.UPI.route) {
            ScannerScreen()
        }
    }
}