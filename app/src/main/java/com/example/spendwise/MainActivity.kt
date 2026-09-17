package com.example.spendwise

import android.content.Intent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.spendwise.core.notifications.TransactionNotifier
import com.example.spendwise.data.repository.ThemeMode
import com.example.spendwise.navigation.AppNavHost
import com.example.spendwise.ui.screens.onboarding.OnboardingNavGraph
import com.example.spendwise.ui.theme.SpendwiseTheme
import com.example.spendwise.viewmodel.OnboardingViewModel
import com.example.spendwise.viewmodel.SettingsViewModel


import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * The route a notification tap asked for, consumed by the nav host once it
     * exists. Held as state rather than navigated immediately because the graph
     * is not composed yet in [onCreate] — and not at all until onboarding is
     * done, which is exactly why the request has to survive until then.
     */
    private val deepLinkRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkRoute.value = intent.toAlertRoute()
        setContent {
            AppRoot(
                deepLinkRoute = deepLinkRoute.value,
                onDeepLinkHandled = { deepLinkRoute.value = null },
            )
        }
    }

    /**
     * The alert intent sets `FLAG_ACTIVITY_SINGLE_TOP`, so a tap while the app
     * is alive is delivered here rather than to [onCreate]. Without this
     * override the intent was dropped on the floor and the app simply resumed
     * on whatever screen it was last showing — the bug this fixes.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkRoute.value = intent.toAlertRoute()
    }
}

/**
 * The in-app route a transaction alert should open, or null for any other
 * launch. Keyed off the notifier's extra alone, so a plain launcher tap (which
 * carries no extras) is never redirected anywhere.
 */
internal fun Intent?.toAlertRoute(): String? {
    val transactionId = this?.getLongExtra(TransactionNotifier.EXTRA_TRANSACTION_ID, -1L) ?: -1L
    return if (transactionId > 0L) "transaction?transactionId=$transactionId" else null
}

@Composable
fun AppRoot(
    deepLinkRoute: String? = null,
    onDeepLinkHandled: () -> Unit = {},
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
            SpendwiseAppComposable(
                deepLinkRoute = deepLinkRoute,
                onDeepLinkHandled = onDeepLinkHandled,
            )
        } else {
            OnboardingNavGraph(viewModel = onboardingViewModel)
        }
    }
}


@Composable
fun SpendwiseAppComposable(
    deepLinkRoute: String? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val navController = rememberNavController()

    AppNavHost(
        navController = navController,
        deepLinkRoute = deepLinkRoute,
        onDeepLinkHandled = onDeepLinkHandled,
    )
}
