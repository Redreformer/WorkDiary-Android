package com.workdiary.app.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.workdiary.app.MainActivity

/**
 * [BroadcastReceiver] that fires when the daily reminder alarm from [NotificationScheduler]
 * triggers.
 *
 * Displays a notification reminding the user to log their work diary entry for the day.
 *
 * ## Manifest registration
 * Add the following inside `<application>` in `AndroidManifest.xml`:
 * ```xml
 * <receiver
 *     android:name=".utils.ReminderReceiver"
 *     android:exported="false" />
 * ```
 *
 * ## Notification icon
 * The receiver references `R.drawable.ic_stat_notification` as the small icon.
 * Create this as a 24 dp white vector in `res/drawable/` via
 * **Android Studio → New → Vector Asset → Category: Notification**.
 * Until the drawable is added, you can temporarily substitute
 * `android.R.drawable.ic_popup_reminder`.
 *
 * ## Permission
 * On Android 13+ (API 33+) the notification will be silently suppressed unless the user has
 * granted `POST_NOTIFICATIONS`. [com.workdiary.app.ui.screens.SettingsViewModel] handles
 * requesting this permission before enabling reminders.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return

        // On Android 13+ verify permission at fire-time; suppress silently if revoked.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        val tapPendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // TODO: replace with R.drawable.ic_stat_notification once the drawable is added.
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Work Diary Reminder")
            .setContentText("Don't forget to log today's duty entry.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Open Work Diary to record today's duty and keep your shift log up to date.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Creates the [NotificationChannel] (Android 8.0+ / API 26+) if it does not already exist.
     * Calling this on every broadcast is safe — [NotificationManager.createNotificationChannel]
     * is idempotent.
     */
    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Daily prompt to log your work diary entry."
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        /** Intent action checked inside [onReceive] to guard against spurious broadcasts. */
        const val ACTION_FIRE = "com.workdiary.app.ACTION_DAILY_REMINDER"

        /** Notification channel ID — matches the string registered in [ensureChannel]. */
        const val CHANNEL_ID = "workdiary_daily_reminders"

        /** Stable notification ID used for display and cancellation. */
        const val NOTIFICATION_ID = 7_001
    }
}
