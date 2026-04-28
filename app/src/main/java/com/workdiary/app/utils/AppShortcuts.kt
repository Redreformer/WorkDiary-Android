package com.workdiary.app.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.workdiary.app.MainActivity
import com.workdiary.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android equivalent of `DutyIntents.swift` (Siri AppIntents).
 *
 * iOS had `GetDutyIntent` and `GetTomorrowDutyIntent` — Siri voice shortcuts that read
 * dayNotesData and returned the duty number / sign-on / sign-off times verbally.
 *
 * Android has no direct equivalent of Siri's voice-response intents. The closest analogy is:
 * - **App Shortcuts** (static + dynamic): launcher long-press menu entries that deep-link into
 *   the app. Defined in `res/xml/shortcuts.xml` (static) and registered here (dynamic).
 * - **Google App Actions** (`actions.xml`): allows "Hey Google, open Today's Duty in Work Diary"
 *   via Google Assistant — a separate integration requiring Google Play publication.
 *
 * This object handles the dynamic shortcut registration equivalent of `AppShortcutProvider`
 * using [ShortcutManagerCompat], which back-fills to API 25.
 *
 * ## Static shortcuts
 * Defined in `res/xml/shortcuts.xml` and declared in `AndroidManifest.xml` via:
 * ```xml
 * <meta-data
 *     android:name="android.app.shortcuts"
 *     android:resource="@xml/shortcuts" />
 * ```
 *
 * ## Dynamic shortcuts
 * Call [registerDynamicShortcuts] once from `Application.onCreate()` or `MainActivity.onCreate()`
 * to ensure shortcuts survive package updates (static shortcuts are reset on update).
 *
 * ## Deep-link extras
 * Both shortcuts launch [MainActivity] with the intent extra
 * `EXTRA_NAVIGATE_TO = "calendar"` so the app can navigate directly to the Calendar screen.
 * Set [EXTRA_DUTY_DAY] to `"today"` or `"tomorrow"` so CalendarScreen can pre-select the day.
 *
 * iOS shortcut phrase equivalents (for reference):
 * - "What's my duty today?"     → [SHORTCUT_ID_TODAY]
 * - "What's my duty tomorrow?"  → [SHORTCUT_ID_TOMORROW]
 */
@Singleton
class AppShortcuts @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ──────────────────────────────────────────────────────────────────────
    //  Constants
    // ──────────────────────────────────────────────────────────────────────

    companion object {
        /** Shortcut ID for the "Today's Duty" shortcut — matches shortcuts.xml. */
        const val SHORTCUT_ID_TODAY    = "shortcut_today_duty"

        /** Shortcut ID for the "Tomorrow's Duty" shortcut — matches shortcuts.xml. */
        const val SHORTCUT_ID_TOMORROW = "shortcut_tomorrow_duty"

        /** Intent extra key used to signal the Calendar screen which day to highlight. */
        const val EXTRA_DUTY_DAY       = "extra_duty_day"

        /** Intent extra value: navigate to today's entry in the Calendar screen. */
        const val EXTRA_DUTY_DAY_TODAY    = "today"

        /** Intent extra value: navigate to tomorrow's entry in the Calendar screen. */
        const val EXTRA_DUTY_DAY_TOMORROW = "tomorrow"

        /**
         * Intent extra key that tells [MainActivity] which root destination to open.
         * Value should be the route string from `Screen` (e.g. `"calendar"`).
         */
        const val EXTRA_NAVIGATE_TO = "navigate_to"
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Registers (or refreshes) the two dynamic shortcuts for "Today's Duty" and
     * "Tomorrow's Duty".
     *
     * Dynamic shortcuts survive app updates, unlike static shortcuts which are reset.
     * Safe to call multiple times — [ShortcutManagerCompat.pushDynamicShortcut] is idempotent
     * for the same shortcut ID.
     *
     * Should be called from `Application.onCreate()` or `MainActivity.onCreate()`.
     */
    fun registerDynamicShortcuts() {
        val todayShortcut    = buildTodayShortcut()
        val tomorrowShortcut = buildTomorrowShortcut()

        ShortcutManagerCompat.pushDynamicShortcut(context, todayShortcut)
        ShortcutManagerCompat.pushDynamicShortcut(context, tomorrowShortcut)
    }

    /**
     * Removes all dynamic shortcuts registered by this app.
     *
     * Useful when the user logs out or resets the app.
     */
    fun removeAllDynamicShortcuts() {
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Shortcut builders
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Builds the "Today's Duty" [ShortcutInfoCompat].
     *
     * iOS equivalent: `GetDutyIntent` — "What's my duty today?"
     */
    private fun buildTodayShortcut(): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_NAVIGATE_TO, "calendar")
            putExtra(EXTRA_DUTY_DAY,    EXTRA_DUTY_DAY_TODAY)
            // Flags ensure MainActivity is brought to front, not stacked.
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID_TODAY)
            .setShortLabel(context.getString(R.string.shortcut_today_short))
            .setLongLabel(context.getString(R.string.shortcut_today_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_calendar))
            .setIntent(intent)
            .setRank(0) // appears first in launcher shortcut menu
            .build()
    }

    /**
     * Builds the "Tomorrow's Duty" [ShortcutInfoCompat].
     *
     * iOS equivalent: `GetTomorrowDutyIntent` — "What's my duty tomorrow?"
     */
    private fun buildTomorrowShortcut(): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_NAVIGATE_TO, "calendar")
            putExtra(EXTRA_DUTY_DAY,    EXTRA_DUTY_DAY_TOMORROW)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID_TOMORROW)
            .setShortLabel(context.getString(R.string.shortcut_tomorrow_short))
            .setLongLabel(context.getString(R.string.shortcut_tomorrow_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_calendar))
            .setIntent(intent)
            .setRank(1) // appears second in launcher shortcut menu
            .build()
    }
}
