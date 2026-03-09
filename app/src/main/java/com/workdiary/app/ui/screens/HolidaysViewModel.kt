package com.workdiary.app.ui.screens

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
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

private val android.content.Context.holidaysStore
    by preferencesDataStore(name = "workdiary_prefs")

// ─────────────────────────────────────────────────────────────────────────────
//  Leave categories shown on the Holidays tab
//  (Lieu Day and Sick Leave are managed via other screens)
// ─────────────────────────────────────────────────────────────────────────────

/** Leave type strings that appear on the Holidays screen — mirrors iOS `categories`. */
val HOLIDAY_CATEGORIES = listOf("Annual Leave", "Allocated", "Personal")

// ─────────────────────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of everything [HolidaysScreen] needs to render.
 *
 * @property holidays       Full list of leave entries for the active profile, filtered to
 *                          [HOLIDAY_CATEGORIES] and sorted by [sortAscending].
 * @property isHoursMode    Whether leave amounts are displayed in hours (`h`) or days (`d`).
 * @property sortAscending  Current sort direction (true = oldest first, false = newest first).
 * @property searchQuery    Current search filter string.
 * @property sheetState     Which modal sheet (if any) is open.
 * @property editingEntry   The holiday being edited, or `null` if adding new.
 */
data class HolidaysUiState(
    val holidays: List<Holiday>       = emptyList(),
    val isHoursMode: Boolean          = false,
    val sortAscending: Boolean        = false,
    val searchQuery: String           = "",
    val sheetState: HolidaySheetState = HolidaySheetState.Hidden,
    val editingEntry: Holiday?        = null,
)

/** Which (if any) bottom sheet the Holidays screen is presenting. */
sealed interface HolidaySheetState {
    /** No sheet open. */
    data object Hidden : HolidaySheetState

    /**
     * Add-or-edit sheet open with a draft entry.
     *
     * @property draft  The mutable form state being edited.
     */
    data class AddEdit(val draft: HolidayDraft) : HolidaySheetState
}

/**
 * Mutable form state for the Add / Edit bottom sheet.
 *
 * Mirrors the `@State` variables in `BookedView` that populate the sheet form.
 *
 * @property name         Description / label for this leave entry.
 * @property amountText   Raw text from the amount field (validated on save).
 * @property type         Selected leave type from [HOLIDAY_CATEGORIES].
 * @property startDate    Leave start date.
 * @property endDate      Leave end date (must be ≥ [startDate]).
 */
