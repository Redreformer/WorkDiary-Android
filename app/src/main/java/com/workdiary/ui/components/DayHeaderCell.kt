package com.workdiary.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A column header cell displaying the abbreviated day-of-week name for the calendar grid.
 *
 * Used as the top row of the month calendar grid. Renders 7 of these in a LazyVerticalGrid
 * row spanning the full width, ordered by [startOnMonday] preference.
 *
 * @param dayName The abbreviated day name to display (e.g. "Mon", "Tue", "Sun").
 */
@Composable
fun DayHeaderCell(
    dayName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, name = "DayHeaderCell row - week starting Monday")
@Composable
private fun DayHeaderCellPreview() {
    MaterialTheme {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                DayHeaderCell(dayName = day, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Preview(showBackground = true, name = "DayHeaderCell row - week starting Sunday")
@Composable
private fun DayHeaderCellSundayFirstPreview() {
    MaterialTheme {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                DayHeaderCell(dayName = day, modifier = Modifier.weight(1f))
            }
        }
    }
}
