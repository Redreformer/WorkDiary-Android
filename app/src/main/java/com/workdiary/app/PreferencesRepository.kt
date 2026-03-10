package com.workdiary.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.workdiary.app.data.storage.PrefsKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// DataStore singleton extension on Context — one instance per process.

/**
 * Repository that wraps Jetpack DataStore for all user preferences.
 *
 * This is the Android equivalent of the iOS app's global `AppStorage` / `UserDefaults` keys.
 * All key strings are centralised here — mirroring the purpose of `KeyGen.swift` on iOS.
 *
 * Preferences are exposed as [Flow]s so Compose can collect them reactively.
 *
 * ## iOS key mapping
 * | iOS `AppStorage` key              | DataStore key / accessor                        |
 * |-----------------------------------|-------------------------------------------------|
 * | `isDarkMode`                      | [Keys.IS_DARK_THEME]                            |
 * | `hasCompletedSetup`               | [Keys.HAS_COMPLETED_SETUP]                      |
 * | `activeProfile`                   | [Keys.ACTIVE_PROFILE]                           |
 * | `startOnMonday`                   | [Keys.START_ON_MONDAY]                          |
 * | `isHoursMode_{profile}`           | [isHoursModeForProfile] / [setIsHoursMode]      |
 * | `annualAllowance_{profile}`       | [annualAllowanceForProfile] / [setAnnualAllowance] |
 * | `hourlyAllowance_{profile}`       | [hourlyAllowanceForProfile] / [setHourlyAllowance] |
 */
@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {

    // ──────────────────────────────────────────────────────────────────────
    //  Key definitions — global (non-profile-scoped)
    // ──────────────────────────────────────────────────────────────────────

    object Keys {
        val IS_DARK_THEME        = booleanPreferencesKey("isDarkMode")
        val HAS_COMPLETED_SETUP  = booleanPreferencesKey("hasCompletedSetup")
        val ACTIVE_PROFILE       = stringPreferencesKey("activeProfile")
        val START_ON_MONDAY      = booleanPreferencesKey("startOnMonday")
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Global flows
    // ──────────────────────────────────────────────────────────────────────

    /** Dark-theme preference. Defaults to `true` (matching iOS default). */
    val isDarkTheme: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[Keys.IS_DARK_THEME] ?: true }

    /** Whether the user has completed first-run onboarding. */
    val hasCompletedSetup: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[Keys.HAS_COMPLETED_SETUP] ?: false }

    /** The active profile identifier ("User1" or "User2"). */
    val activeProfile: Flow<String> = dataStore.data
        .map { prefs -> prefs[Keys.ACTIVE_PROFILE] ?: "User1" }

    /** Whether the calendar week starts on Monday (true) or Sunday (false). */
    val startOnMonday: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[Keys.START_ON_MONDAY] ?: true }

    // ──────────────────────────────────────────────────────────────────────
    //  Profile-scoped flows
    //
    //  Each function returns a Flow backed by a dynamically-named DataStore key,
    //  mirroring the pattern used by KeyGen.swift on iOS.
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Emits whether [profile] tracks leave in hours (`true`) or days (`false`).
     *
     * iOS equivalent: `AppStorage(KeyGen.isHours(profile))` — default `false`.
     *
     * @param profile Profile identifier, e.g. `"User1"`.
     */
    fun isHoursModeForProfile(profile: String): Flow<Boolean> = dataStore.data
        .map { prefs ->
            prefs[booleanPreferencesKey(PrefsKeys.isHours(profile))] ?: false
        }

    /**
     * Emits the annual leave *days* allowance for [profile].
     *
     * iOS equivalent: `AppStorage(KeyGen.annual(profile))` — default `25.0`.
     *
     * @param profile Profile identifier, e.g. `"User1"`.
     */
    fun annualAllowanceForProfile(profile: String): Flow<Double> = dataStore.data
        .map { prefs ->
            prefs[doublePreferencesKey(PrefsKeys.annual(profile))] ?: 25.0
        }

    /**
     * Emits the annual leave *hours* allowance for [profile].
     *
     * iOS equivalent: `AppStorage(KeyGen.hourly(profile))` — default `187.5`.
     *
     * @param profile Profile identifier, e.g. `"User1"`.
     */
    fun hourlyAllowanceForProfile(profile: String): Flow<Double> = dataStore.data
        .map { prefs ->
            prefs[doublePreferencesKey(PrefsKeys.hourly(profile))] ?: 187.5
        }

    // ──────────────────────────────────────────────────────────────────────
    //  Global mutators
    // ──────────────────────────────────────────────────────────────────────

    /** Persists the dark-theme preference. */
    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.IS_DARK_THEME] = enabled }
    }

    /** Marks onboarding as complete so it is not shown again on next launch. */
    suspend fun setSetupComplete() {
        dataStore.edit { prefs -> prefs[Keys.HAS_COMPLETED_SETUP] = true }
    }

    /** Switches the active profile. [profileId] must be `"User1"` or `"User2"`. */
    suspend fun setActiveProfile(profileId: String) {
        dataStore.edit { prefs -> prefs[Keys.ACTIVE_PROFILE] = profileId }
    }

    /** Persists the calendar week-start preference. */
    suspend fun setStartOnMonday(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.START_ON_MONDAY] = value }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Profile-scoped mutators
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Persists the hours-mode flag for [profile].
     *
     * @param profile  Profile identifier, e.g. `"User1"`.
     * @param isHours  `true` = track in hours; `false` = track in days.
     */
    suspend fun setIsHoursMode(profile: String, isHours: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PrefsKeys.isHours(profile))] = isHours
        }
    }

    /**
     * Persists the annual leave *days* allowance for [profile].
     *
     * @param profile Profile identifier, e.g. `"User1"`.
     * @param days    Annual allowance in days.
     */
    suspend fun setAnnualAllowance(profile: String, days: Double) {
        dataStore.edit { prefs ->
            prefs[doublePreferencesKey(PrefsKeys.annual(profile))] = days
        }
    }

    /**
     * Persists the annual leave *hours* allowance for [profile].
     *
     * @param profile Profile identifier, e.g. `"User1"`.
     * @param hours   Annual allowance in hours.
     */
    suspend fun setHourlyAllowance(profile: String, hours: Double) {
        dataStore.edit { prefs ->
            prefs[doublePreferencesKey(PrefsKeys.hourly(profile))] = hours
        }
    }
}
