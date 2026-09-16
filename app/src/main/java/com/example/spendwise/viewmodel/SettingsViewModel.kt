package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.data.database.AppDatabase
import com.example.spendwise.data.database.DatabaseSeeder
import com.example.spendwise.data.repository.AppMetadataRepository
import com.example.spendwise.data.repository.SettingsRepository
import com.example.spendwise.data.repository.ThemeMode
import com.example.spendwise.data.repository.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appMetadataRepository: AppMetadataRepository,
    private val database: AppDatabase,
    private val databaseSeeder: DatabaseSeeder,
) : ViewModel() {

    var settings by mutableStateOf(UserSettings())
        private set

    var isResetting by mutableStateOf(false)
        private set

    /** "Day one" of the ledger — the date onboarding's SMS scan started from. */
    var trackingStartDate by mutableStateOf<Long?>(null)
        private set

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings = it }
        }
        viewModelScope.launch {
            trackingStartDate = runCatching {
                appMetadataRepository.get()?.trackingStartDate
            }.getOrNull()
        }
    }

    fun setThemeMode(mode: ThemeMode) =
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }

    fun setCurrency(currency: String) =
        viewModelScope.launch { settingsRepository.setCurrency(currency) }

    fun setSmsAutoDetect(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setSmsAutoDetect(enabled) }

    fun setNotificationsEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }

    /** Strict mode: buffer transactions can't be confirmed without category + tag. */
    fun setStrictMode(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setStrictMode(enabled) }

    fun setProfileName(name: String) =
        viewModelScope.launch { settingsRepository.setProfileName(name) }

    /** Wipes the Room database and re-seeds the base categories/metadata. */
    fun resetDatabase() {
        if (isResetting) return
        viewModelScope.launch {
            isResetting = true
            try {
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                    databaseSeeder.seed()
                }
            } finally {
                isResetting = false
            }
        }
    }
}
