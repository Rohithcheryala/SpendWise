package com.example.spendwise

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

    // The app supports a manual theme override, so `SystemBarStyle.auto`
    // (which reads the *system* uiMode) is wrong when the user picks the
    // opposite of the system. Force icon appearance from the app's resolved
    // theme instead (UI_UX_AUDIT.md §5.11).
    val activity = LocalActivity.current
    SideEffect {
        val componentActivity = activity as? ComponentActivity
        componentActivity?.enableEdgeToEdge(
            statusBarStyle = if (darkTheme) {
                SystemBarStyle.dark(Color.Transparent.toArgb())
            } else {
                SystemBarStyle.light(
                    Color.Transparent.toArgb(),
                    Color.Transparent.toArgb(),
                )
            },
            navigationBarStyle = if (darkTheme) {
                SystemBarStyle.dark(Color.Transparent.toArgb())
            } else {
                SystemBarStyle.light(
                    Color.Transparent.toArgb(),
                    Color.Transparent.toArgb(),
                )
            },
        )
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
