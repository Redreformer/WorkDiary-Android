package com.workdiary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workdiary.app.data.models.Holiday
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
//  Date format helper — shared across composables in this file
// ─────────────────────────────────────────────────────────────────────────────

private val shortDateFmt = SimpleDateFormat("d MMM", Locale.getDefault())

// ─────────────────────────────────────────────────────────────────────────────
//  HolidaysScreen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Holidays screen — the "Holidays" tab.
 *
 * Direct Android equivalent of `BookedView.swift`. Displays a searchable,
 * sortable list of booked leave entries with swipe-to-delete and an
 * add/edit bottom sheet.
 *
 * Features:
 * - Lists leave entries whose type is one of [HOLIDAY_CATEGORIES].
 * - Search by name or leave type.
 * - Sort toggle: newest-first (default) or oldest-first.
 * - Swipe to delete with red background confirmation.
 * - Tap any row to edit; FAB to add new.
 * - Amount displayed as `Xh` (hours mode) or `Xd` (days mode).
 * - Date range displayed as `d MMM – d MMM`.
 * - Leave type emoji: 🏖️ Annual Leave, 📅 Allocated, 🏡 Personal.
 *
 * @param viewModel Injected [HolidaysViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaysScreen(
    viewModel: HolidaysViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            HolidaysTopBar(
                sortAscending = uiState.sortAscending,
                onToggleSort  = viewModel::toggleSortOrder,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::prepareForNew) {
                Icon(Icons.Default.Add, contentDescription = "Add holiday")
            }
        },
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {

            // ── Search bar ────────────────────────────────────────────────
            HolidaysSearchBar(
                query    = uiState.searchQuery,
                onChange = viewModel::onSearchQueryChange,
            )

            // ── Holiday list ──────────────────────────────────────────────
            if (uiState.holidays.isEmpty()) {
                HolidaysEmptyState(isHoursMode = uiState.isHoursMode)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = uiState.holidays,
                        key   = { it.id.toString() },
                    ) { holiday ->
                        HolidayRow(
                            holiday     = holiday,
                            isHoursMode = uiState.isHoursMode,
                            onTap       = { viewModel.prepareToEdit(holiday) },
                            onDelete    = { viewModel.deleteHoliday(holiday) },
                        )
                    }
                }
            }
        }

        // ── Add / Edit bottom sheet ───────────────────────────────────────
        val sheetState = uiState.sheetState
        if (sheetState is HolidaySheetState.AddEdit) {
            AddEditHolidaySheet(
                draft       = sheetState.draft,
                isHoursMode = uiState.isHoursMode,
                isEditing   = uiState.editingEntry != null,
                onDraftChange = viewModel::updateDraft,
                onSave      = viewModel::saveEntry,
                onDismiss   = viewModel::dismissSheet,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Top app bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HolidaysTopBar(
    sortAscending: Boolean,
    onToggleSort: () -> Unit,
) {
    TopAppBar(
        title = { Text("Holidays", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        actions = {
            IconButton(onClick = onToggleSort) {
                // Material Icons doesn't ship a sort-direction icon; use a bold text arrow
                Text(
                    text       = if (sortAscending) "↑" else "↓",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Search bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HolidaysSearchBar(
    query: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value         = query,
        onValueChange = onChange,
        modifier      = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder   = { Text("Search holidays…") },
        singleLine    = true,
        shape         = RoundedCornerShape(12.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HolidaysEmptyState(isHoursMode: Boolean) {
    Box(
        modifier          = Modifier.fillMaxSize(),
        contentAlignment  = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏖️", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text  = "No holidays booked yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Tap + to add ${if (isHoursMode) "hours" else "days"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Holiday row — swipe-to-delete + tap to edit
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HolidayRow(
    holiday: Holiday,
    isHoursMode: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
    )

    SwipeToDismissBox(
        state              = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent  = {
            Box(
                modifier          = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment  = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector       = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint              = MaterialTheme.colorScheme.onErrorContainer,
                    modifier          = Modifier.padding(end = 24.dp),
                )
            }
        },
    ) {
        HolidayCard(
            holiday     = holiday,
            isHoursMode = isHoursMode,
            onClick     = onTap,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Holiday card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HolidayCard(
    holiday: Holiday,
    isHoursMode: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Emoji badge ───────────────────────────────────────────────
            Text(
                text     = emojiForType(holiday.type),
                fontSize = 30.sp,
            )

            // ── Name + date range ─────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = holiday.name,
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = "${shortDateFmt.format(holiday.date)} – ${shortDateFmt.format(holiday.actualEndDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // ── Amount ────────────────────────────────────────────────────
            Text(
                text       = "${"%.1f".format(holiday.amount)}${if (isHoursMode) "h" else "d"}",
                fontWeight = FontWeight.Black,
                style      = MaterialTheme.typography.bodyLarge,
                color      = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Emoji mapping — mirrors `BookedView.emoji(for:)`
// ─────────────────────────────────────────────────────────────────────────────

private fun emojiForType(type: String): String = when (type) {
    "Annual Leave" -> "🏖️"
    "Allocated"    -> "📅"
    "Personal"     -> "🏡"
    else           -> "📝"
}

// ─────────────────────────────────────────────────────────────────────────────
//  Add / Edit bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Modal bottom sheet for adding or editing a holiday entry.
 *
 * Mirrors the `Form` sheet inside `BookedView` with fields for:
 * description, amount (days/hours), leave type, start date, end date.
 *
 * End date automatically advances to match start date if start is set later.
 *
 * @param draft           Current form state.
 * @param isHoursMode     Determines label (`Total Hours` vs `Total Days`).
 * @param isEditing       `true` when editing an existing entry.
 * @param onDraftChange   Called on every field change.
 * @param onSave          Called when the user confirms.
 * @param onDismiss       Called when the sheet is dismissed without saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditHolidaySheet(
    draft: HolidayDraft,
    isHoursMode: Boolean,
    isEditing: Boolean,
    onDraftChange: (HolidayDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope      = rememberCoroutineScope()

    // Date picker dialog control
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker   by remember { mutableStateOf(false) }

    // Type dropdown control
    var typeExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        dragHandle        = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Title ──────────────────────────────────────────────────────
            Text(
                text       = if (isEditing) {
                    if (isHoursMode) "Edit Hours" else "Edit Days"
                } else {
                    if (isHoursMode) "Add Hours" else "Add Days"
                },
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // ── Description field ──────────────────────────────────────────
            OutlinedTextField(
                value         = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                label         = { Text("Description") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
            )

            // ── Amount field ───────────────────────────────────────────────
            OutlinedTextField(
                value         = draft.amountText,
                onValueChange = { onDraftChange(draft.copy(amountText = it)) },
                label         = { Text(if (isHoursMode) "Total Hours" else "Total Days") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            // ── Leave type dropdown ────────────────────────────────────────
            ExposedDropdownMenuBox(
                expanded        = typeExpanded,
                onExpandedChange = { typeExpanded = !typeExpanded },
                modifier        = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value         = draft.type,
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Type") },
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier      = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded        = typeExpanded,
                    onDismissRequest = { typeExpanded = false },
                ) {
                    HOLIDAY_CATEGORIES.forEach { category ->
                        DropdownMenuItem(
                            text    = { Text(category) },
                            onClick = {
                                onDraftChange(draft.copy(type = category))
                                typeExpanded = false
                            },
                        )
                    }
                }
            }

            // ── Start date ─────────────────────────────────────────────────
            OutlinedTextField(
                value         = shortDateFmt.format(draft.startDate),
                onValueChange = {},
                readOnly      = true,
                label         = { Text("Start Date") },
                modifier      = Modifier
                    .fillMaxWidth()
                    .clickable { showStartPicker = true },
                enabled       = false,
            )

            // ── End date ───────────────────────────────────────────────────
            OutlinedTextField(
                value         = shortDateFmt.format(draft.endDate),
                onValueChange = {},
                readOnly      = true,
                label         = { Text("End Date") },
                modifier      = Modifier
                    .fillMaxWidth()
                    .clickable { showEndPicker = true },
                enabled       = false,
            )

            // ── Action buttons ─────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick  = onSave,
                    modifier = Modifier.weight(1f),
                    enabled  = draft.amountText.toDoubleOrNull() != null,
                ) {
                    Text("Save")
                }
            }
        }
    }

    // ── Start date picker dialog ───────────────────────────────────────────
    if (showStartPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = draft.startDate.time,
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = pickerState.selectedDateMillis
                    if (ms != null) {
                        val newStart = Date(ms)
                        // Auto-advance end date if start moves past it (mirrors iOS)
                        val newEnd   = if (draft.endDate.before(newStart)) newStart else draft.endDate
                        onDraftChange(draft.copy(startDate = newStart, endDate = newEnd))
                    }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    // ── End date picker dialog ─────────────────────────────────────────────
    if (showEndPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis  = draft.endDate.time,
            selectableDates            = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= draft.startDate.time
            },
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = pickerState.selectedDateMillis
                    if (ms != null) onDraftChange(draft.copy(endDate = Date(ms)))
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
