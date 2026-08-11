package com.example.spendwise.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.spendwise.ui.screens.accounts.AccountsScreen
import com.example.spendwise.ui.screens.categories.CategoriesScreen
import com.example.spendwise.ui.screens.settings.SettingsScreen
import com.example.spendwise.ui.screens.transaction.TransactionScreen
import com.example.spendwise.ui.screens.transaction.TransactionUiEvent
import com.example.spendwise.ui.screens.transactions.TransactionsScreen
import com.example.spendwise.viewmodel.TransactionViewModel

@Composable
fun AppNavHost(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {

        composable("main") {
            MainScaffold(
                rootNavController = navController
            )
        }

        composable(Screen.Transactions.route) {
            TransactionsScreen(
                onNavigateBack = { navController.popBackStack() },
                onTransactionClick = { _ ->
                    navController.navigate(Screen.Transaction.route)
                },
                onAddTransactionClick = {
                    navController.navigate(Screen.Transaction.route)
                }
            )
        }

        composable(Screen.Transaction.route) {
            val vm: TransactionViewModel = hiltViewModel()

            TransactionScreen(
                uiState = vm.uiState.collectAsStateWithLifecycle().value,
                onUpdateState = vm::updateState,
                onEvent = { event ->
                    when (event) {
                        is TransactionUiEvent.NavigateBack,
                        is TransactionUiEvent.SaveClicked,
                        is TransactionUiEvent.DeleteClicked,
                        is TransactionUiEvent.VoidClicked -> {
                            navController.popBackStack()
                        }
                        else -> {}
                    }
                }
            )
        }

        composable(Screen.Accounts.route) {
            AccountsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Categories.route) {
            CategoriesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Friends.route) {
            com.example.spendwise.ui.screens.friends.FriendsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Inbox.route) {
            com.example.spendwise.ui.screens.inbox.InboxScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
