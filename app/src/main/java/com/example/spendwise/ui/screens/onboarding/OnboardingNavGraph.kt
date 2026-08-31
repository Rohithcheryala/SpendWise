package com.example.spendwise.ui.screens.onboarding

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.spendwise.viewmodel.OnboardingViewModel


@Composable
fun OnboardingNavGraph(
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val step by viewModel.currentStep.collectAsState()

    when (step) {

        // Persisted state still hydrating — render nothing for this instant.
        null -> {}

        OnboardingStep.Welcome -> {
            Log.d("TAG", "OnboardingNavGraph: inside welcome branch case")
            WelcomeScreen(
                onGetStarted = {
                    viewModel.completeWelcome()
                }
            )
        }

        OnboardingStep.Profile -> {
            ProfileScreen(
                onContinue = { name, color ->
                    viewModel.completeProfile()
                }
            )
        }

        OnboardingStep.Permissions -> {
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { _ ->
                // Move on to the scan step either way — the scan screen can
                // still be skipped, and denied permission just yields no SMS.
                viewModel.completePermissions()
            }

            PermissionScreen(
                onGrantPermissions = {
                    // All three are load-bearing: READ_SMS backs the inbox
                    // sync, RECEIVE_SMS is what makes the LIVE capture
                    // receiver fire at all, and without POST_NOTIFICATIONS
                    // (13+) the "notify you instantly" promise is dead code.
                    val permissions = mutableListOf(
                        android.Manifest.permission.READ_SMS,
                        android.Manifest.permission.RECEIVE_SMS,
                    )
                    if (android.os.Build.VERSION.SDK_INT >=
                        android.os.Build.VERSION_CODES.TIRAMISU
                    ) {
                        permissions += android.Manifest.permission.POST_NOTIFICATIONS
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                },
                onSkip = {
                    viewModel.completePermissions()
                }
            )
        }

        OnboardingStep.ScanMessages -> {
            ScanMessagesScreen(viewModel = viewModel)
        }

        OnboardingStep.SelectAccounts -> {
            AccountSelectionScreen(viewModel = viewModel)
        }

        OnboardingStep.Finished -> {
            // MainActivity swaps to AppNavHost as soon as the step is Finished.
        }
    }

}