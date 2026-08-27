package com.example.spendwise.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "spendwise_settings"
)

/** How the app picks its dark/light palette. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Everything user-configurable that is not ledger data. */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currency: String = "INR (₹)",
    val currencySymbol: String = "₹",
    val smsAutoDetect: Boolean = true,
    val notificationsEnabled: Boolean = true,
    /** When true, buffer entries cannot be confirmed without a real category AND a tag. */
    val strictMode: Boolean = false,
    val profileName: String = ""
)

/**
 * Single source of truth for app settings, persisted in DataStore. The app had
 * zero persisted settings before this — every Settings toggle was a local
 * `remember {}` that died with the screen.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CURRENCY = stringPreferencesKey("currency")
        val SMS_AUTO_DETECT = booleanPreferencesKey("sms_auto_detect")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { mode ->
                runCatching { ThemeMode.valueOf(mode) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            currency = prefs[Keys.CURRENCY] ?: "INR (₹)",
            currencySymbol = currencySymbolFor(prefs[Keys.CURRENCY] ?: "INR (₹)"),
            smsAutoDetect = prefs[Keys.SMS_AUTO_DETECT] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            strictMode = prefs[Keys.STRICT_MODE] ?: false,
            profileName = prefs[Keys.PROFILE_NAME].orEmpty(),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setCurrency(currency: String) {
        context.settingsDataStore.edit {
            it[Keys.CURRENCY] = currency
        }
    }

    suspend fun setSmsAutoDetect(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SMS_AUTO_DETECT] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setStrictMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.STRICT_MODE] = enabled }
    }

    suspend fun setProfileName(name: String) {
        context.settingsDataStore.edit { it[Keys.PROFILE_NAME] = name }
    }

    companion object {
        val CURRENCIES = listOf("INR (₹)", "USD ($)", "EUR (€)", "GBP (£)")

        fun currencySymbolFor(currency: String): String = when (currency) {
            "USD ($)" -> "$"
            "EUR (€)" -> "€"
            "GBP (£)" -> "£"
            else -> "₹"
        }
    }
}
