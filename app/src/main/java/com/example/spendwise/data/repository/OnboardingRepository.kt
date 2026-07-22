package com.example.spendwise.data.repository


import android.content.Context
import com.example.spendwise.ui.screens.onboarding.OnboardingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// Temporary in-memory store. Swap internals for DataStore later —
// the public shape (state flow + update) stays the same.
class OnboardingRepository @Inject constructor(
    @param:ApplicationContext
    private val context: Context
) {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun update(transform: (OnboardingState) -> OnboardingState) {
        _state.value = transform(_state.value)
    }
}