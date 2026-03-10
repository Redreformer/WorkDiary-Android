package com.workdiary.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.workdiary.app.data.models.Holiday
import com.workdiary.app.data.models.ZoomItem
import com.workdiary.app.ui.theme.WorkDiaryTheme
import com.workdiary.app.utils.PhotoManager
import com.workdiary.ui.components.CalendarDayCell
import com.workdiary.ui.components.WeekRowCell
import com.workdiary.ui.components.getCalendarDutyCellColor
import com.workdiary.ui.components.SHIFT_KEYWORDS
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
//  Colour helpers  (mirrors the shift → colour table in CalendarDayCell.kt)
// ─────────────────────────────────────────────────────────────────────────────

/** Quick-assign shift definitions for the two pages of the QuickShiftGrid. */
private data class QuickShiftDef(val label: String, val color: Color, val icon: String)

private val PAGE_ONE_SHIFTS = listOf(
    QuickShiftDef("Early",    Color(0xFFFFB300), "☀️"),
    QuickShiftDef("Middle",   Color(0xFFFF6D00), "🌅"),
    QuickShiftDef("Late",     Color(0xFF1565C0), "🌙"),
    QuickShiftDef("Night",    Color(0xFF4527A0), "🌃"),
    QuickShiftDef("OFF",      Color(0xFF2E7D32), "🏠"),
    QuickShiftDef("CLEAR",    Color(0xFF757575), "✕"),
)

private val PAGE_TWO_SHIFTS = listOf(
    QuickShiftDef("Day",      Color(0xFF00695C), "🌄"),
    QuickShiftDef("Personal", Color(0xFFAD1457), "👤"),
    QuickShiftDef("Holiday",  Color(0xFF1B5E20), "✈️"),
    QuickShiftDef("Training", Color(0xFF0097A7), "📚"),
    QuickShiftDef("Standby",  Color(0xFF5D4037), "⏰"),
    QuickShiftDef("Sickness", Color(0xFFC62828), "🏥"),
)

