package com.example.spendwise.data.repository


import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.spendwise.ui.screens.onboarding.OnboardingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "spendwise_onboarding"
)

/**
 * Onboarding progress, persisted in DataStore so a completed onboarding
 * survives process death. The saved state hydrates asynchronously at
 * construction; [ready] flips true once it is loaded, and the view models gate
 * their first frame on it so a returning user never sees the onboarding flow
 * flash before the main app appears.
 */
@Singleton
class OnboardingRepository @Inject constructor(
    @param:ApplicationContext
    private val context: Context
) {
    private object Keys {
        val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
        val PROFILE_COMPLETED = booleanPreferencesKey("profile_completed")
        val PERMISSION_GRANTED = booleanPreferencesKey("permission_granted")
        val SMS_SCAN_COMPLETED = booleanPreferencesKey("sms_scan_completed")
        val ACCOUNTS_SELECTED = booleanPreferencesKey("accounts_selected")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    /** True once the persisted state has been loaded into [state]. */
    val ready = MutableStateFlow(false)

    init {
        scope.launch {
            val prefs = context.onboardingDataStore.data.first()
            _state.value = OnboardingState(
                welcomeSeen = prefs[Keys.WELCOME_SEEN] ?: false,
                profileCompleted = prefs[Keys.PROFILE_COMPLETED] ?: false,
                permissionGranted = prefs[Keys.PERMISSION_GRANTED] ?: false,
                smsScanCompleted = prefs[Keys.SMS_SCAN_COMPLETED] ?: false,
                accountsSelected = prefs[Keys.ACCOUNTS_SELECTED] ?: false,
            )
            ready.value = true
        }
    }

    fun update(transform: (OnboardingState) -> OnboardingState) {
        val next = transform(_state.value)
        _state.value = next
        scope.launch {
            context.onboardingDataStore.edit { prefs ->
                prefs[Keys.WELCOME_SEEN] = next.welcomeSeen
                prefs[Keys.PROFILE_COMPLETED] = next.profileCompleted
                prefs[Keys.PERMISSION_GRANTED] = next.permissionGranted
                prefs[Keys.SMS_SCAN_COMPLETED] = next.smsScanCompleted
                prefs[Keys.ACCOUNTS_SELECTED] = next.accountsSelected
            }
        }
    }
}