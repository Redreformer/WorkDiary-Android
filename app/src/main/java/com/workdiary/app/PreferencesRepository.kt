package com.workdiary.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// DataStore singleton extension on Context — one instance per process.
private val Context.dataStore by preferencesDataStore(name = "workdiary_prefs")

/**
 * Repository that wraps Jetpack DataStore for all user preferences.
 *
 * This is the Android equivalent of the iOS app's global `AppStorage` / `UserDefaults` keys.
 * All key strings are centralised here — mirroring the purpose of `KeyGen.swift` on iOS.
 *
 * Preferences are exposed as [Flow]s so Compose can collect them reactively.
 *
 * ## iOS key mapping
 * | iOS `AppStorage` key        | DataStore key constant            |
 * |-----------------------------|-----------------------------------|
 * | `isDarkMode`                | [Keys.IS_DARK_THEME]              |
 * | `hasCompletedSetup`         | [Keys.HAS_COMPLETED_SETUP]        |
 * | `activeProfile`             | [Keys.ACTIVE_PROFILE]             |
 * | `startOnMonday`             | [Keys.START_ON_MONDAY]            |
 */
@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ──────────────────────────────────────────────────────────────────────
    //  Key definitions
    // ──────────────────────────────────────────────────────────────────────

    object Keys {
        val IS_DARK_THEME        = booleanPreferencesKey("isDarkMode")
        val HAS_COMPLETED_SETUP  = booleanPreferencesKey("hasCompletedSetup")
        val ACTIVE_PROFILE       = stringPreferencesKey("activeProfile")
        val START_ON_MONDAY      = booleanPreferencesKey("startOnMonday")
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Flows
    // ──────────────────────────────────────────────────────────────────────

    /** Dark-theme preference. Defaults to `true` (matching iOS default). */
    val isDarkTheme: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.IS_DARK_THEME] ?: true }

    /** Whether the user has completed first-run onboarding. */
    val hasCompletedSetup: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.HAS_COMPLETED_SETUP] ?: false }

    /** The active profile identifier ("User1" or "User2"). */
    val activeProfile: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.ACTIVE_PROFILE] ?: "User1" }

    /** Whether the calendar week starts on Monday (true) or Sunday (false). */
    val startOnMonday: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.START_ON_MONDAY] ?: true }

    // ──────────────────────────────────────────────────────────────────────
    //  Mutators
    // ──────────────────────────────────────────────────────────────────────

    /** Persists the dark-theme preference. */
    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.IS_DARK_THEME] = enabled }
    }

    /** Marks onboarding as complete so it is not shown again on next launch. */
    suspend fun setSetupComplete() {
        context.dataStore.edit { prefs -> prefs[Keys.HAS_COMPLETED_SETUP] = true }
    }

    /** Switches the active profile. [profileId] must be `"User1"` or `"User2"`. */
    suspend fun setActiveProfile(profileId: String) {
        context.dataStore.edit { prefs -> prefs[Keys.ACTIVE_PROFILE] = profileId }
    }

    /** Persists the calendar week-start preference. */
    suspend fun setStartOnMonday(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.START_ON_MONDAY] = value }
    }
}
