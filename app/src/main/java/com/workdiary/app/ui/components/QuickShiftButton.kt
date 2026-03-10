package com.workdiary.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.workdiary.app.ui.screens.SettingsViewModel

/**
 * A compact full-width button with a **vertical** icon-above-label layout, mirroring
 * `QuickShiftButton.swift` in the iOS app.
 *
 * ## iOS → Android mapping
 * | iOS (SwiftUI)                              | Android (Compose)                      |
 * |--------------------------------------------|----------------------------------------|
 * | `VStack { Image(systemName:); Text }`      | [Column] with icon above [Text]        |
 * | `.frame(maxWidth: .infinity, height: 60)`  | [fillMaxWidth] + [height] = 60 dp      |
 * | Solid colour background                    | [Button] containerColor = [color]      |
 * | White foreground (regardless of dark mode) | [contentColor] = [Color.White]         |
 * | Rounded rect + shadow                      | [Button] with RoundedCornerShape(14dp) |
 * | `@AppStorage("isDarkMode") var isDarkMode` | Observed from [SettingsViewModel]      |
 *
 * > **Note on `isDarkMode`:** The iOS implementation reads `isDarkMode` only to decide the
 * > shadow colour. On Android the elevation system handles shadow rendering automatically,
 * > so the [isDarkMode] parameter is accepted for API parity but used only for shadow tinting.
 *
 * @param label             Short text label displayed below the icon.
 * @param color             Solid background colour of the button.
 * @param icon              Material [ImageVector] displayed above [label].
 * @param onClick           Lambda invoked on tap.
 * @param modifier          Optional [Modifier] passed to the [Button].
 * @param accessibilityLabel  Accessibility description; defaults to [label].
 * @param isDarkMode        Whether the UI is currently in dark mode (from
 *                          [SettingsViewModel.isDarkTheme] or [PreferencesRepository]).
 *                          Used for shadow alpha calculations.
 */
@Composable
fun QuickShiftButton(
    label: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = label,
    isDarkMode: Boolean = true,
) {
    // Shadow elevation: visually stronger in dark mode to compensate for low contrast.
    val elevation = if (isDarkMode) 8.dp else 4.dp

    Button(
        onClick   = onClick,
        modifier  = modifier
            .fillMaxWidth()
            .height(60.dp)
            .semantics { contentDescription = accessibilityLabel },
        shape     = RoundedCornerShape(14.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = elevation,
            pressedElevation = 2.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor   = Color.White,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null, // covered by outer semantics
                modifier           = Modifier.size(18.dp),
                tint               = Color.White,
            )
            Text(
                text       = label,
                color      = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 11.sp,
                maxLines   = 1,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel-connected variant
//
//  Reads isDarkMode from SettingsViewModel so callers in the main UI don't have
//  to thread the preference manually.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel-connected wrapper for [QuickShiftButton] that automatically reads
 * `isDarkMode` from [SettingsViewModel].
 *
 * Use this at call sites that don't already have a handle to the theme preference.
 *
 * @see QuickShiftButton for parameter documentation.
 */
@Composable
fun QuickShiftButtonConnected(
    label: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = label,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkMode = uiState.isDarkMode

    QuickShiftButton(
        label              = label,
        color              = color,
        icon               = icon,
        onClick            = onClick,
        modifier           = modifier,
        accessibilityLabel = accessibilityLabel,
        isDarkMode         = isDarkMode,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun QuickShiftButtonDarkPreview() {
    QuickShiftButton(
        label      = "Early",
        color      = Color(0xFFFF9F0A),
        icon       = Icons.Default.WbSunny,
        onClick    = {},
        isDarkMode = true,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF2F2F7)
@Composable
private fun QuickShiftButtonLightPreview() {
    QuickShiftButton(
        label      = "Late",
        color      = Color(0xFF0A84FF),
        icon       = Icons.Default.WbSunny,
        onClick    = {},
        isDarkMode = false,
    )
}
