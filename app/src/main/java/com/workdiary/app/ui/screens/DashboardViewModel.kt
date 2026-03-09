package com.workdiary.app.ui.screens

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workdiary.app.PreferencesRepository
import com.workdiary.app.data.models.Holiday
import com.workdiary.app.data.storage.PrefsKeys
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
import java.util.Date
import java.util.UUID
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
//  DataStore accessor — reuses the same singleton as the rest of the app
// ─────────────────────────────────────────────────────────────────────────────

private val android.content.Context.dashboardStore
    by preferencesDataStore(name = "workdiary_prefs")

// ─────────────────────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of everything the Balance / Dashboard screen needs to render.
 *
 * @property isHoursMode        Whether leave is tracked in hours (vs days).
 * @property totalYearly        Annual allowance (days or hours depending on [isHoursMode]).
 * @property carriedOver        Carried-over allowance from previous year.
 * @property daysTaken          Non-lieu leave consumed so far.
 * @property lieuAccrued        Net lieu balance (earned minus taken).
 * @property allowanceRemaining Remaining entitlement: totalYearly + carriedOver + lieuAccrued − daysTaken.
 * @property lieuHistory        All lieu-day entries, sorted newest-first.
 * @property activeSheet        Which bottom sheet (if any) is currently showing.
 */
data class DashboardUiState(
    val isHoursMode: Boolean        = false,
    val totalYearly: Double         = 0.0,
    val carriedOver: Double         = 0.0,
    val daysTaken: Double           = 0.0,
    val lieuAccrued: Double         = 0.0,
    val allowanceRemaining: Double  = 0.0,
    val lieuHistory: List<Holiday>  = emptyList(),
    val activeSheet: DashboardSheet? = null,
)

