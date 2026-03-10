package com.workdiary.app.ui.screens

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workdiary.app.PreferencesRepository
import com.workdiary.app.data.storage.PrefsKeys
import com.workdiary.app.utils.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
//  DataStore accessor — references the same "workdiary_prefs" singleton as
//  PreferencesRepository. The PreferenceDataStoreSingletonDelegate guarantees a
//  single DataStore instance per (context, name) pair regardless of how many
//  Kotlin extension properties point to it.
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
//  DataStore keys used exclusively by SettingsViewModel
//  (not yet present in PreferencesRepository)
// ─────────────────────────────────────────────────────────────────────────────
private object SettingsKeys {
    val PROFILE_NAME_USER1    = stringPreferencesKey(PrefsKeys.PROFILE_NAME_USER1)
    val PROFILE_NAME_USER2    = stringPreferencesKey(PrefsKeys.PROFILE_NAME_USER2)
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey(PrefsKeys.NOTIFICATIONS_ENABLED)
    /** Seconds since midnight — e.g. 32 400 = 09:00. */
    val NOTIFICATION_TIME     = longPreferencesKey(PrefsKeys.NOTIFICATION_TIME)
    val CARRIED_OVER_GLOBAL   = doublePreferencesKey(PrefsKeys.CARRIED_OVER_ALLOWANCE_GLOBAL)
    val SAVED_SPECIAL_DAYS    = stringPreferencesKey(PrefsKeys.SAVED_SPECIAL_DAYS_DATA)
}

// ─────────────────────────────────────────────────────────────────────────────
//  UI State models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Existence flags for the three Duty Board PDF files stored in [Context.filesDir].
 *
 * @property mfExists  `true` if `MF.pdf` exists.
 * @property satExists `true` if `SAT.pdf` exists.
 * @property sunExists `true` if `SUN.pdf` exists.
 */
data class PdfExistenceState(
    val mfExists: Boolean  = false,
    val satExists: Boolean = false,
    val sunExists: Boolean = false,
)

/**
 * Which modal dialog/sheet the Settings screen should currently display.
 * `null` means no dialog is shown.
 */
enum class SettingsDialog {
    ResetAllowances,
    ResetBooked,
    ResetCalendar,
    ResetAll,
    ExportSuccess,
    ImportSuccess,
    TimePicker,
}

/**
 * Immutable snapshot of the entire Settings screen state, collected from DataStore
 * and file-system queries and exposed as a single [StateFlow].
 *
 * @property activeProfile               Currently selected profile ID (`"User1"` / `"User2"`).
 * @property profileNameUser1            Display name for Profile 1.
 * @property profileNameUser2            Display name for Profile 2.
 * @property isDarkMode                  Dark-theme preference.
 * @property isHoursMode                 Whether the active profile tracks leave in hours.
 * @property notificationsEnabled        Whether daily reminder notifications are on.
 * @property notificationTimeSeconds     Reminder time as seconds-since-midnight (e.g. 32 400 = 09:00).
 * @property startOnMonday               Whether the week starts on Monday.
 * @property pdfState                    Existence flags for MF / SAT / SUN PDF files.
 * @property activeDialog                Currently visible dialog, or `null`.
 * @property requestNotificationPermission `true` when the screen should launch the
 *                                         `POST_NOTIFICATIONS` permission request.
 */
