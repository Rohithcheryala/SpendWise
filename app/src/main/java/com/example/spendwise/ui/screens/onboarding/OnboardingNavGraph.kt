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
                    viewModel.completeOnboarding()
                },
                onSkip = {
                    viewModel.completeAccounts()
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