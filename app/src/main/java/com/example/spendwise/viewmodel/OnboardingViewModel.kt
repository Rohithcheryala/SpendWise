package com.example.spendwise.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.data.repository.OnboardingRepository
import com.example.spendwise.ui.screens.onboarding.OnboardingState
import com.example.spendwise.ui.screens.onboarding.OnboardingStep


import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


// For Budget, Friends, Groups, Accounts — each one is just:
//
// XEntity.kt + XDao.kt in features/x/data/
// One line added to AppDatabase.kt's entities = [...] list
// One @Provides fun provideXDao(...) line in DatabaseModule.kt
// XRepository @Inject constructor(private val xDao: XDao)
// @HiltViewModel class XViewModel @Inject constructor(private val repository: XRepository)
// val viewModel: XViewModel = hiltViewModel() in the screen
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository
) : ViewModel() {

    val currentStep: StateFlow<OnboardingStep> = repository.state
        .map { state -> computeStep(state) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OnboardingStep.Welcome)

    val isOnboardingComplete: StateFlow<Boolean> = currentStep
        .map { it == OnboardingStep.Finished }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun completeWelcome() = repository.update { it.copy(welcomeSeen = true) }
    fun completeProfile() = repository.update { it.copy(profileCompleted = true) }
    fun completePermissions() = repository.update { it.copy(permissionGranted = true) }
    fun completeScan() = repository.update { it.copy(smsScanCompleted = true) }
    fun completeAccounts() = repository.update { it.copy(accountsSelected = true) }

    private fun computeStep(state: OnboardingState): OnboardingStep = when {
        !state.welcomeSeen -> OnboardingStep.Welcome
        !state.profileCompleted -> OnboardingStep.Profile
        !state.permissionGranted -> OnboardingStep.Permissions
        !state.smsScanCompleted -> OnboardingStep.ScanMessages
        !state.accountsSelected -> OnboardingStep.SelectAccounts
        else -> OnboardingStep.Finished
    }
}