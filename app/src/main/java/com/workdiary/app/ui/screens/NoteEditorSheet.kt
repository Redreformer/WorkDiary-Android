package com.workdiary.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.workdiary.app.ui.theme.WorkDiaryTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Full-screen note-editor bottom sheet for a single calendar day.
 *
 * Mirrors `SingleDayNoteSheet` in CalendarView.swift.
 *
 * Allows the user to:
 * - Type personal free-text notes in a [TextField].
 * - Quick-assign shift types via the embedded [QuickShiftGrid].
 * - Delete the personal note text (preserves shift keywords and OCR scanner lines).
 *
 * The editor loads **only** personal text (filters out OCR/scanner lines on open)
 * and saves the personal text back while preserving OCR lines (via [onSave]).
 *
 * @param date          The calendar date being edited.
 * @param notesJson     Current full notes JSON string (used to populate the editor on open).
 * @param onSave        Called with the edited personal text when "Done" is tapped.
 * @param onDeleteNote  Called when the "Delete Note" button is confirmed.
 * @param onAssignShift Called when a quick-shift button is tapped (passes shift keyword).
 * @param onDismiss     Called when the sheet should be dismissed without saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorSheet(
    date: LocalDate,
    notesJson: String,
    onSave: (String) -> Unit,
    onDeleteNote: () -> Unit,
    onAssignShift: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Load personal-text portion of the note on first composition
    val initialPersonalText = remember(date, notesJson) {
        getNoteForDate(date, notesJson)
            .lines()
            .filterNot { isScannerLine(it) }
            .joinToString("\n")
            .trim()
    }

    var text by remember(initialPersonalText) { mutableStateOf(initialPersonalText) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title            = { Text("Delete Note?") },
            text             = { Text("Your typed notes will be removed. Shift type and duty scan data will be preserved.") },
            confirmButton    = {
                TextButton(onClick = {
                    onDeleteNote()
                    showDeleteConfirm = false
                    onDismiss()
                }) {
                    Text("Delete Note", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Sheet title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text     = "Edit Notes — ${date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))}",
                    style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )

                // Done button
                IconButton(
                    onClick = {
                        onSave(text)
                        onDismiss()
                    },
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Check,
                        contentDescription = "Done",
                        tint               = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Divider()

            // Text editor
            TextField(
                value         = text,
                onValueChange = { text = it },
                modifier      = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 240.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder   = { Text("Add personal notes for this day…") },
                shape         = RoundedCornerShape(12.dp),
                colors        = TextFieldDefaults.colors(
                    focusedIndicatorColor   = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick-shift grid (live-updates the underlying note storage, then reloads text)
            QuickShiftGrid(
                onAssignShift = { shiftType ->
                    onAssignShift(shiftType)
                    // After assigning, reload personal text from the updated notes
                    // (The parent recomposes with updated notesJson, which re-derives initialPersonalText)
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Delete note button
            TextButton(
                onClick  = { showDeleteConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                Icon(
                    imageVector        = Icons.Filled.Delete,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text  = "Delete Note",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "NoteEditorSheet preview")
@Composable
private fun NoteEditorSheetPreview() {
    WorkDiaryTheme {
        // Previewing the sheet content (not in a ModalBottomSheet for preview compatibility)
        Column(modifier = Modifier.fillMaxSize()) {
            NoteEditorSheet(
                date          = LocalDate.now(),
                notesJson     = """{"${LocalDate.now()}":"Late\nSign On: 14:00\nSign Off: 22:30\nBrought in my flask"}""",
                onSave        = {},
                onDeleteNote  = {},
                onAssignShift = {},
                onDismiss     = {},
            )
        }
    }
}
