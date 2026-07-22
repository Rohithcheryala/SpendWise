package com.example.spendwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.spendwise.navigation.AppNavHost
import com.example.spendwise.ui.screens.onboarding.OnboardingNavGraph
import com.example.spendwise.ui.theme.SpendwiseTheme
import com.example.spendwise.viewmodel.OnboardingViewModel


import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpendwiseTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val isOnboardingComplete by onboardingViewModel.isOnboardingComplete
        .collectAsStateWithLifecycle()

    if (isOnboardingComplete || true) {
        SpendwiseAppComposable()
    } else {
        OnboardingNavGraph(viewModel = onboardingViewModel)
    }
}


@Composable
fun SpendwiseAppComposable() {
    val navController = rememberNavController()

    AppNavHost(
        navController = navController
    )
}