/** Which modal bottom-sheet the Dashboard screen is currently showing. */
enum class DashboardSheet {
    Lieu,
    EditAllowance,
    EditCarriedOver,
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for [DashboardScreen].
 *
 * Mirrors the computed properties and AppStorage bindings in iOS `BalanceView` /
 * `QuickLieuView` / `QuickAllowanceEditView`.
 *
 * All DataStore reads are reactive; writes are dispatched on [viewModelScope].
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val app: Application,
    private val repo: PreferencesRepository,
) : AndroidViewModel(app) {

    private val store = app.dashboardStore

    // ── Sheet state ───────────────────────────────────────────────────────
    private val _activeSheet = MutableStateFlow<DashboardSheet?>(null)

    // ── Profile-scoped preference flows ──────────────────────────────────

    private val isHoursModeFlow: Flow<Boolean> = repo.activeProfile
        .flatMapLatest { profile ->
            store.data.map { it[booleanPreferencesKey(PrefsKeys.isHours(profile))] ?: false }
        }

    private val annualAllowanceFlow: Flow<Double> = repo.activeProfile
        .flatMapLatest { profile ->
            store.data.map { it[doublePreferencesKey(PrefsKeys.annual(profile))] ?: 0.0 }
        }

    private val hourlyAllowanceFlow: Flow<Double> = repo.activeProfile
        .flatMapLatest { profile ->
            store.data.map { it[doublePreferencesKey(PrefsKeys.hourly(profile))] ?: 0.0 }
        }

    private val carriedOverFlow: Flow<Double> = repo.activeProfile
        .flatMapLatest { profile ->
            store.data.map { it[doublePreferencesKey(PrefsKeys.carriedOver(profile))] ?: 0.0 }
        }

    private val holidaysJsonFlow: Flow<String> = repo.activeProfile
        .flatMapLatest { profile ->
            store.data.map { it[stringPreferencesKey(PrefsKeys.holidays(profile))] ?: "" }
        }

    // ── Combined UI state ─────────────────────────────────────────────────

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(isHoursModeFlow, annualAllowanceFlow, hourlyAllowanceFlow) { hours, annual, hourly ->
            Triple(hours, annual, hourly)
        },
        combine(carriedOverFlow, holidaysJsonFlow, _activeSheet) { carried, json, sheet ->
            Triple(carried, json, sheet)
        },
    ) { (isHours, annual, hourly), (carriedOver, json, sheet) ->

        val holidays     = Holiday.decode(json)
        val totalYearly  = if (isHours) hourly else annual

        val daysTaken   = holidays
            .filter { it.type != "Lieu Day" }
            .sumOf { it.amount }

        val lieuEarned  = holidays
            .filter { it.type == "Lieu Day" && it.amount < 0 }
            .sumOf { kotlin.math.abs(it.amount) }
        val lieuTaken   = holidays
            .filter { it.type == "Lieu Day" && it.amount > 0 }
            .sumOf { it.amount }
        val lieuAccrued = lieuEarned - lieuTaken

        val allowanceRemaining = (totalYearly + carriedOver + lieuAccrued) - daysTaken

        val lieuHistory = holidays
            .filter { it.type == "Lieu Day" }
            .sortedByDescending { it.date }

        DashboardUiState(
            isHoursMode        = isHours,
            totalYearly        = totalYearly,
            carriedOver        = carriedOver,
            daysTaken          = daysTaken,
            lieuAccrued        = lieuAccrued,
            allowanceRemaining = allowanceRemaining,
            lieuHistory        = lieuHistory,
            activeSheet        = sheet,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    // ── Sheet actions ─────────────────────────────────────────────────────

    /** Shows the given bottom-sheet. */
    fun showSheet(sheet: DashboardSheet) { _activeSheet.value = sheet }

    /** Dismisses any open bottom-sheet. */
    fun dismissSheet() { _activeSheet.value = null }

    // ── Allowance editing ─────────────────────────────────────────────────

    /**
     * Saves a new yearly allowance value for the active profile.
     * Writes to the hours key or the days key based on [isHoursMode].
     */
    fun saveYearlyAllowance(value: Double) = viewModelScope.launch {
        val profile   = repo.activeProfile.first()
        val isHours   = uiState.value.isHoursMode
        val key       = if (isHours) doublePreferencesKey(PrefsKeys.hourly(profile))
                        else         doublePreferencesKey(PrefsKeys.annual(profile))
        store.edit { it[key] = value }
        _activeSheet.value = null
    }

    /**
     * Saves a new carried-over allowance value for the active profile.
     */
    fun saveCarriedOver(value: Double) = viewModelScope.launch {
        val profile = repo.activeProfile.first()
        store.edit { it[doublePreferencesKey(PrefsKeys.carriedOver(profile))] = value }
        _activeSheet.value = null
    }

    // ── Lieu management ───────────────────────────────────────────────────

    /**
     * Adds a "Lieu Earned" entry to the active profile's holiday list.
     *
     * The amount is stored as **negative** (matching iOS convention where earned lieu
     * has a negative amount, and taken lieu has a positive amount).
     *
     * @param amount The amount of lieu earned (positive; negated internally).
     */
    fun addLieuEarned(amount: Double) = viewModelScope.launch {
        val profile   = repo.activeProfile.first()
        val key       = stringPreferencesKey(PrefsKeys.holidays(profile))
        val json      = store.data.first()[key] ?: ""
        val holidays  = Holiday.decode(json).toMutableList()
        holidays.add(
            Holiday(
                id     = UUID.randomUUID(),
                name   = "Lieu Earned",
                amount = -amount,                  // negative = earned
                date   = Date(Long.MIN_VALUE),     // mirrors iOS Date.distantPast
                type   = "Lieu Day",
            )
        )
        store.edit { it[key] = Holiday.encode(holidays) }
    }

    /**
     * Removes the lieu entry at the given [index] within [DashboardUiState.lieuHistory].
     */
    fun deleteLieu(index: Int) = viewModelScope.launch {
        val profile   = repo.activeProfile.first()
        val key       = stringPreferencesKey(PrefsKeys.holidays(profile))
        val json      = store.data.first()[key] ?: ""
        val all       = Holiday.decode(json).toMutableList()
        val target    = uiState.value.lieuHistory.getOrNull(index) ?: return@launch
        all.removeAll { it.id == target.id }
        store.edit { it[key] = Holiday.encode(all) }
    }
}
