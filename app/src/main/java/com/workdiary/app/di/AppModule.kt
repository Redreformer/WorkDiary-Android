package com.workdiary.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.workdiary.app.PreferencesRepository
import com.workdiary.app.utils.CalendarManager
import com.workdiary.app.utils.NotificationScheduler
import com.workdiary.app.utils.PDFManager
import com.workdiary.app.utils.PhotoManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Module-level DataStore extension property.
// Uses the same name ("workdiary_prefs") as the one inside PreferencesRepository
// so they resolve to the same on-disk file. The `preferencesDataStore` delegate
// guarantees a single instance per name per process via a static map.
private val Context.appDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "workdiary_prefs")

/**
 * Hilt [Module] that provides the application-scoped (singleton) dependencies.
 *
 * All bindings are installed in [SingletonComponent], matching the `@Singleton` scope.
 *
 * ## Dependency map
 * | Binding                   | Implementation class           | Notes                              |
 * |---------------------------|--------------------------------|------------------------------------|
 * | [DataStore]<[Preferences]> | `preferencesDataStore` delegate | Wraps Jetpack DataStore            |
 * | [PreferencesRepository]   | [PreferencesRepository]        | Wraps the DataStore                |
 * | [CalendarManager]         | [CalendarManager]              | CalendarContract all-day events    |
 * | [PhotoManager]            | [PhotoManager]                 | Camera / gallery photo helpers     |
 * | [PDFManager]              | [PDFManager]                   | Duty-board PDF parse + search      |
 * | [NotificationScheduler]   | [NotificationScheduler]        | WorkManager-based daily reminders  |
 *
 * ## iOS equivalent
 * There is no direct equivalent; iOS uses SwiftUI's `@EnvironmentObject` / `@StateObject`
 * for dependency injection at the view level. This module centralises the same role.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ──────────────────────────────────────────────────────────────────────
    //  DataStore
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Provides the app-wide [DataStore]<[Preferences]> instance.
     *
     * The `preferencesDataStore` Kotlin delegate guarantees exactly one instance is created
     * per process for the given name, so this binding is safe to provide here and use
     * directly via injection in components that need raw DataStore access.
     *
     * @param context Application [Context] injected by Hilt.
     * @return Singleton [DataStore] instance backed by `workdiary_prefs`.
     */
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.appDataStore

    // ──────────────────────────────────────────────────────────────────────
    //  PreferencesRepository
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Provides the [PreferencesRepository] singleton.
     *
     * Although [PreferencesRepository] has an `@Inject constructor`, providing it explicitly
     * here makes all application-level dependencies visible in one place and documents
     * the DataStore dependency clearly.
     *
     * iOS equivalent: all `@AppStorage` values scattered across SwiftUI views — centralised
     * here in a single observable repository.
     *
     * @param context Application [Context] injected by Hilt.
     * @return Singleton [PreferencesRepository].
     */
    @Provides
    @Singleton
    fun providePreferencesRepository(
        @ApplicationContext context: Context,
    ): PreferencesRepository = PreferencesRepository(context)

    // ──────────────────────────────────────────────────────────────────────
    //  CalendarManager
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Provides the [CalendarManager] singleton.
     *
     * iOS equivalent: `CalendarManager.swift` (uses `EventKit` / `EKEventStore`).
     * Android version uses [android.provider.CalendarContract] via [android.content.ContentResolver].
     *
     * @param context Application [Context] injected by Hilt.
     * @return Singleton [CalendarManager].
     */
    @Provides
    @Singleton
    fun provideCalendarManager(
        @ApplicationContext context: Context,
    ): CalendarManager = CalendarManager(context)

    // ──────────────────────────────────────────────────────────────────────
    //  PhotoManager
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Provides the [PhotoManager] singleton.
     *
     * Handles camera capture, gallery picker, and day-photo storage/retrieval.
     * iOS equivalent: the photo persistence logic embedded in `CalendarDayView` and
     * the `FileManager` helpers.
     *
     * @param context Application [Context] injected by Hilt.
     * @return Singleton [PhotoManager].
     */
    @Provides
    @Singleton
    fun providePhotoManager(
        @ApplicationContext context: Context,
    ): PhotoManager = PhotoManager(context)

    // ──────────────────────────────────────────────────────────────────────
    //  PDFManager
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Provides the [PDFManager] singleton.
     *
     * Handles PDF loading, text extraction, and duty-number lookup inside
     * imported duty-board PDFs. iOS equivalent: the `PDFKit` + Vision pipeline
     * used in `PDFManager.swift`.
     *
     * @param context Application [Context] injected by Hilt.
     * @return Singleton [PDFManager].
     */
    @Provides
    @Singleton
    fun providePDFManager(
        @ApplicationContext context: Context,
    ): PDFManager = PDFManager(context)

    // ──────────────────────────────────────────────────────────────────────
    //  NotificationScheduler
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Provides the [NotificationScheduler] singleton.
     *
     * Schedules and cancels daily duty-reminder notifications via WorkManager.
     * iOS equivalent: `UNUserNotificationCenter` scheduling in `NotificationManager.swift`.
     *
     * @param context Application [Context] injected by Hilt.
     * @return Singleton [NotificationScheduler].
     */
    @Provides
    @Singleton
    fun provideNotificationScheduler(
        @ApplicationContext context: Context,
    ): NotificationScheduler = NotificationScheduler(context)
}
