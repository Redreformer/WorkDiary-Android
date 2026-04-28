package com.workdiary.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel scoped to [MainActivity].
 *
 * Holds app-level UI state that must survive configuration changes and be available at the root
 * of the composable tree — primarily the dark/light theme preference.
 *
 * ## Dark mode
 * The iOS app stores `isDarkMode` in `UserDefaults` (defaulting to `true`). The Android
 * equivalent stores this preference in Jetpack [DataStore][androidx.datastore.preferences.core]
 * via [PreferencesRepository]. The [isDarkTheme] [StateFlow] is collected in [MainActivity] and
 * passed into [com.workdiary.app.ui.theme.WorkDiaryTheme].
 *
 * @param preferencesRepository Repository that wraps DataStore for user preferences.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    /**
     * Emits the current dark-theme preference. Defaults to `true` (matching the iOS default)
     * until the DataStore value is loaded. The [SharingStarted.WhileSubscribed] policy stops
     * the upstream flow when no collectors are active, resuming on resubscription.
     */
    val isDarkTheme: StateFlow<Boolean> = preferencesRepository
        .isDarkTheme
        .stateIn(
            scope            = viewModelScope,
            started          = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue     = true, // mirror iOS default: isDarkMode = true
        )
}
