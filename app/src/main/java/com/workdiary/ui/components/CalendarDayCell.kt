package com.workdiary.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Shift type keywords used in note strings to identify the type of duty assigned to a day.
 * These map directly to the iOS app's keyword set in CalendarDayCell..swift.
 */
val SHIFT_KEYWORDS = listOf(
    "Early", "Middle", "Late", "Night", "OFF", "Day",
    "Personal", "Holiday", "Training", "Standby", "Sickness"
)

/** Scanner/OCR prefixes that should not be counted as personal notes. */
private val SCANNER_PREFIXES = listOf("Sign", "Duty:", "On:", "Off:", "SignOn:", "SignOff:")

/**
 * Returns the background colour for a calendar day cell based on the shift keyword
 * found in the note string. Falls back to [MaterialTheme.colorScheme.surfaceVariant] if no keyword matches.
 */
fun getCalendarDutyCellColor(note: String): Color {
    return when {
        note.contains("Early", ignoreCase = true) -> Color(0xFFFFB300)
        note.contains("Middle", ignoreCase = true) -> Color(0xFFFF6D00)
        note.contains("Late", ignoreCase = true) -> Color(0xFF1565C0)
        note.contains("Night", ignoreCase = true) -> Color(0xFF4527A0)
        note.contains("OFF", ignoreCase = true) -> Color(0xFF2E7D32)
        note.contains("Day", ignoreCase = true) -> Color(0xFF00695C)
        note.contains("Training", ignoreCase = true) -> Color(0xFF0097A7)
        note.contains("Standby", ignoreCase = true) -> Color(0xFFAD1457)
        note.contains("Sickness", ignoreCase = true) -> Color(0xFFC62828)
        note.contains("Holiday", ignoreCase = true)
                || note.contains("Annual Leave", ignoreCase = true)
                || note.contains("Allocated", ignoreCase = true)
                || note.contains("Personal", ignoreCase = true) -> Color(0xFF1B5E20)
        else -> Color.Transparent // caller should fall back to MaterialTheme
    }
}

/**
 * Returns true if the note contains any user-written content beyond known shift keywords
 * and OCR/scanner lines. Used to decide whether to show the 📝 note indicator.
 */
fun hasPersonalNote(note: String): Boolean {
    if (note.isBlank()) return false
    val lines = note.lines()
    val meaningful = lines.filter { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@filter false
        if (SHIFT_KEYWORDS.any { trimmed.equals(it, ignoreCase = true) }) return@filter false
        if (SCANNER_PREFIXES.any { trimmed.startsWith(it, ignoreCase = true) }) return@filter false
        true
    }
    return meaningful.isNotEmpty()
}

/**
 * Returns the emoji that represents a booked holiday's leave type.
 * Matches the iOS emoji mapping from CalendarManager.swift.
 */
fun getHolidayEmoji(type: String): String = when {
    type.contains("Annual", ignoreCase = true) || type.contains("Allocated", ignoreCase = true) -> "🏖️"
    type.contains("Sick", ignoreCase = true) -> "🤒"
    type.contains("Personal", ignoreCase = true) -> "🏡"
    else -> "📅"
}

/**
 * Placeholder: returns true if the given date is a UK bank holiday.
 * Will be replaced by the full UK holiday calculator in Phase 9.
 *
 * @param date The date to check.
 * @param holidays List of booked holidays to check for bank-holiday type entries.
 */
fun isUKBankHoliday(date: LocalDate, holidays: List<Holiday>): Boolean {
    return holidays.any { h ->
        h.type.contains("Bank", ignoreCase = true) && h.containsDate(date)
    }
}

/**
 * A single day cell for the month calendar grid.
 *
 * Displays the day number with colour-coded background based on the assigned shift type,
 * a priority-ordered indicator icon at the bottom, and a selection border.
 *
 * Visual priority for indicator (highest wins):
 * 1. 📝 Personal note icon — user has typed content beyond shift keywords
 * 2. Leave emoji — day falls within a booked holiday range
 * 3. Red dot — UK bank holiday
 *
 * @param date The calendar date this cell represents.
 * @param noteText Raw note string stored for this day (may contain shift keywords + personal content).
 * @param isSelected Whether this cell is currently selected (shows blue border).
 * @param isToday Whether this cell represents today's date (blue day number).
 * @param holidays Full list of booked holidays for the active profile (used for leave and bank holiday detection).
 * @param onTap Callback invoked when the cell is tapped.
 */
@Composable
fun CalendarDayCell(
    date: LocalDate,
    noteText: String,
    isSelected: Boolean,
    isToday: Boolean,
    holidays: List<Holiday>,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cellColor = getCalendarDutyCellColor(noteText)
    val isBankHoliday = isUKBankHoliday(date, holidays)

    val dayNumberColor = when {
        isToday -> Color(0xFF1976D2)
        isBankHoliday -> Color(0xFFC62828)
        else -> Color.White.takeIf { cellColor != Color.Transparent } ?: Color.Unspecified
    }

    // Find the first booked holiday that covers this date
    val matchingHoliday = holidays.firstOrNull { h ->
        !h.type.contains("Bank", ignoreCase = true) && h.containsDate(date)
    }

    val personalNote = hasPersonalNote(noteText)

    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .then(
                if (cellColor != Color.Transparent)
                    Modifier.background(cellColor)
                else
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            )
            .then(
                if (isSelected)
                    Modifier // border handled below
                else
                    Modifier
            )
            .clickable { onTap() }
            .padding(2.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Selection border overlay
        if (isSelected) {
            androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                drawRoundRect(
                    color = Color(0xFF1976D2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Day number
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium.copy(
                color = dayNumberColor,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
        )

        // Bottom indicator (priority ordered)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 3.dp)
        ) {
            when {
                personalNote -> Text(text = "📝", fontSize = 10.sp)
                matchingHoliday != null -> Text(
                    text = getHolidayEmoji(matchingHoliday.type),
                    fontSize = 10.sp
                )
                isBankHoliday -> Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFC62828))
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Data class (inline copy for this file — project-wide Holiday will replace this)
// ---------------------------------------------------------------------------

data class Holiday(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val amount: Double,
    val date: LocalDate,
    val endDate: LocalDate? = null,
    val type: String
) {
    val actualEndDate: LocalDate get() = endDate ?: date

    /** Returns true if [d] falls within this holiday's date range (inclusive). */
    fun containsDate(d: LocalDate): Boolean =
        !d.isBefore(date) && !d.isAfter(actualEndDate)
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 48, heightDp = 48, name = "Today - Late shift")
@Composable
private fun CalendarDayCellPreviewToday() {
    MaterialTheme {
        CalendarDayCell(
            date = LocalDate.now(),
            noteText = "Late\nSign On: 14:00",
            isSelected = false,
            isToday = true,
            holidays = emptyList(),
            onTap = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 48, heightDp = 48, name = "Selected - OFF")
@Composable
private fun CalendarDayCellPreviewSelected() {
    MaterialTheme {
        CalendarDayCell(
            date = LocalDate.now().plusDays(1),
            noteText = "OFF\nBrought bike in for service",
            isSelected = true,
            isToday = false,
            holidays = emptyList(),
            onTap = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 48, heightDp = 48, name = "Holiday emoji")
@Composable
private fun CalendarDayCellPreviewHoliday() {
    MaterialTheme {
        val h = Holiday(name = "Summer hols", amount = 1.0, date = LocalDate.now(), type = "Annual Leave")
        CalendarDayCell(
            date = LocalDate.now(),
            noteText = "",
            isSelected = false,
            isToday = false,
            holidays = listOf(h),
            onTap = {}
        )
    }
}
