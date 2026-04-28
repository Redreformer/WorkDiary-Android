package com.workdiary.app.ui.screens

import androidx.lifecycle.ViewModel
import com.workdiary.app.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * ViewModel for [SplashScreen].
 *
 * Exposes the [hasCompletedSetup] preference as a [Flow] so the splash composable can
 * decide whether to route to onboarding or the main shell without reading DataStore on
 * the main thread.
 *
 * @param preferencesRepository Repository wrapping Jetpack DataStore.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    /**
     * Emits `true` once the user has completed first-run setup; `false` otherwise.
     *
     * iOS equivalent: `@AppStorage("hasCompletedSetup") var hasCompletedSetup = false`
     * in `AnnualLeaveTrackerApp.swift`.
     */
    val hasCompletedSetup: Flow<Boolean> = preferencesRepository.hasCompletedSetup
}
