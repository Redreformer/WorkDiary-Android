package com.workdiary.app.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Schedules and cancels the WorkDiary daily reminder alarm via [AlarmManager].
 *
 * ## Scheduling strategy
 * We use [AlarmManager.setInexactRepeating] with [AlarmManager.INTERVAL_DAY], which is
 * battery-efficient and does not require the `SCHEDULE_EXACT_ALARM` or
 * `USE_EXACT_ALARM` manifest permissions. The first fire is set to the next
 * wall-clock occurrence of the requested time; subsequent fires repeat every 24 hours.
 *
 * If the app needs exact per-minute precision (e.g. for paid tiers), swap to
 * `setExactAndAllowWhileIdle` + reschedule inside [ReminderReceiver].
 *
 * ## Manifest requirements
 * Register [ReminderReceiver] in `AndroidManifest.xml`:
 * ```xml
 * <receiver
 *     android:name=".utils.ReminderReceiver"
 *     android:exported="false" />
 * ```
 */
object NotificationScheduler {

    /** Unique request code used for all [PendingIntent] operations. */
    private const val REQUEST_CODE = 0xD1A4_9001.toInt()

    /**
     * Schedules (or reschedules) a daily repeating alarm to fire at [timeSeconds]
     * seconds after midnight each day.
     *
     * - If the computed time for **today** has already passed, the alarm is set for
     *   the same time **tomorrow**.
     * - Calling this while an alarm is already set silently replaces it
     *   ([PendingIntent.FLAG_UPDATE_CURRENT]).
     *
     * @param context     Android [Context] (application context preferred).
     * @param timeSeconds Seconds since midnight, e.g. `32400` = 09:00.
     */
    fun schedule(context: Context, timeSeconds: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

        val hour   = (timeSeconds / 3_600).toInt().coerceIn(0, 23)
        val minute = ((timeSeconds % 3_600) / 60).toInt().coerceIn(0, 59)

        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE,      minute)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
            // Advance to tomorrow if the time has already passed today.
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            trigger.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            buildIntent(context),
        )
    }

    /**
     * Cancels the previously scheduled daily reminder alarm, if any.
     *
     * Safe to call even if no alarm has been scheduled.
     *
     * @param context Android [Context] (application context preferred).
     */
    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(buildIntent(context))
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds the [PendingIntent] that triggers [ReminderReceiver].
     *
     * Uses [PendingIntent.FLAG_UPDATE_CURRENT] so that repeated calls overwrite
     * an existing pending intent rather than create duplicates.
     */
    private fun buildIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
