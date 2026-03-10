package com.workdiary.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplaneTicket
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * A full-width horizontal shift action button that mirrors the visual style of
 * `ShiftButton.swift` in the iOS app.
 *
 * ## iOS → Android mapping
 * | iOS (SwiftUI)                              | Android (Compose)                    |
 * |--------------------------------------------|--------------------------------------|
 * | `Label(label, systemImage: icon)`          | [icon] `ImageVector` + [label] text  |
 * | `.background(color.opacity(0.15))`         | [Surface] fill at 15 % alpha         |
 * | `.border(color.opacity(0.60), cornerRadius)` | [Surface] border at 60 % alpha     |
 * | `.foregroundColor(color)`                  | [tint] applied to icon + text        |
 * | `.accessibilityLabel(...)`                 | `Modifier.semantics { contentDescription }` |
 *
 * ## SF Symbol → Material Icon mapping (used by caller)
 * ```
 * "sun.max"          → Icons.Default.WbSunny
 * "moon.fill"        → Icons.Default.NightlightRound
 * "briefcase"        → Icons.Default.Work
 * "house"            → Icons.Default.Home
 * "bed.double"       → Icons.Default.Hotel
 * "star"             → Icons.Default.Star
 * "cross.case"       → Icons.Default.LocalHospital
 * "airplane"         → Icons.Default.AirplaneTicket
 * "beach.umbrella"   → Icons.Default.BeachAccess
 * "clock"            → Icons.Default.Schedule
 * "person"           → Icons.Default.Person
 * "calendar"         → Icons.Default.CalendarToday
 * "pencil"           → Icons.Default.Edit
 * ```
 *
 * @param label             Button text label.
 * @param tint              Accent colour applied to both icon and label text.
 * @param icon              Material [ImageVector] to display to the left of [label].
 * @param accessibilityLabel  Accessibility description; defaults to [label] when blank.
 * @param onClick           Lambda invoked on tap.
 * @param modifier          Optional [Modifier] applied to the outer [Surface].
 */
@Composable
fun ShiftButton(
    label: String,
    tint: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = label,
) {
    Surface(
        onClick      = onClick,
        modifier     = modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityLabel },
        shape        = RoundedCornerShape(12.dp),
        color        = tint.copy(alpha = 0.15f),
        border       = BorderStroke(
            width = 1.dp,
            color = tint.copy(alpha = 0.60f),
        ),
    ) {
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                imageVector         = icon,
                contentDescription  = null, // covered by outer semantics
                tint                = tint,
                modifier            = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text       = label,
                color      = tint,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SF Symbol → ImageVector helpers
//  Callers can use these directly instead of remembering the mapping.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Convenience object that maps iOS SF Symbol name strings to their closest
 * Material Icons equivalents. Use when converting iOS shift-type icon names
 * received from stored data or configuration.
 *
 * ```kotlin
 * val icon = SfSymbolToMaterialIcon.from("sun.max") ?: Icons.Default.WbSunny
 * ```
 */
object SfSymbolToMaterialIcon {

    /** Returns the Material [ImageVector] for a given SF Symbol [name], or `null` if unknown. */
    fun from(name: String): ImageVector? = mapping[name]

    private val mapping: Map<String, ImageVector> = mapOf(
        "sun.max"            to Icons.Default.WbSunny,
        "sun.max.fill"       to Icons.Default.WbSunny,
        "moon.fill"          to Icons.Default.NightlightRound,
        "moon"               to Icons.Default.NightlightRound,
        "briefcase"          to Icons.Default.Work,
        "briefcase.fill"     to Icons.Default.Work,
        "house"              to Icons.Default.Home,
        "house.fill"         to Icons.Default.Home,
        "bed.double"         to Icons.Default.Hotel,
        "bed.double.fill"    to Icons.Default.Hotel,
        "star"               to Icons.Default.Star,
        "star.fill"          to Icons.Default.Star,
        "cross.case"         to Icons.Default.LocalHospital,
        "cross.case.fill"    to Icons.Default.LocalHospital,
        "airplane"           to Icons.Default.AirplaneTicket,
        "beach.umbrella"     to Icons.Default.BeachAccess,
        "clock"              to Icons.Default.Schedule,
        "clock.fill"         to Icons.Default.Schedule,
        "person"             to Icons.Default.Person,
        "person.fill"        to Icons.Default.Person,
        "calendar"           to Icons.Default.CalendarToday,
        "pencil"             to Icons.Default.Edit,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun ShiftButtonPreview() {
    ShiftButton(
        label   = "Early Shift",
        tint    = Color(0xFFFFD60A),
        icon    = Icons.Default.WbSunny,
        onClick = {},
    )
}
