package com.example.spendwise.ui.screens.onboarding

sealed interface OnboardingStep {
    data object Welcome : OnboardingStep
    data object Profile : OnboardingStep
    data object Permissions : OnboardingStep
    data object ScanMessages : OnboardingStep
    data object SelectAccounts : OnboardingStep
    data object Finished : OnboardingStep
}