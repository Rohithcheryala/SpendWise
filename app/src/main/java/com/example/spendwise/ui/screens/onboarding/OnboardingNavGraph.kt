package com.example.spendwise.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.example.spendwise.viewmodel.OnboardingViewModel


@Composable
fun OnboardingNavGraph(
    viewModel: OnboardingViewModel
) {

    when (viewModel.currentStep) {

        OnboardingStep.Welcome -> {
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
            ) { results ->
                // results: Map<String, Boolean> — each permission -> granted?
                results.values.all { it }
                viewModel.completeScan()
            }

            PermissionScreen(
                onGrantPermissions = {
                    permissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.READ_SMS,
                        )
                    )
                },
                onSkip = {
                    viewModel.completeWelcome()
                }
            )
        }

        OnboardingStep.ScanMessages -> {
            // ScanScreen()
        }

        OnboardingStep.SelectAccounts -> {
//            AccountSelectionScreen()
        }

        OnboardingStep.Finished -> {
            // Navigate Home
        }
    }

}