data class HolidayDraft(
    val name: String       = "",
    val amountText: String = "",
    val type: String       = "Annual Leave",
    val startDate: Date    = Date(),
    val endDate: Date      = Date(),
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for [HolidaysScreen].
 *
 * Mirrors the AppStorage bindings and logic of `BookedView.swift`:
 * - Reads/writes profile-scoped `bookedHolidaysData_{profile}` from DataStore.
 * - Filters to [HOLIDAY_CATEGORIES] (Annual Leave, Allocated, Personal).
 * - Supports full-text search by name or type.
 * - Sort direction toggle (ascending / descending by start date).
 * - Add / edit / delete via an [HolidaySheetState.AddEdit] bottom sheet.
 *
 * @property uiState Reactive UI state consumed by [HolidaysScreen].
 */
@HiltViewModel
class HolidaysViewModel @Inject constructor(
    private val app: Application,
    private val repo: PreferencesRepository,
) : AndroidViewModel(app) {

    private val store = app.holidaysStore

    // ── Local mutable state ───────────────────────────────────────────────

    private val _sortAscending = MutableStateFlow(false)
    private val _searchQuery   = MutableStateFlow("")
    private val _sheetState    = MutableStateFlow<HolidaySheetState>(HolidaySheetState.Hidden)
    private val _editingEntry  = MutableStateFlow<Holiday?>(null)

    // ── Profile-scoped DataStore flows ────────────────────────────────────

    private val isHoursModeFlow: Flow<Boolean> = repo.activeProfile
        .flatMapLatest { profile ->
            store.data.map { it[booleanPreferencesKey(PrefsKeys.isHours(profile))] ?: false }
        }

    private val holidaysJsonFlow: Flow<String> = repo.activeProfile
        .flatMapLatest { profile ->
            store.data.map { it[stringPreferencesKey(PrefsKeys.holidays(profile))] ?: "" }
        }

    // ── Combined UI state ─────────────────────────────────────────────────

    val uiState: StateFlow<HolidaysUiState> = combine(
        isHoursModeFlow,
        holidaysJsonFlow,
        _sortAscending,
        _searchQuery,
        combine(_sheetState, _editingEntry) { sheet, editing -> sheet to editing },
    ) { isHours, json, ascending, query, (sheet, editing) ->

        val allHolidays = Holiday.decode(json)
            .filter { it.type in HOLIDAY_CATEGORIES }

        val filtered = if (query.isBlank()) allHolidays
        else allHolidays.filter { h ->
            h.name.contains(query, ignoreCase = true) ||
            h.type.contains(query, ignoreCase = true)
        }

        val sorted = if (ascending) filtered.sortedBy { it.date }
                     else           filtered.sortedByDescending { it.date }

        HolidaysUiState(
            holidays      = sorted,
            isHoursMode   = isHours,
            sortAscending = ascending,
            searchQuery   = query,
            sheetState    = sheet,
            editingEntry  = editing,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = HolidaysUiState(),
    )

    // ── Search & Sort ─────────────────────────────────────────────────────

    /** Updates the search filter applied to the holiday list. */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /** Flips the sort direction between oldest-first and newest-first. */
    fun toggleSortOrder() {
        _sortAscending.value = !_sortAscending.value
    }

    // ── Sheet lifecycle ───────────────────────────────────────────────────

    /**
     * Opens the Add sheet with an empty draft.
     *
     * Mirrors `BookedView.prepareForNew()`.
     */
    fun prepareForNew() {
        _editingEntry.value = null
        _sheetState.value = HolidaySheetState.AddEdit(HolidayDraft())
    }

    /**
     * Opens the Edit sheet pre-populated with [holiday]'s data.
     *
     * Mirrors `BookedView.prepareToEdit(_:)`.
     */
    fun prepareToEdit(holiday: Holiday) {
        _editingEntry.value = holiday
        _sheetState.value = HolidaySheetState.AddEdit(
            HolidayDraft(
                name       = holiday.name,
                amountText = "%.1f".format(holiday.amount),
                type       = holiday.type,
                startDate  = holiday.date,
                endDate    = holiday.actualEndDate,
            )
        )
    }

    /** Dismisses the sheet without saving. */
    fun dismissSheet() {
        _sheetState.value = HolidaySheetState.Hidden
        _editingEntry.value = null
    }

    /**
     * Updates the draft while the user edits the form.
     *
     * Call this for every field change to keep the draft reactive.
     */
    fun updateDraft(draft: HolidayDraft) {
        val currentSheet = _sheetState.value
        if (currentSheet is HolidaySheetState.AddEdit) {
            _sheetState.value = HolidaySheetState.AddEdit(draft)
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    /**
     * Saves the current draft as a new or updated holiday entry.
     *
     * - Validates that the amount field parses to a non-null [Double].
     * - Falls back to `"Holiday"` if the name is blank (mirrors iOS behaviour).
     * - On new entry: appends to the list (iOS would also sync to Calendar — stubbed here).
     * - On edit: replaces the existing entry by [Holiday.id].
     *
     * Mirrors `BookedView.saveEntry()`.
     */
    fun saveEntry() = viewModelScope.launch {
        val sheet    = _sheetState.value as? HolidaySheetState.AddEdit ?: return@launch
        val draft    = sheet.draft
        val amount   = draft.amountText.toDoubleOrNull() ?: return@launch
        val finalName = draft.name.ifBlank { "Holiday" }

        val profile  = repo.activeProfile.first()
        val key      = stringPreferencesKey(PrefsKeys.holidays(profile))
        val json     = store.data.first()[key] ?: ""
        val holidays = Holiday.decode(json).toMutableList()

        val existing = _editingEntry.value
        if (existing != null) {
            // Edit — replace in-place
            val idx = holidays.indexOfFirst { it.id == existing.id }
            if (idx >= 0) {
                holidays[idx] = Holiday(
                    id      = existing.id,
                    name    = finalName,
                    amount  = amount,
                    date    = draft.startDate,
                    endDate = draft.endDate,
                    type    = draft.type,
                )
            }
        } else {
            // New entry
            val newHoliday = Holiday(
                id      = UUID.randomUUID(),
                name    = finalName,
                amount  = amount,
                date    = draft.startDate,
                endDate = draft.endDate,
                type    = draft.type,
            )
            holidays.add(newHoliday)
            // TODO Phase 8: sync to device calendar via CalendarManager equivalent
        }

        store.edit { it[key] = Holiday.encode(holidays) }
        _sheetState.value = HolidaySheetState.Hidden
        _editingEntry.value = null
    }

    /**
     * Deletes the given [holiday] entry.
     *
     * Mirrors the `onDelete` handler in `BookedView`.
     */
    fun deleteHoliday(holiday: Holiday) = viewModelScope.launch {
        val profile  = repo.activeProfile.first()
        val key      = stringPreferencesKey(PrefsKeys.holidays(profile))
        val json     = store.data.first()[key] ?: ""
        val holidays = Holiday.decode(json).toMutableList()
        holidays.removeAll { it.id == holiday.id }
        store.edit { it[key] = Holiday.encode(holidays) }
    }
}
