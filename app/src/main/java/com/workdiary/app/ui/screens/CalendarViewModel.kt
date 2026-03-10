package com.workdiary.app.ui.screens

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workdiary.app.PreferencesRepository
import com.workdiary.app.data.models.Holiday
import com.workdiary.app.data.models.ZoomItem
import com.workdiary.app.data.storage.PrefsKeys
import com.workdiary.app.utils.DutyScanner
import com.workdiary.app.utils.PDFManager
import com.workdiary.app.utils.PhotoManager
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
//  DataStore accessor — reuses the same singleton as the rest of the app
// ─────────────────────────────────────────────────────────────────────────────

private val Context.calendarStore by preferencesDataStore(name = "workdiary_prefs")

// ─────────────────────────────────────────────────────────────────────────────
//  Note helpers (mirrors decodeNotes / encodeNotes / getNoteForDate in CalendarView.swift)
// ─────────────────────────────────────────────────────────────────────────────

/** Decodes a JSON string into a [Map] of date-key → note text. */
fun decodeNotesMap(json: String): MutableMap<String, String> {
    if (json.isBlank()) return mutableMapOf()
    return try {
        Json.decodeFromString<Map<String, String>>(json).toMutableMap()
    } catch (_: Exception) {
        mutableMapOf()
    }
}

/** Encodes a [Map] of date-key → note text back to JSON. */
fun encodeNotesMap(map: Map<String, String>): String {
    return try {
        Json.encodeToString(map)
    } catch (_: Exception) {
        ""
    }
}

/**
 * Converts a [LocalDate] to the storage key used in the notes map.
 *
 * iOS uses `Calendar.current.startOfDay(for:).description` which produces a string like
 * `"2025-06-15 00:00:00 +0000"`. Android uses an ISO-8601 date string for clarity and
 * portability. A migration shim should be applied when importing iOS backups.
 */
fun LocalDate.toNoteKey(): String = this.format(DateTimeFormatter.ISO_LOCAL_DATE)

/** Retrieves the note text for a specific date from the encoded notes JSON. */
fun getNoteForDate(date: LocalDate, notesJson: String): String {
    return decodeNotesMap(notesJson)[date.toNoteKey()] ?: ""
}

/**
 * Extracts OCR-sourced shift detail lines from a note string.
 *
 * Returns the matched values for Duty, Sign On, and Sign Off (all nullable).
 * Mirrors `extractShiftDetails(from:)` in CalendarView.swift.
 */
data class ShiftDetails(
    val duty: String?,
    val signOn: String?,
    val signOff: String?,
)

fun extractShiftDetails(note: String): ShiftDetails {
    fun matchLine(label: String): String? {
        val regex = Regex("""(?i)$label:?\s*(.+)""")
        return regex.find(note)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
    }
    return ShiftDetails(
        duty    = matchLine("Duty"),
        signOn  = matchLine("Sign On"),
        signOff = matchLine("Sign Off"),
    )
}

/** Shift type keywords — mirrors iOS constant list. */
val CALENDAR_SHIFT_TYPES = listOf(
    "Early", "Middle", "Late", "Night", "OFF", "Day",
    "Personal", "Holiday", "Training", "Standby", "Sickness"
)

/** Lines injected by OCR/scanner that are not personal notes. */
private val SCANNER_LINE_PREFIXES = listOf("Duty:", "Sign On:", "Sign Off:", "On:", "Off:")

/** Returns true if [line] is a technical scanner line that should not appear in user-facing note text. */
fun isScannerLine(line: String): Boolean {
    val lower = line.trim().lowercase()
    return lower.startsWith("duty:") ||
           lower.startsWith("sign on:") ||
           lower.startsWith("sign off:") ||
           lower.startsWith("on:") ||
           lower.startsWith("off:")
}

// ─────────────────────────────────────────────────────────────────────────────
//  Calendar view modes  (mirrors selectedViewMode = 0/1/2 in CalendarView.swift)
// ─────────────────────────────────────────────────────────────────────────────

/** The three view modes for the calendar screen. */
enum class CalendarViewMode { DAY, WEEK, MONTH }

// ─────────────────────────────────────────────────────────────────────────────
//  Pattern-generator draft
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Mutable state for the pattern-generator bottom sheet.
 *
 * Mirrors the `@State` variables inside `RestDayPatternView`.
 *
 * @property anchorDate       Date from which the cycle is anchored.
 * @property cycleLength      Number of days in one shift cycle (2–84).
 * @property weeksToGenerate  How many weeks forward to apply the pattern (4–104).
 */
