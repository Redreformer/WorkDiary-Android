package com.workdiary.app.data.models

import android.content.SharedPreferences
import com.workdiary.app.data.storage.PrefsKeys
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.Date
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Custom serialisers for types that kotlinx.serialization can't handle natively
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Serialises [UUID] as its canonical string representation
 * (e.g. `"550e8400-e29b-41d4-a716-446655440000"`).
 */
object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): UUID =
        UUID.fromString(decoder.decodeString())
}

/**
 * Serialises [Date] as a [Long] Unix timestamp (epoch milliseconds).
 *
 * This matches the JSON output of `JSONEncoder` / `JSONDecoder` used on iOS, which
 * encodes `Date` as a `Double` of seconds since the Unix epoch. Here we use
 * **milliseconds** for native Android compatibility — a one-off migration may be needed
 * if importing data produced by the iOS app (multiply iOS value × 1000).
 */
object DateSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Date) =
        encoder.encodeLong(value.time)

    override fun deserialize(decoder: Decoder): Date =
        Date(decoder.decodeLong())
}

/**
 * Nullable variant of [DateSerializer].
 */
object NullableDateSerializer : KSerializer<Date?> {
    private val inner = DateSerializer

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullableDate", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Date?) {
        if (value == null) encoder.encodeLong(-1L)
        else encoder.encodeLong(value.time)
    }

    override fun deserialize(decoder: Decoder): Date? {
        val v = decoder.decodeLong()
        return if (v == -1L) null else Date(v)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Holiday model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Core data model representing a single booked leave entry.
 *
 * Mirrors the iOS `Holiday` struct (Identifiable + Codable).
 *
 * ### Leave type values
 * `"Annual Leave"` | `"Sick Leave"` | `"Personal"` | `"Allocated"` | `"Lieu Day"`
 *
 * @property id      Unique identifier for this entry.
 * @property name    User-provided description / label for the leave.
 * @property amount  Duration of leave in days **or** hours, depending on the profile's
 *                   `isHoursMode` flag (see [PrefsKeys.isHours]).
 * @property date    Start date of the leave period.
 * @property endDate Optional end date. If `null`, the leave is single-day.
 * @property type    Leave category string (see values above).
 */
@Serializable
data class Holiday(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),

    val name: String,

    val amount: Double,

    @Serializable(with = DateSerializer::class)
    val date: Date,

    @Serializable(with = NullableDateSerializer::class)
    val endDate: Date? = null,

    val type: String,
) {

    /**
     * The effective end date of the leave period.
     *
     * Returns [endDate] if set, otherwise falls back to [date] (single-day leave).
     * Mirrors the iOS computed property `actualEndDate`.
     */
    val actualEndDate: Date
        get() = endDate ?: date

    // ─────────────────────────────────────────────────────────────────────────
    // Companion — static encode / decode helpers (matches iOS static methods)
    // ─────────────────────────────────────────────────────────────────────────

    companion object {

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /**
         * Decodes a JSON string into a list of [Holiday] objects.
         *
         * Returns an empty list if [string] is blank or cannot be parsed.
         * Mirrors `Holiday.decode(from:)` in Swift.
         */
        fun decode(from: String): List<Holiday> {
            if (from.isBlank()) return emptyList()
            return runCatching {
                json.decodeFromString(ListSerializer(serializer()), from)
            }.getOrElse { emptyList() }
        }

        /**
         * Encodes a list of [Holiday] objects to a JSON string.
         *
         * Returns an empty string on failure.
         * Mirrors `Holiday.encode(_:)` in Swift.
         */
        fun encode(holidays: List<Holiday>): String =
            runCatching {
                json.encodeToString(ListSerializer(serializer()), holidays)
            }.getOrElse { "" }

        // ── UK bank holiday / special day calculator ──────────────────────

        /** SharedPreferences key for user-defined custom special-day overrides. */
        const val SPECIAL_DAYS_KEY = PrefsKeys.SAVED_SPECIAL_DAYS_DATA

        /**
         * Returns the name of any special day (bank holiday, celebration, etc.) that
         * falls on [date], or `null` if there is none.
         *
         * 1. Checks user-saved custom overrides stored in [prefs] first.
         * 2. Falls back to the built-in UK bank holiday calculator.
         *
         * Mirrors `Holiday.getSpecialDay(for:)` in Swift.
         *
         * @param date  The calendar date to query.
         * @param prefs A [SharedPreferences] instance containing the optional
         *              [SPECIAL_DAYS_KEY] JSON payload.
         */
        fun getSpecialDay(date: Date, prefs: SharedPreferences): String? {
            // 1. User overrides
            val savedString = prefs.getString(SPECIAL_DAYS_KEY, "") ?: ""
            val savedHolidays = decode(savedString)
            val cal = Calendar.getInstance()
            savedHolidays.forEach { h ->
                if (isSameDay(h.date, date, cal)) return h.name
            }

            // 2. Built-in UK calculator
            return calculateUKHoliday(date)
        }

        /**
         * Calculates standard UK bank holidays and notable dates for [date].
         *
         * Mirrors `Holiday.calculateUKHoliday(for:)` in Swift — full anonymous
         * Gregorian Easter algorithm included.
         *
         * @return The name of the special day, or `null` if none.
         */
        fun calculateUKHoliday(date: Date): String? {
            val cal = Calendar.getInstance().apply { time = date }
            val year  = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1  // Calendar.MONTH is 0-based
            val day   = cal.get(Calendar.DAY_OF_MONTH)

            // ── Fixed dates ───────────────────────────────────────────────
            if (month == 1  && day == 1)  return "New Year's Day"
            if (month == 2  && day == 14) return "Valentine's Day ❤️"
            if (month == 12 && day == 25) return "Christmas Day 🎄"
            if (month == 12 && day == 26) return "Boxing Day"
            if (month == 10 && day == 31) return "Halloween 🎃"
            if (month == 11 && day == 5)  return "Bonfire Night 🔥"
            if (month == 12 && day == 31) return "New Year's Eve 🎆"

            // ── Moving dates ──────────────────────────────────────────────

            val easterSunday = getEasterSunday(year)
            val easterCal    = Calendar.getInstance().apply { time = easterSunday }

            if (isSameDay(date, easterSunday, Calendar.getInstance())) return "Easter Sunday 🥚"

            val goodFriday = (easterCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }.time
            if (isSameDay(date, goodFriday, Calendar.getInstance())) return "Good Friday"

            val easterMonday = (easterCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.time
            if (isSameDay(date, easterMonday, Calendar.getInstance())) return "Easter Monday"

            // Mother's Day (UK) — 3 weeks before Easter Sunday
            val mothersDay = (easterCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -21) }.time
            if (isSameDay(date, mothersDay, Calendar.getInstance())) return "Mother's Day"

            // Early May Bank Holiday — 1st Monday in May
            if (isNthWeekday(1, Calendar.MONDAY, month = 5, year = year, date = date))
                return "Early May Bank Holiday"

            // Spring Bank Holiday — last Monday in May
            if (isLastWeekday(Calendar.MONDAY, month = 5, year = year, date = date))
                return "Spring Bank Holiday"

            // Summer Bank Holiday — last Monday in August
            if (isLastWeekday(Calendar.MONDAY, month = 8, year = year, date = date))
                return "Summer Bank Holiday"

            // Father's Day — 3rd Sunday in June
            if (isNthWeekday(3, Calendar.SUNDAY, month = 6, year = year, date = date))
                return "Father's Day"

            // Remembrance Sunday — 2nd Sunday in November
            if (isNthWeekday(2, Calendar.SUNDAY, month = 11, year = year, date = date))
                return "Remembrance Sunday 🌺"

            return null
        }

        // ── Mathematical helpers ──────────────────────────────────────────

        /**
         * Returns `true` if [date] is the [n]th occurrence of [weekday]
         * (a [Calendar] weekday constant) within [month] of [year].
         *
         * Mirrors `Holiday.isNthWeekday(_:_:month:year:date:)` in Swift.
         *
         * @param n        Ordinal occurrence (1 = first, 2 = second, …).
         * @param weekday  A [Calendar] weekday constant, e.g. [Calendar.MONDAY].
         * @param month    1-based month number (January = 1).
         * @param year     The full year.
         * @param date     The date to test.
         */
        fun isNthWeekday(n: Int, weekday: Int, month: Int, year: Int, date: Date): Boolean {
            val cal = Calendar.getInstance().apply { time = date }
            if (cal.get(Calendar.MONTH) + 1 != month) return false
            if (cal.get(Calendar.YEAR) != year) return false
            if (cal.get(Calendar.DAY_OF_WEEK) != weekday) return false
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            return (dayOfMonth - 1) / 7 == (n - 1)
        }

        /**
         * Returns `true` if [date] is the *last* occurrence of [weekday]
         * (a [Calendar] weekday constant) within [month] of [year].
         *
         * Mirrors `Holiday.isLastWeekday(_:month:year:date:)` in Swift.
         *
         * @param weekday  A [Calendar] weekday constant, e.g. [Calendar.MONDAY].
         * @param month    1-based month number (January = 1).
         * @param year     The full year.
         * @param date     The date to test.
         */
        fun isLastWeekday(weekday: Int, month: Int, year: Int, date: Date): Boolean {
            val cal = Calendar.getInstance().apply { time = date }
            if (cal.get(Calendar.MONTH) + 1 != month) return false
            if (cal.get(Calendar.YEAR) != year) return false
            if (cal.get(Calendar.DAY_OF_WEEK) != weekday) return false
            // If adding 7 days crosses into the next month, this is the last occurrence
            val nextWeek = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
            return nextWeek.get(Calendar.MONTH) + 1 != month
        }

        /**
         * Calculates Easter Sunday for the given [year] using the anonymous
         * Gregorian algorithm.
         *
         * This is an exact port of `Holiday.getEasterSunday(year:)` in Swift.
         *
         * @param year The full four-digit year.
         * @return     A [Date] representing Easter Sunday at midnight local time.
         */
        fun getEasterSunday(year: Int): Date {
            val a = year % 19
            val b = year / 100
            val c = year % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val month = (h + l - 7 * m + 114) / 31
            val day   = ((h + l - 7 * m + 114) % 31) + 1

            return Calendar.getInstance().apply {
                set(year, month - 1, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
        }

        // ── Internal helpers ──────────────────────────────────────────────

        /**
         * Returns `true` if [a] and [b] fall on the same calendar day.
         *
         * @param reusableCal A [Calendar] instance to reuse (avoids allocation in hot paths).
         */
        private fun isSameDay(a: Date, b: Date, reusableCal: Calendar): Boolean {
            reusableCal.time = a
            val ay = reusableCal.get(Calendar.YEAR)
            val am = reusableCal.get(Calendar.DAY_OF_YEAR)
            reusableCal.time = b
            return ay == reusableCal.get(Calendar.YEAR) && am == reusableCal.get(Calendar.DAY_OF_YEAR)
        }
    }
}
