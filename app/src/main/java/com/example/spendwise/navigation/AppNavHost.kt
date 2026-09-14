package com.example.spendwise.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.spendwise.ui.screens.accounts.AccountsScreen
import com.example.spendwise.ui.screens.categories.CategoriesScreen
import com.example.spendwise.ui.screens.settings.SettingsScreen
import com.example.spendwise.ui.screens.transaction.TransactionScreen
import com.example.spendwise.ui.screens.transaction.TransactionUiEvent
import com.example.spendwise.ui.screens.transactions.TransactionsScreen
import com.example.spendwise.ui.screens.update.UpdateScreen
import com.example.spendwise.viewmodel.TransactionViewModel
import com.example.spendwise.viewmodel.TransactionsViewModel

private const val TRANSACTION_ROUTE = "transaction?entryId={entryId}"

@Composable
fun AppNavHost(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = "main",
        // Paint the host with the themed background: during slide transitions
        // any not-yet-covered area must match the theme, otherwise the white
        // window background shows through as a flashing bar.
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        // Directional slide — forward: new screen slides in from the right,
        // old screen eases out to the left; back is mirrored. This gives the
        // transition a sense of motion instead of an abrupt crossfade.
        enterTransition = {
            slideInHorizontally(tween(300)) { it } + fadeIn(tween(220))
        },
        exitTransition = {
            slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(220))
        },
        popEnterTransition = {
            slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(220))
        },
        popExitTransition = {
            slideOutHorizontally(tween(300)) { it } + fadeOut(tween(220))
        },
    ) {

        composable("main") {
            MainScaffold(
                rootNavController = navController
            )
        }

        composable(Screen.Transactions.route) {
            val vm: TransactionsViewModel = hiltViewModel()

            // Reload from the ledger whenever this screen resumes, so entries
            // created/edited elsewhere show up when you navigate back.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            TransactionsScreen(
                state = vm.filterState,
                transactions = vm.uiState.items,
                onNavigateBack = { navController.popBackStack() },
                onTransactionClick = { id ->
                    navController.navigate("transaction?entryId=$id")
                },
                onAddTransactionClick = {
                    navController.navigate("transaction")
                },
                onEvent = vm::onEvent,
                onStatusChange = vm::setStatusFilter
            )
        }

        composable(
            route = TRANSACTION_ROUTE,
            arguments = listOf(
                navArgument("entryId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            val vm: TransactionViewModel = hiltViewModel()

            LaunchedEffect(Unit) {
                vm.finished.collect { navController.popBackStack() }
            }

            TransactionScreen(
                uiState = vm.uiState.collectAsStateWithLifecycle().value,
                onUpdateState = vm::updateState,
                onAddCounterparty = vm::addCounterparty,
                onEvent = { event ->
                    when (event) {
                        is TransactionUiEvent.NavigateBack -> navController.popBackStack()
                        is TransactionUiEvent.SaveClicked -> vm.save()
                        is TransactionUiEvent.DeleteClicked -> vm.delete()
                        is TransactionUiEvent.VoidClicked -> vm.void()
                        else -> {}
                    }
                }
            )
        }

        composable(Screen.Accounts.route) {
            AccountsScreen(
                onNavigateBack = { navController.popBackStack() },
                onAccountClick = { accountId ->
                    navController.navigate("account/$accountId")
                }
            )
        }

        composable(
            route = "account/{accountId}",
            arguments = listOf(
                navArgument("accountId") { type = NavType.LongType }
            )
        ) {
            val vm: com.example.spendwise.viewmodel.AccountEditViewModel = hiltViewModel()

            LaunchedEffect(Unit) {
                vm.finished.collect { navController.popBackStack() }
            }

            com.example.spendwise.ui.screens.accounts.AccountEditScreen(
                uiState = vm.uiState.collectAsStateWithLifecycle().value,
                onUpdateState = vm::updateState,
                onSave = vm::save,
                onCloseAccount = vm::closeAccount,
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

        composable(Screen.Counterparties.route) {
            com.example.spendwise.ui.screens.counterparties.CounterpartiesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Inbox.route) {
            com.example.spendwise.ui.screens.inbox.InboxScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditItem = { entryId ->
                    navController.navigate("transaction?entryId=$entryId")
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUpdate = { navController.navigate(Screen.Update.route) }
            )
        }

        composable(Screen.Update.route) {
            UpdateScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