// ─────────────────────────────────────────────────────────────────────────────
//  Root screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Main calendar screen — the primary tab in WorkDiary Android.
 *
 * Mirrors `CalendarView.swift`, providing three swipeable calendar display modes:
 * - **Month** — full grid with [CalendarDayCell] composables (Phase 8).
 * - **Week** — 7-row vertical list with [WeekRowCell] composables (Phase 8).
 * - **Day** — single-day view with 3 photo placeholder slots.
 *
 * Below the calendar widget: a scrollable area with [DetailArea] (selected day summary)
 * and [QuickShiftGrid] (quick-assign shift buttons).
 *
 * Sheets:
 * - Tapping the detail area opens [NoteEditorSheet].
 * - The wand button opens [PatternGeneratorSheet].
 *
 * Photo/OCR hooks are marked with `// TODO Phase 10` throughout.
 *
 * @param viewModel  Hilt-injected [CalendarViewModel]; provided automatically.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state   by viewModel.uiState.collectAsState()
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()

    // ── Full-screen zoom viewer (mirrors fullScreenCover in iOS) ───────────
    state.selectedZoomItem?.let { zoomItem ->
        val photoManager = remember {
            // Resolve PhotoManager from Hilt's EntryPoint since we're in a composable scope
            com.workdiary.app.utils.PhotoManagerProvider.get(context)
        }
        var allBitmaps by remember(zoomItem, state.photoRefreshTick) {
            mutableStateOf(listOf<Bitmap?>(null, null, null))
        }
        LaunchedEffect(zoomItem, state.photoRefreshTick) {
            val date = zoomItem.date.toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            allBitmaps = (0..2).map { i ->
                photoManager.loadPhoto(state.activeProfile, date, i)
            }
        }
        ZoomImageScreen(
            item       = zoomItem,
            allBitmaps = allBitmaps,
            onDelete   = { slotIndex ->
                val date = zoomItem.date.toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                viewModel.deletePhoto(date, slotIndex)
            },
            onDismiss  = { viewModel.closeZoomView() },
        )
    }

    // ── Sheets ─────────────────────────────────────────────────────────────
    if (state.showNoteEditor) {
        NoteEditorSheet(
            date         = state.selectedDate,
            notesJson    = state.notesJson,
            onSave       = { text -> viewModel.savePersonalNote(state.selectedDate, text) },
            onDeleteNote = { viewModel.deletePersonalNote(state.selectedDate) },
            onAssignShift = { shiftType -> viewModel.assignShift(state.selectedDate, shiftType) },
            onDismiss    = { viewModel.closeNoteEditor() },
        )
    }

    if (state.showPatternGenerator) {
        PatternGeneratorSheet(
            draft      = state.patternDraft,
            syncing    = state.patternSyncing,
            onUpdate   = { viewModel.updatePatternDraft(it) },
            onSync     = { viewModel.applyPattern() },
            onReset    = { viewModel.resetPattern() },
            onDismiss  = { viewModel.closePatternGenerator() },
        )
    }

    // ── Screen scaffold ────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // Header: month/year label + Today button + wand button + segmented picker
        CalendarHeader(
            state    = state,
            onToday  = { viewModel.jumpToToday() },
            onWand   = { viewModel.openPatternGenerator(state.selectedDate) },
            onModeChange = { viewModel.setViewMode(it) },
        )

        // Calendar widget (Day / Week / Month)
        when (state.viewMode) {
            CalendarViewMode.MONTH -> MonthView(
                state       = state,
                onDateSelect = { viewModel.selectDate(it) },
                modifier    = Modifier.height(380.dp),
            )
            CalendarViewMode.WEEK  -> WeekView(
                state       = state,
                onDateSelect = { viewModel.selectDate(it) },
                modifier    = Modifier.height(400.dp),
            )
            CalendarViewMode.DAY   -> DayView(
                state       = state,
                onDateSelect = { viewModel.selectDate(it) },
                modifier    = Modifier.height(280.dp),
            )
        }

        // Scrollable lower section: detail + quick-shift grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            DetailArea(
                state           = state,
                onTapToEdit     = { viewModel.openNoteEditor() },
                onDeleteDuty    = { viewModel.deleteDutyData(state.selectedDate) },
            )

            QuickShiftGrid(
                modifier       = Modifier.padding(top = 10.dp, bottom = 30.dp),
                onAssignShift  = { shiftType -> viewModel.assignShift(state.selectedDate, shiftType) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CalendarHeader(
    state: CalendarUiState,
    onToday: () -> Unit,
    onWand: () -> Unit,
    onModeChange: (CalendarViewMode) -> Unit,
) {
    // Show the month/year for the current selected date
    val monthYearLabel = state.selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top row: month label + Today + wand
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text  = monthYearLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )

            // "Today" button
            TextButton(
                onClick  = onToday,
                modifier = Modifier.padding(end = 4.dp),
            ) {
                Text(
                    text  = "Today",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            // Wand / pattern generator button
            IconButton(onClick = onWand) {
                Icon(
                    imageVector = Icons.Filled.AutoFixHigh,
                    contentDescription = "Pattern Generator",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Segmented control: Day / Week / Month
        val modes = CalendarViewMode.entries
        val labels = listOf("Day", "Week", "Month")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.viewMode == mode,
                    onClick  = { onModeChange(mode) },
                    shape    = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                ) {
                    Text(labels[index])
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Month View
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Horizontally-paged month grid.
 *
 * Each page shows one calendar month. The user swipes left/right to move between months.
 * Tapping a cell updates the selected date.
 *
 * Page count covers ±24 months from today (mirrors iOS `ForEach(-24...24, id: \.self)`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthView(
    state: CalendarUiState,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val pageCount  = 49          // -24 to +24 inclusive
    val initialPage = 24         // index of "current month"

    // Determine the page for the selected date
    val selectedPage = remember(state.selectedDate) {
        val diff = ChronoUnit.MONTHS.between(
            YearMonth.now(),
            YearMonth.from(state.selectedDate)
        ).toInt()
        (initialPage + diff).coerceIn(0, pageCount - 1)
    }

    val pagerState = rememberPagerState(initialPage = selectedPage) { pageCount }

    // When pager page changes (user swipes), update selected date to first of that month
    LaunchedEffect(pagerState.currentPage) {
        val monthOffset = pagerState.currentPage - initialPage
        val newMonth    = YearMonth.now().plusMonths(monthOffset.toLong())
        val newDate     = newMonth.atDay(1)
        if (newDate.month != state.selectedDate.month || newDate.year != state.selectedDate.year) {
            onDateSelect(newDate)
        }
    }

    // When selected date changes externally (e.g. "Today"), scroll pager to correct month
    LaunchedEffect(state.selectedDate) {
        val targetPage = (initialPage + ChronoUnit.MONTHS.between(
            YearMonth.now(), YearMonth.from(state.selectedDate)
        ).toInt()).coerceIn(0, pageCount - 1)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    HorizontalPager(
        state    = pagerState,
        modifier = modifier,
    ) { page ->
        val monthOffset = page - initialPage
        val month       = YearMonth.now().plusMonths(monthOffset.toLong())
        CalendarMonthGrid(
            month        = month,
            selectedDate = state.selectedDate,
            holidays     = state.holidays,
            notesJson    = state.notesJson,
            startOnMonday = state.startOnMonday,
            onDateSelect = onDateSelect,
        )
    }
}

@Composable
private fun CalendarMonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    holidays: List<Holiday>,
    notesJson: String,
    startOnMonday: Boolean,
    onDateSelect: (LocalDate) -> Unit,
) {
    val today      = LocalDate.now()
    val dayHeaders = if (startOnMonday)
        listOf("M", "T", "W", "T", "F", "S", "S")
    else
        listOf("S", "M", "T", "W", "T", "F", "S")

    // Build grid: leading nulls + actual days
    val firstOfMonth = month.atDay(1)
    val startDow     = if (startOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val leadingNulls = ((firstOfMonth.dayOfWeek.value - (if (startOnMonday) 1 else 0) + 7) % 7)
    val days: List<LocalDate?> = List(leadingNulls) { null } +
            (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        // Day-of-week header row
        Row(modifier = Modifier.fillMaxWidth()) {
            dayHeaders.forEachIndexed { i, label ->
                val isWeekend = if (startOnMonday) i >= 5 else (i == 0 || i == 6)
                Text(
                    text      = label,
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.labelSmall.copy(
                        color      = if (isWeekend) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns       = GridCells.Fixed(7),
            userScrollEnabled = false,
            verticalArrangement   = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(days) { date ->
                if (date == null) {
                    Spacer(modifier = Modifier.aspectRatio(1f))
                } else {
                    val noteText = getNoteForDate(date, notesJson)
                    CalendarDayCell(
                        date       = date,
                        noteText   = noteText,
                        isSelected = date == selectedDate,
                        isToday    = date == today,
                        holidays   = holidays.map { h ->
                            com.workdiary.ui.components.Holiday(
                                id       = h.id.toString(),
                                name     = h.name,
                                amount   = h.amount,
                                date     = h.date.toLocalDate(),
                                endDate  = h.endDate?.toLocalDate(),
                                type     = h.type,
                            )
                        },
                        onTap      = { onDateSelect(date) },
                        modifier   = Modifier.height(52.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Week View
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Horizontally-paged week list.
 *
 * Each page shows 7 [WeekRowCell]s for the days in that week.
 * Swiping advances or retreats one week.
 *
 * Page count: ±100 weeks from the current week (mirrors iOS `ForEach(-100...100)`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekView(
    state: CalendarUiState,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today       = LocalDate.now()
    val weekStart   = today.with(if (state.startOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY)
    val pageCount   = 201   // -100 to +100
    val initialPage = 100

    val selectedPage = remember(state.selectedDate, state.startOnMonday) {
        val selectedWeekStart = state.selectedDate.with(
            if (state.startOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
        )
        val diff = ChronoUnit.WEEKS.between(weekStart, selectedWeekStart).toInt()
        (initialPage + diff).coerceIn(0, pageCount - 1)
    }

    val pagerState = rememberPagerState(initialPage = selectedPage) { pageCount }

    LaunchedEffect(pagerState.currentPage) {
        val weekOffset = pagerState.currentPage - initialPage
        val newWeekStart = weekStart.plusWeeks(weekOffset.toLong())
        if (!state.selectedDate.let { d ->
                val ws = d.with(if (state.startOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY)
                ws == newWeekStart
            }) {
            onDateSelect(newWeekStart)
        }
    }

    LaunchedEffect(state.selectedDate) {
        val selectedWeekStart = state.selectedDate.with(
            if (state.startOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
        )
        val targetPage = (initialPage + ChronoUnit.WEEKS.between(weekStart, selectedWeekStart).toInt())
            .coerceIn(0, pageCount - 1)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    HorizontalPager(
        state    = pagerState,
        modifier = modifier,
    ) { page ->
        val weekOffset      = page - initialPage
        val thisWeekStart   = weekStart.plusWeeks(weekOffset.toLong())
        val days            = (0 until 7).map { thisWeekStart.plusDays(it.toLong()) }

        Column(modifier = Modifier.fillMaxSize()) {
            days.forEach { date ->
                val noteText = getNoteForDate(date, state.notesJson)
                WeekRowCell(
                    date       = date,
                    noteText   = noteText,
                    isSelected = date == state.selectedDate,
                    isToday    = date == LocalDate.now(),
                    holidays   = state.holidays.map { h ->
                        com.workdiary.ui.components.Holiday(
                            id       = h.id.toString(),
                            name     = h.name,
                            amount   = h.amount,
                            date     = h.date.toLocalDate(),
                            endDate  = h.endDate?.toLocalDate(),
                            type     = h.type,
                        )
                    },
                    onTap      = { onDateSelect(date) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Day View
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Horizontally-paged single-day view.
 *
 * Each page shows the day label and up to 3 photo placeholder slots.
 * Actual camera/gallery/OCR capture is implemented in Phase 10.
 *
 * Page count: ±1000 days from today (mirrors iOS `ForEach(-1000...1000)`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayView(
    state: CalendarUiState,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today     = LocalDate.now()
    val pageCount = 2001   // -1000 to +1000
    val initialPage = 1000

    val selectedPage = remember(state.selectedDate) {
        val diff = ChronoUnit.DAYS.between(today, state.selectedDate).toInt()
        (initialPage + diff).coerceIn(0, pageCount - 1)
    }

    val pagerState = rememberPagerState(initialPage = selectedPage) { pageCount }

    LaunchedEffect(pagerState.currentPage) {
        val dayOffset = pagerState.currentPage - initialPage
        val newDate   = today.plusDays(dayOffset.toLong())
        if (newDate != state.selectedDate) {
            onDateSelect(newDate)
        }
    }

    LaunchedEffect(state.selectedDate) {
        val targetPage = (initialPage + ChronoUnit.DAYS.between(today, state.selectedDate).toInt())
            .coerceIn(0, pageCount - 1)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    HorizontalPager(
        state    = pagerState,
        modifier = modifier,
    ) { page ->
        val dayOffset = page - initialPage
        val date      = today.plusDays(dayOffset.toLong())
        DayPageContent(
            date             = date,
            activeProfile    = state.activeProfile,
            photoRefreshTick = state.photoRefreshTick,
            onPhotoTapped    = { bitmap, index ->
                val legacyDate = Date.from(
                    date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                )
                onDateSelect(date)
                viewModel.openZoomView(ZoomItem(id = UUID.randomUUID(), image = bitmap, date = legacyDate, index = index))
            },
            onPhotoSaved     = { index, bitmap -> viewModel.savePhotoAndScan(date, index, bitmap) },
            onRawBytesSaved  = { index, bytes  -> viewModel.savePhotoBytesAndScan(date, index, bytes) },
            onDutySearch     = { duty           -> viewModel.renderDutyToSlot3(duty, date) },
        )
    }
}

@Composable
private fun DayPageContent(
    date: LocalDate,
    activeProfile: String,
    photoRefreshTick: Int,
    onPhotoTapped: (Bitmap, Int) -> Unit,
    onPhotoSaved: (Int, Bitmap) -> Unit,
    onRawBytesSaved: (Int, ByteArray) -> Unit,
    onDutySearch: (String) -> Unit,
) {
    val context = LocalContext.current

    // Load all three bitmaps from disk (refreshed when photoRefreshTick changes)
    val bitmaps = remember(date, activeProfile, photoRefreshTick) {
        mutableStateListOf<Bitmap?>(null, null, null)
    }
    LaunchedEffect(date, activeProfile, photoRefreshTick) {
        val epochSeconds = date.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
        (0 until 3).forEach { i ->
            val file = java.io.File(
                context.filesDir,
                "photo_${activeProfile}_${epochSeconds}_$i.jpg",
            )
            bitmaps[i] = if (file.exists())
                withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
            else null
        }
    }

    Column(
        modifier            = Modifier.fillMaxSize().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Day header
        Text(
            text  = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            style = MaterialTheme.typography.labelLarge.copy(
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text  = date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Three photo slots
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            for (index in 0 until 3) {
                val bitmap = bitmaps.getOrNull(index)
                if (bitmap != null) {
                    // Photo exists → show thumbnail with tap-to-zoom
                    PhotoThumbnail(
                        bitmap   = bitmap,
                        index    = index,
                        onTap    = { onPhotoTapped(bitmap, index) },
                        modifier = Modifier.weight(1f).aspectRatio(0.75f),
                    )
                } else {
                    // Empty slot → show Add button
                    AddPhotoButton(
                        slotIndex       = index,
                        onPhotoSaved    = { bmp -> onPhotoSaved(index, bmp) },
                        onRawBytesSaved = { bytes -> onRawBytesSaved(index, bytes) },
                        onDutySearch    = onDutySearch,
                        modifier        = Modifier.weight(1f).aspectRatio(0.75f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Photo thumbnail (existing photo)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoThumbnail(
    bitmap: Bitmap,
    index: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onTap),
    ) {
        Image(
            bitmap             = bitmap.asImageBitmap(),
            contentDescription = "Photo slot ${index + 1}",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Add-photo button (empty slot)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Mirrors `AddPhotoButton` in CalendarView.swift.
 *
 * Shows a bottom-sheet action menu with four options:
 * - **Take Photo** — camera capture via `TakePicture` contract
 * - **Choose from Library** — photo picker via `PickVisualMedia`
 * - **Choose from Files** — document picker via `OpenDocument`
 * - **Search Duty Board** — duty-number text input → PDF render to slot 3
 *
 * When the captured/picked image lands on slot 0 it is automatically passed to
 * the [onPhotoSaved] callback, which triggers OCR + PDF auto-render in the ViewModel.
 */
@Composable
private fun AddPhotoButton(
    slotIndex: Int,
    onPhotoSaved: (Bitmap) -> Unit,
    onRawBytesSaved: (ByteArray) -> Unit,
    onDutySearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var showActionSheet by remember { mutableStateOf(false) }
    var showDutyDialog  by remember { mutableStateOf(false) }
    var dutyInput       by remember { mutableStateOf("") }
    var isSearching     by remember { mutableStateOf(false) }

    // ── Camera temp file + launcher ────────────────────────────────────────
    val cameraTempFile = remember {
        val file = java.io.File(context.cacheDir, "camera_slot_${slotIndex}_temp.jpg")
        if (!file.exists()) file.createNewFile()
        file
    }
    val cameraTempUri = remember(cameraTempFile) {
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cameraTempFile,
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            scope.launch(Dispatchers.IO) {
                val bmp = BitmapFactory.decodeFile(cameraTempFile.absolutePath)
                if (bmp != null) withContext(Dispatchers.Main) { onPhotoSaved(bmp) }
            }
        }
    }

    // ── Gallery launcher ───────────────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) withContext(Dispatchers.Main) { onRawBytesSaved(bytes) }
            }
        }
    }

    // ── File picker launcher ───────────────────────────────────────────────
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = try {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (_: Exception) { null }
                if (bytes != null) withContext(Dispatchers.Main) { onRawBytesSaved(bytes) }
            }
        }
    }

    // ── Duty search dialog ─────────────────────────────────────────────────
    if (showDutyDialog) {
        AlertDialog(
            onDismissRequest = { showDutyDialog = false; dutyInput = "" },
            title = { Text("Quick Jump to Duty") },
            text  = {
                Column {
                    Text(
                        "Enter duty number to render from the Duty Board PDF to Slot 3.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value         = dutyInput,
                        onValueChange = { dutyInput = it.filter { c -> c.isDigit() } },
                        label         = { Text("Duty Number (e.g. 1096)") },
                        singleLine    = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dutyInput.isNotBlank()) onDutySearch(dutyInput)
                    showDutyDialog = false
                    dutyInput = ""
                }) {
                    Text("Render to Slot 3")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDutyDialog = false; dutyInput = "" }) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── Action sheet ───────────────────────────────────────────────────────
    if (showActionSheet) {
        ModalBottomSheet(onDismissRequest = { showActionSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    text     = "Add Photo — Slot ${slotIndex + 1}",
                    style    = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider()
                ActionSheetItem("📷  Take Photo") {
                    showActionSheet = false
                    cameraLauncher.launch(cameraTempUri)
                }
                ActionSheetItem("🖼  Choose from Library") {
                    showActionSheet = false
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                ActionSheetItem("📄  Choose from Files") {
                    showActionSheet = false
                    fileLauncher.launch(arrayOf("image/*"))
                }
                ActionSheetItem("🔍  Search Duty Board") {
                    showActionSheet = false
                    showDutyDialog  = true
                }
                ActionSheetItem("Cancel", isCancel = true) {
                    showActionSheet = false
                }
            }
        }
    }

    // ── Button face ────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !isSearching) { showActionSheet = true },
        contentAlignment = Alignment.Center,
    ) {
        if (isSearching) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Searching…",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector        = Icons.Filled.Add,
                    contentDescription = "Add photo slot ${slotIndex + 1}",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(28.dp),
                )
                Text(
                    text  = "Add ${slotIndex + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ActionSheetItem(
    label: String,
    isCancel: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                color      = if (isCancel) MaterialTheme.colorScheme.error
                             else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCancel) FontWeight.Bold else FontWeight.Normal,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Detail area
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Shows the selected day's shift details (duty, sign-on/off), any booked holiday marker,
 * bank holidays / special days, and personal note text.
 *
 * Tapping the whole area opens the note editor.
 *
 * Mirrors `DetailArea` in CalendarView.swift.
 */
@Composable
private fun DetailArea(
    state: CalendarUiState,
    onTapToEdit: () -> Unit,
    onDeleteDuty: () -> Unit,
) {
    val date      = state.selectedDate
    val noteText  = getNoteForDate(date, state.notesJson)
    val details   = extractShiftDetails(noteText)

    // Personal text: note lines that are not OCR scanner lines
    val personalText = noteText.lines()
        .filterNot { isScannerLine(it) }
        .joinToString("\n")
        .trim()

    // Matching holiday (if any)
    val matchingHoliday = state.holidays.firstOrNull { h ->
        val start = h.date.toLocalDate()
        val end   = (h.endDate ?: h.date).toLocalDate()
        !date.isBefore(start) && !date.isAfter(end)
    }

    var showDeleteDutyDialog by remember { mutableStateOf(false) }

    if (showDeleteDutyDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDutyDialog = false },
            title            = { Text("Delete Duty?") },
            text             = { Text("Are you sure you want to delete the scanned duty data for this day?") },
            confirmButton    = {
                TextButton(onClick = {
                    onDeleteDuty()
                    showDeleteDutyDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showDeleteDutyDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

        // Date label + "Tap to edit" hint
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text  = date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                style = MaterialTheme.typography.labelMedium.copy(
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text  = "Tap to edit notes",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main detail card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTapToEdit() },
            shape  = RoundedCornerShape(12.dp),
            color  = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Holiday banner
                if (matchingHoliday != null) {
                    Text(
                        text  = matchingHoliday.type,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color      = Color(0xFFC62828),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    if (matchingHoliday.name.isNotBlank()) {
                        Text(
                            text  = matchingHoliday.name,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE57373)),
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Shift stats row (duty / sign-on / sign-off)
                if (details.duty != null || details.signOn != null || details.signOff != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier             = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            details.signOn?.let  { ShiftStat("SIGN ON", it) }
                            details.signOff?.let { ShiftStat("SIGN OFF", it) }
                            details.duty?.let    { ShiftStat("DUTY", it) }
                        }

                        IconButton(
                            onClick  = { showDeleteDutyDialog = true },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Text(text = "🗑️", fontSize = 16.sp)
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Personal note text
                Text(
                    text  = personalText.ifEmpty { "No notes" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (personalText.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ShiftStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                fontSize   = 9.sp,
            ),
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Quick-shift grid
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Two horizontally-swiped pages of quick-assign shift buttons.
 *
 * Page 1: Early, Middle, Late, Night, OFF, CLEAR
 * Page 2: Day, Personal, Holiday, Training, Standby, Sickness
 *
 * Mirrors `QuickShiftGrid` in CalendarView.swift.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickShiftGrid(
    onAssignShift: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages      = listOf(PAGE_ONE_SHIFTS, PAGE_TWO_SHIFTS)
    val pagerState = rememberPagerState { pages.size }

    Column(modifier = modifier) {
        Text(
            text  = "Swipe for more • Quick Assign",
            style = MaterialTheme.typography.labelSmall.copy(
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            ),
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )

        HorizontalPager(
            state    = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
        ) { page ->
            val shifts = pages[page]
            Column(
                modifier  = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Row 1: shifts 0–2
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    shifts.take(3).forEach { def ->
                        QuickShiftButton(
                            label    = def.label,
                            color    = def.color,
                            icon     = def.icon,
                            onClick  = { onAssignShift(def.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // Row 2: shifts 3–5
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    shifts.drop(3).take(3).forEach { def ->
                        QuickShiftButton(
                            label    = def.label,
                            color    = def.color,
                            icon     = def.icon,
                            onClick  = { onAssignShift(def.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Page indicator dots
        Row(
            modifier              = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            pages.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == i)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        ),
                )
            }
        }
    }
}

@Composable
private fun QuickShiftButton(
    label: String,
    color: Color,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick  = onClick,
        modifier = modifier.height(52.dp),
        shape    = RoundedCornerShape(10.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(4.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = icon, fontSize = 16.sp)
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pattern generator sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bottom-sheet modal for the pattern generator.
 *
 * Mirrors `RestDayPatternView` in CalendarView.swift.
 *
 * Allows configuring:
 * - Anchor date (start of the shift cycle)
 * - Cycle length in days (2–84, e.g. 8 = 4-on/4-off)
 * - Number of weeks to extend the pattern forward (4–104)
 *
 * The "Sync Sequence" button calls [onSync]; "Reset" calls [onReset].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternGeneratorSheet(
    draft: PatternDraft,
    syncing: Boolean,
    onUpdate: (PatternDraft) -> Unit,
    onSync: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title            = { Text("Reset generated pattern?") },
            text             = { Text("This will remove all auto-generated shift keywords within the selected date range.") },
            confirmButton    = {
                TextButton(onClick = {
                    onReset()
                    showResetConfirm = false
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text  = "Auto-Generate",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Anchor date picker label
            Text("Pattern Start Date", style = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            ))
            Text(
                text  = draft.anchorDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            // TODO: add a date-picker dialog trigger if desired

            // Cycle length
            Text(
                text  = "Cycle Length: ${draft.cycleLength} days",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text  = "(8 = 4-on/4-off  |  84 = 12-week roster)",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Slider(
                value         = draft.cycleLength.toFloat(),
                onValueChange = { onUpdate(draft.copy(cycleLength = it.toInt())) },
                valueRange    = 2f..84f,
                steps         = 81,
                modifier      = Modifier.padding(vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Weeks to generate
            Text(
                text  = "Extend for: ${draft.weeksToGenerate} weeks",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value         = draft.weeksToGenerate.toFloat(),
                onValueChange = { onUpdate(draft.copy(weeksToGenerate = it.toInt())) },
                valueRange    = 4f..104f,
                steps         = 99,
                modifier      = Modifier.padding(vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sync button
            Button(
                onClick  = onSync,
                enabled  = !syncing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color    = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("🔄  Sync Sequence", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reset button
            OutlinedButton(
                onClick  = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                border   = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                ),
            ) {
                Text("↺  Reset Generated Pattern", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text  = "Matches your shift keywords (Early, Late, OFF, etc.) to the cycle length and repeats them forward.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helper: Date → LocalDate adaptor for the Holiday model
// ─────────────────────────────────────────────────────────────────────────────

private fun java.util.Date.toLocalDate(): LocalDate =
    toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
