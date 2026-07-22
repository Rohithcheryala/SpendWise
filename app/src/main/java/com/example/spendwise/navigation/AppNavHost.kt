package com.example.spendwise.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.spendwise.ui.screens.transaction.TransactionScreen
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

        composable(Screen.Transaction.route) {
            val vm: TransactionViewModel = hiltViewModel()

            TransactionScreen(
                uiState = vm.uiState.collectAsStateWithLifecycle().value,
                onUpdateState = vm::updateState,
                onEvent = {}
            )
        }
    }
}


