package com.workdiary.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workdiary.app.data.storage.PrefsKeys
import com.workdiary.app.ui.theme.SuccessGreen
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
//  Settings Screen entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Top-level Settings screen composable.
 *
 * Hosts all eight settings sections:
 * 1. Profile Management
 * 2. Appearance
 * 3. Share Data (Export / Import)
 * 4. Duty Board PDFs
 * 5. Notifications
 * 6. Calendar Configuration
 * 7. Data Management (destructive resets)
 * 8. Support Development
 *
 * All user actions are delegated to [SettingsViewModel]. Runtime side-effects
 * (permission requests, PDF picker launch) are triggered via [LaunchedEffect].
 *
 * @param onNavigateBack Callback invoked when the back arrow is tapped.
 * @param viewModel      Hilt-injected [SettingsViewModel]; defaults to the nearest
 *                       hilt-view-model entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // True when the POST_NOTIFICATIONS permission is already granted (or not required on < API 33).
    val hasNotificationPermission = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ── Launchers ──────────────────────────────────────────────────────────

    // Tracks which PDF type (MF / SAT / SUN) is pending upload so the single
    // launcher can route the result to the correct file.
    var pendingPdfType by remember { mutableStateOf("") }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { viewModel.uploadPdf(it, pendingPdfType) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    // Trigger the permission dialog whenever the ViewModel asks for it.
    LaunchedEffect(uiState.requestNotificationPermission) {
        if (uiState.requestNotificationPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ── Scaffold ───────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor         = MaterialTheme.colorScheme.surface,
                    titleContentColor      = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ── 1. Profile Management ──────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                ProfileManagementSection(
                    activeProfile    = uiState.activeProfile,
                    profileNameUser1 = uiState.profileNameUser1,
                    profileNameUser2 = uiState.profileNameUser2,
                    onProfileChange  = viewModel::setActiveProfile,
                    onNameSave       = { name -> viewModel.setProfileName(uiState.activeProfile, name) },
                )
            }

            // ── 2. Appearance ──────────────────────────────────────────────
            item {
                AppearanceSection(
                    isDarkMode   = uiState.isDarkMode,
                    isHoursMode  = uiState.isHoursMode,
                    onDarkMode   = viewModel::setDarkMode,
                    onHoursMode  = viewModel::setHoursMode,
                )
            }

            // ── 3. Share Data ──────────────────────────────────────────────
            item {
                ShareDataSection(
                    onExport = viewModel::exportData,
                    onImport = viewModel::importFromClipboard,
                )
            }

            // ── 4. Duty Board PDFs ─────────────────────────────────────────
            item {
                DutyBoardPdfSection(
                    pdfState  = uiState.pdfState,
                    onUpload  = { type ->
                        pendingPdfType = type
                        pdfPickerLauncher.launch("application/pdf")
                    },
                    onDelete  = viewModel::deletePdf,
                )
            }

            // ── 5. Notifications ───────────────────────────────────────────
            item {
                NotificationsSection(
                    enabled             = uiState.notificationsEnabled,
                    notificationSeconds = uiState.notificationTimeSeconds,
                    onToggle            = { enabled ->
                        viewModel.setNotificationsEnabled(
                            enabled       = enabled,
                            hasPermission = hasNotificationPermission,
                        )
                    },
                    onShowTimePicker    = { viewModel.showDialog(SettingsDialog.TimePicker) },
                )
            }

            // ── 6. Calendar Configuration ──────────────────────────────────
            item {
                CalendarConfigSection(
                    startOnMonday   = uiState.startOnMonday,
                    onStartOnMonday = viewModel::setStartOnMonday,
                )
            }

            // ── 7. Data Management ─────────────────────────────────────────
            item {
                DataManagementSection(
                    onResetAllowances = { viewModel.showDialog(SettingsDialog.ResetAllowances) },
                    onResetBooked     = { viewModel.showDialog(SettingsDialog.ResetBooked) },
                    onResetCalendar   = { viewModel.showDialog(SettingsDialog.ResetCalendar) },
                    onResetAll        = { viewModel.showDialog(SettingsDialog.ResetAll) },
                )
            }

            // ── 8. Support Development ─────────────────────────────────────
            item {
                SupportDevelopmentSection()
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────

    when (uiState.activeDialog) {
        SettingsDialog.ResetAllowances -> ConfirmationDialog(
            title   = "Reset Allowances",
            message = "This will clear all annual, hourly, and carried-over allowances for the active profile. This action cannot be undone.",
            onConfirm = viewModel::confirmResetAllowances,
            onDismiss = viewModel::dismissDialog,
        )

        SettingsDialog.ResetBooked -> ConfirmationDialog(
            title   = "Reset Booked Leave",
            message = "All booked holidays for the active profile will be permanently deleted.",
            onConfirm = viewModel::confirmResetBooked,
            onDismiss = viewModel::dismissDialog,
        )

        SettingsDialog.ResetCalendar -> ConfirmationDialog(
            title   = "Reset Calendar Data",
            message = "All day notes and special-day overrides will be permanently deleted.",
            onConfirm = viewModel::confirmResetCalendar,
            onDismiss = viewModel::dismissDialog,
        )

        SettingsDialog.ResetAll -> ConfirmationDialog(
            title   = "Reset All Data",
            message = "Everything will be cleared — notes, holidays, allowances, and custom days across all profiles. This cannot be undone.",
            confirmLabel = "Delete Everything",
            onConfirm = viewModel::confirmResetAll,
            onDismiss = viewModel::dismissDialog,
        )

        SettingsDialog.ExportSuccess -> InfoDialog(
            title   = "Export Successful",
            message = "Your data has been copied to the clipboard. Paste it somewhere safe to back it up.",
            onDismiss = viewModel::dismissDialog,
        )

        SettingsDialog.ImportSuccess -> InfoDialog(
            title   = "Import Successful",
            message = "Your duty notes and booked holidays have been restored.",
            onDismiss = viewModel::dismissDialog,
        )

        SettingsDialog.TimePicker -> TimePickerDialog(
            notificationTimeSeconds = uiState.notificationTimeSeconds,
            onConfirm = { seconds ->
                viewModel.setNotificationTime(seconds)
                viewModel.dismissDialog()
            },
            onDismiss = viewModel::dismissDialog,
        )

        null -> Unit
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Section: 1 — Profile Management
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileManagementSection(
    activeProfile: String,
    profileNameUser1: String,
    profileNameUser2: String,
    onProfileChange: (String) -> Unit,
    onNameSave: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val currentName  = if (activeProfile == PrefsKeys.PROFILE_USER1) profileNameUser1 else profileNameUser2
    var nameInput by rememberSaveable(activeProfile, currentName) { mutableStateOf(currentName) }

    SettingsSection(title = "Profile Management") {

        // Segmented profile picker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(PrefsKeys.PROFILE_USER1 to profileNameUser1,
                       PrefsKeys.PROFILE_USER2 to profileNameUser2)
                    .forEachIndexed { index, (id, label) ->
                        SegmentedButton(
                            selected = activeProfile == id,
                            onClick  = { onProfileChange(id) },
                            shape    = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            label    = { Text(label) },
                        )
                    }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color    = MaterialTheme.colorScheme.outlineVariant,
        )

        // Rename text field
        OutlinedTextField(
            value         = nameInput,
            onValueChange = { nameInput = it },
            label         = { Text("Rename active profile") },
            singleLine    = true,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onNameSave(nameInput)
                focusManager.clearFocus()
            }),
            trailingIcon = {
                if (nameInput.isNotBlank() && nameInput != currentName) {
                    TextButton(onClick = {
                        onNameSave(nameInput)
                        focusManager.clearFocus()
                    }) { Text("Save") }
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Section: 2 — Appearance
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppearanceSection(
    isDarkMode: Boolean,
    isHoursMode: Boolean,
    onDarkMode: (Boolean) -> Unit,
    onHoursMode: (Boolean) -> Unit,
) {
    SettingsSection(title = "Appearance") {
        SwitchRow(label = "Dark Mode", checked = isDarkMode, onCheckedChange = onDarkMode)
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color    = MaterialTheme.colorScheme.outlineVariant,
        )
        SwitchRow(
            label          = "Track Leave in Hours",
            checked        = isHoursMode,
            onCheckedChange = onHoursMode,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Section: 3 — Share Data
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShareDataSection(
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    SettingsSection(title = "Share Data") {
        ClickableRow(label = "Export — Copy to Clipboard", onClick = onExport)
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color    = MaterialTheme.colorScheme.outlineVariant,
        )
        ClickableRow(label = "Import from Clipboard", onClick = onImport)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Section: 4 — Duty Board PDFs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DutyBoardPdfSection(
    pdfState: PdfExistenceState,
    onUpload: (type: String) -> Unit,
    onDelete: (type: String) -> Unit,
) {
    val entries = listOf(
        "MF"  to pdfState.mfExists,
        "SAT" to pdfState.satExists,
        "SUN" to pdfState.sunExists,
    )
    SettingsSection(title = "Duty Board PDFs") {
        entries.forEachIndexed { index, (type, exists) ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color    = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            PdfRow(
                label    = "$type Roster PDF",
                exists   = exists,
                onUpload = { onUpload(type) },
                onDelete = { onDelete(type) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Section: 5 — Notifications
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationsSection(
    enabled: Boolean,
    notificationSeconds: Long,
    onToggle: (Boolean) -> Unit,
    onShowTimePicker: () -> Unit,
) {
    SettingsSection(title = "Notifications") {
        SwitchRow(
            label          = "Enable Daily Reminders",
            checked        = enabled,
            onCheckedChange = onToggle,
        )
        if (enabled) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color    = MaterialTheme.colorScheme.outlineVariant,
            )
            ClickableRow(
                label    = "Reminder Time",
                trailing = {
                    Text(
                        text  = formatTime(notificationSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = onShowTimePicker,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Section: 6 — Calendar Configuration
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CalendarConfigSection(
    startOnMonday: Boolean,
    onStartOnMonday: (Boolean) -> Unit,
) {
    SettingsSection(title = "Calendar Configuration") {
        SwitchRow(
            label          = "Start Week on Monday",
            checked        = startOnMonday,
            onCheckedChange = onStartOnMonday,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Section: 7 — Data Management
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DataManagementSection(
    onResetAllowances: () -> Unit,
    onResetBooked: () -> Unit,
    onResetCalendar: () -> Unit,
    onResetAll: () -> Unit,
) {
    val actions = listOf(
        "Reset Allowances"   to onResetAllowances,
        "Reset Booked Leave" to onResetBooked,
        "Reset Calendar Data" to onResetCalendar,
        "Reset All Data"     to onResetAll,
    )
    SettingsSection(title = "Data Management") {
        actions.forEachIndexed { index, (label, action) ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color    = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            TextButton(
                onClick  = action,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors   = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(
                    text      = label,
                    modifier  = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                    style     = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Section: 8 — Support Development
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SupportDevelopmentSection() {
    val uriHandler = LocalUriHandler.current
    SettingsSection(title = "Support Development") {
        ClickableRow(
            label   = "☕  Buy Me a Coffee",
            onClick = { uriHandler.openUri("https://buymeacoffee.com/workdiary") },
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color    = MaterialTheme.colorScheme.outlineVariant,
        )
        ClickableRow(
            label   = "💙  Donate via PayPal",
            onClick = { uriHandler.openUri("https://paypal.me/workdiary") },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Reusable composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A titled card that groups related settings rows.
 *
 * @param title   Section heading displayed in uppercase above the card.
 * @param content Composable rows to render inside the card.
 */
@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text     = title.uppercase(Locale.getDefault()),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(content = content)
        }
    }
}

/**
 * A row with a label on the left and a [Switch] on the right.
 */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment      = Alignment.CenterVertically,
        horizontalArrangement  = Arrangement.SpaceBetween,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * A tappable row that optionally shows a [trailing] composable on the right.
 */
@Composable
private fun ClickableRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * A row for a single Duty Board PDF type showing its upload/delete controls.
 *
 * @param label    Display label (e.g. `"MF Roster PDF"`).
 * @param exists   Whether the file currently exists in `filesDir`.
 * @param onUpload Invoked when the user taps "Upload".
 * @param onDelete Invoked when the user taps the delete icon.
 */
@Composable
private fun PdfRow(
    label: String,
    exists: Boolean,
    onUpload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (exists) {
            Icon(
                imageVector        = Icons.Default.CheckCircle,
                contentDescription = "File uploaded",
                tint               = SuccessGreen,
                modifier           = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = "Delete PDF",
                    tint               = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            OutlinedButton(
                onClick = onUpload,
            ) {
                Icon(
                    imageVector        = Icons.Default.Upload,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Upload")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Dialogs
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Confirmation dialog for destructive actions.
 *
 * @param title        Dialog title.
 * @param message      Descriptive warning shown to the user.
 * @param confirmLabel Label of the confirm button (defaults to `"Confirm"`).
 * @param onConfirm    Callback invoked on confirmation.
 * @param onDismiss    Callback invoked on cancel/dismiss.
 */
@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(title) },
        text             = { Text(message) },
        confirmButton    = {
            TextButton(
                onClick = onConfirm,
                colors  = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Informational dialog used for Export/Import success messages.
 *
 * @param title     Dialog title.
 * @param message   Body text.
 * @param onDismiss Callback invoked when the user taps "OK" or dismisses.
 */
@Composable
private fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(title) },
        text             = { Text(message) },
        confirmButton    = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

/**
 * Full-screen-width Material 3 Time Picker wrapped in an [AlertDialog].
 *
 * @param notificationTimeSeconds Current stored time as seconds since midnight.
 * @param onConfirm               Invoked with the new time in seconds-since-midnight.
 * @param onDismiss               Invoked when the user cancels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    notificationTimeSeconds: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHour   = (notificationTimeSeconds / 3_600).toInt().coerceIn(0, 23)
    val initialMinute = ((notificationTimeSeconds % 3_600) / 60).toInt().coerceIn(0, 59)
    val state         = rememberTimePickerState(
        initialHour   = initialHour,
        initialMinute = initialMinute,
        is24Hour      = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Set Reminder Time") },
        text             = {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val seconds = (state.hour * 3_600L) + (state.minute * 60L)
                onConfirm(seconds)
            }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Formats seconds-since-midnight as `HH:mm` (24-hour).
 *
 * @param seconds Seconds since midnight, e.g. `32400` → `"09:00"`.
 */
private fun formatTime(seconds: Long): String {
    val hour   = (seconds / 3_600).toInt().coerceIn(0, 23)
    val minute = ((seconds % 3_600) / 60).toInt().coerceIn(0, 59)
    return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
}