data class PatternDraft(
    val anchorDate: LocalDate      = LocalDate.now(),
    val cycleLength: Int           = 8,
    val weeksToGenerate: Int       = 26,
)

// ─────────────────────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Complete, immutable snapshot of everything [CalendarScreen] needs to render.
 *
 * @property selectedDate         Currently highlighted date.
 * @property viewMode             Active calendar display mode (Day / Week / Month).
 * @property notesJson            Raw JSON string of all day notes for the active profile.
 * @property holidays             Decoded list of all booked holidays for the active profile.
 * @property startOnMonday        Whether the calendar week starts on Monday.
 * @property activeProfile        The active profile identifier (e.g. "User1").
 * @property showNoteEditor       Whether the single-day note editor sheet is visible.
 * @property showPatternGenerator Whether the pattern-generator sheet is visible.
 * @property patternDraft         Current state of the pattern-generator form.
 * @property patternSyncing       True while the pattern generator is computing.
 * @property selectedZoomItem     Non-null when the full-screen photo viewer is open.
 * @property photoRefreshTick     Incremented each time a photo is saved or deleted,
 *                                triggering recomposition of photo slots in [DayView].
 */
data class CalendarUiState(
    val selectedDate: LocalDate          = LocalDate.now(),
    val viewMode: CalendarViewMode       = CalendarViewMode.MONTH,
    val notesJson: String                = "",
    val holidays: List<Holiday>          = emptyList(),
    val startOnMonday: Boolean           = true,
    val activeProfile: String            = "User1",
    val showNoteEditor: Boolean          = false,
    val showPatternGenerator: Boolean    = false,
    val patternDraft: PatternDraft       = PatternDraft(),
    val patternSyncing: Boolean          = false,
    val selectedZoomItem: ZoomItem?      = null,
    val photoRefreshTick: Int            = 0,
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel for [CalendarScreen].
 *
 * Mirrors the `@AppStorage` bindings and business logic of `CalendarView.swift`:
 * - Reads/writes profile-scoped `dayNotesData_{profile}` and `bookedHolidaysData_{profile}`
 *   from DataStore.
 * - Manages the selected date and calendar view mode (Day / Week / Month).
 * - Provides note CRUD: add shift type, save personal note, delete duty lines, delete full note.
 * - Implements the pattern generator (equivalent of `RestDayPatternView`).
 * - Photo file-path helpers (Phase 10 will add actual camera/gallery capture flows).
 *
 * @property uiState Reactive state consumed by [CalendarScreen].
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val app: Application,
    private val repo: PreferencesRepository,
    private val photoManager: PhotoManager,
    private val pdfManager: PDFManager,
) : AndroidViewModel(app) {

    private val store = app.calendarStore

    // ── Local mutable state ───────────────────────────────────────────────

    private val _selectedDate         = MutableStateFlow(LocalDate.now())
    private val _viewMode             = MutableStateFlow(CalendarViewMode.MONTH)
    private val _showNoteEditor       = MutableStateFlow(false)
    private val _showPatternGenerator = MutableStateFlow(false)
    private val _patternDraft         = MutableStateFlow(PatternDraft())
    private val _patternSyncing       = MutableStateFlow(false)
    private val _selectedZoomItem     = MutableStateFlow<ZoomItem?>(null)
    private val _photoRefreshTick     = MutableStateFlow(0)

    // ── Profile-scoped DataStore flows ────────────────────────────────────

    private val notesJsonFlow: Flow<String> = repo.activeProfile.flatMapLatest { profile ->
        store.data.map { it[stringPreferencesKey(PrefsKeys.notes(profile))] ?: "" }
    }

    private val holidaysJsonFlow: Flow<String> = repo.activeProfile.flatMapLatest { profile ->
        store.data.map { it[stringPreferencesKey(PrefsKeys.holidays(profile))] ?: "" }
    }

    private val holidaysFlow: Flow<List<Holiday>> = holidaysJsonFlow.map { json ->
        Holiday.decode(json)
    }

    private val notesAndHolidaysFlow: Flow<Pair<String, List<Holiday>>> =
        combine(notesJsonFlow, holidaysFlow) { notes, holidays -> notes to holidays }

    private val startOnMondayFlow: Flow<Boolean> = store.data.map {
        it[booleanPreferencesKey(PrefsKeys.START_ON_MONDAY)] ?: true
    }

    // ── Combined UI state ─────────────────────────────────────────────────

    val uiState: StateFlow<CalendarUiState> = combine(
        _selectedDate,
        _viewMode,
        notesAndHolidaysFlow,
        combine(startOnMondayFlow, repo.activeProfile) { startOnMonday, profile ->
            startOnMonday to profile
        },
        combine(_showNoteEditor, _showPatternGenerator, _patternDraft, _patternSyncing,
                _selectedZoomItem, _photoRefreshTick) { arr ->
            // arr: [Boolean, Boolean, PatternDraft, Boolean, ZoomItem?, Int]
            @Suppress("UNCHECKED_CAST")
            Sextet(
                arr[0] as Boolean,
                arr[1] as Boolean,
                arr[2] as PatternDraft,
                arr[3] as Boolean,
                arr[4] as ZoomItem?,
                arr[5] as Int,
            )
        },
    ) { selectedDate, viewMode, notesAndHolidays, startPair, sex ->
        CalendarUiState(
            selectedDate          = selectedDate,
            viewMode              = viewMode,
            notesJson             = notesAndHolidays.first,
            holidays              = notesAndHolidays.second,
            startOnMonday         = startPair.first,
            activeProfile         = startPair.second,
            showNoteEditor        = sex.first,
            showPatternGenerator  = sex.second,
            patternDraft          = sex.third,
            patternSyncing        = sex.fourth,
            selectedZoomItem      = sex.fifth,
            photoRefreshTick      = sex.sixth,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

    // ── Navigation helpers ────────────────────────────────────────────────

    /** Selects a new date (e.g. tapping a day cell). */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /** Jumps the selected date back to today. */
    fun jumpToToday() {
        _selectedDate.value = LocalDate.now()
    }

    /** Switches between Day, Week, and Month view modes. */
    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
    }

    // ── Note editor sheet ─────────────────────────────────────────────────

    /** Opens the single-day note editor sheet for the currently selected date. */
    fun openNoteEditor() {
        _showNoteEditor.value = true
    }

    /** Closes the note editor sheet without saving (save happens inside the sheet). */
    fun closeNoteEditor() {
        _showNoteEditor.value = false
    }

    // ── Pattern generator sheet ───────────────────────────────────────────

    /** Opens the pattern-generator sheet anchored to [anchorDate]. */
    fun openPatternGenerator(anchorDate: LocalDate = _selectedDate.value) {
        _patternDraft.value = PatternDraft(anchorDate = anchorDate)
        _showPatternGenerator.value = true
    }

    /** Closes the pattern-generator sheet. */
    fun closePatternGenerator() {
        _showPatternGenerator.value = false
    }

    /** Updates the pattern-generator form draft (called on every field change). */
    fun updatePatternDraft(draft: PatternDraft) {
        _patternDraft.value = draft
    }

    // ── Quick-assign shift ────────────────────────────────────────────────

    /**
     * Assigns [shiftType] to the [date]'s note, replacing any existing shift keyword
     * at the start of the note. Passing `"CLEAR"` removes the shift keyword entirely.
     *
     * Mirrors `QuickShiftGrid.addShiftNote(type:)` in CalendarView.swift.
     */
    fun assignShift(date: LocalDate, shiftType: String) = viewModelScope.launch {
        val profile = repo.activeProfile.first()
        val key     = stringPreferencesKey(PrefsKeys.notes(profile))
        val json    = store.data.first()[key] ?: ""
        val notes   = decodeNotesMap(json)
        val noteKey = date.toNoteKey()

        val existing = notes[noteKey] ?: ""
        val lines    = existing.lines().toMutableList()

        val firstLine = lines.firstOrNull()
        if (firstLine != null && CALENDAR_SHIFT_TYPES.any { it == firstLine }) {
            if (shiftType == "CLEAR") lines.removeAt(0)
            else lines[0] = shiftType
        } else if (shiftType != "CLEAR") {
            lines.add(0, shiftType)
        }

        val updated = lines.joinToString("\n").trim()
        notes[noteKey] = updated.ifEmpty { null } ?: run { notes.remove(noteKey); "" }
        if (updated.isEmpty()) notes.remove(noteKey)

        store.edit { it[key] = encodeNotesMap(notes) }
    }

    // ── Personal note save ────────────────────────────────────────────────

    /**
     * Saves the personal (user-typed) note for [date].
     *
     * Preserves any existing OCR/scanner lines (Duty, Sign On, Sign Off) and shift
     * keywords — only the personal text portion is replaced.
     *
     * Mirrors `SingleDayNoteSheet.saveNote()` in CalendarView.swift.
     *
     * @param date       The date whose note is being saved.
     * @param personalText  The new personal text (may be blank to clear it).
     */
    fun savePersonalNote(date: LocalDate, personalText: String) = viewModelScope.launch {
        val profile = repo.activeProfile.first()
        val key     = stringPreferencesKey(PrefsKeys.notes(profile))
        val json    = store.data.first()[key] ?: ""
        val notes   = decodeNotesMap(json)
        val noteKey = date.toNoteKey()

        val existing = notes[noteKey] ?: ""

        // Preserve OCR/scanner lines and shift keywords
        val scannerLines = existing.lines().filter { isScannerLine(it) }

        val newLines = personalText.lines().toMutableList()
        newLines.addAll(scannerLines)

        val updated = newLines.joinToString("\n").trim()
        if (updated.isEmpty()) notes.remove(noteKey)
        else notes[noteKey] = updated

        store.edit { it[key] = encodeNotesMap(notes) }
    }

    // ── Delete duty (scanner lines only) ─────────────────────────────────

    /**
     * Removes only the OCR-scanned duty/sign-on/sign-off lines from [date]'s note.
     *
     * Preserves personal text and shift-keyword lines.
     *
     * Mirrors `DetailArea.deleteDutyDataOnly()` in CalendarView.swift.
     */
    fun deleteDutyData(date: LocalDate) = viewModelScope.launch {
        val profile = repo.activeProfile.first()
        val key     = stringPreferencesKey(PrefsKeys.notes(profile))
        val json    = store.data.first()[key] ?: ""
        val notes   = decodeNotesMap(json)
        val noteKey = date.toNoteKey()

        val existing = notes[noteKey] ?: return@launch
        val cleaned  = existing.lines()
            .filterNot { isScannerLine(it) }
            .joinToString("\n")
            .trim()

        if (cleaned.isEmpty()) notes.remove(noteKey)
        else notes[noteKey] = cleaned

        store.edit { it[key] = encodeNotesMap(notes) }
    }

    // ── Delete personal note (preserve shift keywords + OCR lines) ────────

    /**
     * Deletes the personal note text for [date], preserving shift keywords and
     * OCR scanner lines.
     *
     * Mirrors `SingleDayNoteSheet.deleteNote()` in CalendarView.swift.
     */
    fun deletePersonalNote(date: LocalDate) = viewModelScope.launch {
        val profile = repo.activeProfile.first()
        val key     = stringPreferencesKey(PrefsKeys.notes(profile))
        val json    = store.data.first()[key] ?: ""
        val notes   = decodeNotesMap(json)
        val noteKey = date.toNoteKey()

        val existing = notes[noteKey] ?: return@launch
        val remaining = existing.lines().filter { line ->
            val trimmed = line.trim()
            isScannerLine(trimmed) || CALENDAR_SHIFT_TYPES.any { it == trimmed }
        }.joinToString("\n").trim()

        if (remaining.isEmpty()) notes.remove(noteKey)
        else notes[noteKey] = remaining

        store.edit { it[key] = encodeNotesMap(notes) }
    }

    // ── Pattern generator ─────────────────────────────────────────────────

    /**
     * Reads existing shift keywords in the [PatternDraft.cycleLength]-day window
     * anchored at [PatternDraft.anchorDate], then applies the detected pattern forward
     * for [PatternDraft.weeksToGenerate] weeks.
     *
     * Mirrors `RestDayPatternView.pullAndApply()` in CalendarView.swift.
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun applyPattern() = viewModelScope.launch {
        _patternSyncing.value = true
        val draft   = _patternDraft.value
        val profile = repo.activeProfile.first()
        val key     = stringPreferencesKey(PrefsKeys.notes(profile))
        val json    = store.data.first()[key] ?: ""
        val notes   = decodeNotesMap(json)

        // Build pattern map from existing 175-day window
        val patternMap = mutableMapOf<Int, String>()
        for (i in 0 until 175) {
            val date    = draft.anchorDate.plusDays(i.toLong())
            val noteKey = date.toNoteKey()
            val note    = notes[noteKey] ?: continue
            val shift   = CALENDAR_SHIFT_TYPES.firstOrNull { note.contains(it) } ?: continue
            patternMap.getOrPut(i % draft.cycleLength) { shift }
        }

        if (patternMap.isEmpty()) {
            _patternSyncing.value = false
            return@launch
        }

        // Apply pattern forward
        val endDate = draft.anchorDate.plusDays((draft.weeksToGenerate * 7).toLong())
        var current = draft.anchorDate
        while (!current.isAfter(endDate)) {
            val diff  = java.time.temporal.ChronoUnit.DAYS.between(draft.anchorDate, current).toInt()
            val shift = patternMap[diff % draft.cycleLength]
            if (shift == null) { current = current.plusDays(1); continue }
            val noteKey = current.toNoteKey()
            val existing = notes[noteKey] ?: ""
            if (!existing.contains(shift)) {
                notes[noteKey] = if (existing.isEmpty()) shift else "$shift\n$existing"
            }
            current = current.plusDays(1)
        }

        store.edit { it[key] = encodeNotesMap(notes) }
        _patternSyncing.value = false
        _showPatternGenerator.value = false
    }

    /**
     * Resets shift keywords within the pattern-generator's configured date range.
     *
     * Only removes keywords that were in scope (Early, Middle, Late, Night, OFF).
     *
     * Mirrors `RestDayPatternView.resetGeneratedPattern()` in CalendarView.swift.
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun resetPattern() = viewModelScope.launch {
        val draft   = _patternDraft.value
        val profile = repo.activeProfile.first()
        val key     = stringPreferencesKey(PrefsKeys.notes(profile))
        val json    = store.data.first()[key] ?: ""
        val notes   = decodeNotesMap(json)

        val resetKeywords = listOf("Early", "Middle", "Late", "Night", "OFF")
        val endDate = draft.anchorDate.plusDays((draft.weeksToGenerate * 7).toLong())
        var current = draft.anchorDate
        while (!current.isAfter(endDate)) {
            val noteKey  = current.toNoteKey()
            val existing = notes[noteKey]
            if (existing == null) { current = current.plusDays(1); continue }
            val cleaned  = existing.lines()
                .filterNot { line -> resetKeywords.any { line.trim().contains(it) } }
                .joinToString("\n")
                .trim()
            if (cleaned.isEmpty()) notes.remove(noteKey) else notes[noteKey] = cleaned
            current = current.plusDays(1)
        }

        store.edit { it[key] = encodeNotesMap(notes) }
        _showPatternGenerator.value = false
    }

    // ── Photo file path helper ────────────────────────────────────────────

    /**
     * Returns the [File] path for a photo slot associated with [date] and slot [index] (0–2).
     *
     * Mirrors `CalendarView.getImagePath(for:index:)` in CalendarView.swift.
     * File name pattern: `photo_{profile}_{epochSeconds}_{index}.jpg`
     *
     * @param date    Calendar day the photo belongs to.
     * @param index   Slot index: 0 = roster scan (triggers OCR), 1 = PDF render, 2 = manual.
     */
    fun getPhotoFile(date: LocalDate, index: Int): File {
        val profile = uiState.value.activeProfile
        return photoManager.getPhotoFile(profile, date, index)
    }

    // ── Photo zoom viewer ─────────────────────────────────────────────────

    /**
     * Opens the full-screen zoom viewer for the given [item].
     *
     * Mirrors `selectedZoomItem = ZoomItem(...)` in CalendarView.swift.
     */
    fun openZoomView(item: ZoomItem) {
        _selectedZoomItem.value = item
    }

    /** Closes the full-screen zoom viewer. */
    fun closeZoomView() {
        _selectedZoomItem.value = null
    }

    // ── Photo save + OCR pipeline ─────────────────────────────────────────

    /**
     * Saves [bitmap] to [date]/[index], then (if index == 0) triggers OCR and
     * auto-renders the duty PDF page to slot 1.
     *
     * Mirrors `AddPhotoButton.saveToDisk(_:)` in CalendarView.swift, including
     * the `DutyScanner.scanImage` → `PDFManager.renderPDFPage` chain.
     *
     * @param date    Calendar day for the photo.
     * @param index   Slot index (0 triggers OCR).
     * @param bitmap  The captured/picked photo bitmap.
     */
    fun savePhotoAndScan(date: LocalDate, index: Int, bitmap: android.graphics.Bitmap) =
        viewModelScope.launch {
            val profile = repo.activeProfile.first()
            photoManager.saveBitmap(profile, date, index, bitmap)

            if (index == 0) {
                // Run OCR (mirrors DutyScanner.scanImage in iOS)
                val scanResult = DutyScanner.scanImage(bitmap)

                // Append scanned text to notes
                if (scanResult.scannedText.isNotBlank()) {
                    appendScannedTextToNotes(date, scanResult.scannedText)
                }

                // Auto-render the matching PDF page to slot 1
                if (scanResult.dutyNumber != null) {
                    val pdfBitmap = pdfManager.renderPDFPage(scanResult.dutyNumber, date)
                    if (pdfBitmap != null) {
                        photoManager.saveBitmap(profile, date, 1, pdfBitmap)
                    }
                }
            }

            _photoRefreshTick.value++
        }

    /**
     * Saves raw JPEG [bytes] to [date]/[index] and triggers the OCR pipeline if needed.
     *
     * Used by gallery/file-picker handlers that supply raw bytes directly.
     */
    fun savePhotoBytesAndScan(date: LocalDate, index: Int, bytes: ByteArray) =
        viewModelScope.launch {
            val profile = repo.activeProfile.first()
            photoManager.savePhotoBytes(profile, date, index, bytes)

            if (index == 0) {
                // Decode for OCR
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    val scanResult = DutyScanner.scanImage(bitmap)
                    if (scanResult.scannedText.isNotBlank()) {
                        appendScannedTextToNotes(date, scanResult.scannedText)
                    }
                    if (scanResult.dutyNumber != null) {
                        val pdfBitmap = pdfManager.renderPDFPage(scanResult.dutyNumber, date)
                        if (pdfBitmap != null) {
                            val profile2 = repo.activeProfile.first()
                            photoManager.saveBitmap(profile2, date, 1, pdfBitmap)
                        }
                    }
                }
            }
            _photoRefreshTick.value++
        }

    /**
     * Saves the PDF-rendered duty page for [dutyNumber] directly to slot 2 (index 2).
     *
     * Mirrors the "Render to Slot 3" action in `AddPhotoButton.showingJumpAlert` in iOS.
     */
    fun renderDutyToSlot3(dutyNumber: String, date: LocalDate) = viewModelScope.launch {
        val pdfBitmap = pdfManager.renderPDFPage(dutyNumber, date) ?: return@launch
        val profile   = repo.activeProfile.first()
        photoManager.saveBitmap(profile, date, 2, pdfBitmap)
        _photoRefreshTick.value++
    }

    // ── Photo delete ──────────────────────────────────────────────────────

    /**
     * Deletes the photo for [date]/[index] and, if slot 0 is deleted, removes the
     * OCR-sourced duty/sign-on/sign-off lines from the day's note.
     *
     * Mirrors `CalendarView.deleteImageForDate(date:index:)` in CalendarView.swift.
     */
    fun deletePhoto(date: LocalDate, index: Int) = viewModelScope.launch {
        val profile = repo.activeProfile.first()
        photoManager.deletePhoto(profile, date, index)

        // If slot 0 deleted, wipe OCR lines from notes (mirrors iOS behaviour)
        if (index == 0) {
            deleteDutyData(date)
        }

        _selectedZoomItem.value = null
        _photoRefreshTick.value++
    }

    // ── Internal: append scanned text to day note ─────────────────────────

    /**
     * Appends OCR [scannedText] to the day note for [date], replacing any previous
     * Duty/Sign-On/Sign-Off lines.
     *
     * Mirrors `AddPhotoButton.appendScannedTextToNotes(_:)` in CalendarView.swift.
     */
    private suspend fun appendScannedTextToNotes(date: LocalDate, scannedText: String) {
        val profile = repo.activeProfile.first()
        val key     = stringPreferencesKey(PrefsKeys.notes(profile))
        val json    = store.data.first()[key] ?: ""
        val notes   = decodeNotesMap(json)
        val noteKey = date.toNoteKey()

        val existing = notes[noteKey] ?: ""
        val filtered = existing.lines()
            .filterNot { isScannerLine(it) }
            .joinToString("\n")
            .trim()

        val updated = if (filtered.isEmpty()) scannedText
                      else "$scannedText\n$filtered"
        notes[noteKey] = updated

        store.edit { it[key] = encodeNotesMap(notes) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Utility: 4-element tuple (Kotlin has Pair and Triple but not larger)
// ─────────────────────────────────────────────────────────────────────────────

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private data class Sextet<A, B, C, D, E, F>(
    val first: A, val second: B, val third: C,
    val fourth: D, val fifth: E, val sixth: F,
)


