package com.example.spendwise.ui.screens.onboarding

data class OnboardingState(
    val welcomeSeen: Boolean = false,
    val profileCompleted: Boolean = false,
    val permissionGranted: Boolean = false,   // real SMS permission, not onboarding-complete
    val smsScanCompleted: Boolean = false,
    val accountsSelected: Boolean = false,
)