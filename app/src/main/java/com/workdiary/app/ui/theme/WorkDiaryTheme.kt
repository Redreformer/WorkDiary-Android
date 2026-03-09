package com.workdiary.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────
//  Palette — Work Diary visual identity
//  Professional shift-work diary: navy / teal / white
// ─────────────────────────────────────────────

/** Deep navy — primary brand colour, used for app bars and key actions. */
val Navy900 = Color(0xFF0D1B2A)
val Navy800 = Color(0xFF1B2E45)
val Navy700 = Color(0xFF22395A)
val Navy600 = Color(0xFF2B4872)

/** Teal — accent/secondary, interactive highlights, FABs. */
val Teal400 = Color(0xFF2BBFBF)
val Teal300 = Color(0xFF50D0D0)
val Teal200 = Color(0xFF7DDEDE)
val Teal100 = Color(0xFFB2EDED)

/** Neutral surfaces */
val Slate100 = Color(0xFFEDF1F5)
val Slate200 = Color(0xFFD6DFE8)
val Slate700 = Color(0xFF3D4F63)
val Slate900 = Color(0xFF1A2535)

/** Semantic colours */
val ErrorRed = Color(0xFFCF6679)
val SuccessGreen = Color(0xFF4CAF7D)
val WarnAmber = Color(0xFFF5A623)

// ─────────────────────────────────────────────
//  Dark colour scheme  (default — matches iOS isDarkMode = true default)
// ─────────────────────────────────────────────
private val DarkColors = darkColorScheme(
    primary          = Teal300,
    onPrimary        = Navy900,
    primaryContainer = Navy700,
    onPrimaryContainer = Teal100,

    secondary        = Teal200,
    onSecondary      = Navy800,
    secondaryContainer = Navy600,
    onSecondaryContainer = Teal100,

    tertiary         = WarnAmber,
    onTertiary       = Navy900,

    background       = Navy900,
    onBackground     = Slate100,

    surface          = Navy800,
    onSurface        = Slate100,
    surfaceVariant   = Navy700,
    onSurfaceVariant = Slate200,

    outline          = Slate700,

    error            = ErrorRed,
    onError          = Color.White,
    errorContainer   = Color(0xFF5C2A35),
    onErrorContainer = Color(0xFFF4B8C1),
)

// ─────────────────────────────────────────────
//  Light colour scheme
// ─────────────────────────────────────────────
private val LightColors = lightColorScheme(
    primary          = Navy700,
    onPrimary        = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Navy900,

    secondary        = Teal400,
    onSecondary      = Color.White,
    secondaryContainer = Teal100,
    onSecondaryContainer = Navy800,

    tertiary         = WarnAmber,
    onTertiary       = Color.White,

    background       = Slate100,
    onBackground     = Navy900,

    surface          = Color.White,
    onSurface        = Navy900,
    surfaceVariant   = Slate200,
    onSurfaceVariant = Slate700,

    outline          = Slate700,

    error            = ErrorRed,
    onError          = Color.White,
    errorContainer   = Color(0xFFFFDADC),
    onErrorContainer = Color(0xFF410010),
)

/**
 * Root Material 3 theme for Work Diary.
 *
 * Automatically selects [DarkColors] or [LightColors] based on [darkTheme]. The caller can
 * override with the user preference stored in DataStore (wired through a ViewModel), defaulting
 * to the system setting via [isSystemInDarkTheme].
 *
 * @param darkTheme Whether to apply the dark colour scheme. Defaults to the system setting.
 * @param content   The composable content rendered inside this theme.
 */
@Composable
fun WorkDiaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = WorkDiaryTypography,
        content     = content,
    )
}
