package com.example.spendwise

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.spendwise.data.repository.ThemeMode
import com.example.spendwise.navigation.AppNavHost
import com.example.spendwise.ui.screens.onboarding.OnboardingNavGraph
import com.example.spendwise.ui.theme.SpendwiseTheme
import com.example.spendwise.viewmodel.OnboardingViewModel
import com.example.spendwise.viewmodel.SettingsViewModel


import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

@Composable
fun AppRoot(
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val isOnboardingComplete by onboardingViewModel.isOnboardingComplete
        .collectAsStateWithLifecycle()

    val settings = settingsViewModel.settings

    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    SpendwiseTheme(darkTheme = darkTheme) {
        Log.d("isOnboardingComplete", "AppRoot: $isOnboardingComplete")
        if (isOnboardingComplete) {
            SpendwiseAppComposable()
        } else {
            OnboardingNavGraph(viewModel = onboardingViewModel)
        }
    }
}


@Composable
fun SpendwiseAppComposable() {
    val navController = rememberNavController()

    AppNavHost(
        navController = navController
    )
}