data class SettingsUiState(
    val activeProfile: String                    = PrefsKeys.PROFILE_USER1,
    val profileNameUser1: String                 = "Profile 1",
    val profileNameUser2: String                 = "Profile 2",
    val isDarkMode: Boolean                      = true,
    val isHoursMode: Boolean                     = false,
    val notificationsEnabled: Boolean            = false,
    val notificationTimeSeconds: Long            = 32_400L,
    val startOnMonday: Boolean                   = true,
    val pdfState: PdfExistenceState              = PdfExistenceState(),
    val activeDialog: SettingsDialog?            = null,
    val requestNotificationPermission: Boolean   = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for [SettingsScreen].
 *
 * Reads and writes all WorkDiary user preferences via Jetpack DataStore, and manages
 * file-system operations for Duty Board PDFs. All mutations are coroutine-based and
 * execute on the [viewModelScope].
 *
 * ## Key responsibilities
 * - Profile switching and renaming
 * - Dark mode / hours-mode / calendar-start toggles
 * - Import/Export via clipboard (`DUTY_DATA:{json}###HOLIDAY_DATA:{json}`)
 * - PDF upload (copy from content URI → `filesDir`), delete, and existence tracking
 * - Notification enable/disable with runtime permission handling (Android 13+)
 * - Reminder time persistence and AlarmManager scheduling via [NotificationScheduler]
 * - Destructive data-reset operations (allowances / booked / calendar / all)
 *
 * @param app  Injected by Hilt; provides [Context] and [Application] for system services.
 * @param repo Injected [PreferencesRepository] for existing preference flows.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app: Application,
    private val repo: PreferencesRepository,
    private val store: DataStore<Preferences>,
) : AndroidViewModel(app) {

    // ──────────────────────────────────────────────────────────────────────
    //  Mutable internal state
    // ──────────────────────────────────────────────────────────────────────

    private val _pdfState    = MutableStateFlow(refreshPdfState())
    private val _dialogState = MutableStateFlow<SettingsDialog?>(null)
    private val _requestPerm = MutableStateFlow(false)

    // ──────────────────────────────────────────────────────────────────────
    //  Private DataStore flows
    // ──────────────────────────────────────────────────────────────────────

    private val profileNameUser1Flow: Flow<String> = store.data
        .map { it[SettingsKeys.PROFILE_NAME_USER1] ?: "Profile 1" }

    private val profileNameUser2Flow: Flow<String> = store.data
        .map { it[SettingsKeys.PROFILE_NAME_USER2] ?: "Profile 2" }

    private val notificationsEnabledFlow: Flow<Boolean> = store.data
        .map { it[SettingsKeys.NOTIFICATIONS_ENABLED] ?: false }

    private val notificationTimeFlow: Flow<Long> = store.data
        .map { it[SettingsKeys.NOTIFICATION_TIME] ?: 32_400L }

    /** isHoursMode is profile-scoped — re-derives whenever the active profile changes. */
    private val isHoursModeFlow: Flow<Boolean> = repo.activeProfile
        .flatMapLatest { profile -> repo.isHoursModeForProfile(profile) }

    // ──────────────────────────────────────────────────────────────────────
    //  Public UI state — single combined StateFlow
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Reactive snapshot of the entire Settings screen.
     * Collect in the composable with [androidx.lifecycle.compose.collectAsStateWithLifecycle].
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        // Group A — profile & appearance
        combine(
            repo.activeProfile,
            profileNameUser1Flow,
            profileNameUser2Flow,
            repo.isDarkTheme,
            isHoursModeFlow,
        ) { ap, n1, n2, dark, hours ->
            ProfileAppearanceGroup(ap, n1, n2, dark, hours)
        },
        // Group B — notifications & calendar
        combine(
            notificationsEnabledFlow,
            notificationTimeFlow,
            repo.startOnMonday,
        ) { enabled, time, monday ->
            NotifCalendarGroup(enabled, time, monday)
        },
        // Group C — PDF + dialog state
        combine(
            _pdfState,
            _dialogState,
            _requestPerm,
        ) { pdf, dialog, perm ->
            UiAuxGroup(pdf, dialog, perm)
        },
    ) { a, b, c ->
        SettingsUiState(
            activeProfile                 = a.activeProfile,
            profileNameUser1              = a.nameUser1,
            profileNameUser2              = a.nameUser2,
            isDarkMode                    = a.isDarkMode,
            isHoursMode                   = a.isHoursMode,
            notificationsEnabled          = b.notificationsEnabled,
            notificationTimeSeconds       = b.notificationTimeSeconds,
            startOnMonday                 = b.startOnMonday,
            pdfState                      = c.pdfState,
            activeDialog                  = c.activeDialog,
            requestNotificationPermission = c.requestPermission,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    // ──────────────────────────────────────────────────────────────────────
    //  Profile actions
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Switches the active profile to [profileId] (`"User1"` or `"User2"`).
     */
    fun setActiveProfile(profileId: String) = viewModelScope.launch {
        repo.setActiveProfile(profileId)
    }

    /**
     * Persists the display name for the given [profileId].
     *
     * @param profileId `"User1"` or `"User2"`.
     * @param name      New display name (empty string is ignored).
     */
    fun setProfileName(profileId: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val key = if (profileId == PrefsKeys.PROFILE_USER1)
                SettingsKeys.PROFILE_NAME_USER1
            else
                SettingsKeys.PROFILE_NAME_USER2
            store.edit { it[key] = name.trim() }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Appearance actions
    // ──────────────────────────────────────────────────────────────────────

    /** Toggles dark mode. */
    fun setDarkMode(enabled: Boolean) = viewModelScope.launch {
        repo.setDarkTheme(enabled)
    }

    /** Toggles hours-mode for the currently active profile. */
    fun setHoursMode(enabled: Boolean) = viewModelScope.launch {
        val profile = repo.activeProfile.first()
        repo.setIsHoursMode(profile, enabled)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Notification actions
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Attempts to enable or disable daily reminders.
     *
     * On Android 13+ (API 33+), enabling notifications when [hasPermission] is `false`
     * sets [SettingsUiState.requestNotificationPermission] to `true`, signalling the
     * composable to launch the runtime permission request. The caller must then invoke
     * [onNotificationPermissionResult] with the result.
     *
     * @param enabled       `true` to enable reminders, `false` to cancel.
     * @param hasPermission Whether `POST_NOTIFICATIONS` is currently granted.
     */
    fun setNotificationsEnabled(enabled: Boolean, hasPermission: Boolean) =
        viewModelScope.launch {
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
                _requestPerm.value = true
                return@launch
            }
            persistNotificationState(enabled)
        }

    /**
     * Called by the composable after the `POST_NOTIFICATIONS` permission dialog resolves.
     *
     * @param granted `true` if the user granted the permission.
     */
    fun onNotificationPermissionResult(granted: Boolean) = viewModelScope.launch {
        _requestPerm.value = false
        if (granted) persistNotificationState(enabled = true)
    }

    /**
     * Persists a new reminder time (seconds since midnight) and reschedules the alarm
     * if notifications are currently enabled.
     *
     * @param seconds Seconds since midnight (e.g. `32400` = 09:00).
     */
    fun setNotificationTime(seconds: Long) = viewModelScope.launch {
        store.edit { it[SettingsKeys.NOTIFICATION_TIME] = seconds }
        if (notificationsEnabledFlow.first()) {
            NotificationScheduler.schedule(app, seconds)
        }
    }

    private suspend fun persistNotificationState(enabled: Boolean) {
        store.edit { it[SettingsKeys.NOTIFICATIONS_ENABLED] = enabled }
        if (enabled) {
            val time = notificationTimeFlow.first()
            NotificationScheduler.schedule(app, time)
        } else {
            NotificationScheduler.cancelAll(app)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Calendar configuration
    // ──────────────────────────────────────────────────────────────────────

    /** Sets whether the calendar week starts on Monday. */
    fun setStartOnMonday(value: Boolean) = viewModelScope.launch {
        repo.setStartOnMonday(value)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Share data — Export / Import
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Exports the active profile's duty notes and booked holidays to the system clipboard
     * in the format: `DUTY_DATA:{notesJson}###HOLIDAY_DATA:{holidaysJson}`.
     *
     * On success, shows [SettingsDialog.ExportSuccess].
     */
    fun exportData() = viewModelScope.launch {
        val profile = repo.activeProfile.first()
        val prefs   = store.data.first()
        val notes    = prefs[stringPreferencesKey(PrefsKeys.notes(profile))]    ?: "{}"
        val holidays = prefs[stringPreferencesKey(PrefsKeys.holidays(profile))] ?: "[]"
        val payload  = "DUTY_DATA:$notes###HOLIDAY_DATA:$holidays"

        val clipboard = app.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("WorkDiary Export", payload))
        _dialogState.value = SettingsDialog.ExportSuccess
    }

    /**
     * Reads the clipboard and attempts to parse the WorkDiary export format
     * (`DUTY_DATA:…###HOLIDAY_DATA:…`), writing the data back into DataStore
     * for the currently active profile.
     *
     * On success, shows [SettingsDialog.ImportSuccess].
     * Silently no-ops if the clipboard content does not match the expected format.
     */
    fun importFromClipboard() = viewModelScope.launch {
        val clipboard = app.getSystemService(ClipboardManager::class.java) ?: return@launch
        val raw = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return@launch

        if (!raw.contains("DUTY_DATA:") || !raw.contains("###HOLIDAY_DATA:")) return@launch

        val parts        = raw.split("###", limit = 2)
        val notesJson    = parts.getOrNull(0)?.removePrefix("DUTY_DATA:")    ?: return@launch
        val holidaysJson = parts.getOrNull(1)?.removePrefix("HOLIDAY_DATA:") ?: return@launch

        val profile = repo.activeProfile.first()
        store.edit { prefs ->
            prefs[stringPreferencesKey(PrefsKeys.notes(profile))]    = notesJson
            prefs[stringPreferencesKey(PrefsKeys.holidays(profile))] = holidaysJson
        }
        _dialogState.value = SettingsDialog.ImportSuccess
    }

    // ──────────────────────────────────────────────────────────────────────
    //  PDF file management
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Copies the PDF at [uri] into `filesDir/{type}.pdf`.
     *
     * @param uri  Content URI returned by the system file picker.
     * @param type One of `"MF"`, `"SAT"`, `"SUN"`.
     */
    fun uploadPdf(uri: Uri, type: String) = viewModelScope.launch {
        val dest = File(app.filesDir, "$type.pdf")
        try {
            app.contentResolver.openInputStream(uri)?.use { src ->
                FileOutputStream(dest).use { out -> src.copyTo(out) }
            }
        } catch (e: Exception) {
            // In a production app, surface this via a Snackbar/error state.
            e.printStackTrace()
        }
        _pdfState.value = refreshPdfState()
    }

    /**
     * Deletes `filesDir/{type}.pdf` if it exists.
     *
     * @param type One of `"MF"`, `"SAT"`, `"SUN"`.
     */
    fun deletePdf(type: String) {
        File(app.filesDir, "$type.pdf").also { if (it.exists()) it.delete() }
        _pdfState.value = refreshPdfState()
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Data reset actions
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Clears annual/hourly allowances and the global carried-over value for the
     * active profile, then dismisses the current dialog.
     */
    fun confirmResetAllowances() = viewModelScope.launch {
        val p = repo.activeProfile.first()
        store.edit { prefs ->
            prefs.remove(doublePreferencesKey(PrefsKeys.annual(p)))
            prefs.remove(doublePreferencesKey(PrefsKeys.hourly(p)))
            prefs.remove(SettingsKeys.CARRIED_OVER_GLOBAL)
        }
        _dialogState.value = null
    }

    /**
     * Clears all booked holiday records for the active profile, then dismisses
     * the current dialog.
     */
    fun confirmResetBooked() = viewModelScope.launch {
        val p = repo.activeProfile.first()
        store.edit { prefs ->
            prefs.remove(stringPreferencesKey(PrefsKeys.holidays(p)))
        }
        _dialogState.value = null
    }

    /**
     * Clears all day notes and saved special days for the active profile, then
     * dismisses the current dialog.
     */
    fun confirmResetCalendar() = viewModelScope.launch {
        val p = repo.activeProfile.first()
        store.edit { prefs ->
            prefs.remove(stringPreferencesKey(PrefsKeys.notes(p)))
            prefs.remove(SettingsKeys.SAVED_SPECIAL_DAYS)
        }
        _dialogState.value = null
    }

    /**
     * Clears **all** profile data across both profiles (notes, holidays, allowances,
     * hours-mode, carried-over), plus global carried-over and saved special days.
     * Then dismisses the current dialog.
     */
    fun confirmResetAll() = viewModelScope.launch {
        store.edit { prefs ->
            listOf(PrefsKeys.PROFILE_USER1, PrefsKeys.PROFILE_USER2).forEach { p ->
                prefs.remove(stringPreferencesKey(PrefsKeys.notes(p)))
                prefs.remove(stringPreferencesKey(PrefsKeys.holidays(p)))
                prefs.remove(doublePreferencesKey(PrefsKeys.annual(p)))
                prefs.remove(doublePreferencesKey(PrefsKeys.hourly(p)))
                prefs.remove(booleanPreferencesKey(PrefsKeys.isHours(p)))
                prefs.remove(doublePreferencesKey(PrefsKeys.carriedOver(p)))
            }
            prefs.remove(SettingsKeys.CARRIED_OVER_GLOBAL)
            prefs.remove(SettingsKeys.SAVED_SPECIAL_DAYS)
        }
        _dialogState.value = null
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Dialog management
    // ──────────────────────────────────────────────────────────────────────

    /** Shows the given [dialog], replacing any currently visible one. */
    fun showDialog(dialog: SettingsDialog) {
        _dialogState.value = dialog
    }

    /** Dismisses the currently visible dialog without taking any action. */
    fun dismissDialog() {
        _dialogState.value = null
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ──────────────────────────────────────────────────────────────────────

    /** Reads PDF existence from disk. Called after any upload/delete operation. */
    private fun refreshPdfState() = PdfExistenceState(
        mfExists  = File(app.filesDir, "MF.pdf").exists(),
        satExists = File(app.filesDir, "SAT.pdf").exists(),
        sunExists = File(app.filesDir, "SUN.pdf").exists(),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Private intermediate combine groups — avoids Any? casting
// ─────────────────────────────────────────────────────────────────────────────

private data class ProfileAppearanceGroup(
    val activeProfile: String,
    val nameUser1: String,
    val nameUser2: String,
    val isDarkMode: Boolean,
    val isHoursMode: Boolean,
)

private data class NotifCalendarGroup(
    val notificationsEnabled: Boolean,
    val notificationTimeSeconds: Long,
    val startOnMonday: Boolean,
)

private data class UiAuxGroup(
    val pdfState: PdfExistenceState,
    val activeDialog: SettingsDialog?,
    val requestPermission: Boolean,
)
