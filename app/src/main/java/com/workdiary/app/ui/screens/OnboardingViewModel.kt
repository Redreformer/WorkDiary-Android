package com.workdiary.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workdiary.app.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
//  Onboarding page indices (matches HorizontalPager page count)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Total number of pages in the onboarding [HorizontalPager].
 *
 * - 0 = Welcome
 * - 1 = Tracking mode (Days vs Hours)
 * - 2 = Allowance entry
 */
const val ONBOARDING_PAGE_COUNT = 3

// ─────────────────────────────────────────────────────────────────────────────
//  UI state
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of all user-facing onboarding form state.
 *
 * Consumed by [OnboardingScreen] via [OnboardingViewModel.uiState].
 *
 * @property currentPage   Zero-based index of the currently visible pager page.
 * @property isHoursMode   Whether the user has chosen hours-based tracking (vs days).
 * @property allowanceText Raw text field content for the allowance value. Stored as
 *                         [String] to give the UI full control over keyboard input;
 *                         parsed to [Double] only on save.
 * @property isSaving      `true` while DataStore writes are in-flight (disables buttons).
 * @property isComplete    `true` once [PreferencesRepository.setSetupComplete] has been
 *                         called; triggers navigation to the main shell in the screen.
 */
data class OnboardingUiState(
    val currentPage:   Int     = 0,
    val isHoursMode:   Boolean = false,
    val allowanceText: String  = "25",
    val isSaving:      Boolean = false,
    val isComplete:    Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Hilt ViewModel for [OnboardingScreen].
 *
 * Mirrors the state management of `OnboardingView.swift`, which used `@AppStorage` bindings
 * and a local `@State private var setupAmount`. Here, all mutable state is owned by this
 * ViewModel so it survives orientation changes.
 *
 * ## Page flow
 * ```
 * [0] Welcome  →  [1] Tracking Mode  →  [2] Allowance Entry  →  save & navigate
 * ```
 *
 * ## iOS parity
 * | iOS behaviour                               | Android equivalent                         |
 * |---------------------------------------------|--------------------------------------------|
 * | `Toggle("Track in Hours…", isOn: $isHoursMode)` onChange pre-fills `setupAmount`  | [onHoursModeChanged] updates [uiState.allowanceText] with the mode-appropriate default |
 * | `TextField("Amount", value: $setupAmount)` | [onAllowanceTextChanged]                   |
 * | `Button("Get Started")` saves to `AppStorage` and sets `hasCompletedSetup = true` | [completeOnboarding] writes DataStore then sets `isComplete = true` |
 *
 * @param preferencesRepository Repository wrapping Jetpack DataStore. Injected via Hilt.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    // ── Internal mutable state ─────────────────────────────────────────────

    private val _uiState = MutableStateFlow(OnboardingUiState())

    /**
     * The current onboarding UI state. Collected by [OnboardingScreen] with
     * `collectAsStateWithLifecycle`.
     */
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * The active profile identifier, exposed as a [StateFlow] so the screen can display
     * profile-aware copy if needed (e.g. "Profile 1").
     *
     * Read-only; profile switching is not part of the onboarding flow.
     */
    val activeProfile: StateFlow<String> = preferencesRepository.activeProfile
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000L),
            initialValue = "User1",
        )

    // ── Event handlers — called from the UI ───────────────────────────────

    /**
     * Advances the pager to the next page.
     *
     * Should be called by the "Next" button on pages 0 and 1.
     * The "Get Started" button on page 2 calls [completeOnboarding] instead.
     */
    fun onNextPage() {
        _uiState.update { state ->
            state.copy(currentPage = (state.currentPage + 1).coerceAtMost(ONBOARDING_PAGE_COUNT - 1))
        }
    }

    /**
     * Moves the pager back one page.
     *
     * Called by the "Back" button / system back gesture on pages 1 and 2.
     */
    fun onPreviousPage() {
        _uiState.update { state ->
            state.copy(currentPage = (state.currentPage - 1).coerceAtLeast(0))
        }
    }

    /**
     * Synchronises the ViewModel's [uiState.currentPage] when the user swipes the pager
     * directly (pager → ViewModel direction).
     *
     * @param page The new page index reported by [HorizontalPager].
     */
    fun onPageChanged(page: Int) {
        _uiState.update { it.copy(currentPage = page) }
    }

    /**
     * Handles a change to the "Track in Hours" toggle.
     *
     * Mirrors the iOS `onChange(of: isHoursMode)` block in `OnboardingView.swift` that
     * pre-fills a sensible default: `187.5` hours (standard UK full-time annual hours)
     * or `25` days.
     *
     * @param isHours `true` = hours mode; `false` = days mode.
     */
    fun onHoursModeChanged(isHours: Boolean) {
        val defaultAllowance = if (isHours) "187.5" else "25"
        _uiState.update { it.copy(isHoursMode = isHours, allowanceText = defaultAllowance) }
    }

    /**
     * Updates the raw allowance text field value.
     *
     * Validation (non-empty, parseable as [Double]) is deferred to [completeOnboarding].
     *
     * @param text Raw string from the text field.
     */
    fun onAllowanceTextChanged(text: String) {
        // Allow only digits, a single decimal point, and an empty string.
        if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.update { it.copy(allowanceText = text) }
        }
    }

    /**
     * Validates the form, persists all preferences, marks setup as complete, and signals
     * the UI to navigate to the main shell.
     *
     * ## What is written (mirrors iOS `OnboardingView.swift` "Get Started" action)
     * - `isHoursMode_{profile}` → [PreferencesRepository.setIsHoursMode]
     * - `annualAllowance_{profile}` OR `hourlyAllowance_{profile}` depending on mode
     *   → [PreferencesRepository.setAnnualAllowance] / [PreferencesRepository.setHourlyAllowance]
     * - `hasCompletedSetup = true` → [PreferencesRepository.setSetupComplete]
     *
     * If [uiState.allowanceText] cannot be parsed as a [Double], a sensible default is used
     * (25 days / 187.5 hours) rather than failing silently — matching iOS behaviour of
     * initialising `setupAmount` with a default.
     */
    fun completeOnboarding() {
        val currentState = _uiState.value
        if (currentState.isSaving) return

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val profile     = preferencesRepository.activeProfile.first()
            val allowance   = currentState.allowanceText.toDoubleOrNull()
                ?: if (currentState.isHoursMode) 187.5 else 25.0

            // Persist tracking mode.
            preferencesRepository.setIsHoursMode(profile, currentState.isHoursMode)

            // Persist the allowance to the correct key (mirrors iOS conditional save).
            if (currentState.isHoursMode) {
                preferencesRepository.setHourlyAllowance(profile, allowance)
            } else {
                preferencesRepository.setAnnualAllowance(profile, allowance)
            }

            // Mark onboarding as complete — gates future launches to the main shell.
            preferencesRepository.setSetupComplete()

            // Signal the UI to navigate.
            _uiState.update { it.copy(isSaving = false, isComplete = true) }
        }
    }
}
