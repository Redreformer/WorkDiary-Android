package com.workdiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
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
 * A full-width row cell for the week-view mode of the calendar.
 *
 * Displays:
 * - Left: day-of-week abbreviation + date number
 * - Center: shift type badge (coloured chip with keyword text)
 * - Right: personal note / holiday / bank holiday indicators
 *
 * @param date The calendar date this row represents.
 * @param noteText Raw note string for this day.
 * @param isSelected Whether this row is currently selected (blue left accent bar).
 * @param isToday Whether this row represents today (bold blue date number).
 * @param holidays Booked holidays for leave/bank-holiday indicator display.
 * @param onTap Callback when the row is tapped.
 */
@Composable
fun WeekRowCell(
    date: LocalDate,
    noteText: String,
    isSelected: Boolean,
    isToday: Boolean,
    holidays: List<Holiday>,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBankHoliday = isUKBankHoliday(date, holidays)
    val matchingHoliday = holidays.firstOrNull { h ->
        !h.type.contains("Bank", ignoreCase = true) && h.containsDate(date)
    }
    val personalNote = hasPersonalNote(noteText)

    val detectedShift = SHIFT_KEYWORDS.firstOrNull { noteText.contains(it, ignoreCase = true) }
    val shiftColor = getCalendarDutyCellColor(noteText)

    val dateColor = when {
        isToday -> Color(0xFF1976D2)
        isBankHoliday -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .then(
                    if (isSelected)
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    else Modifier
                )
                .clickable { onTap() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left accent bar for selection
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        if (isSelected) Color(0xFF1976D2) else Color.Transparent
                    )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Day label + number
            Column(
                modifier = Modifier.width(44.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = dateColor
                    )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Shift badge (center, takes remaining space)
            Box(modifier = Modifier.weight(1f)) {
                if (detectedShift != null && shiftColor != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(shiftColor)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = detectedShift,
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Right indicators
            Row(
                modifier = Modifier.padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (personalNote) Text(text = "📝", fontSize = 14.sp)
                if (matchingHoliday != null) Text(
                    text = getHolidayEmoji(matchingHoliday.type),
                    fontSize = 14.sp
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

@Preview(showBackground = true, name = "WeekRowCell - Late shift, selected")
@Composable
private fun WeekRowCellPreview() {
    MaterialTheme {
        Column {
            WeekRowCell(
                date = LocalDate.now(),
                noteText = "Late\nSign On: 14:00",
                isSelected = true,
                isToday = true,
                holidays = emptyList(),
                onTap = {}
            )
            WeekRowCell(
                date = LocalDate.now().plusDays(1),
                noteText = "OFF\nGym session booked",
                isSelected = false,
                isToday = false,
                holidays = emptyList(),
                onTap = {}
            )
            WeekRowCell(
                date = LocalDate.now().plusDays(2),
                noteText = "Early",
                isSelected = false,
                isToday = false,
                holidays = listOf(
                    Holiday("1", "Holiday", 1.0, LocalDate.now().plusDays(2), type = "Annual Leave")
                ),
                onTap = {}
            )
        }
    }
}
