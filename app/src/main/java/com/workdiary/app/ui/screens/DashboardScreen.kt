package com.workdiary.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ─────────────────────────────────────────────────────────────────────────────
//  Screen entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Balance / Dashboard screen — the Android equivalent of iOS `BalanceView`.
 *
 * Displays:
 * - A circular arc chart showing remaining or used allowance (swipeable).
 * - Three stat boxes: yearly allowance, carried-over, and lieu balance.
 * - Bottom sheets for lieu management and allowance editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Balance", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding      = PaddingValues(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                // ── Circular chart with swipeable centre ──────────────────
                BalanceChartSection(
                    uiState   = uiState,
                    modifier  = Modifier.padding(top = 24.dp),
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                // ── Three stat boxes ──────────────────────────────────────
                StatBoxRow(
                    uiState   = uiState,
                    onEditYearly      = { viewModel.showSheet(DashboardSheet.EditAllowance) },
                    onEditCarriedOver = { viewModel.showSheet(DashboardSheet.EditCarriedOver) },
                    onEditLieu        = { viewModel.showSheet(DashboardSheet.Lieu) },
                    modifier  = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    // ── Bottom sheets ─────────────────────────────────────────────────────
    when (uiState.activeSheet) {
        DashboardSheet.Lieu -> {
            LieuSheet(
                uiState   = uiState,
                onAddLieu = { viewModel.addLieuEarned(it) },
                onDelete  = { viewModel.deleteLieu(it) },
                onDismiss = { viewModel.dismissSheet() },
            )
        }
        DashboardSheet.EditAllowance -> {
            AllowanceEditSheet(
                title     = if (uiState.isHoursMode) "Yearly Hours" else "Yearly Days",
                initial   = uiState.totalYearly,
                onSave    = { viewModel.saveYearlyAllowance(it) },
                onDismiss = { viewModel.dismissSheet() },
            )
        }
        DashboardSheet.EditCarriedOver -> {
            AllowanceEditSheet(
                title     = "Carried Over",
                initial   = uiState.carriedOver,
                onSave    = { viewModel.saveCarriedOver(it) },
                onDismiss = { viewModel.dismissSheet() },
            )
        }
        null -> { /* nothing */ }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Circular chart section
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Circular arc chart with a swipeable centre panel showing either remaining or used.
 * Mirrors the iOS ZStack of Circle strokes + TabView combination.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BalanceChartSection(
    uiState:  DashboardUiState,
    modifier: Modifier = Modifier,
) {
    val pagerState  = rememberPagerState(pageCount = { 2 })
    val selectedTab = pagerState.currentPage

    val total = uiState.totalYearly + uiState.carriedOver + uiState.lieuAccrued
    val fraction = if (total > 0.0) {
        if (selectedTab == 0) (uiState.allowanceRemaining / total).coerceIn(0.0, 1.0)
        else                  (uiState.daysTaken          / total).coerceIn(0.0, 1.0)
    } else 0.0

    val arcColor  = if (selectedTab == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error

    val animatedFraction by animateFloatAsState(
        targetValue  = fraction.toFloat(),
        animationSpec = tween(durationMillis = 600),
        label        = "arcFraction",
    )

    Box(
        modifier        = modifier.size(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Background track + animated arc drawn in Canvas
        CircularArc(
            fraction    = animatedFraction,
            trackColor  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            arcColor    = arcColor,
            strokeWidth = 14.dp,
            modifier    = Modifier.fillMaxSize(),
        )

        // Swipeable centre content
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.size(200.dp),
        ) { page ->
            Column(
                modifier            = Modifier.fillMaxSize(),
                verticalArrangement   = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (page == 0) {
                    Text(
                        text       = "%.1f".format(uiState.allowanceRemaining),
                        fontSize   = 58.sp,
                        fontWeight = FontWeight.Black,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text  = if (uiState.isHoursMode) "Hours Remaining" else "Days Remaining",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text       = "%.1f".format(uiState.daysTaken),
                        fontSize   = 58.sp,
                        fontWeight = FontWeight.Black,
                        color      = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text  = if (uiState.isHoursMode) "Hours Used" else "Days Used",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Canvas-based circular arc
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A circular track + arc drawn purely with [Canvas] / [drawBehind].
 *
 * - Arc starts at 12 o'clock (−90°) and sweeps clockwise.
 * - [fraction] 0.0 → no arc, 1.0 → full circle.
 */
@Composable
fun CircularArc(
    fraction:    Float,
    trackColor:  Color,
    arcColor:    Color,
    strokeWidth: Dp,
    modifier:    Modifier = Modifier,
) {
    Box(
        modifier = modifier.drawBehind {
            val stroke    = strokeWidth.toPx()
            val inset     = stroke / 2f
            val arcRect   = Size(size.width - stroke, size.height - stroke)
            val topLeft   = Offset(inset, inset)

            // Background track
            drawArc(
                color       = trackColor,
                startAngle  = 0f,
                sweepAngle  = 360f,
                useCenter   = false,
                topLeft     = topLeft,
                size        = arcRect,
                style       = Stroke(width = stroke),
            )

            // Foreground arc
            if (fraction > 0f) {
                drawArc(
                    color       = arcColor,
                    startAngle  = -90f,
                    sweepAngle  = 360f * fraction,
                    useCenter   = false,
                    topLeft     = topLeft,
                    size        = arcRect,
                    style       = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Stat box row
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Row of three tappable stat cards: yearly allowance, carried-over, lieu.
 * Mirrors iOS `HStack` of `StatBox` views.
 */
@Composable
private fun StatBoxRow(
    uiState: DashboardUiState,
    onEditYearly:      () -> Unit,
    onEditCarriedOver: () -> Unit,
    onEditLieu:        () -> Unit,
    modifier:          Modifier = Modifier,
) {
    Row(
        modifier            = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatBox(
            label    = "Yearly allowance",
            value    = uiState.totalYearly,
            modifier = Modifier.weight(1f),
            onClick  = onEditYearly,
        )
        StatBox(
            label    = "Carried over",
            value    = uiState.carriedOver,
            modifier = Modifier.weight(1f),
            onClick  = onEditCarriedOver,
        )
        StatBox(
            label    = "Lieu",
            value    = uiState.lieuAccrued,
            modifier = Modifier.weight(1f),
            onClick  = onEditLieu,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  StatBox composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Single tappable stat card. Mirrors iOS `StatBox` view.
 */
@Composable
fun StatBox(
    label:    String,
    value:    Double,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick  = onClick,
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.outlinedCardColors(),
        border   = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text      = label,
                style     = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines  = 2,
                maxLines  = 2,
            )
            Text(
                text       = "%.1f".format(value),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Lieu management sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bottom sheet for adding and reviewing lieu-day entries.
 * Mirrors iOS `QuickLieuView`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LieuSheet(
    uiState:  DashboardUiState,
    onAddLieu: (Double) -> Unit,
    onDelete:  (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var amount by remember { mutableDoubleStateOf(1.0) }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        dragHandle        = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement  = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text       = "Lieu Management",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(bottom = 4.dp),
            )

            // ── Add Lieu Earned ───────────────────────────────────────────
            Text(
                text  = "Add Lieu Earned",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier            = Modifier.fillMaxWidth(),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { if (amount > 0.5) amount -= 0.5 }) {
                    Text("−", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text  = "%.1f %s".format(amount, if (uiState.isHoursMode) "Hours" else "Days"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = { amount += 0.5 }) {
                    Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick  = {
                    onAddLieu(amount)
                    amount = 1.0
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text("Add to Balance", fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── History ───────────────────────────────────────────────────
            Text(
                text  = "History",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            if (uiState.lieuHistory.isEmpty()) {
                Text(
                    text  = "No entries yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                Column {
                    uiState.lieuHistory.forEachIndexed { index, item ->
                        ListItem(
                            headlineContent = {
                                Text("Lieu Earned", fontWeight = FontWeight.Bold)
                            },
                            leadingContent = { Text("🎟️", fontSize = 24.sp) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text  = "+%.1f".format(kotlin.math.abs(item.amount)),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontWeight = FontWeight.Black,
                                    )
                                    IconButton(onClick = { onDelete(index) }) {
                                        Icon(
                                            imageVector        = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint               = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                        )
                        if (index < uiState.lieuHistory.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Allowance edit sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A compact bottom sheet for editing a numeric allowance value.
 * Mirrors iOS `QuickAllowanceEditView`.
 *
 * @param title   Label shown above the text field.
 * @param initial Starting value pre-filled in the field.
 * @param onSave  Called with the new value when the user taps "Save Changes".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowanceEditSheet(
    title:    String,
    initial:  Double,
    onSave:   (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textValue by remember { mutableStateOf(if (initial == 0.0) "" else "%.1f".format(initial)) }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        dragHandle        = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text       = "Update $title",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value         = textValue,
                onValueChange = { textValue = it },
                textStyle     = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    textAlign  = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
                modifier      = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val parsed = textValue.toDoubleOrNull() ?: return@Button
                    onSave(parsed)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick  = onDismiss,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                Text("Cancel")
            }
        }
    }
}
