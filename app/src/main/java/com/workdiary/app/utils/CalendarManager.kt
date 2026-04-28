package com.workdiary.app.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.workdiary.app.data.models.Holiday
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android equivalent of iOS `CalendarManager.swift`.
 *
 * Inserts all-day calendar events via the Android [CalendarContract] provider.
 *
 * iOS mapping:
 * | iOS function                           | Android equivalent                      |
 * |----------------------------------------|-----------------------------------------|
 * | `addHolidayToCalendar(holiday,endDate)` | [addHolidayToCalendar]                  |
 * | `emoji(for type:)`                      | [emojiForType]                          |
 *
 * ## Permissions
 * The caller must have been granted [Manifest.permission.WRITE_CALENDAR] and
 * [Manifest.permission.READ_CALENDAR] before calling [addHolidayToCalendar].
 * Use [hasCalendarPermission] to gate the call and request permissions via
 * `ActivityResultContracts.RequestMultiplePermissions` at the call site.
 *
 * ## Usage
 * ```kotlin
 * if (calendarManager.hasCalendarPermission()) {
 *     val results = calendarManager.addHolidayToCalendar(holiday, endDate)
 *     // results: list of inserted event URIs (null entries indicate failed days)
 * }
 * ```
 */
@Singleton
class CalendarManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ──────────────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns `true` if both READ and WRITE calendar permissions are currently granted.
     */
    fun hasCalendarPermission(): Boolean {
        val read  = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read  == PackageManager.PERMISSION_GRANTED &&
               write == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Inserts one all-day calendar event for every day in the range
     * [[holiday].startDate .. [endDate]] (inclusive), identical to the iOS implementation
     * that loops through each day.
     *
     * The event title is: `"<emoji> <holiday.name>"`, e.g. `"🌴 Annual Leave"`.
     *
     * @param holiday  The [Holiday] whose [Holiday.startDate] defines the first day.
     * @param endDate  Last day of the leave block (inclusive).
     * @return A list, one entry per calendar day, of the inserted event ID (or `null` if
     *         that day's insert failed or permission was not granted at call time).
     */
    fun addHolidayToCalendar(holiday: Holiday, endDate: LocalDate): List<Long?> {
        if (!hasCalendarPermission()) return emptyList()

        val calendarId = primaryCalendarId() ?: return emptyList()
        val emoji      = emojiForType(holiday.type)
        val title      = "$emoji ${holiday.name}"
        val results    = mutableListOf<Long?>()

        var current = holiday.date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        while (!current.isAfter(endDate)) {
            val eventId = insertAllDayEvent(
                calendarId  = calendarId,
                title       = title,
                description = holiday.name,
                date        = current,
            )
            results += eventId
            current  = current.plusDays(1)
        }

        return results
    }

    /**
     * Maps a [LeaveType] to the matching emoji string, mirroring `CalendarManager.swift`'s
     * `emoji(for type:)` switch statement exactly.
     *
     * @param type The leave category.
     * @return Emoji character string, e.g. `"🌴"` for [LeaveType.Annual].
     */
    fun emojiForType(type: String): String = when (type) {
        "Annual Leave"  -> "🌴"
        "Allocated"     -> "📅"
        "Personal"      -> "🏖️"
        "Sick Leave"    -> "🤒"
        "Maternity"     -> "👶"
        "Paternity"     -> "👨‍👧"
        "Unpaid"        -> "💸"
        "Bank Holiday"  -> "🏦"
        "Lieu Day"      -> "🔄"
        else            -> "📝"
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the content-provider ID of the primary (writable) calendar account,
     * or `null` if no calendar is available.
     */
    private fun primaryCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val selection = "(${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?)"
        val selArgs   = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selArgs,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                return cursor.getLong(idIdx)
            }
        }
        return null
    }

    /**
     * Writes a single all-day event to the CalendarContract provider for [date].
     *
     * All-day events are stored as UTC midnight-to-midnight, per the CalendarContract spec:
     * https://developer.android.com/reference/android/provider/CalendarContract.Events
     *
     * @param calendarId Content-provider calendar row ID.
     * @param title      Event title string.
     * @param description Optional description / notes text.
     * @param date       The [LocalDate] to create the event on.
     * @return The inserted row ID from the content provider, or `null` on failure.
     */
    private fun insertAllDayEvent(
        calendarId: Long,
        title: String,
        description: String,
        date: LocalDate,
    ): Long? {
        // All-day events: dtstart = UTC midnight of date, dtend = UTC midnight of date+1
        val utcZone    = ZoneId.of("UTC")
        val dtStart    = date.atStartOfDay(utcZone).toInstant().toEpochMilli()
        val dtEnd      = date.plusDays(1).atStartOfDay(utcZone).toInstant().toEpochMilli()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID,     calendarId)
            put(CalendarContract.Events.TITLE,           title)
            put(CalendarContract.Events.DESCRIPTION,     description)
            put(CalendarContract.Events.DTSTART,         dtStart)
            put(CalendarContract.Events.DTEND,           dtEnd)
            put(CalendarContract.Events.ALL_DAY,         1)
            put(CalendarContract.Events.EVENT_TIMEZONE,  TimeZone.getTimeZone("UTC").id)
            put(CalendarContract.Events.STATUS,
                CalendarContract.Events.STATUS_CONFIRMED)
            put(CalendarContract.Events.HAS_ALARM,       0)
        }

        return try {
            val uri = context.contentResolver.insert(
                CalendarContract.Events.CONTENT_URI, values,
            )
            uri?.lastPathSegment?.toLongOrNull()
        } catch (e: SecurityException) {
            // Permission revoked between check and insert — silently skip.
            null
        }
    }
}
